package af.storemis.settings.entity;

import af.storemis.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The single settings row (id = 1). */
@Entity
@Table(name = "business_settings")
public class BusinessSettings extends AuditableEntity {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "logo_path")
    private String logoPath;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "base_currency_code", nullable = false, columnDefinition = "bpchar(3)")
    private String baseCurrencyCode;

    @Column(name = "default_currency_code", nullable = false, columnDefinition = "bpchar(3)")
    private String defaultCurrencyCode;

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @Column(name = "default_theme", nullable = false)
    private String defaultTheme;

    @Column(name = "calendar_display", nullable = false)
    private String calendarDisplay;

    @Column(name = "digit_style", nullable = false)
    private String digitStyle;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(name = "allow_negative_cash", nullable = false)
    private boolean allowNegativeCash;

    @Column(name = "block_sale_below_cost", nullable = false)
    private boolean blockSaleBelowCost;

    @Column(name = "receipt_format", nullable = false)
    private String receiptFormat;

    @Column(name = "invoice_header_note")
    private String invoiceHeaderNote;

    @Column(name = "invoice_footer_note")
    private String invoiceFooterNote;

    @Column(name = "show_logo_on_receipt", nullable = false)
    private boolean showLogoOnReceipt;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    public Integer getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public String getDefaultCurrencyCode() {
        return defaultCurrencyCode;
    }

    public void setDefaultCurrencyCode(String defaultCurrencyCode) {
        this.defaultCurrencyCode = defaultCurrencyCode;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public String getDefaultTheme() {
        return defaultTheme;
    }

    public void setDefaultTheme(String defaultTheme) {
        this.defaultTheme = defaultTheme;
    }

    public String getCalendarDisplay() {
        return calendarDisplay;
    }

    public void setCalendarDisplay(String calendarDisplay) {
        this.calendarDisplay = calendarDisplay;
    }

    public String getDigitStyle() {
        return digitStyle;
    }

    public void setDigitStyle(String digitStyle) {
        this.digitStyle = digitStyle;
    }

    public boolean isAllowNegativeStock() {
        return allowNegativeStock;
    }

    public void setAllowNegativeStock(boolean allowNegativeStock) {
        this.allowNegativeStock = allowNegativeStock;
    }

    public boolean isAllowNegativeCash() {
        return allowNegativeCash;
    }

    public void setAllowNegativeCash(boolean allowNegativeCash) {
        this.allowNegativeCash = allowNegativeCash;
    }

    public boolean isBlockSaleBelowCost() {
        return blockSaleBelowCost;
    }

    public void setBlockSaleBelowCost(boolean blockSaleBelowCost) {
        this.blockSaleBelowCost = blockSaleBelowCost;
    }

    public String getReceiptFormat() {
        return receiptFormat;
    }

    public void setReceiptFormat(String receiptFormat) {
        this.receiptFormat = receiptFormat;
    }

    public String getInvoiceHeaderNote() {
        return invoiceHeaderNote;
    }

    public void setInvoiceHeaderNote(String invoiceHeaderNote) {
        this.invoiceHeaderNote = invoiceHeaderNote;
    }

    public String getInvoiceFooterNote() {
        return invoiceFooterNote;
    }

    public void setInvoiceFooterNote(String invoiceFooterNote) {
        this.invoiceFooterNote = invoiceFooterNote;
    }

    public boolean isShowLogoOnReceipt() {
        return showLogoOnReceipt;
    }

    public void setShowLogoOnReceipt(boolean showLogoOnReceipt) {
        this.showLogoOnReceipt = showLogoOnReceipt;
    }

    public String getTimezone() {
        return timezone;
    }
}
