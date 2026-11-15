package af.storemis.settings.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.files.service.FileStorageService;
import af.storemis.files.service.FileStorageService.Area;
import af.storemis.settings.dto.BusinessSettingsResponse;
import af.storemis.settings.dto.CurrencyResponse;
import af.storemis.settings.dto.PublicSettingsResponse;
import af.storemis.settings.dto.UpdateBusinessSettingsRequest;
import af.storemis.settings.entity.BusinessSettings;
import af.storemis.settings.repository.BusinessSettingsRepository;
import af.storemis.settings.repository.CurrencyRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SettingsService {

    private final BusinessSettingsRepository settingsRepository;
    private final CurrencyRepository currencyRepository;
    private final FileStorageService files;
    private final AuditService audit;

    public SettingsService(BusinessSettingsRepository settingsRepository, CurrencyRepository currencyRepository, FileStorageService files,
            AuditService audit) {
        this.settingsRepository = settingsRepository;
        this.currencyRepository = currencyRepository;
        this.files = files;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BusinessSettings getEntity() {
        return settingsRepository.findById(BusinessSettings.SINGLETON_ID).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PublicSettingsResponse getPublic() {
        BusinessSettings s = getEntity();
        return new PublicSettingsResponse(s.getBusinessName(), logoUrl(s), s.getDefaultLanguage(), s.getDefaultTheme(), s.getBaseCurrencyCode(),
                s.getDefaultCurrencyCode(), currencies(), s.getCalendarDisplay(), s.getDigitStyle());
    }

    @Transactional(readOnly = true)
    public BusinessSettingsResponse get() {
        BusinessSettings s = getEntity();
        return BusinessSettingsResponse.from(s, logoUrl(s));
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> currencies() {
        return currencyRepository.findByActiveTrueOrderByCode().stream().map(CurrencyResponse::from).toList();
    }

    @Transactional
    public BusinessSettingsResponse update(UpdateBusinessSettingsRequest r) {
        BusinessSettings s = getEntity();
        s.checkVersion(r.version());
        String currency = r.defaultCurrency().toUpperCase();
        if (currencyRepository.findById(currency).filter(c -> c.isActive()).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "defaultCurrency"));
        }
        Map<String, Object> before = snapshot(s);
        s.setBusinessName(r.businessName().trim());
        s.setAddress(trim(r.address()));
        s.setPhone(trim(r.phone()));
        s.setEmail(trim(r.email()));
        s.setDefaultCurrencyCode(currency);
        s.setDefaultLanguage(r.defaultLanguage());
        s.setDefaultTheme(r.defaultTheme());
        s.setCalendarDisplay(r.calendarDisplay());
        s.setDigitStyle(r.digitStyle());
        s.setAllowNegativeStock(r.allowNegativeStock());
        s.setAllowNegativeCash(r.allowNegativeCash());
        s.setBlockSaleBelowCost(r.blockSaleBelowCost());
        s.setReceiptFormat(r.receiptFormat());
        s.setInvoiceHeaderNote(trim(r.invoiceHeaderNote()));
        s.setInvoiceFooterNote(trim(r.invoiceFooterNote()));
        s.setShowLogoOnReceipt(r.showLogoOnReceipt());
        settingsRepository.saveAndFlush(s);
        audit.log(AuditEvent.of(AuditAction.SETTINGS_CHANGE, AuditModule.SETTINGS, "BusinessSettings", 1L, null, "Business settings updated")
                .withDetails(diff(before, snapshot(s))));
        return BusinessSettingsResponse.from(s, logoUrl(s));
    }

    @Transactional
    public BusinessSettingsResponse uploadLogo(MultipartFile file) {
        BusinessSettings s = getEntity();
        String stored = files.store(Area.PUBLIC, file, FileStorageService.IMAGE_TYPES);
        String old = s.getLogoPath();
        s.setLogoPath(stored);
        settingsRepository.saveAndFlush(s);
        if (old != null) files.delete(Area.PUBLIC, old);
        audit.log(AuditEvent.of(AuditAction.UPLOAD, AuditModule.SETTINGS, "BusinessSettings", 1L, null, "Business logo uploaded"));
        return BusinessSettingsResponse.from(s, logoUrl(s));
    }

    @Transactional
    public BusinessSettingsResponse removeLogo() {
        BusinessSettings s = getEntity();
        if (s.getLogoPath() != null) {
            files.delete(Area.PUBLIC, s.getLogoPath());
            s.setLogoPath(null);
            settingsRepository.saveAndFlush(s);
            audit.log(AuditEvent.of(AuditAction.SETTINGS_CHANGE, AuditModule.SETTINGS, "BusinessSettings", 1L, null, "Business logo removed"));
        }
        return BusinessSettingsResponse.from(s, null);
    }

    private static String logoUrl(BusinessSettings s) {
        return s.getLogoPath() == null ? null : "/api/v1/files/public/" + s.getLogoPath();
    }

    private static Map<String, Object> snapshot(BusinessSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessName", s.getBusinessName());
        m.put("address", s.getAddress());
        m.put("phone", s.getPhone());
        m.put("email", s.getEmail());
        m.put("defaultCurrency", s.getDefaultCurrencyCode());
        m.put("defaultLanguage", s.getDefaultLanguage());
        m.put("defaultTheme", s.getDefaultTheme());
        m.put("calendarDisplay", s.getCalendarDisplay());
        m.put("digitStyle", s.getDigitStyle());
        m.put("allowNegativeStock", s.isAllowNegativeStock());
        m.put("allowNegativeCash", s.isAllowNegativeCash());
        m.put("blockSaleBelowCost", s.isBlockSaleBelowCost());
        m.put("receiptFormat", s.getReceiptFormat());
        m.put("invoiceHeaderNote", s.getInvoiceHeaderNote());
        m.put("invoiceFooterNote", s.getInvoiceFooterNote());
        m.put("showLogoOnReceipt", s.isShowLogoOnReceipt());
        return m;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("from", before.get(k));
                c.put("to", v);
                changes.put(k, c);
            }
        });
        return changes;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
