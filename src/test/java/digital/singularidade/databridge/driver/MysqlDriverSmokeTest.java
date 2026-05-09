package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MysqlDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("com.mysql.cj.jdbc.Driver")).doesNotThrowAnyException();
    }
}
