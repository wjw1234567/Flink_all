package org.window;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;


/**
 *
 * ProcessWindowFunction： ProcessWindowFunction主要用于对窗口内的数据进行自定义处理
 *   ，它可以访问窗口内的所有元素，并对这些元素进行处理
 *    。ProcessWindowFunction需要实现process方法，该方法接收一个键、一个窗口、一个迭代器（包含窗口内的所有元素）和一个输出收集器作为参数。在process方法中，可以对窗口内的元素进行处理，并将结果添加到输出收集器中
 *
 */

public class WindowProcessDemo {


    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env
                .socketTextStream("192.168.254.128", 7777)
                .map(new WaterSensorMapFunction());

        KeyedStream<WaterSensor, String> sensorKS = sensorDS.keyBy(sensor -> sensor.getId());

        // 1. 窗口分配器  TumblingProcessingTimeWindows（滚动时间窗口）
        // 2. 窗口分配器  SlidingProcessingTimeWindows（滑动时间窗口）
        WindowedStream<WaterSensor, String, TimeWindow> sensorWS = sensorKS.window(TumblingProcessingTimeWindows.of(Time.seconds(10)));

        SingleOutputStreamOperator<String> process = sensorWS
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

        process.print();

        env.execute();
    }





}
