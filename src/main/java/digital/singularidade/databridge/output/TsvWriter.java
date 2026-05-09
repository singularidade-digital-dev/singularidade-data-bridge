package digital.singularidade.databridge.output;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TsvWriter {

    public void writeAll(Metadata m, Path outDir) {
        try {
            Files.createDirectories(outDir);
            writeColumns(m.columns(), outDir.resolve("columns.tsv"));
            writeFks(m.foreignKeys(), outDir.resolve("fks.tsv"));
            writeIndexes(m.indexes(), outDir.resolve("indexes.tsv"));
            writeUniques(m.uniqueConstraints(), outDir.resolve("unique-constraints.tsv"));
            writeChecks(m.checkConstraints(), outDir.resolve("check-constraints.tsv"));
            writeSample(m.sample(), outDir.resolve("sample.tsv"));
            writeCardinality(m.cardinality(), outDir.resolve("cardinality.tsv"));
        } catch (IOException e) {
            throw new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "TSV write failed: " + e.getMessage(), null, e);
        }
    }

    private static String esc(Object v) {
        if (v == null) return "";
        String s = v.toString();
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void writeColumns(List<Column> cols, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tordinalPosition\tsqlType\tjdbcType\tnullable\tprimaryKey\tcomment\n");
            for (Column c : cols) {
                w.write(String.join("\t",
                    esc(c.name()), esc(c.ordinalPosition()), esc(c.sqlType()),
                    esc(c.jdbcType()), esc(c.nullable()), esc(c.primaryKey()),
                    esc(c.comment())));
                w.write('\n');
            }
        }
    }

    private void writeFks(List<ForeignKey> fks, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("constraintName\tfkColumns\trefSchema\trefTable\trefColumns\n");
            for (ForeignKey fk : fks) {
                w.write(String.join("\t",
                    esc(fk.constraintName()),
                    esc(String.join(",", fk.fkColumns())),
                    esc(fk.refSchema()), esc(fk.refTable()),
                    esc(String.join(",", fk.refColumns()))));
                w.write('\n');
            }
        }
    }

    private void writeIndexes(List<Index> ix, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tcolumns\tunique\tprimary\tmethod\twhere\n");
            for (Index i : ix) {
                w.write(String.join("\t",
                    esc(i.name()), esc(String.join(",", i.columns())),
                    esc(i.unique()), esc(i.primary()),
                    esc(i.method()), esc(i.whereClause())));
                w.write('\n');
            }
        }
    }

    private void writeUniques(List<UniqueConstraint> ucs, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tcolumns\n");
            for (UniqueConstraint u : ucs) {
                w.write(esc(u.name()) + "\t" + esc(String.join(",", u.columns())) + "\n");
            }
        }
    }

    private void writeChecks(List<CheckConstraint> ccs, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tdefinition\n");
            for (CheckConstraint c : ccs) {
                w.write(esc(c.name()) + "\t" + esc(c.definition()) + "\n");
            }
        }
    }

    private void writeSample(Sample s, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            if (s.rows().isEmpty()) {
                return;
            }
            List<String> headers = List.copyOf(s.rows().get(0).keySet());
            w.write(String.join("\t", headers));
            w.write('\n');
            for (Map<String, Object> row : s.rows()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) sb.append('\t');
                    sb.append(esc(row.get(headers.get(i))));
                }
                sb.append('\n');
                w.write(sb.toString());
            }
        }
    }

    private void writeCardinality(Cardinality c, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("# totalRows=" + c.totalRows() + "\n");
            w.write("name\tdistinctCount\tnullCount\n");
            for (Cardinality.PerColumn pc : c.perColumn()) {
                w.write(String.join("\t",
                    esc(pc.name()), esc(pc.distinctCount()), esc(pc.nullCount())));
                w.write('\n');
            }
        }
    }
}
