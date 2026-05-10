package digital.singularidade.databridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

public final class BuildInfo {

    public static final String NAME;
    public static final String VERSION;

    static {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
            if (in == null) {
                throw new IllegalStateException("build-info.properties missing from classpath");
            }
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load build-info.properties", e);
        }
        NAME = require(p, "name");
        VERSION = require(p, "version");
    }

    private static String require(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException("build-info.properties missing or unfiltered key: " + key);
        }
        return value;
    }

    private BuildInfo() {}
}
