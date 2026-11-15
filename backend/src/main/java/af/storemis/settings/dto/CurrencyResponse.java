package af.storemis.settings.dto;

import af.storemis.settings.entity.Currency;

public record CurrencyResponse(String code, String name, String symbol, int decimalPlaces) {

    public static CurrencyResponse from(Currency c) {
        return new CurrencyResponse(c.getCode(), c.getName(), c.getSymbol(), c.getDecimalPlaces());
    }
}
