package digital.singularidade.databridge.query;

/** One block of a queries file: its name and its single statement (with {@code :param} placeholders). */
public record NamedQuery(String name, String sql) {}
