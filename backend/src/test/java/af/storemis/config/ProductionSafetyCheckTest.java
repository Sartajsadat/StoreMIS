package af.storemis.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductionSafetyCheckTest {

    @Test
    void rejectsDevelopmentAndExampleValues() {
        assertThat(ProductionSafetyCheck.problems(ProductionSafetyCheck.DEV_SECRET, "storemis")).hasSize(2);
        assertThat(ProductionSafetyCheck.problems("short", "x9Kq2mLp7Rt")).hasSize(1);
        assertThat(ProductionSafetyCheck.problems("change-me-to-at-least-32-random-characters-xxxxxxxx", "change-me-to-a-long-random-password")).hasSize(2);
    }

    @Test
    void acceptsRealSecrets() {
        assertThat(ProductionSafetyCheck.problems("aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bC1dF3hJ5kL7nP9", "x9Kq2mLp7RtW4vZ8")).isEmpty();
    }
}
