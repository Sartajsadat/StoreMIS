package af.storemis.settings.dto;

import java.util.List;

/** Unauthenticated: what the login screen and app shell need before sign-in. */
public record PublicSettingsResponse(String businessName, String logoUrl, String defaultLanguage, String defaultTheme, String baseCurrency,
        String defaultCurrency, List<CurrencyResponse> currencies, String calendarDisplay, String digitStyle) {}
