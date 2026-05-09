package digital.singularidade.databridge.error;

public enum ErrorCodes {
    OK(0, 200, "OK"),
    UNSPECIFIED(1, 500, "UNSPECIFIED"),
    INVALID_ARGS(2, 400, "INVALID_ARGS"),
    CONNECTION_FAILED(10, 502, "CONNECTION_FAILED"),
    TABLE_NOT_FOUND(11, 404, "TABLE_NOT_FOUND"),
    SCHEMA_NOT_FOUND(12, 404, "SCHEMA_NOT_FOUND"),
    QUERY_FAILED(13, 502, "QUERY_FAILED"),
    OUTPUT_WRITE_FAILED(14, 500, "OUTPUT_WRITE_FAILED"),
    UNSUPPORTED(64, 501, "UNSUPPORTED");

    private final int exitCode;
    private final int httpStatus;
    private final String wireName;

    ErrorCodes(int exitCode, int httpStatus, String wireName) {
        this.exitCode = exitCode;
        this.httpStatus = httpStatus;
        this.wireName = wireName;
    }

    public int exitCode() { return exitCode; }
    public int httpStatus() { return httpStatus; }
    public String wireName() { return wireName; }
}
