package digital.singularidade.databridge.output;

import java.util.List;
import java.util.Map;

public record Sample(int rowCount, List<Map<String, Object>> rows) {}
