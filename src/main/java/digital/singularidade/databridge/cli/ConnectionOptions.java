package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.ConnectionSettings;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Connection options shared by every command (picocli mixin). Credentials can live outside the JDBC
 * URL — {@code --user} plus {@code --password-env VAR} or {@code --password-file PATH} — so they stay out
 * of shell history and process listings. Giving the same credential in the URL and in a flag is an error.
 */
public final class ConnectionOptions {

    private static final Pattern URL_USER = Pattern.compile("(?i)[?&;](user|username|user_name)=");
    private static final Pattern URL_PASSWORD = Pattern.compile("(?i)[?&;](password|passwd)=");
    private static final Pattern USERINFO = Pattern.compile("://[^/@?;]+@");
    private static final Pattern USERINFO_PASSWORD = Pattern.compile("://[^/@?;:]+:[^/@?;]*@");

    @Option(names = "--jdbc-url", required = true,
            description = "JDBC URL. Driver auto-detected from the prefix.")
    String jdbcUrl;

    @Option(names = "--user", description = "Database user, when not in the URL.")
    String user;

    @Option(names = "--password-env", paramLabel = "VAR",
            description = "Read the password from this environment variable.")
    String passwordEnv;

    @Option(names = "--password-file", paramLabel = "PATH",
            description = "Read the password from this file (a final line break is ignored).")
    Path passwordFile;

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public ConnectionSettings resolve() {
        return resolve(System::getenv);
    }

    ConnectionSettings resolve(Function<String, String> env) {
        if (passwordEnv != null && passwordFile != null) {
            throw invalid("pass only one of --password-env and --password-file");
        }
        boolean passwordFlag = passwordEnv != null || passwordFile != null;
        if (user != null && (URL_USER.matcher(jdbcUrl).find() || USERINFO.matcher(jdbcUrl).find())) {
            throw invalid("the user is given twice (in --jdbc-url and in --user)");
        }
        if (passwordFlag && (URL_PASSWORD.matcher(jdbcUrl).find() || USERINFO_PASSWORD.matcher(jdbcUrl).find())) {
            throw invalid("the password is given twice (in --jdbc-url and in --password-env/--password-file)");
        }
        String password = null;
        if (passwordEnv != null) {
            password = env.apply(passwordEnv);
            if (password == null || password.isEmpty()) {
                throw invalid("environment variable " + passwordEnv + " is not set or empty");
            }
        } else if (passwordFile != null) {
            password = readPasswordFile(passwordFile);
        }
        return new ConnectionSettings(jdbcUrl, user, password);
    }

    private static String readPasswordFile(Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw invalid("cannot read --password-file " + file + ": " + e.getMessage());
        }
        if (content.endsWith("\n")) content = content.substring(0, content.length() - 1);
        if (content.endsWith("\r")) content = content.substring(0, content.length() - 1);
        if (content.isEmpty()) throw invalid("--password-file " + file + " is empty");
        return content;
    }

    private static DataBridgeException invalid(String message) {
        return new DataBridgeException(ErrorCodes.INVALID_ARGS, message,
            "Use --user with --password-env VAR or --password-file PATH, and keep credentials out of --jdbc-url");
    }
}
