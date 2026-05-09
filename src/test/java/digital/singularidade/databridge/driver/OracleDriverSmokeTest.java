package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class OracleDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("oracle.jdbc.driver.OracleDriver")).doesNotThrowAnyException();
    }
}
