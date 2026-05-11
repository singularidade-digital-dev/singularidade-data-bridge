package digital.singularidade.databridge.source;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.output.UniqueConstraint;

import java.util.List;

public interface Source extends AutoCloseable {

    String type();
    String driverWireName();

    TableInfo tableInfo(String schema, String table);
    List<Column> columns(String schema, String table);
    List<String> primaryKey(String schema, String table);
    List<ForeignKey> foreignKeys(String schema, String table);
    List<Index> indexes(String schema, String table);
    List<UniqueConstraint> uniqueConstraints(String schema, String table);
    List<CheckConstraint> checkConstraints(String schema, String table);
    Sample sample(String schema, String table, int limit);
    List<ColumnStats> columnStats(String schema, String table);
    Partitioning partitioning(String schema, String table);
    /** Equivalent to {@link #cardinality(String, String, java.util.List, CardinalityMode)} with {@code EXACT}. */
    default Cardinality cardinality(String schema, String table, List<Column> columns) {
        return cardinality(schema, table, columns, CardinalityMode.EXACT);
    }

    /**
     * Compute total row count and per-column distinct/null counts. Behavior depends on {@code mode}.
     * BLOB / CLOB / NCLOB / LONGVARBINARY / LONGVARCHAR columns are always omitted from the per-column
     * result regardless of mode (they're either expensive or impossible to {@code COUNT DISTINCT}).
     * Pipeline post-processing turns those omissions into warnings on the {@code Metadata.warnings} list.
     */
    Cardinality cardinality(String schema, String table, List<Column> columns, CardinalityMode mode);

    List<String> listTables(String schema);

    /** Same as {@link #listTables(String)} but optionally includes views (and materialized views, where supported). */
    default List<String> listTables(String schema, boolean includeViews) {
        return listTables(schema);
    }

    /**
     * Execute one SQL statement and return the result. Read-only by default; pass {@code writable=true} to
     * permit DML (INSERT/UPDATE/DELETE). DDL is rejected unconditionally. Multi-statement input is rejected.
     *
     * @param sql           the SQL — must be a single statement
     * @param limit         max rows the result set will contain (defense against accidental dumps)
     * @param timeoutSec    JDBC query timeout in seconds (0 = no timeout)
     * @param writable      if false, only SELECT/WITH/EXPLAIN/SHOW are accepted; if true, DML is also accepted
     */
    QueryResult executeQuery(String sql, int limit, int timeoutSec, boolean writable);

    @Override void close();
}
