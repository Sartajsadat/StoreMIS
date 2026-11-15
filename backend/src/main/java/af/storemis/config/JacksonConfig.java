package af.storemis.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /** Money and quantities go over the wire as strings ("10000.00") so JavaScript never rounds them. */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer bigDecimalAsString() {
        return builder -> {
            SimpleModule module = new SimpleModule("BigDecimalAsString");
            module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            builder.modulesToInstall(module);
        };
    }
}
