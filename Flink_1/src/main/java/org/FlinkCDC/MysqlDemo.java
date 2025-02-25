package org.FlinkCDC;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.CheckpointingMode;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;


public class MysqlDemo {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 开启检查点（每10秒一次，模式为EXACTLY_ONCE）[1,4]
        // 代码中用到hdfs，需要导入hadoop依赖、指定访问hdfs的用户名
        System.setProperty("HADOOP_USER_NAME", "hadoop");

        env.enableCheckpointing(10000, CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setCheckpointStorage("hdfs://node1:8020/flink/SavePoint");


        // 配置MySQL CDC Source（替换实际参数）[6]
        MySqlSource<String> source = MySqlSource.<String>builder()
                .hostname("node1")
                .port(3306)
                .databaseList("Parking")
                .tableList("Parking.ws")
                .username("root")
                .password("root")
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();

        // 数据写入目标MySQL（需自定义Sink逻辑）[6,7]



        DataStreamSource<String> Source_stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Mysql-source");

/*        Source_stream.keyBy(data -> 1).process(new KeyedProcessFunction() {
            @Override
            public void processElement(Object value, Context ctx, Collector out) throws Exception {

            }
        })*/

        Source_stream
                .keyBy(event -> 1)
                .process(new BatchProcessor(5,5000L))
                .addSink(new JdbcBatchSinkFunction());




       //  Source_stream.addSink(new JdbcBatchSinkFunction());
        env.execute("MySQL-to-MySQL-CDC-Sync");


    }
}


