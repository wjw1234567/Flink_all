package org.watermark;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.bean.WaterSensor;

import java.time.Duration;

public class WatermarkAllowLatenessDemo {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /**
         *
         * 窗口允许迟到，推迟关窗时间，即使水位线到达了，还不能触发窗口
         * ，还会来一次计算一次。但是出现数据>水位线-水位线推迟时间-窗口推迟时间就会触发窗口
         *
         * */

         env.setParallelism(1);
        //env.setParallelism(2);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("192.168.254.128", 7777).map(new WaterSensorMapFunction());


        // TODO 1.定义Watermark策略
        // forBoundedOutOfOrderness表示乱序水位线，延迟3S
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy.<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3)) //乱序程度延迟3S
                .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                    @Override
                    public long extractTimestamp(WaterSensor element, long recordTimestamp) {
                        // 返回的时间戳，要 毫秒
                        System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                        return element.getTs() * 1000L;
                    }
                });//设置定义的水位线

        // TODO 2.指定Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDSwithWatermark = sensorDS.assignTimestampsAndWatermarks(watermarkStrategy);



    //定义侧输出流
        OutputTag<WaterSensor> lateTag = new OutputTag<>("late-data", Types.POJO(WaterSensor.class));


        SingleOutputStreamOperator<String> process = sensorDSwithWatermark.keyBy(sensor -> sensor.getId())
                //TODO 根据ID创建窗口函数统计,使用事件时间窗口，水位线才能起作用

                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .allowedLateness(Time.seconds(2)) //推迟2S关窗
                .sideOutputLateData(lateTag) //关窗后的迟到数据放到侧输出流进 行特殊处理
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

                );



        //主输出流打印
        process.print();
        //侧输出流打印
        process.getSideOutput(lateTag).printToErr();




        env.execute();
    }

}
