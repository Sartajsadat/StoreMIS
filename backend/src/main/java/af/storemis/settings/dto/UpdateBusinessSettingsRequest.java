package af.storemis.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Business information and general system settings. The base currency is changed only through currency settings. */
public record UpdateBusinessSettingsRequest(
        @NotBlank @Size(max = 150) String businessName,
        @Size(max = 300) String address,
        @Size(max = 50) String phone,
        @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 3, max = 3) String defaultCurrency,
        @NotNull @Pattern(regexp = "en|fa|ps") String defaultLanguage,
        @NotNull @Pattern(regexp = "light|dark") String defaultTheme,
        @NotNull @Pattern(regexp = "GREGORIAN|SOLAR_HIJRI") String calendarDisplay,
        @NotNull @Pattern(regexp = "LATIN|PERSIAN") String digitStyle,
        boolean allowNegativeStock,
        boolean allowNegativeCash,
        boolean blockSaleBelowCost,
        @NotNull @Pattern(regexp = "THERMAL_80|THERMAL_58|A4") String receiptFormat,
        @Size(max = 500) String invoiceHeaderNote,
        @Size(max = 500) String invoiceFooterNote,
        boolean showLogoOnReceipt,
        @NotNull Integer version) {}
