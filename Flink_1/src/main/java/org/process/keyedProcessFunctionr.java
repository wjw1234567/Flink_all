package org.process;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;

public class keyedProcessFunctionr {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


         env.setParallelism(1);
        // env.setParallelism(2);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("192.168.254.128", 7777).map(new WaterSensorMapFunction());


        // TODO 1.定义Watermark策略
        // forBoundedOutOfOrderness表示乱序水位线，延迟3S
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy.<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3)) //延迟3S
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


        KeyedStream<WaterSensor, String> sensorDs = sensorDSwithWatermark.keyBy(sensor -> sensor.getId());

        sensorDs.process(new KeyedProcessFunction<String, WaterSensor, Object>() {
            @Override
            public void processElement(WaterSensor value, Context ctx, Collector<Object> out) throws Exception {

                    //数据中提取出来的事件时间
                Long ts = ctx.timestamp();
                String currentKey = ctx.getCurrentKey();


                //定时器
                TimerService timerService = ctx.timerService();



                // 注册定时器 事件时间
                //timerService.registerEventTimeTimer(5000L);
                //timerService.deleteEventTimeTimer(5000L);
                //System.out.println("当前时间是"+ts+"注册了一个5秒的定时器");


                // 获取当前时间进展 处理时间-当前系统时间 事件时间-当前waterMark
                long currentTs = timerService.currentProcessingTime();
                timerService.registerProcessingTimeTimer(currentTs+5000L);
                System.out.println("当前的key值是"+currentKey+",当前时间="+currentTs+",注册了一个5s后的定时器");


                //获取当前水位线
                long cw = timerService.currentWatermark();


            }

            /**
             *
             * 时间进展到定时器注册时间，调用改方法，类似于闹钟响了
             * @param timestamp 当前时间进展
             * @param ctx     上下文
             * @param out     采集器
             * @throws Exception
             */
            @Override
            public void onTimer(long timestamp, OnTimerContext ctx, Collector<Object> out) throws Exception {
                super.onTimer(timestamp, ctx, out);
                String currentKey = ctx.getCurrentKey();

                System.out.println("当前的key是:"+currentKey+"现在的时间是"+timestamp+"定时器触发");
            }


        }).print();


        env.execute();
    }

}


/**
 *
 * todo 定时器总结
 * 1.keyed才有
 * 2.事件时间定时器，通过watermark触发的
 *    watermark>注册时间
 *    注意：watermark=当前最大事件时间-等待时间-1ms
 *
 * 3.在process中获取当前的watermark，显示的是上一次的watermark
 * 因为process还没接收到这条数据对应生成的信watermark
 *
 */
