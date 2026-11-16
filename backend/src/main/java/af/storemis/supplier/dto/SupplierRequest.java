package af.storemis.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(@NotBlank @Size(max = 150) String name, @Size(max = 30) String phone, @Size(max = 300) String address,
        @Size(max = 150) String contactPerson, @Size(max = 2000) String notes, Integer version) {}
