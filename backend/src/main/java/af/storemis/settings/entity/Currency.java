package af.storemis.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "currencies")
public class Currency {

    @Id
    @Column(name = "code", length = 3, columnDefinition = "bpchar(3)")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private short decimalPlaces;

    @Column(name = "active", nullable = false)
    private boolean active;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public short getDecimalPlaces() {
        return decimalPlaces;
    }

    public boolean isActive() {
        return active;
    }
}
