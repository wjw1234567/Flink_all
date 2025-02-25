package org.sink;


import org.Partition.WaterSensorMapFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;

import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.bean.WaterSensor;

import java.sql.PreparedStatement;
import java.sql.SQLException;


public class SinkMySQL {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);



        //TODO 开启changeLog
        //要求checkpoint的最大并发必须为1，其他参数建议再flink-conf配置文件中指定
        env.enableChangelogStateBackend(true);



       // 代码中用到hdfs，需要导入hadoop依赖、指定访问hdfs的用户名
        System.setProperty("HADOOP_USER_NAME", "hadoop");

        //TODO 检查点配置
        //1.启用检查点，默认是barrier对齐的,周期为5S,精准一次
        env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE);
        //2.指定检查点的存储位置,支持HDFS，也支持本地路径
        CheckpointConfig checkpointConfig = env.getCheckpointConfig();

        // 2、指定检查点的存储位置
        checkpointConfig.setCheckpointStorage("hdfs://node1:8020/flink/SavePoint");
        // 3、checkpoint的超时时间: 默认10分钟
        checkpointConfig.setCheckpointTimeout(60000);

        //设置超时时间
        checkpointConfig.setCheckpointTimeout(60000);
        //同时运行中的checkpoint的最大数量
        checkpointConfig.setMaxConcurrentCheckpoints(2);

        //取消作业时，checkpoint的数据是否保留在外部系统
        // DELETE_ON_CANCELLATION  取消作业时表示删除
        // RETAIN_ON_CANCELLATION  取消作业时表示保留

        checkpointConfig.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        // 7、允许 checkpoint 连续失败的次数，默认0--》表示checkpoint一失败，job就挂掉
        checkpointConfig.setTolerableCheckpointFailureNumber(10);





        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("node1", 7777)
                .map(new WaterSensorMapFunction());


        /**
         * TODO 写入mysql
         * 1、只能用老的sink写法： addsink
         * 2、JDBCSink的4个参数:
         *    第一个参数： 执行的sql，一般就是 insert into
         *    第二个参数： 预编译sql， 对占位符填充值
         *    第三个参数： 执行选项 ---》 攒批、重试
         *    第四个参数： 连接选项 ---》 url、用户名、密码
         */
        SinkFunction<WaterSensor> jdbcSink = JdbcSink.sink(
                "insert into ws values(?,?,?)",
                new JdbcStatementBuilder<WaterSensor>() {
                    @Override
                    public void accept(PreparedStatement preparedStatement, WaterSensor waterSensor) throws SQLException {
                        //每收到一条WaterSensor，如何去填充占位符
                        preparedStatement.setString(1, waterSensor.getId());
                        preparedStatement.setLong(2, waterSensor.getTs());
                        preparedStatement.setInt(3, waterSensor.getVc());
                    }
                },
                JdbcExecutionOptions.builder()
                        .withMaxRetries(3) // 重试次数
                        .withBatchSize(100) // 批次的大小：条数
                        .withBatchIntervalMs(3000) // 批次的时间
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:mysql://node1:3306/Parking?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8")
                        .withUsername("root")
                        .withPassword("root")
                        .withConnectionCheckTimeoutSeconds(60) // 重试的超时时间
                        .build()
        );


        sensorDS.addSink(jdbcSink);


        env.execute();
    }


}
