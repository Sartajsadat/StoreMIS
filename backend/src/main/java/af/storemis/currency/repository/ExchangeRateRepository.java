package af.storemis.currency.repository;

import af.storemis.currency.entity.ExchangeRate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findFirstByCurrencyCodeOrderByEffectiveAtDescIdDesc(String currencyCode);

    Page<ExchangeRate> findByCurrencyCodeOrderByEffectiveAtDescIdDesc(String currencyCode, Pageable pageable);
}
