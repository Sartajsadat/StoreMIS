package af.storemis.numbering.controller;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.common.api.ApiResponse;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentNumberService.SequenceInfo;
import af.storemis.numbering.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Document number prefixes (INV, PUR, RCV…), part of Invoice Settings. */
@RestController
@RequestMapping("/api/v1/settings/sequences")
@PreAuthorize("hasRole('ADMIN')")
public class NumberingController {

    public record PrefixRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$") String prefix) {}

    private final DocumentNumberService numbers;
    private final AuditService audit;

    public NumberingController(DocumentNumberService numbers, AuditService audit) {
        this.numbers = numbers;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<SequenceInfo>> list() {
        return ApiResponse.ok(numbers.list());
    }

    @PutMapping("/{type}")
    @Transactional
    public ApiResponse<SequenceInfo> update(@PathVariable DocumentType type, @Valid @RequestBody PrefixRequest request) {
        SequenceInfo info = numbers.updatePrefix(type, request.prefix());
        audit.log(AuditEvent.of(AuditAction.SETTINGS_CHANGE, AuditModule.SETTINGS, "DocumentSequence", null, type.name(), "Document prefix changed")
                .withDetails(Map.of("prefix", info.prefix())));
        return ApiResponse.ok(info);
    }
}
