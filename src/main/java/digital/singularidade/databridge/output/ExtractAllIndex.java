package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record ExtractAllIndex(
    @JsonProperty("$schema") String schemaUrl,
    String version,
    Instant generatedAt,
    Metadata.Generator generator,
    SchemaSource source,
    int tableCount,
    List<TableSummary> tables
) {

    public record SchemaSource(String type, String driver, String url, String schema) {}

    public record TableSummary(
        String schema,
        String table,
        String type,
        int columnCount,
        List<String> primaryKey,
        Long totalRows
    ) {}
}
