package af.storemis.catalog.dto;

import af.storemis.catalog.entity.Unit;

/** code is non-null for system units; the UI shows the translated name for those. */
public record UnitResponse(Long id, String code, String name, String symbol, boolean allowDecimal, boolean active, int version) {

    public static UnitResponse from(Unit u) {
        return new UnitResponse(u.getId(), u.getCode(), u.getName(), u.getSymbol(), u.isAllowDecimal(), u.isActive(), u.getVersion());
    }
}
