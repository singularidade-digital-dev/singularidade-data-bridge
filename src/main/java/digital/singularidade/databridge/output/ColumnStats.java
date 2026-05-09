package digital.singularidade.databridge.output;

import java.util.List;

public record ColumnStats(
    String name,
    Long nDistinctEstimate,
    Double nullFraction,
    List<String> mostCommonValues,
    List<Double> mostCommonFrequencies,
    Double correlation
) {}
