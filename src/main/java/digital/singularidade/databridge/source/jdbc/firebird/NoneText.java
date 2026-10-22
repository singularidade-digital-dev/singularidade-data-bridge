package digital.singularidade.databridge.source.jdbc.firebird;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Decodes text stored with {@code CHARACTER SET NONE}, which Firebird keeps as raw bytes with no encoding
 * attached — legacy databases often mix UTF-8 and a single-byte code page in the same column. The rule is
 * fixed and independent of the machine: the bytes are read as UTF-8 when they are valid UTF-8, otherwise
 * with the fallback charset (default WIN1252). For single-byte fallbacks a byte with no character in that
 * charset maps to the code point of the same value (U+0080–U+009F), so nothing is lost.
 */
public final class NoneText {

    public static final String DEFAULT_FALLBACK = "WIN1252";

    /** Firebird character set names that differ from Java's. */
    private static final Map<String, String> FIREBIRD_NAMES = Map.ofEntries(
        Map.entry("UTF8", "UTF-8"), Map.entry("UNICODE_FSS", "UTF-8"),
        Map.entry("WIN1250", "windows-1250"), Map.entry("WIN1251", "windows-1251"),
        Map.entry("WIN1252", "windows-1252"), Map.entry("WIN1253", "windows-1253"),
        Map.entry("WIN1254", "windows-1254"), Map.entry("WIN1255", "windows-1255"),
        Map.entry("WIN1256", "windows-1256"), Map.entry("WIN1257", "windows-1257"),
        Map.entry("WIN1258", "windows-1258"),
        Map.entry("ISO8859_1", "ISO-8859-1"), Map.entry("ISO8859_2", "ISO-8859-2"),
        Map.entry("ISO8859_3", "ISO-8859-3"), Map.entry("ISO8859_4", "ISO-8859-4"),
        Map.entry("ISO8859_5", "ISO-8859-5"), Map.entry("ISO8859_6", "ISO-8859-6"),
        Map.entry("ISO8859_7", "ISO-8859-7"), Map.entry("ISO8859_8", "ISO-8859-8"),
        Map.entry("ISO8859_9", "ISO-8859-9"), Map.entry("ISO8859_13", "ISO-8859-13"),
        Map.entry("DOS437", "IBM437"), Map.entry("DOS850", "IBM850"), Map.entry("DOS852", "IBM852"),
        Map.entry("DOS857", "IBM857"), Map.entry("DOS860", "IBM860"), Map.entry("DOS861", "IBM861"),
        Map.entry("DOS863", "IBM863"), Map.entry("DOS865", "IBM865"), Map.entry("DOS866", "IBM866"),
        Map.entry("KOI8R", "KOI8-R"), Map.entry("KOI8U", "KOI8-U"));

    private final Charset fallback;
    private final char[] singleByteTable;

    private NoneText(Charset fallback) {
        this.fallback = fallback;
        this.singleByteTable = fallback.newEncoder().maxBytesPerChar() == 1.0f ? table(fallback) : null;
    }

    /** @throws IllegalArgumentException for a charset Java does not know */
    public static NoneText of(String charsetName) {
        String name = charsetName == null ? DEFAULT_FALLBACK : charsetName.strip();
        String javaName = FIREBIRD_NAMES.getOrDefault(name.toUpperCase(Locale.ROOT), name);
        try {
            return new NoneText(Charset.forName(javaName));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown character set: " + charsetName, e);
        }
    }

    public String charsetName() {
        return fallback.name();
    }

    public String decode(byte[] bytes) {
        String utf8 = strictUtf8(bytes);
        if (utf8 != null) return utf8;
        if (singleByteTable == null) return new String(bytes, fallback);
        char[] out = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) out[i] = singleByteTable[bytes[i] & 0xFF];
        return new String(out);
    }

    private static String strictUtf8(byte[] bytes) {
        CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return d.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** Character for every byte value; bytes the charset leaves undefined keep their own value. */
    private static char[] table(Charset cs) {
        char[] t = new char[256];
        for (int b = 0; b < 256; b++) {
            CharsetDecoder d = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                CharBuffer c = d.decode(ByteBuffer.wrap(new byte[] {(byte) b}));
                t[b] = c.length() == 1 && c.charAt(0) != '�' ? c.charAt(0) : (char) b;
            } catch (CharacterCodingException e) {
                t[b] = (char) b;
            }
        }
        return t;
    }
}
