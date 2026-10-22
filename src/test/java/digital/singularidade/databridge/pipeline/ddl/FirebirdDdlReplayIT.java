package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.cli.Main;
import digital.singularidade.databridge.output.SystemNames;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbCheck;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbColumn;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbForeignKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbIndex;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbSequenceLink;
import digital.singularidade.databridge.source.jdbc.firebird.FirebirdCatalog;
import digital.singularidade.databridge.support.FbFixture;
import digital.singularidade.databridge.support.FbFixture.IsqlResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The strongest check on the Firebird DDL: extract every fixture table, replay ddl.sql + triggers.sql into
 * an empty database with isql, then require the copy's catalog to equal the original's (apart from names
 * the database generates).
 */
class FirebirdDdlReplayIT {

    private static FbFixture fx;

    @BeforeAll
    static void start() {
        fx = new FbFixture();
    }

    @AfterAll
    static void stop() {
        fx.close();
    }

    @Test
    void should_replay_every_table_into_an_empty_database_with_the_same_catalog(@TempDir Path tmp) throws Exception {
        int exit = new CommandLine(new Main())
            .setErr(new PrintWriter(OutputStream.nullOutputStream()))
            .execute("extract-all", "--jdbc-url", fx.jdbcUrl(), "--out", tmp.toString(),
                "--include-triggers", "--cardinality-mode", "skip", "-q");
        assertThat(exit).isZero();
        List<String> tables = tableDirs(tmp);
        assertThat(tables).contains("PRITENSESQ", "TBPAI", "TBGRUPO", "TbMista", "PRODUCTS");

        IsqlResult r = fx.isqlWithoutDatabase(replayScript(tmp, tables, "replay.fdb"));
        assertThat(r.exitCode()).withFailMessage("isql failed:%n%s", r.output()).isZero();
        assertThat(r.output()).doesNotContain("Statement failed");

        try (Connection original = DriverManager.getConnection(fx.jdbcUrl());
             Connection copy = DriverManager.getConnection(fx.jdbcUrl("replay.fdb"))) {
            for (String table : tables) {
                assertThat(normalize(FirebirdCatalog.read(copy, table)))
                    .as("catalog of %s after replay", table)
                    .isEqualTo(normalize(FirebirdCatalog.read(original, table)));
            }
        }
    }

    static List<String> tableDirs(Path out) throws IOException {
        try (Stream<Path> s = Files.list(out)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /**
     * One isql script: CREATE DATABASE, shared objects once, every table's statements, then every foreign
     * key (parents must exist), then the triggers.
     */
    static String replayScript(Path out, List<String> tables, String dbFile) throws IOException {
        Set<String> shared = new LinkedHashSet<>();
        List<String> main = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();
        StringBuilder triggers = new StringBuilder();
        for (String table : tables) {
            for (String stmt : statements(Files.readString(out.resolve(table).resolve("ddl.sql")))) {
                if (stmt.startsWith("CREATE DOMAIN") || stmt.startsWith("CREATE SEQUENCE")) shared.add(stmt);
                else if (stmt.startsWith("ALTER TABLE") && stmt.contains(" FOREIGN KEY ")) foreignKeys.add(stmt);
                else main.add(stmt);
            }
            Path trg = out.resolve(table).resolve("triggers.sql");
            if (Files.exists(trg)) triggers.append(withoutComments(Files.readString(trg))).append('\n');
        }
        return "SET SQL DIALECT 3;\n"
            + "CREATE DATABASE '" + FbFixture.DB_DIR + "/" + dbFile + "' USER '" + FbFixture.USER
            + "' PASSWORD '" + FbFixture.PASSWORD + "' DEFAULT CHARACTER SET ISO8859_1;\n"
            + String.join("\n", shared) + "\nCOMMIT;\n"
            + String.join("\n", main) + "\nCOMMIT;\n"
            + String.join("\n", foreignKeys) + "\nCOMMIT;\n"
            + triggers + "\nCOMMIT;\n";
    }

    private static List<String> statements(String ddl) {
        List<String> out = new ArrayList<>();
        for (String part : withoutComments(ddl).split(";\\s*(\\n|$)")) {
            String s = part.strip();
            if (!s.isEmpty()) out.add(s + ";");
        }
        return out;
    }

    private static String withoutComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n", -1)) {
            if (!line.startsWith("--")) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Drop the names the database invents and sort what is ordered by those names. */
    static FbTable normalize(FbTable t) {
        List<FbColumn> columns = t.columns().stream().map(c -> new FbColumn(c.name(), c.position(), c.type(),
            c.nativeType(), c.charset(), c.collation(), c.charsetDefaultCollation(), c.domainName(),
            c.columnNotNull(), c.columnDefault(), c.columnCollation(), c.computedSource(), c.identityType(),
            sys(c.identityGenerator()), c.description(),
            c.sequence() == null ? null
                : new FbSequenceLink(sys(c.sequence().generator()), c.sequence().source(), c.sequence().trigger())))
            .toList();
        List<FbKey> uniques = t.uniques().stream().map(FirebirdDdlReplayIT::key)
            .sorted(Comparator.comparing(k -> String.join(",", k.columns()))).toList();
        List<FbForeignKey> fks = t.foreignKeys().stream()
            .map(f -> new FbForeignKey(sys(f.name()), f.columns(), f.refTable(), f.refColumns(), f.updateRule(),
                f.deleteRule(), sys(f.indexName()), f.descending()))
            .sorted(Comparator.comparing((FbForeignKey f) -> f.refTable()).thenComparing(f -> String.join(",", f.columns())))
            .toList();
        List<FbCheck> checks = t.checks().stream().map(c -> new FbCheck(sys(c.name()), c.source()))
            .sorted(Comparator.comparing(FbCheck::source)).toList();
        List<FbIndex> indexes = t.indexes().stream()
            .map(i -> new FbIndex(sys(i.name()), i.columns(), i.unique(), i.descending(), i.expression(),
                i.active(), sys(i.constraintName())))
            .sorted(Comparator.comparing((FbIndex i) -> String.valueOf(i.name()))
                .thenComparing(i -> String.join(",", i.columns()) + "|" + i.expression()))
            .toList();
        return new FbTable(t.name(), t.view(), t.viewSource(), t.description(), t.databaseCharset(), columns,
            t.domains(), t.primaryKey() == null ? null : key(t.primaryKey()), uniques, fks, checks, indexes,
            t.triggers(), t.triggerGenerators());
    }

    private static FbKey key(FbKey k) {
        return new FbKey(sys(k.name()), k.columns(), sys(k.indexName()), k.descending());
    }

    private static String sys(String name) {
        return SystemNames.isSystemGenerated(name) ? null : name;
    }
}
