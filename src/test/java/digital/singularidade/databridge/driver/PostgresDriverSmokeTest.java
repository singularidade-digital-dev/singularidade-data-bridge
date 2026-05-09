package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class PostgresDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("org.postgresql.Driver")).doesNotThrowAnyException();
    }
}
