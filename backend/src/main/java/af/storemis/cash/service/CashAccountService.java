package af.storemis.cash.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.cash.dto.CashAccountResponse;
import af.storemis.cash.dto.CashTransactionResponse;
import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTransaction;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.repository.CashTransactionRepository;
import af.storemis.cash.service.CashPostingService.CashSource;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.security.SecurityUtils;
import af.storemis.currency.service.CurrencyService;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashAccountService {

    private final CashAccountRepository accounts;
    private final CashTransactionRepository transactions;
    private final CashPostingService posting;
    private final CurrencyService currency;
    private final UserDirectory users;
    private final AuditService audit;

    public CashAccountService(CashAccountRepository accounts, CashTransactionRepository transactions, CashPostingService posting,
            CurrencyService currency, UserDirectory users, AuditService audit) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.posting = posting;
        this.currency = currency;
        this.users = users;
        this.audit = audit;
    }

    /** Admins see balances; other users only get names and currencies (to pick an account when paying). */
    @Transactional(readOnly = true)
    public List<CashAccountResponse> list() {
        boolean admin = SecurityUtils.currentPrincipal().map(p -> p.isAdmin()).orElse(false);
        return accounts.findAllByOrderByIdAsc().stream().filter(a -> admin || a.isActive())
                .map(a -> CashAccountResponse.of(a, admin, admin && transactions.existsByCashAccountId(a.getId()))).toList();
    }

    /** The cash on hand when the system starts being used. Allowed once, before any other movement on the account. */
    @Transactional
    public CashAccountResponse setOpeningBalance(Long accountId, BigDecimal amount) {
        CashAccount a = accounts.lock(accountId).orElseThrow(() -> new NotFoundException("CashAccount", accountId));
        if (transactions.existsByCashAccountId(a.getId())) {
            throw new BusinessException(ErrorCode.OPENING_BALANCE_EXISTS, Map.of("account", a.getName()));
        }
        CashTransaction t = posting.post(a.getId(), CashDirection.IN, amount, CashTxnType.OPENING_BALANCE, currency.currentRate(a.getCurrencyCode()),
                new CashSource("CASH_ACCOUNT", a.getId(), null), "Opening balance");
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.CASH, "CashAccount", a.getId(), t.getTxnNo(), "Opening cash balance")
                .withDetails(Map.of("account", a.getName(), "amount", amount.toPlainString())));
        return CashAccountResponse.of(a, true, true);
    }

    @Transactional(readOnly = true)
    public Page<CashTransactionResponse> transactions(Long accountId, CashTxnType type, LocalDate from, LocalDate to, String q, Pageable pageable) {
        Specification<CashTransaction> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (accountId != null) ps.add(cb.equal(root.get("cashAccountId"), accountId));
            if (type != null) ps.add(cb.equal(root.get("txnType"), type));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("txnNo")), like), cb.like(cb.lower(cb.coalesce(root.get("referenceNo"), "")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<CashTransaction> page = transactions.findAll(spec, pageable);
        Map<Long, CashAccount> acc = accounts.findAll().stream().collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        Map<Long, String> names = users.names(page.getContent().stream().map(CashTransaction::getCreatedBy).toList());
        return page.map(t -> {
            CashAccount a = acc.get(t.getCashAccountId());
            return new CashTransactionResponse(t.getId(), t.getTxnNo(), a.getId(), a.getName(), a.getCurrencyCode(), t.getTxnType(), t.getDirection(),
                    t.getAmount(), t.getBalanceAfter(), t.getSourceType(), t.getSourceId(), t.getReferenceNo(), t.getBusinessDate(), t.getDescription(),
                    t.getCreatedAt(), names.get(t.getCreatedBy()));
        });
    }
}
