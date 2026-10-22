package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the packaged JAR under two very different JVM setups (default charset, line separator, locale)
 * and requires byte-identical output: --stable files and query JSON must not depend on the machine.
 */
class MachineIndependenceIT {

    private static final Path JAR = Path.of("target", "data-bridge.jar");
    private static final List<String> MACHINE_A = List.of(
        "-Dfile.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US");
    private static final List<String> MACHINE_B = List.of(
        "-Dfile.encoding=ISO-8859-1", "-Dline.separator=\r\n", "-Duser.language=tr", "-Duser.country=TR",
        "-Dstdout.encoding=ISO-8859-1", "-Dsun.stdout.encoding=ISO-8859-1");

    private static FbFixture fx;
    private static String mixedUrl;

    @BeforeAll
    static void start() {
        fx = new FbFixture();
        mixedUrl = fx.jdbcUrlWithoutEncoding(fx.mixedEncodingDatabase());
    }

    @AfterAll
    static void stop() { fx.close(); }

    private static byte[] run(List<String> jvm, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(jvm);
        cmd.add("-jar");
        cmd.add(JAR.toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        byte[] out = p.getInputStream().readAllBytes();
        assertThat(p.waitFor()).as("exit code of %s", cmd).isZero();
        return out;
    }

    /** executionTimeMs is the one field that legitimately differs between two runs. */
    private static String withoutTiming(byte[] json) {
        return new String(json, StandardCharsets.UTF_8).replaceAll("\"executionTimeMs\" : \\d+", "\"executionTimeMs\" : 0");
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).map(root::relativize).sorted().toList();
        }
    }

    private static void assertSameTree(Path a, Path b) throws IOException {
        List<Path> fa = files(a);
        assertThat(fa).isNotEmpty().isEqualTo(files(b));
        for (Path rel : fa) {
            assertThat(Files.readAllBytes(b.resolve(rel))).as(rel.toString()).isEqualTo(Files.readAllBytes(a.resolve(rel)));
        }
    }

    @Test
    void should_write_identical_stable_files_on_different_machines(@TempDir Path tmp) throws Exception {
        for (String url : List.of(fx.jdbcUrl(), mixedUrl)) {
            Path a = tmp.resolve("a-" + url.hashCode());
            Path b = tmp.resolve("b-" + url.hashCode());
            run(MACHINE_A, "extract-all", "--jdbc-url", url, "--out", a.toString(), "--include-triggers", "--stable", "-q");
            run(MACHINE_B, "extract-all", "--jdbc-url", url, "--out", b.toString(), "--include-triggers", "--stable", "-q");
            assertSameTree(a, b);
        }
        String meta = Files.readString(tmp.resolve("a-" + mixedUrl.hashCode()).resolve("TMIX").resolve("metadata.json"));
        assertThat(meta).contains("Descrição com acento").doesNotContain("\r");
    }

    @Test
    void should_print_identical_query_json_on_different_machines() throws Exception {
        String sql = "SELECT ID, D, B FROM TMIX ORDER BY ID";
        String a = withoutTiming(run(MACHINE_A, "query", "--jdbc-url", mixedUrl, "--sql", sql));
        String b = withoutTiming(run(MACHINE_B, "query", "--jdbc-url", mixedUrl, "--sql", sql));
        assertThat(b).isEqualTo(a);
        String json = a;
        assertThat(json).contains("ação ‘x’ é").doesNotContain("\uFFFD").doesNotContain("\r");
    }
}
