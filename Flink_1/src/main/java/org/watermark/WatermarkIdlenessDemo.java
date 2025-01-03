package org.watermark;

import org.Partition.MyPartitioner;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.TimestampAssigner;
import org.apache.flink.api.common.eventtime.TimestampAssignerSupplier;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.awt.image.ImageProducer;
import java.time.Duration;

public class WatermarkIdlenessDemo {

    public static void main(String[] args)  throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);



        SingleOutputStreamOperator<Integer> sensorDSwithWatermark = env
                .socketTextStream("192.168.254.128", 7777)
                //指定自定义分区
                .partitionCustom(new MyPartitioner(), r -> r)
                .map(r -> Integer.parseInt(r))
                //指定水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Integer>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner((Integer element, long recordTimestamp) -> element * 1000L)
                                .withIdleness(Duration.ofSeconds(5))  //空闲等待5s

                );





        sensorDSwithWatermark.keyBy(sensor -> sensor%2)
                //TODO 根据ID创建窗口函数统计,使用事件时间窗口，水位线才能起作用

                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .allowedLateness(Time.seconds(2)) //推迟2S关窗

                //TODO 窗口处理函数
                .process(

                        new ProcessWindowFunction<Integer, String, Integer, TimeWindow>() {
                            @Override
                            public void process(Integer s, Context context, Iterable<Integer> elements, Collector<String> out) throws Exception {
                                long count = elements.spliterator().estimateSize(); //获取对象个数
                                long windowStartTs = context.window().getStart();  //获取窗口开始时间
                                long windowEndTs = context.window().getEnd();      //获取窗口结束时间
                                String windowStart = DateFormatUtils.format(windowStartTs, "yyyy-MM-dd HH:mm:ss.SSS");
                                String windowEnd = DateFormatUtils.format(windowEndTs, "yyyy-MM-dd HH:mm:ss.SSS");

                                out.collect("key=" + s + "的窗口[" + windowStart + "," + windowEnd + ")包含" + count + "条数据===>" + elements.toString());
                            }

                        }).print();

        env.execute();

    }

}
