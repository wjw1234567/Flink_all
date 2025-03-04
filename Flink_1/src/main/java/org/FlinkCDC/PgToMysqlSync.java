package org.FlinkCDC;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.ververica.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import com.ververica.cdc.connectors.postgres.source.PostgresSourceBuilder;
import com.ververica.cdc.debezium.DebeziumSourceFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import com.ververica.cdc.connectors.postgres.PostgreSQLSource;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;

public class PgToMysqlSync {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. PostgreSQL CDC Source配置[3][7]

/*        DebeziumSourceFunction<String> postgresSource = PostgreSQLSource.<String>builder()
                .hostname("localhost")
                .port(5432)
                .database("your_db")
                .schemaList("public")
                .tableList("public.your_table")
                .username("postgres")
                .password("postgres")
                .decodingPluginName("pgoutput") // 必须配置逻辑解码插件[3]
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();*/




        JdbcIncrementalSource<String> postgresSource =
                PostgresSourceBuilder.PostgresIncrementalSource.<String>builder()
                        .hostname("node1")
                        .port(5432)
                        .database("db_pg1")
                        .schemaList("parking")
                        .tableList("parking.ws_pg")
                        .username("pgu1")
                        .password("pgu1")
                        .slotName("flink")
                        .decodingPluginName("decoderbufs") // use pgoutput for PostgreSQL 10+
                        .deserializer(new JsonDebeziumDeserializationSchema())
                        .splitSize(2) // the split size of each snapshot split
                        .build();





        // 2. 构建数据流
        env.fromSource(postgresSource, WatermarkStrategy.noWatermarks(), "PostgreSQL Source")
                .map(json -> {
                    // 解析Debezium JSON格式，提取操作类型和数据[7]


                    JsonNode obj =new ObjectMapper().readTree(json);
                    String op = obj.get("op").asText(); // "c"=insert, "d"=delete
                    JsonNode after = obj.get("after");
                    JsonNode before = obj.get("before");





                    return new Tuple3<String,String,String>(op,
                            after != null ? after.asText() : before.asText(),
                            obj.get("source").get("ts_ms").asText() );



                }).print();
/*                .addSink(
                        JdbcSink.sink(
                                // 动态生成SQL[2][7]
                                "INSERT INTO target_table VALUES (?,?) ON DUPLICATE KEY UPDATE data=?; " +
                                        "DELETE FROM target_table WHERE id=?;",
                                (statement, tuple) -> {
                                    String op = tuple.f0;
                                    JSONObject data = new JSONObject(tuple.f1);

                                    if ("r".equals(op) || "c".equals(op)) { // 插入/创建
                                        statement.setString(1, data.getString("id"));
                                        statement.setString(2, data.toString());
                                        statement.setString(3, data.toString());
                                    } else if ("d".equals(op)) { // 删除
                                        statement.setString(4, data.getString("id"));
                                    }
                                },
                                // 批量执行配置[2][9]
                                new JdbcExecutionOptions.Builder()
                                        .withBatchSize(100)  // 每100条提交一次
                                        .withBatchIntervalMs(2000)
                                        .withMaxRetries(3)
                                        .build(),
                                // MySQL连接配置
                                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                        .withUrl("jdbc:mysql://localhost:3306/target_db")
                                        .withDriverName("com.mysql.cj.jdbc.Driver")
                                        .withUsername("root")
                                        .withPassword("root")
                                        .build()
                        )
                );*/

        env.execute("PostgreSQL to MySQL Sync");
    }


  /*  public static class ws_pg  {


        private String id;

        private String op;
        private int ts;
        private int vc;


        public ws_pg(String id, int ts, int vc) {
            this.id = id;
            this.ts = ts;
            this.vc = vc;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getTs() {
            return ts;
        }

        public void setTs(int ts) {
            this.ts = ts;
        }

        public int getVc() {
            return vc;
        }

        public void setVc(int vc) {
            this.vc = vc;
        }


    }*/



}