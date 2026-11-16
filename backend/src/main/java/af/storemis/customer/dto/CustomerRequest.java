package af.storemis.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(@NotBlank @Size(max = 150) String name, @Size(max = 30) String phone, @Size(max = 300) String address,
        @Pattern(regexp = "RETAIL|WHOLESALE") String customerType, @Size(max = 2000) String notes, Integer version) {}
