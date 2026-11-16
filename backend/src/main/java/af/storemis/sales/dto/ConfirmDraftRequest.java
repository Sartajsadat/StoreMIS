package af.storemis.sales.dto;

import af.storemis.payment.dto.PaymentLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ConfirmDraftRequest(@Size(max = 5) List<@Valid PaymentLine> payments, UUID clientRequestId) {}
