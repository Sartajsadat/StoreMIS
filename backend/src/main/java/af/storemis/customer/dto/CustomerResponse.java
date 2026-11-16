package af.storemis.customer.dto;

import af.storemis.customer.entity.Customer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** balances: what the customer owes per currency (negative = customer credit). */
public record CustomerResponse(Long id, String name, String phone, String address, String customerType, String notes, boolean walkIn, boolean active,
        Map<String, BigDecimal> balances, OffsetDateTime createdAt, int version) {

    public static CustomerResponse from(Customer c, Map<String, BigDecimal> balances) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getCustomerType(), c.getNotes(), c.isWalkIn(), c.isActive(),
                balances == null ? Map.of() : balances, c.getCreatedAt(), c.getVersion());
    }
}
