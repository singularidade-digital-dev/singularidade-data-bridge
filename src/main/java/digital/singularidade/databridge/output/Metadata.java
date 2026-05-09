package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record Metadata(
    @JsonProperty("$schema") String schemaUrl,
    String version,
    Instant generatedAt,
    Generator generator,
    SourceInfo source,
    TableInfo tableInfo,
    List<Column> columns,
    List<String> primaryKey,
    List<ForeignKey> foreignKeys,
    List<Index> indexes,
    List<UniqueConstraint> uniqueConstraints,
    List<CheckConstraint> checkConstraints,
    Sample sample,
    List<ColumnStats> columnStats,
    Cardinality cardinality,
    Partitioning partitioning,
    List<String> warnings
) {
    public record Generator(String name, String version) {}
}
