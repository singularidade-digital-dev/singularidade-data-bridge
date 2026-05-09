package digital.singularidade.databridge.source.jdbc;

import java.sql.Types;

public final class TypeNormalization {

    private TypeNormalization() {}

    public static String toSqlType(int jdbcType, String driverTypeName,
                                    Integer charLen, Integer precision, Integer scale) {
        return switch (jdbcType) {
            case Types.BIGINT -> "bigint";
            case Types.INTEGER -> "integer";
            case Types.SMALLINT -> "smallint";
            case Types.TINYINT -> "tinyint";
            case Types.BOOLEAN, Types.BIT -> "boolean";
            case Types.REAL -> "real";
            case Types.FLOAT, Types.DOUBLE -> "double";
            case Types.DATE -> "date";
            case Types.TIME -> "time";
            case Types.TIME_WITH_TIMEZONE -> "time with time zone";
            case Types.TIMESTAMP -> "timestamp";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "timestamp with time zone";
            case Types.VARCHAR, Types.LONGVARCHAR ->
                charLen != null ? "varchar(" + charLen + ")" : "varchar";
            case Types.NVARCHAR, Types.LONGNVARCHAR ->
                charLen != null ? "nvarchar(" + charLen + ")" : "nvarchar";
            case Types.CHAR ->
                charLen != null ? "char(" + charLen + ")" : "char";
            case Types.NCHAR ->
                charLen != null ? "nchar(" + charLen + ")" : "nchar";
            case Types.NUMERIC, Types.DECIMAL ->
                precision != null
                    ? "numeric(" + precision + "," + (scale != null ? scale : 0) + ")"
                    : "numeric";
            case Types.CLOB -> "clob";
            case Types.NCLOB -> "nclob";
            case Types.BLOB -> "blob";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                "BYTEA".equalsIgnoreCase(driverTypeName) ? "bytea" : "binary";
            case Types.OTHER, Types.SQLXML, Types.ARRAY, Types.STRUCT ->
                driverTypeName != null ? driverTypeName.toLowerCase() : "other";
            default -> driverTypeName != null ? driverTypeName.toLowerCase() : "unknown";
        };
    }
}
