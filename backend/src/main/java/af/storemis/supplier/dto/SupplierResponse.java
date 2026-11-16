package af.storemis.supplier.dto;

import af.storemis.supplier.entity.Supplier;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** balances: amount owed to the supplier per currency (negative = the supplier owes us). */
public record SupplierResponse(Long id, String name, String phone, String address, String contactPerson, String notes, boolean active,
        Map<String, BigDecimal> balances, OffsetDateTime createdAt, int version) {

    public static SupplierResponse from(Supplier s, Map<String, BigDecimal> balances) {
        return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getAddress(), s.getContactPerson(), s.getNotes(), s.isActive(),
                balances == null ? Map.of() : balances, s.getCreatedAt(), s.getVersion());
    }
}
