package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Index(
    String name,
    List<String> columns,
    List<Boolean> ordinalAsc,
    boolean unique,
    boolean primary,
    String method,
    @JsonProperty("where") String whereClause
) {}
