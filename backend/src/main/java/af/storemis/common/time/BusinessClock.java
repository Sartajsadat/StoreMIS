package af.storemis.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The store's clock (Asia/Kabul). Business dates for documents and daily closing come from here. */
@Component
public class BusinessClock {

    private final Clock clock;

    public BusinessClock(@Value("${storemis.timezone:Asia/Kabul}") String zone) {
        this.clock = Clock.system(ZoneId.of(zone));
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
