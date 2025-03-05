import com.ververica.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import com.ververica.cdc.connectors.postgres.source.PostgresSourceBuilder;
import com.ververica.cdc.debezium.DebeziumDeserializationSchema;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
/*import org.apache.flink.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import org.apache.flink.cdc.connectors.postgres.source.PostgresSourceBuilder;
import org.apache.flink.cdc.debezium.DebeziumDeserializationSchema;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;*/
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class PostgresParallelSourceExample {

    public static void main(String[] args) throws Exception {

        DebeziumDeserializationSchema<String> deserializer =
                new JsonDebeziumDeserializationSchema();

        JdbcIncrementalSource<String> postgresSource =
                PostgresSourceBuilder.PostgresIncrementalSource.<String>builder()
                        .hostname("node1")
                        .port(5432)
                        .database("db_pg1")
                        .schemaList("parking")
                        .tableList("parking.ws_pg")
                        .username("pgu1")
                        .password("pgu1")
                        //.slotName("flink")
                        .decodingPluginName("decoderbufs") // use pgoutput for PostgreSQL 10+
                        .deserializer(new JsonDebeziumDeserializationSchema())
                        .splitSize(2) // the split size of each snapshot split
                        .build();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(3000);

        env.fromSource(
                        postgresSource,
                        WatermarkStrategy.noWatermarks(),
                        "PostgresParallelSource")
                .setParallelism(2)
                .map(json -> {
                    // 解析Debezium JSON格式，提取操作类型和数据[7]


                    JsonNode obj =new ObjectMapper().readTree(json);
                    String op = obj.get("op").asText(); // "c"=insert, "d"=delete
                    JsonNode after = obj.get("after");
                    JsonNode before = obj.get("before");


                    return new Tuple3<String,String,String>(op,
                            after != null ? after.asText() : before.asText(),
                            obj.get("source").get("ts_ms").asText() );



                })
                .returns(Types.TUPLE(Types.STRING,Types.STRING,Types.STRING))
                .print();

        env.execute("Output Postgres Snapshot");
    }
}

