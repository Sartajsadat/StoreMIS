package af.storemis.settings.repository;

import af.storemis.settings.entity.Currency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {

    List<Currency> findByActiveTrueOrderByCode();
}
