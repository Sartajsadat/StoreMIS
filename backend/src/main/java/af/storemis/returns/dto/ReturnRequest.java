package af.storemis.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A return against a sale ({@code documentId} = sale id) or a purchase (purchase id). The part of the value that is
 * not still owed on the document is settled as a cash REFUND (from/to {@code refundCashAccountId}) or kept as CREDIT.
 */
public record ReturnRequest(UUID clientRequestId, @NotNull Long documentId, @NotBlank @Size(max = 300) String reason,
        @Pattern(regexp = "REFUND|CREDIT") String settlement, Long refundCashAccountId, @NotEmpty @Size(max = 500) List<@Valid ReturnLine> items) {}
