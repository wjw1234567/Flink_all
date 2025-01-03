package org.window;
import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.bean.WaterSensor;


/**
 *
 *
 * AggregateFunction： AggregateFunction主要用于对窗口内的数据进行聚合操作，它只能访问窗口内的一个元素，并对其进行聚合。AggregateFunction需要实现四个方法：createAccumulator、add、getResult和merge。其中，createAccumulator用于创建累加器，add用于将元素添加到累加器中，getResult用于从累加器中获取聚合结果，merge用于合并两个累加器。
 *
 */

public class WindowAggregateDemo {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env
                .socketTextStream("192.168.133.5", 7777)
                .map(new WaterSensorMapFunction());


        KeyedStream<WaterSensor, String> sensorKS = sensorDS.keyBy(sensor -> sensor.getId());

        // 1. 窗口分配器
        WindowedStream<WaterSensor, String, TimeWindow> sensorWS = sensorKS.window(TumblingProcessingTimeWindows.of(Time.seconds(10)));

        // 统计窗口聚合  增量聚合，窗口10秒就统计10秒内的数据

        SingleOutputStreamOperator<String> aggregate = sensorWS
                .aggregate(
                        new AggregateFunction<WaterSensor, Integer, String>() {
                            @Override
                            public Integer createAccumulator() {
                                System.out.println("创建累加器");
                                return 0;
                            }

                            @Override
                            public Integer add(WaterSensor value, Integer accumulator) {
                                System.out.println("调用add方法,value="+value+"-----"+value.getId());
                                return accumulator + value.getVc();
                            }

                            @Override
                            public String getResult(Integer accumulator) {
                                System.out.println("调用getResult方法"+accumulator.toString());
                                return accumulator.toString();
                            }

                            @Override
                            public Integer merge(Integer a, Integer b) {
                                System.out.println("调用merge方法");
                                return null;
                            }
                        }
                );

        aggregate.print();

        env.execute();
    }


}
