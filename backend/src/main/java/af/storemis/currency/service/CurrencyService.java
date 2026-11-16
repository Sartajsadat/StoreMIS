package af.storemis.currency.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.dto.RateRequest;
import af.storemis.currency.dto.RateResponse;
import af.storemis.currency.entity.ExchangeRate;
import af.storemis.currency.repository.ExchangeRateRepository;
import af.storemis.settings.entity.Currency;
import af.storemis.settings.repository.CurrencyRepository;
import af.storemis.settings.service.SettingsService;
import af.storemis.user.service.UserDirectory;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange rates. The current rate is only a default for new documents: every document stores the rate it used,
 * and old documents are never recalculated.
 */
@Service
public class CurrencyService {

    private final ExchangeRateRepository rates;
    private final CurrencyRepository currencies;
    private final SettingsService settings;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public CurrencyService(ExchangeRateRepository rates, CurrencyRepository currencies, SettingsService settings, UserDirectory users,
            AuditService audit, BusinessClock clock) {
        this.rates = rates;
        this.currencies = currencies;
        this.settings = settings;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    public String baseCurrency() {
        return settings.getEntity().getBaseCurrencyCode();
    }

    /** Checks the currency exists and is active. */
    @Transactional(readOnly = true)
    public Currency requireActive(String code) {
        return currencies.findById(code == null ? "" : code.toUpperCase()).filter(Currency::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "currency")));
    }

    /** Current rate (base per unit). The base currency is always 1. */
    @Transactional(readOnly = true)
    public BigDecimal currentRate(String code) {
        if (code.equalsIgnoreCase(baseCurrency())) return BigDecimal.ONE.setScale(Money.RATE_SCALE);
        return rates.findFirstByCurrencyCodeOrderByEffectiveAtDescIdDesc(code.toUpperCase()).map(ExchangeRate::getRate)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_RATE_MISSING, Map.of("currency", code)));
    }

    /**
     * The rate to store on a document: the base currency is forced to 1; otherwise the user's rate if given,
     * else the current rate.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveRate(String code, BigDecimal requested) {
        if (code.equalsIgnoreCase(baseCurrency())) return BigDecimal.ONE.setScale(Money.RATE_SCALE);
        if (requested != null) {
            if (requested.signum() <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "exchangeRate"));
            return Money.rate(requested);
        }
        return currentRate(code);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentRates() {
        Map<String, Object> result = new LinkedHashMap<>();
        String base = baseCurrency();
        result.put("baseCurrency", base);
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Currency c : currencies.findByActiveTrueOrderByCode()) {
            if (c.getCode().equals(base)) continue;
            rates.findFirstByCurrencyCodeOrderByEffectiveAtDescIdDesc(c.getCode()).ifPresent(r -> map.put(c.getCode(), r.getRate()));
        }
        result.put("rates", map);
        return result;
    }

    @Transactional
    public RateResponse setRate(RateRequest r) {
        Currency c = requireActive(r.currency());
        if (c.getCode().equals(baseCurrency())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "currency"));
        BigDecimal previous = rates.findFirstByCurrencyCodeOrderByEffectiveAtDescIdDesc(c.getCode()).map(ExchangeRate::getRate).orElse(null);
        ExchangeRate rate = rates.save(new ExchangeRate(c.getCode(), Money.rate(r.rate()), clock.now(), blankToNull(r.notes()), clock.now(),
                SecurityUtils.currentUserId().orElse(null)));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rate", Map.of("from", previous == null ? "—" : previous.stripTrailingZeros().toPlainString(), "to", rate.getRate().stripTrailingZeros().toPlainString()));
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.CURRENCY, "ExchangeRate", rate.getId(), c.getCode(), "Exchange rate updated").withDetails(details));
        return toResponse(rate, users.names(List.of(rate.getCreatedBy() == null ? -1L : rate.getCreatedBy())));
    }

    @Transactional(readOnly = true)
    public Page<RateResponse> history(String code, Pageable pageable) {
        Page<ExchangeRate> page = rates.findByCurrencyCodeOrderByEffectiveAtDescIdDesc(code.toUpperCase(), pageable);
        Map<Long, String> names = users.names(page.getContent().stream().map(ExchangeRate::getCreatedBy).toList());
        return page.map(r -> toResponse(r, names));
    }

    private RateResponse toResponse(ExchangeRate r, Map<Long, String> names) {
        return new RateResponse(r.getId(), r.getCurrencyCode(), baseCurrency(), r.getRate(), r.getEffectiveAt(), r.getNotes(), names.get(r.getCreatedBy()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
