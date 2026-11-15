package af.storemis.settings.dto;

import af.storemis.settings.entity.BusinessSettings;

public record BusinessSettingsResponse(String businessName, String logoUrl, String address, String phone, String email, String baseCurrency,
        String defaultCurrency, String defaultLanguage, String defaultTheme, String calendarDisplay, String digitStyle, boolean allowNegativeStock,
        boolean allowNegativeCash, boolean blockSaleBelowCost, String receiptFormat, String invoiceHeaderNote, String invoiceFooterNote,
        boolean showLogoOnReceipt, String timezone, int version) {

    public static BusinessSettingsResponse from(BusinessSettings s, String logoUrl) {
        return new BusinessSettingsResponse(s.getBusinessName(), logoUrl, s.getAddress(), s.getPhone(), s.getEmail(), s.getBaseCurrencyCode(),
                s.getDefaultCurrencyCode(), s.getDefaultLanguage(), s.getDefaultTheme(), s.getCalendarDisplay(), s.getDigitStyle(),
                s.isAllowNegativeStock(), s.isAllowNegativeCash(), s.isBlockSaleBelowCost(), s.getReceiptFormat(), s.getInvoiceHeaderNote(),
                s.getInvoiceFooterNote(), s.isShowLogoOnReceipt(), s.getTimezone(), s.getVersion());
    }
}
