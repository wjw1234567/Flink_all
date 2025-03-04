package org.FlinkCDC;

import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;



public class MysqltoPgsqlDemo {



    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(2);

        //修改taskmanager的内存
        Configuration conf = new Configuration();
        // 设置TaskManager进程总内存为4GB（包含堆内外内存）
        conf.setString("taskmanager.memory.process.size", "4096m");



        // 代码中用到hdfs，需要导入hadoop依赖、指定访问hdfs的用户名
        System.setProperty("HADOOP_USER_NAME", "hadoop");

        env.enableCheckpointing(10*60*1000L, CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setCheckpointStorage("hdfs://node1:8020/flink/SavePoint_pg");




        // 1. 配置MySQL CDC Source（支持全量+增量）4][9]
        MySqlSource<String> source = MySqlSource.<String>builder()
                .hostname("node1")
                .port(3306)
                .databaseList("Parking")
                .tableList("Parking.ws_datagen")
                .username("root")
                .password("root")
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();

        // 2. 构建数据处理管道
/*        env.fromSource(source, WatermarkStrategy.noWatermarks(), "MySQL CDC Source")
                .map(json -> {
                    // 解析Debezium JSON（示例结构需与实际表结构匹配）
                    JsonNode obj =new ObjectMapper().readTree(json);
                    String op = obj.get("op").asText(); // "c"=insert, "d"=delete
                    JsonNode after = obj.get("after");
                    JsonNode before = obj.get("before");

                    // 返回包含操作类型和数据的POJO
                    return new DataEvent(
                            op,
                            after.get("id").asInt(),
                            after.get("name").asText(),
                            before.get("id").asInt()
                    );
                })
                .addSink(
                        JdbcSink.sink(
                                // 动态SQL生成（根据操作类型）[3][6]
                                "INSERT INTO target_table (id, name) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name",
                               // "DELETE FROM target_table WHERE id=?",


                                (stmt, event) -> {
                                    if ("d".equals(event.op)) {
                                        stmt.setInt(1, event.beforeId); // DELETE使用before数据
                                    } else {
                                        stmt.setInt(1, event.id);       // INSERT/UPDATE使用after数据
                                        stmt.setString(2, event.name);
                                    }
                                },
                                // ✅ 批量配置：每100条或5秒提交[5]
                                JdbcExecutionOptions.builder()
                                        .withBatchSize(1000)
                                        .withBatchIntervalMs(5000)
                                        .build(),
                                // ✅ PostgreSQL连接配置（带批量优化）[7]
                                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                        .withUrl("jdbc:postgresql://pg-host:5432/target_db?reWriteBatchedInserts=true")
                                        .withDriverName("org.postgresql.Driver")
                                        .withUsername("pg_user")
                                        .withPassword("pg_pass")
                                        .build()
                        )*/

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "MySQL CDC Source")
                .keyBy(r->1)
                .process(new BatchProcessor_pg(10000,3000L))
                .addSink(new PgsqlBatchSink());


        env.execute("MySQL->PostgreSQL Sync");
    }





    // 数据事件POJO（根据实际表结构扩展）
    private static class DataEvent {
        public String op;
        public int id;
        public String name;
        public int beforeId;

        public DataEvent(String op, int id, String name, int beforeId) {
            this.op = op;
            this.id = id;
            this.name = name;
            this.beforeId = beforeId;
        }
    }




}



