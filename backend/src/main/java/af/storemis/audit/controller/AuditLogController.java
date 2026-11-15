package af.storemis.audit.controller;

import af.storemis.audit.dto.AuditLogResponse;
import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditService;
import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AuditModule module,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("occurredAt", "username", "module", "action"), Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ApiResponse.ok(PageResponse.of(auditService.search(userId, module, action, from, to, q, pageable), AuditLogResponse::from));
    }
}
