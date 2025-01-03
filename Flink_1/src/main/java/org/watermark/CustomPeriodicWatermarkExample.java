package org.watermark;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkGeneratorSupplier;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;

public class CustomPeriodicWatermarkExample {


    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);


        // 默认周期200毫秒
        env.getConfig().setAutoWatermarkInterval(2000);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("192.168.254.128", 7777).map(new WaterSensorMapFunction());





        // TODO 1.定义Watermark策略
        // forBoundedOutOfOrderness表示乱序水位线，延迟3S
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy.<WaterSensor>forGenerator(new WatermarkGeneratorSupplier<WaterSensor>() {
            @Override
            public WatermarkGenerator<WaterSensor> createWatermarkGenerator(Context context) {

               //TODO 指定自定义生成器
                //自定义周期性生成
               // return new MyPeriodWaterMarkGenerator<>(3000L);

                //自定义断电式生成
                return new MyPuntuaedWaterMarkGenerator<>(3000L);
            }
        }) //自定义水位线策略 相当于也是延迟3S

         //指定数据里面的Ts作为事件时间
         .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                    @Override
                    public long extractTimestamp(WaterSensor element, long recordTimestamp) {
                        // 返回的时间戳，要 毫秒
                        System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                        return element.getTs() * 1000L;
                    }
                });



        // TODO 2.指定Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDSwithWatermark = sensorDS.assignTimestampsAndWatermarks(watermarkStrategy);



        sensorDSwithWatermark.keyBy(sensor -> sensor.getId())
                //TODO 根据ID创建窗口函数统计,使用事件时间窗口，水位线才能起作用

                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .allowedLateness(Time.seconds(2)) //推迟2S关窗

                //TODO 窗口处理函数
                .process(

                        new ProcessWindowFunction<WaterSensor, String, String, TimeWindow>() {
                            @Override
                            public void process(String s, Context context, Iterable<WaterSensor> elements, Collector<String> out) throws Exception {


                                long count = elements.spliterator().estimateSize(); //获取对象个数
                                long windowStartTs = context.window().getStart();  //获取窗口开始时间
                                long windowEndTs = context.window().getEnd();      //获取窗口结束时间
                                String windowStart = DateFormatUtils.format(windowStartTs, "yyyy-MM-dd HH:mm:ss.SSS");
                                String windowEnd = DateFormatUtils.format(windowEndTs, "yyyy-MM-dd HH:mm:ss.SSS");

                                out.collect("key=" + s + "的窗口[" + windowStart + "," + windowEnd + ")包含" + count + "条数据===>" + elements.toString());
                            }
                        }

                ).print()

        ;


        env.execute();
    }
}
