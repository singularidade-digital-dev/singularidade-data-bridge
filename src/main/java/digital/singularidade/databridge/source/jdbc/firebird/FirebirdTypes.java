package digital.singularidade.databridge.source.jdbc.firebird;

/**
 * Renders a column type from its {@code RDB$FIELDS} codes the way it is declared in Firebird DDL.
 * Text lengths are characters ({@code RDB$CHARACTER_LENGTH}), never bytes.
 */
public final class FirebirdTypes {

    /** The {@code RDB$FIELDS} columns that define a type. {@code scale} is stored negative. */
    public record FieldType(int code, Integer subType, Integer charLength, Integer fieldLength,
                            Integer bytesPerChar, Integer precision, Integer scale, Integer segmentLength) {}

    private static final int SMALLINT = 7;
    private static final int INTEGER = 8;
    private static final int QUAD = 9;
    private static final int FLOAT = 10;
    private static final int D_FLOAT = 11;
    private static final int DATE = 12;
    private static final int TIME = 13;
    private static final int CHAR = 14;
    private static final int BIGINT = 16;
    private static final int BOOLEAN = 23;
    private static final int DECFLOAT16 = 24;
    private static final int DECFLOAT34 = 25;
    private static final int INT128 = 26;
    private static final int DOUBLE = 27;
    private static final int TIME_TZ = 28;
    private static final int TIMESTAMP_TZ = 29;
    private static final int TIMESTAMP = 35;
    private static final int VARCHAR = 37;
    private static final int CSTRING = 40;
    private static final int BLOB = 261;
    private static final int DEFAULT_SEGMENT = 80;

    private FirebirdTypes() {}

    public static String nativeType(FieldType t) {
        return switch (t.code()) {
            case SMALLINT -> exact(t, "SMALLINT", 4);
            case INTEGER -> exact(t, "INTEGER", 9);
            case BIGINT, QUAD -> exact(t, "BIGINT", 18);
            case INT128 -> exact(t, "INT128", 38);
            case FLOAT -> "FLOAT";
            case D_FLOAT -> "DOUBLE PRECISION";
            case DOUBLE -> scale(t) > 0 ? "NUMERIC(" + precision(t, 15) + "," + scale(t) + ")" : "DOUBLE PRECISION";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIME_TZ -> "TIME WITH TIME ZONE";
            case TIMESTAMP -> "TIMESTAMP";
            case TIMESTAMP_TZ -> "TIMESTAMP WITH TIME ZONE";
            case CHAR -> "CHAR(" + charLength(t) + ")";
            case VARCHAR -> "VARCHAR(" + charLength(t) + ")";
            case CSTRING -> "CSTRING(" + charLength(t) + ")";
            case BOOLEAN -> "BOOLEAN";
            case DECFLOAT16 -> "DECFLOAT(16)";
            case DECFLOAT34 -> "DECFLOAT(34)";
            case BLOB -> blob(t);
            default -> "UNKNOWN_TYPE_" + t.code();
        };
    }

    /** CHAR, VARCHAR, CSTRING or a text BLOB: the types that carry a character set. */
    public static boolean isText(FieldType t) {
        return t.code() == CHAR || t.code() == VARCHAR || t.code() == CSTRING
            || (t.code() == BLOB && subType(t) == 1);
    }

    public static boolean isBlob(FieldType t) {
        return t.code() == BLOB;
    }

    /** Normalized {@code sqlType} for BLOBs: {@code text} for SUB_TYPE TEXT, {@code blob} otherwise. */
    public static String blobSqlType(FieldType t) {
        if (!isBlob(t)) return null;
        return subType(t) == 1 ? "text" : "blob";
    }

    private static String exact(FieldType t, String plain, int defaultPrecision) {
        int sub = subType(t);
        if (sub == 1 || sub == 2 || scale(t) > 0) {
            String kind = sub == 2 ? "DECIMAL" : "NUMERIC";
            return kind + "(" + precision(t, defaultPrecision) + "," + scale(t) + ")";
        }
        return plain;
    }

    private static String blob(FieldType t) {
        int sub = subType(t);
        String base = switch (sub) {
            case 0 -> "BLOB SUB_TYPE BINARY";
            case 1 -> "BLOB SUB_TYPE TEXT";
            default -> "BLOB SUB_TYPE " + sub;
        };
        Integer seg = t.segmentLength();
        return seg != null && seg != DEFAULT_SEGMENT ? base + " SEGMENT SIZE " + seg : base;
    }

    private static int subType(FieldType t) {
        return t.subType() == null ? 0 : t.subType();
    }

    /** Positive scale (number of decimal places). */
    private static int scale(FieldType t) {
        return t.scale() == null ? 0 : -t.scale();
    }

    private static int precision(FieldType t, int fallback) {
        return t.precision() == null || t.precision() <= 0 ? fallback : t.precision();
    }

    private static int charLength(FieldType t) {
        if (t.charLength() != null && t.charLength() > 0) return t.charLength();
        int bytes = t.fieldLength() == null ? 0 : t.fieldLength();
        int perChar = t.bytesPerChar() == null || t.bytesPerChar() <= 0 ? 1 : t.bytesPerChar();
        return bytes / perChar;
    }
}
