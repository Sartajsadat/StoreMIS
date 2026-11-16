package af.storemis.expense.controller;

import af.storemis.cash.dto.ReasonRequest;
import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.expense.dto.ExpenseCategoryRequest;
import af.storemis.expense.dto.ExpenseCategoryResponse;
import af.storemis.expense.dto.ExpenseRequest;
import af.storemis.expense.dto.ExpenseResponse;
import af.storemis.expense.service.ExpenseService;
import af.storemis.user.dto.StatusRequest;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("@authz.has('RECORD_EXPENSES')")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @GetMapping("/expense-categories")
    public ApiResponse<List<ExpenseCategoryResponse>> categories(@RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(expenses.categories(active));
    }

    @PostMapping("/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ExpenseCategoryResponse> createCategory(@Valid @RequestBody ExpenseCategoryRequest r) {
        return ApiResponse.ok(expenses.createCategory(r));
    }

    @PutMapping("/expense-categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ExpenseCategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody ExpenseCategoryRequest r) {
        return ApiResponse.ok(expenses.updateCategory(id, r));
    }

    @PatchMapping("/expense-categories/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ExpenseCategoryResponse> categoryStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest r) {
        return ApiResponse.ok(expenses.setCategoryActive(id, r.active()));
    }

    @GetMapping("/expenses")
    public ApiResponse<PageResponse<ExpenseResponse>> search(@RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long cashAccountId, @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(expenses.search(q, categoryId, cashAccountId, status, from, to, userId, pageable), x -> x));
    }

    @GetMapping("/expenses/totals")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> totals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(expenses.totals(from, to));
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest r) {
        return ApiResponse.ok(expenses.create(r));
    }

    @GetMapping("/expenses/{id}")
    public ApiResponse<ExpenseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(expenses.get(id));
    }

    @PostMapping("/expenses/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ExpenseResponse> voidExpense(@PathVariable Long id, @Valid @RequestBody ReasonRequest r) {
        return ApiResponse.ok(expenses.voidExpense(id, r.reason()));
    }

    @PostMapping("/expenses/{id}/attachment")
    public ApiResponse<ExpenseResponse> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(expenses.uploadAttachment(id, file));
    }

    @GetMapping("/expenses/{id}/attachment")
    public ResponseEntity<Resource> attachment(@PathVariable Long id) {
        Path p = expenses.attachment(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(expenses.contentType(p)))
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(p));
    }
}
