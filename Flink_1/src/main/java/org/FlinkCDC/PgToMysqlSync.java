package org.FlinkCDC;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.ververica.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import com.ververica.cdc.connectors.postgres.source.PostgresSourceBuilder;
import com.ververica.cdc.debezium.DebeziumSourceFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
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
import org.apache.flink.streaming.api.functions.source.SourceFunction;

public class PgToMysqlSync {
    public static void main(String[] args) throws Exception {


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







        SourceFunction<String> sourceFunction = PostgreSQLSource.<String>builder()
                .hostname("node1")
                .port(5432)
                .database("db_pg1") // monitor postgres database
                .schemaList("parking")  // monitor inventory schema
                .tableList("parking.ws_pg") // monitor products table
                .username("postgres")
                .password("postgres")
                .decodingPluginName("pgoutput")
                .deserializer(new JsonDebeziumDeserializationSchema()) // converts SourceRecord to JSON String
                .build();



        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        env.setRestartStrategy(
                RestartStrategies.fixedDelayRestart(3, Time.seconds(2))
        );


        // 2. 构建数据流
        env
                .setParallelism(2)
                .addSource(sourceFunction)
                .assignTimestampsAndWatermarks(WatermarkStrategy.noWatermarks())
                // .fromSource(postgresSource, WatermarkStrategy.noWatermarks(), "PostgreSQL Source")
                .map(json -> {
                    // 解析Debezium JSON格式，提取操作类型和数据[7]


                    JsonNode obj =new ObjectMapper().readTree(json);
                    String op = obj.get("op").asText(); // "c"=insert, "d"=delete
                    JsonNode after = obj.get("after");
                    JsonNode before = obj.get("before");





                    return new Tuple3<String,JsonNode,String>(op,
                            after != null ? after : before,
                            obj.get("source").get("ts_ms").asText() );



                })
                .returns(Types.TUPLE(Types.STRING, TypeInformation.of(JsonNode.class),Types.STRING))
                //.print();
                .addSink(
                        JdbcSink.sink(
                                // 动态生成SQL[2][7]
                                "replace INTO ws_frompg VALUES (?,?,?) ; " ,
                                       // + "DELETE FROM ws_frompg WHERE id=?;",
                                (statement, tuple) -> {
                                    String op = tuple.f0;
                                   // JSONObject data = new JSONObject(tuple.f1);

                                    JsonNode data = tuple.f1;


                                    if ("r".equals(op) || "c".equals(op)) { // 插入/创建
                                        statement.setString(1, data.get("id").asText());
                                        statement.setInt(2, data.get("ts").asInt());
                                        statement.setInt(3, data.get("vc").asInt());
                                    } else if ("d".equals(op)) { // 删除
                                        statement.setString(4, data.get("id").asText());
                                    }
                                },
                                // 批量执行配置[2][9]
                                new JdbcExecutionOptions.Builder()
                                        .withBatchSize(7)  // 每100条提交一次
                                        .withBatchIntervalMs(2000)
                                        .withMaxRetries(3)
                                        .build(),
                                // MySQL连接配置
                                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                        .withUrl("jdbc:mysql://node1:3306/Parking")
                                        .withDriverName("com.mysql.cj.jdbc.Driver")
                                        .withUsername("root")
                                        .withPassword("root")
                                        .build()
                        )
                );

        env.execute("PostgreSQL to MySQL Sync");
    }






}