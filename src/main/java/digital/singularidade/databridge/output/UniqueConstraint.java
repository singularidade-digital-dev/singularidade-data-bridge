package digital.singularidade.databridge.output;

import java.util.List;

public record UniqueConstraint(String name, List<String> columns) {}
