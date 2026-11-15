package af.storemis.config;

import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<Long> auditorAware() {
        return SecurityUtils::currentUserId;
    }

    @Bean
    DateTimeProvider auditDateTimeProvider(BusinessClock clock) {
        return () -> Optional.of(clock.now());
    }
}
