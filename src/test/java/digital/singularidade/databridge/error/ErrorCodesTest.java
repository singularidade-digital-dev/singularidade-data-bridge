package digital.singularidade.databridge.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodesTest {

    @Test
    void exit_codes_match_spec() {
        assertThat(ErrorCodes.OK.exitCode()).isZero();
        assertThat(ErrorCodes.INVALID_ARGS.exitCode()).isEqualTo(2);
        assertThat(ErrorCodes.CONNECTION_FAILED.exitCode()).isEqualTo(10);
        assertThat(ErrorCodes.TABLE_NOT_FOUND.exitCode()).isEqualTo(11);
        assertThat(ErrorCodes.SCHEMA_NOT_FOUND.exitCode()).isEqualTo(12);
        assertThat(ErrorCodes.QUERY_FAILED.exitCode()).isEqualTo(13);
        assertThat(ErrorCodes.OUTPUT_WRITE_FAILED.exitCode()).isEqualTo(14);
        assertThat(ErrorCodes.UNSUPPORTED.exitCode()).isEqualTo(64);
    }

    @Test
    void http_status_mapping() {
        assertThat(ErrorCodes.OK.httpStatus()).isEqualTo(200);
        assertThat(ErrorCodes.INVALID_ARGS.httpStatus()).isEqualTo(400);
        assertThat(ErrorCodes.TABLE_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCodes.SCHEMA_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCodes.CONNECTION_FAILED.httpStatus()).isEqualTo(502);
        assertThat(ErrorCodes.QUERY_FAILED.httpStatus()).isEqualTo(502);
        assertThat(ErrorCodes.UNSPECIFIED.httpStatus()).isEqualTo(500);
    }

    @Test
    void exception_carries_code_and_hint() {
        DataBridgeException e = new DataBridgeException(
            ErrorCodes.TABLE_NOT_FOUND, "no such table", "list-tables to discover");
        assertThat(e.code()).isEqualTo(ErrorCodes.TABLE_NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo("no such table");
        assertThat(e.hint()).isEqualTo("list-tables to discover");
    }
}
