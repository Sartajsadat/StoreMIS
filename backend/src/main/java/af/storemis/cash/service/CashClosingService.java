package af.storemis.cash.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.cash.dto.CloseDayRequest;
import af.storemis.cash.dto.ClosingPreview;
import af.storemis.cash.dto.ClosingRow;
import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashClosing;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTransaction;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.repository.CashClosingRepository;
import af.storemis.cash.repository.CashTransactionRepository;
import af.storemis.cash.service.CashPostingService.CashSource;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
import af.storemis.user.service.UserDirectory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily cash closing: the Admin compares the expected balance of every account with the counted cash. A difference
 * is posted as a CLOSING_DIFFERENCE so the ledger matches the drawer, and the day is then locked against postings.
 */
@Service
public class CashClosingService {

    private final CashClosingRepository closings;
    private final CashAccountRepository accounts;
    private final CashTransactionRepository transactions;
    private final CashPostingService posting;
    private final CurrencyService currency;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public CashClosingService(CashClosingRepository closings, CashAccountRepository accounts, CashTransactionRepository transactions,
            CashPostingService posting, CurrencyService currency, UserDirectory users, AuditService audit, BusinessClock clock) {
        this.closings = closings;
        this.accounts = accounts;
        this.transactions = transactions;
        this.posting = posting;
        this.currency = currency;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClosingPreview preview(LocalDate date) {
        LocalDate day = date == null ? clock.today() : date;
        Optional<LocalDate> last = closings.lastClosedDate();
        Map<Long, CashClosing> closed = new LinkedHashMap<>();
        Map<Long, CashClosing> reopened = new LinkedHashMap<>();
        for (CashClosing c : closings.findByBusinessDateOrderByIdAsc(day)) {
            (CashClosing.CLOSED.equals(c.getStatus()) ? closed : reopened).put(c.getCashAccountId(), c);
        }
        boolean isClosed = !closed.isEmpty();

        List<Object[]> types = transactions.totalsByType(day);
        List<Object[]> byUsers = transactions.totalsByUser(day);
        Set<Long> userIds = new HashSet<>();
        for (Object[] row : byUsers) if (row[1] != null) userIds.add((Long) row[1]);
        for (CashClosing c : closed.values()) addUsers(userIds, c);
        for (CashClosing c : reopened.values()) addUsers(userIds, c);
        Map<Long, String> names = users.names(userIds);

        List<ClosingPreview.AccountDay> days = new ArrayList<>();
        for (CashAccount a : accounts.findAllByOrderByIdAsc()) {
            boolean hasActivity = types.stream().anyMatch(r -> a.getId().equals(r[0]));
            if (!a.isActive() && !hasActivity && !closed.containsKey(a.getId())) continue;
            BigDecimal opening = Money.amount(transactions.balanceBefore(a.getId(), day));
            BigDecimal in = Money.ZERO;
            BigDecimal out = Money.ZERO;
            CashClosing c = closed.get(a.getId());
            List<ClosingPreview.TypeTotal> typeTotals = new ArrayList<>();
            for (Object[] r : types) {
                if (!a.getId().equals(r[0])) continue;
                CashTxnType type = (CashTxnType) r[1];
                CashDirection dir = (CashDirection) r[2];
                BigDecimal sum = Money.amount((BigDecimal) r[3]);
                typeTotals.add(new ClosingPreview.TypeTotal(type, dir, sum, (Long) r[4]));
                if (dir == CashDirection.IN) in = in.add(sum); else out = out.add(sum);
            }
            typeTotals.sort(Comparator.comparing((ClosingPreview.TypeTotal t) -> t.direction()).thenComparing(t -> t.type()));
            // While the day is closed, show the figures that were counted against (before the difference was posted).
            BigDecimal shownIn = c != null ? c.getTotalIn() : in;
            BigDecimal shownOut = c != null ? c.getTotalOut() : out;
            BigDecimal expected = c != null ? c.getExpectedBalance() : opening.add(in).subtract(out);
            Map<Long, BigDecimal[]> perUser = new LinkedHashMap<>();
            for (Object[] r : byUsers) {
                if (!a.getId().equals(r[0])) continue;
                BigDecimal[] t = perUser.computeIfAbsent((Long) r[1], k -> new BigDecimal[] {Money.ZERO, Money.ZERO});
                if (r[2] == CashDirection.IN) t[0] = t[0].add((BigDecimal) r[3]); else t[1] = t[1].add((BigDecimal) r[3]);
            }
            List<ClosingPreview.UserTotal> userTotals = perUser.entrySet().stream()
                    .map(e -> new ClosingPreview.UserTotal(e.getKey(), names.get(e.getKey()), Money.amount(e.getValue()[0]), Money.amount(e.getValue()[1])))
                    .toList();
            CashClosing shown = c != null ? c : reopened.get(a.getId());
            days.add(new ClosingPreview.AccountDay(a.getId(), a.getName(), a.getCurrencyCode(), opening, shownIn, shownOut, expected, typeTotals, userTotals,
                    shown == null ? null : toClosing(shown, names)));
        }
        LocalDate lastDate = last.orElse(null);
        boolean canClose = !isClosed && !day.isAfter(clock.today()) && (lastDate == null || day.isAfter(lastDate));
        boolean canReopen = isClosed && day.equals(lastDate);
        return new ClosingPreview(day, lastDate, isClosed, canClose, canReopen, days);
    }

    @Transactional
    public ClosingPreview close(CloseDayRequest r) {
        LocalDate day = r.date();
        if (day.isAfter(clock.today())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "date"));
        posting.ensureDayOpen(day);
        ClosingPreview preview = preview(day);
        Map<Long, BigDecimal> counted = new LinkedHashMap<>();
        for (CloseDayRequest.Counted c : r.accounts()) counted.put(c.accountId(), Money.amount(c.countedBalance()));
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        Long by = SecurityUtils.currentUserId().orElse(null);
        List<Map<String, Object>> details = new ArrayList<>();
        for (ClosingPreview.AccountDay d : preview.accounts()) {
            BigDecimal count = counted.get(d.accountId());
            if (count == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "countedBalance", "accountId", d.accountId()));
            BigDecimal diff = count.subtract(d.expectedBalance());
            Long txnId = null;
            if (diff.signum() != 0) {
                if (notes == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "notes"));
                CashTransaction t = posting.postOn(day, d.accountId(), diff.signum() > 0 ? CashDirection.IN : CashDirection.OUT, diff.abs(),
                        CashTxnType.CLOSING_DIFFERENCE, currency.currentRate(d.currency()), new CashSource("CASH_CLOSING", null, day.toString()),
                        "Cash count difference " + day + ": " + notes);
                txnId = t.getId();
            }
            closings.save(new CashClosing(day, d.accountId(), d.openingBalance(), d.totalIn(), d.totalOut(), d.expectedBalance(), count, txnId, notes,
                    by, clock.now()));
            details.add(Map.of("account", d.accountName(), "expected", d.expectedBalance().toPlainString(), "counted", count.toPlainString(),
                    "difference", diff.toPlainString()));
        }
        closings.flush();
        audit.log(AuditEvent.of(AuditAction.CLOSE, AuditModule.CASH, "CashClosing", null, day.toString(), "Daily cash closing")
                .withDetails(Map.of("date", day.toString(), "accounts", details)));
        return preview(day);
    }

    @Transactional
    public ClosingPreview reopen(LocalDate day, String reason) {
        Optional<LocalDate> last = closings.lastClosedDate();
        if (last.isEmpty() || !last.get().equals(day)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("date", day.toString()));
        }
        Long by = SecurityUtils.currentUserId().orElse(null);
        for (CashClosing c : closings.findByBusinessDateAndStatus(day, CashClosing.CLOSED)) c.reopen(reason.trim(), by, clock.now());
        closings.flush();
        audit.log(AuditEvent.of(AuditAction.REOPEN, AuditModule.CASH, "CashClosing", null, day.toString(), "Cash day reopened")
                .withDetails(Map.of("date", day.toString(), "reason", reason)));
        return preview(day);
    }

    @Transactional(readOnly = true)
    public List<ClosingRow> history(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        List<CashClosing> rows = closings.findBetween(start, end);
        Map<Long, CashAccount> acc = accounts.findAll().stream().collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        Map<Long, String> names = users.names(rows.stream().map(CashClosing::getClosedBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream().map(c -> {
            CashAccount a = acc.get(c.getCashAccountId());
            return new ClosingRow(c.getId(), c.getBusinessDate(), a.getId(), a.getName(), a.getCurrencyCode(), c.getOpeningBalance(), c.getTotalIn(),
                    c.getTotalOut(), c.getExpectedBalance(), c.getCountedBalance(), c.getDifference(), c.getStatus(), c.getNotes(),
                    names.get(c.getClosedBy()), c.getClosedAt(), c.getReopenReason());
        }).toList();
    }

    private static void addUsers(Set<Long> ids, CashClosing c) {
        if (c.getClosedBy() != null) ids.add(c.getClosedBy());
        if (c.getReopenedBy() != null) ids.add(c.getReopenedBy());
    }

    private static ClosingPreview.Closing toClosing(CashClosing c, Map<Long, String> names) {
        return new ClosingPreview.Closing(c.getId(), c.getStatus(), c.getCountedBalance(), c.getDifference(), c.getNotes(), names.get(c.getClosedBy()),
                c.getClosedAt(), names.get(c.getReopenedBy()), c.getReopenedAt(), c.getReopenReason());
    }
}
