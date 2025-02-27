package org.FlinkCDC;

import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import com.ververica.cdc.debezium.StringDebeziumDeserializationSchema;
import org.apache.commons.collections4.IterableUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.curator5.com.google.common.collect.Lists;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.List;

public class MySQLToClickHouseSync {


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
        env.getCheckpointConfig().setCheckpointStorage("hdfs://node1:8020/flink/SavePoint_ck");


        // CDC Source配置[2][7]
        MySqlSource<String> source = MySqlSource.<String>builder()
                .hostname("node1")
                .port(3306)
                .databaseList("Parking")
                .tableList("Parking.ws_datagen")
                .username("root")
                .password("root")
               // .deserializer(new StringDebeziumDeserializationSchema())
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();


        DataStreamSource<String> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Mysql-source");


        // 批量处理（每10条或每5秒）[7]
        stream
                // .keyBy(r -> new ObjectMapper().readTree(r).get("op").asText()   )//根据增删改类型进行分组
                .keyBy(r->1)
/*                .process(new KeyedProcessFunction<Integer, String, List<String>>() {


                    private transient ListState<String> bufferState;

                    private transient ValueState<Long> timerState; //用于记录是否有新的定时器，过了5秒执行就清空需要重新定义一个定时器，如果为空，就需要初始化定时器。定义定时器填入时间
                    private final int batchSize = 5;
                    private final long timeout = 3000L; // 5秒


                    @Override
                    public void open(Configuration parameters) {
                        // 初始化批量缓冲状态
                        bufferState = getRuntimeContext().getListState(
                                new ListStateDescriptor<String>("buffer", Types.STRING));
                        // 初始化定时器状态
                        timerState = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Long.class));
                    }

                    @Override
                    public void processElement(String value, Context ctx, Collector<List<String>> out) throws Exception {


                        System.out.println("binlog插入的数据"+new ObjectMapper().readTree(value).get("after").toString());
                        System.out.println("binlog删除的数据"+new ObjectMapper().readTree(value).get("before").toString());

                        bufferState.add(value);

                        // 首次记录时注册定时器
                        if (timerState.value() == null) {
                            long nextTimer = ctx.timerService().currentProcessingTime() + timeout;
                            ctx.timerService().registerProcessingTimeTimer(nextTimer);
                            timerState.update(nextTimer);
                        }


                        // 数量达到阈值时提前触发向下游发送数据
                        if (IterableUtils.size(bufferState.get()) >= batchSize) {
                            flushBuffer(out, ctx.timerService());
                        }

                    }


                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<List<String>> out) throws Exception {

                        // 超时触发提交向下游发送数据
                        if (!IterableUtils.isEmpty(bufferState.get())) {
                            flushBuffer(out, ctx.timerService());
                        }
                        timerState.clear(); // 清除当前定时器
                    }


                    private void flushBuffer(Collector<List<String>> out, TimerService timerService) throws Exception {
                        List<String> batch = Lists.newArrayList(bufferState.get());
                        out.collect(batch); // 向下游发送批量数据
                        bufferState.clear();

                        // 注册新定时器
                        long nextTimer = timerService.currentProcessingTime() + timeout;
                        timerService.registerProcessingTimeTimer(nextTimer);
                        timerState.update(nextTimer);
                    }


                })*/

                 .process(new BatchProcessor_ck(10000,3000L))
                 .addSink(new ClickHouseBatchSink())

        ;
                //.print();

        env.execute("MySQL-ClickHouse Sync");
    }


}


