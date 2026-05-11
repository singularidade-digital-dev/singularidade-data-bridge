package digital.singularidade.databridge.server;

public record QueryRequest(
    String jdbcUrl,
    String sql,
    Integer limit,
    Integer timeoutSec,
    Boolean writable
) {
    public int limitOrDefault()      { return limit      == null ? 100 : limit; }
    public int timeoutSecOrDefault() { return timeoutSec == null ? 30  : timeoutSec; }
    public boolean writableOrDefault() { return writable != null && writable; }
}
