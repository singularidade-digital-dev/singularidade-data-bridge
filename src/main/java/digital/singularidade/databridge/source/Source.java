package digital.singularidade.databridge.source;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Partitioning;
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
    Cardinality cardinality(String schema, String table, List<Column> columns);

    List<String> listTables(String schema);

    @Override void close();
}
