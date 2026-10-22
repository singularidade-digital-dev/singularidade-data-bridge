package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonWriter {

    /**
     * Pretty-printed, UTF-8, and always {@code \n} line breaks — Jackson's default pretty printer uses the
     * platform line separator, which would make files differ between Windows and Linux.
     */
    public static ObjectMapper newMapper() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"));
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDefaultPrettyPrinter(printer)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    private final ObjectMapper mapper;

    public JsonWriter() { this(newMapper()); }
    public JsonWriter(ObjectMapper mapper) { this.mapper = mapper; }

    /** Writes {@code value} (a {@link Metadata}, an index, or a JSON tree) as pretty JSON, atomically. */
    public void write(Object value, Path target) {
        Path dir = target.getParent();
        Path tmp = dir.resolve("." + target.getFileName().toString() + ".partial");
        try {
            if (dir != null) Files.createDirectories(dir);
            byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
            Files.write(tmp, body);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            throw new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "Failed to write " + target + ": " + e.getMessage(),
                "Verify --out is a writable directory with free space", e);
        }
    }
}
