package af.storemis.expense.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.service.CashPostingService;
import af.storemis.cash.service.CashPostingService.CashSource;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
import af.storemis.expense.dto.ExpenseCategoryRequest;
import af.storemis.expense.dto.ExpenseCategoryResponse;
import af.storemis.expense.dto.ExpenseRequest;
import af.storemis.expense.dto.ExpenseResponse;
import af.storemis.expense.entity.Expense;
import af.storemis.expense.entity.ExpenseCategory;
import af.storemis.expense.repository.ExpenseCategoryRepository;
import af.storemis.expense.repository.ExpenseRepository;
import af.storemis.files.service.FileStorageService;
import af.storemis.files.service.FileStorageService.Area;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Operating expenses. Recording one takes the money out of the chosen cash account at once; voiding (Admin) puts it
 * back with a REVERSAL. Entry users see only the expenses they recorded.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;
    private final CashAccountRepository cashAccounts;
    private final CashPostingService cash;
    private final CurrencyService currency;
    private final DocumentNumberService numbers;
    private final FileStorageService files;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public ExpenseService(ExpenseRepository expenses, ExpenseCategoryRepository categories, CashAccountRepository cashAccounts, CashPostingService cash,
            CurrencyService currency, DocumentNumberService numbers, FileStorageService files, UserDirectory users, AuditService audit,
            BusinessClock clock) {
        this.expenses = expenses;
        this.categories = categories;
        this.cashAccounts = cashAccounts;
        this.cash = cash;
        this.currency = currency;
        this.numbers = numbers;
        this.files = files;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- categories

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> categories(Boolean active) {
        return categories.findAllByOrderByIdAsc().stream().filter(c -> active == null || c.isActive() == active).map(ExpenseCategoryResponse::from).toList();
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(ExpenseCategoryRequest r) {
        String name = r.name().trim();
        if (categories.existsByNameIgnoreCase(name, null)) throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "name"));
        ExpenseCategory c = new ExpenseCategory();
        c.setName(name);
        categories.save(c);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.EXPENSES, "ExpenseCategory", c.getId(), c.getName(), "Expense category created"));
        return ExpenseCategoryResponse.from(c);
    }

    @Transactional
    public ExpenseCategoryResponse updateCategory(Long id, ExpenseCategoryRequest r) {
        ExpenseCategory c = category(id);
        c.checkVersion(r.version());
        String name = r.name().trim();
        if (categories.existsByNameIgnoreCase(name, id)) throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "name"));
        Map<String, Object> details = Map.of("name", Map.of("from", c.getName(), "to", name));
        c.setName(name);
        categories.saveAndFlush(c);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.EXPENSES, "ExpenseCategory", c.getId(), c.getName(), "Expense category updated")
                .withDetails(details));
        return ExpenseCategoryResponse.from(c);
    }

    @Transactional
    public ExpenseCategoryResponse setCategoryActive(Long id, boolean active) {
        ExpenseCategory c = category(id);
        if (c.isActive() != active) {
            c.setActive(active);
            audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.EXPENSES, "ExpenseCategory", c.getId(),
                    c.getName(), active ? "Expense category activated" : "Expense category deactivated"));
        }
        return ExpenseCategoryResponse.from(c);
    }

    private ExpenseCategory category(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("ExpenseCategory", id));
    }

    // ---------------------------------------------------------------- expenses

    @Transactional
    public ExpenseResponse create(ExpenseRequest r) {
        if (r.clientRequestId() != null) {
            Optional<Expense> existing = expenses.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        ExpenseCategory c = category(r.categoryId());
        if (!c.isActive()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "categoryId"));
        CashAccount account = cash.requireAccount(r.cashAccountId(), null);
        LocalDate today = clock.today();
        LocalDate date = r.expenseDate() == null ? today : r.expenseDate();
        if (date.isAfter(today)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "expenseDate"));
        BigDecimal rate = currency.currentRate(account.getCurrencyCode());
        BigDecimal amount = Money.amount(r.amount());
        Expense e = expenses.save(new Expense(numbers.next(DocumentType.EXPENSE), r.clientRequestId(), c.getId(), date, today, r.description().trim(),
                amount, account.getCurrencyCode(), rate, account.getId(), clock.now(), SecurityUtils.currentUserId().orElse(null)));
        cash.post(account.getId(), CashDirection.OUT, amount, CashTxnType.EXPENSE, rate, new CashSource("EXPENSE", e.getId(), e.getExpenseNo()),
                c.getName() + ": " + e.getDescription());
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.EXPENSES, "Expense", e.getId(), e.getExpenseNo(), "Expense recorded")
                .withDetails(Map.of("category", c.getName(), "amount", amount.toPlainString(), "currency", e.getCurrencyCode())));
        return toResponse(e);
    }

    /** Admin: the money goes back into the same account. */
    @Transactional
    public ExpenseResponse voidExpense(Long id, String reason) {
        Expense e = expenses.lock(id).orElseThrow(() -> new NotFoundException("Expense", id));
        if (!Expense.POSTED.equals(e.getStatus())) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", e.getStatus()));
        cash.post(e.getCashAccountId(), CashDirection.IN, e.getAmount(), CashTxnType.REVERSAL, e.getExchangeRate(),
                new CashSource("EXPENSE", e.getId(), e.getExpenseNo()), "Void " + e.getExpenseNo() + ": " + reason.trim());
        e.voidExpense(reason.trim(), clock.now(), SecurityUtils.currentUserId().orElse(null));
        expenses.saveAndFlush(e);
        audit.log(AuditEvent.of(AuditAction.VOID, AuditModule.EXPENSES, "Expense", e.getId(), e.getExpenseNo(), "Expense voided")
                .withDetails(Map.of("reason", reason)));
        return toResponse(e);
    }

    @Transactional
    public ExpenseResponse uploadAttachment(Long id, MultipartFile file) {
        Expense e = expenses.lock(id).orElseThrow(() -> new NotFoundException("Expense", id));
        checkCanView(e);
        String stored = files.store(Area.ATTACHMENTS, file, FileStorageService.DOCUMENT_TYPES);
        String old = e.getAttachmentPath();
        e.setAttachmentPath(stored);
        expenses.saveAndFlush(e);
        if (old != null) files.delete(Area.ATTACHMENTS, old);
        audit.log(AuditEvent.of(AuditAction.UPLOAD, AuditModule.EXPENSES, "Expense", e.getId(), e.getExpenseNo(), "Expense receipt attached"));
        return toResponse(e);
    }

    /** The attached receipt — only for Admins and the user who recorded the expense. */
    @Transactional(readOnly = true)
    public Path attachment(Long id) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense", id));
        checkCanView(e);
        if (e.getAttachmentPath() == null) throw new NotFoundException("File", id);
        return files.resolve(Area.ATTACHMENTS, e.getAttachmentPath()).orElseThrow(() -> new NotFoundException("File", id));
    }

    public String contentType(Path p) {
        return files.contentType(p);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long id) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense", id));
        checkCanView(e);
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> search(String q, Long categoryId, Long cashAccountId, String status, LocalDate from, LocalDate to, Long userId,
            Pageable pageable) {
        boolean admin = SecurityUtils.currentPrincipal().map(p -> p.isAdmin()).orElse(false);
        Long onlyUser = admin ? userId : SecurityUtils.currentUserId().orElse(-1L);
        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (categoryId != null) ps.add(cb.equal(root.get("categoryId"), categoryId));
            if (cashAccountId != null) ps.add(cb.equal(root.get("cashAccountId"), cashAccountId));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            if (onlyUser != null) ps.add(cb.equal(root.get("createdBy"), onlyUser));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("expenseNo")), like), cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Expense> page = expenses.findAll(spec, pageable);
        Lookups l = lookups(page.getContent());
        return page.map(e -> toResponse(e, l));
    }

    /** Totals per currency for the filtered period (posted only), with the base-currency total. */
    @Transactional(readOnly = true)
    public Map<String, Object> totals(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
        BigDecimal base = Money.ZERO;
        for (Object[] row : expenses.totals(start, end)) {
            byCurrency.put((String) row[0], Money.amount((BigDecimal) row[1]));
            base = base.add(Money.amount((BigDecimal) row[2]));
        }
        return Map.of("from", start, "to", end, "byCurrency", byCurrency, "baseTotal", base, "baseCurrency", currency.baseCurrency());
    }

    private void checkCanView(Expense e) {
        boolean admin = SecurityUtils.currentPrincipal().map(p -> p.isAdmin()).orElse(false);
        Long me = SecurityUtils.currentUserId().orElse(null);
        if (!admin && !Objects.equals(me, e.getCreatedBy())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private record Lookups(Map<Long, ExpenseCategory> categories, Map<Long, CashAccount> accounts, Map<Long, String> users) {}

    private Lookups lookups(List<Expense> list) {
        Map<Long, ExpenseCategory> cats = categories.findAll().stream().collect(Collectors.toMap(ExpenseCategory::getId, Function.identity()));
        Map<Long, CashAccount> acc = cashAccounts.findAll().stream().collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        Set<Long> ids = new HashSet<>();
        for (Expense e : list) {
            if (e.getCreatedBy() != null) ids.add(e.getCreatedBy());
            if (e.getVoidedBy() != null) ids.add(e.getVoidedBy());
        }
        return new Lookups(cats, acc, users.names(ids));
    }

    private ExpenseResponse toResponse(Expense e) {
        return toResponse(e, lookups(List.of(e)));
    }

    private static ExpenseResponse toResponse(Expense e, Lookups l) {
        ExpenseCategory c = l.categories().get(e.getCategoryId());
        CashAccount a = l.accounts().get(e.getCashAccountId());
        return new ExpenseResponse(e.getId(), e.getExpenseNo(), c.getId(), c.getCode(), c.getName(), e.getExpenseDate(), e.getBusinessDate(),
                e.getDescription(), e.getAmount(), e.getCurrencyCode(), e.getExchangeRate(), a.getId(), a.getName(), e.getAttachmentPath() != null,
                e.getStatus(), e.getVoidReason(), e.getVoidedAt(), l.users().get(e.getVoidedBy()), e.getCreatedAt(), e.getCreatedBy(),
                l.users().get(e.getCreatedBy()));
    }
}
