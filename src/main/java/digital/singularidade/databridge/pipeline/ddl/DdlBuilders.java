package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.source.jdbc.DriverHints;

public final class DdlBuilders {

    private DdlBuilders() {}

    public static DdlBuilder forDriver(DriverHints hints) {
        return switch (hints) {
            case PG       -> new PostgresDdlBuilder();
            case MYSQL    -> new MysqlDdlBuilder();
            case ORACLE   -> new OracleDdlBuilder();
            case FIREBIRD -> new FirebirdDdlBuilder();
            case MSSQL    -> new MssqlDdlBuilder();
        };
    }
}
