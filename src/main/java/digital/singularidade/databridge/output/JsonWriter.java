package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    public static ObjectMapper newMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    private final ObjectMapper mapper;

    public JsonWriter() { this(newMapper()); }
    public JsonWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(Metadata m, Path target) {
        Path dir = target.getParent();
        Path tmp = dir.resolve("." + target.getFileName().toString() + ".partial");
        try {
            if (dir != null) Files.createDirectories(dir);
            byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(m);
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
