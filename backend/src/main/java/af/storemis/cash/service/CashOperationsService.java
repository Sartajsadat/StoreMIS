package af.storemis.cash.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.cash.dto.CashTransactionResponse;
import af.storemis.cash.dto.CashTransferRequest;
import af.storemis.cash.dto.CashTransferResponse;
import af.storemis.cash.dto.OwnerCashRequest;
import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTransaction;
import af.storemis.cash.entity.CashTransfer;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.repository.CashTransferRepository;
import af.storemis.cash.service.CashPostingService.CashSource;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner deposits and withdrawals, same-currency transfers and currency exchanges (Admin only). */
@Service
public class CashOperationsService {

    private final CashAccountRepository accounts;
    private final CashTransferRepository transfers;
    private final CashPostingService posting;
    private final CashAccountService cashAccounts;
    private final CurrencyService currency;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public CashOperationsService(CashAccountRepository accounts, CashTransferRepository transfers, CashPostingService posting,
            CashAccountService cashAccounts, CurrencyService currency, DocumentNumberService numbers, UserDirectory users, AuditService audit,
            BusinessClock clock) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.posting = posting;
        this.cashAccounts = cashAccounts;
        this.currency = currency;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CashTransactionResponse ownerCash(OwnerCashRequest r, boolean deposit) {
        CashAccount a = posting.requireAccount(r.accountId(), null);
        CashTxnType type = deposit ? CashTxnType.OWNER_DEPOSIT : CashTxnType.OWNER_WITHDRAWAL;
        CashTransaction t = posting.post(a.getId(), deposit ? CashDirection.IN : CashDirection.OUT, r.amount(), type,
                currency.currentRate(a.getCurrencyCode()), new CashSource("OWNER", null, null), r.notes().trim());
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.CASH, "CashTransaction", t.getId(), t.getTxnNo(),
                deposit ? "Owner deposit" : "Owner withdrawal").withDetails(Map.of("account", a.getName(), "amount", t.getAmount().toPlainString())));
        return cashAccounts.toResponse(t);
    }

    @Transactional
    public CashTransferResponse transfer(CashTransferRequest r) {
        if (r.clientRequestId() != null) {
            Optional<CashTransfer> existing = transfers.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        if (r.fromAccountId().equals(r.toAccountId())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "toAccountId"));
        CashAccount from = posting.requireAccount(r.fromAccountId(), null);
        CashAccount to = posting.requireAccount(r.toAccountId(), null);
        boolean exchange = CashTransfer.EXCHANGE.equals(r.type());
        boolean sameCurrency = from.getCurrencyCode().equals(to.getCurrencyCode());
        if (exchange == sameCurrency) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH, Map.of("account", to.getName(), "currency", from.getCurrencyCode()));
        }
        BigDecimal fromAmount = Money.amount(r.fromAmount());
        BigDecimal toAmount = exchange ? Money.amount(required(r.toAmount())) : fromAmount;
        BigDecimal rate = exchange ? toAmount.divide(fromAmount, Money.RATE_SCALE, RoundingMode.HALF_UP) : BigDecimal.ONE;

        // Base-currency value per unit for each leg, so reports can value both sides at the deal's own rate.
        String base = currency.baseCurrency();
        BigDecimal fromBaseRate;
        BigDecimal toBaseRate;
        if (!exchange) {
            fromBaseRate = toBaseRate = currency.currentRate(from.getCurrencyCode());
        } else if (to.getCurrencyCode().equals(base)) {
            toBaseRate = BigDecimal.ONE;
            fromBaseRate = rate;
        } else if (from.getCurrencyCode().equals(base)) {
            fromBaseRate = BigDecimal.ONE;
            toBaseRate = fromAmount.divide(toAmount, Money.RATE_SCALE, RoundingMode.HALF_UP);
        } else {
            fromBaseRate = currency.currentRate(from.getCurrencyCode());
            toBaseRate = currency.currentRate(to.getCurrencyCode());
        }

        CashTransfer t = transfers.save(new CashTransfer(numbers.next(DocumentType.CASH_TRANSFER), r.clientRequestId(), r.type(), from.getId(),
                to.getId(), fromAmount, toAmount, rate, clock.today(), blankToNull(r.notes()), clock.now(), SecurityUtils.currentUserId().orElse(null)));
        CashSource source = new CashSource("CASH_TRANSFER", t.getId(), t.getTransferNo());
        String label = (exchange ? "Exchange " : "Transfer ") + from.getName() + " → " + to.getName();
        // Lock in id order to avoid deadlocks between two opposite transfers.
        if (from.getId() < to.getId()) {
            posting.post(from.getId(), CashDirection.OUT, fromAmount, exchange ? CashTxnType.EXCHANGE_OUT : CashTxnType.TRANSFER_OUT, fromBaseRate, source, label);
            posting.post(to.getId(), CashDirection.IN, toAmount, exchange ? CashTxnType.EXCHANGE_IN : CashTxnType.TRANSFER_IN, toBaseRate, source, label);
        } else {
            posting.post(to.getId(), CashDirection.IN, toAmount, exchange ? CashTxnType.EXCHANGE_IN : CashTxnType.TRANSFER_IN, toBaseRate, source, label);
            posting.post(from.getId(), CashDirection.OUT, fromAmount, exchange ? CashTxnType.EXCHANGE_OUT : CashTxnType.TRANSFER_OUT, fromBaseRate, source, label);
        }
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.CASH, "CashTransfer", t.getId(), t.getTransferNo(), exchange ? "Currency exchange" : "Cash transfer")
                .withDetails(Map.of("from", from.getName(), "to", to.getName(), "fromAmount", fromAmount.toPlainString(), "toAmount", toAmount.toPlainString())));
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public Page<CashTransferResponse> transfers(String type, Long accountId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<CashTransfer> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (type != null && !type.isBlank()) ps.add(cb.equal(root.get("transferType"), type));
            if (accountId != null) ps.add(cb.or(cb.equal(root.get("fromAccountId"), accountId), cb.equal(root.get("toAccountId"), accountId)));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<CashTransfer> page = transfers.findAll(spec, pageable);
        Map<Long, CashAccount> acc = accounts.findAll().stream().collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        Map<Long, String> names = users.names(page.getContent().stream().map(CashTransfer::getCreatedBy).toList());
        return page.map(t -> toResponse(t, acc, names));
    }

    private CashTransferResponse toResponse(CashTransfer t) {
        Map<Long, CashAccount> acc = accounts.findAllById(List.of(t.getFromAccountId(), t.getToAccountId())).stream()
                .collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        return toResponse(t, acc, users.names(t.getCreatedBy() == null ? List.of() : List.of(t.getCreatedBy())));
    }

    private static CashTransferResponse toResponse(CashTransfer t, Map<Long, CashAccount> acc, Map<Long, String> names) {
        CashAccount f = acc.get(t.getFromAccountId());
        CashAccount to = acc.get(t.getToAccountId());
        return new CashTransferResponse(t.getId(), t.getTransferNo(), t.getTransferType(), f.getId(), f.getName(), f.getCurrencyCode(), to.getId(),
                to.getName(), to.getCurrencyCode(), t.getFromAmount(), t.getToAmount(), t.getExchangeRate(), t.getBusinessDate(), t.getNotes(),
                t.getCreatedAt(), names.get(t.getCreatedBy()));
    }

    private static BigDecimal required(BigDecimal v) {
        if (v == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "toAmount"));
        return v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
