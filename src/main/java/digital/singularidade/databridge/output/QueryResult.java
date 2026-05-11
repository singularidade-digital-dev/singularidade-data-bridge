package digital.singularidade.databridge.output;

import java.util.List;
import java.util.Map;

/**
 * Result of a {@code data-bridge query} or {@code POST /v1/query} call.
 *
 * <p>Two shapes via {@code kind}:
 * <ul>
 *   <li>{@code "query"}: rowCount + truncated + columns + rows are populated; updateCount is null.</li>
 *   <li>{@code "update"}: updateCount is populated; rowCount/truncated/columns/rows are null.</li>
 * </ul>
 */
public record QueryResult(
    String kind,
    Integer rowCount,
    Boolean truncated,
    List<ColumnInfo> columns,
    List<Map<String, Object>> rows,
    Long updateCount,
    long executionTimeMs,
    List<String> warnings
) {

    public record ColumnInfo(String name, String sqlType, int jdbcType) {}

    public static QueryResult query(int rowCount, boolean truncated,
                                     List<ColumnInfo> columns,
                                     List<Map<String, Object>> rows,
                                     long executionTimeMs) {
        return new QueryResult("query", rowCount, truncated, columns, rows, null,
                               executionTimeMs, List.of());
    }

    public static QueryResult update(long updateCount, long executionTimeMs) {
        return new QueryResult("update", null, null, null, null, updateCount,
                               executionTimeMs, List.of());
    }
}
