package af.storemis.settings.repository;

import af.storemis.settings.entity.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Integer> {}
