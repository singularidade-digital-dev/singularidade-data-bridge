package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MssqlDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")).doesNotThrowAnyException();
    }
}
