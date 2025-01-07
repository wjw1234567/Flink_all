package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;
import java.util.Map;


/**
 *
 * 检测每种传感器的水位值，计算每种传感器的水位和
 *
 */


public class keyedReducingStateDemo {

    public static void main(String[] args)  throws Exception{

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        env.setParallelism(1);
        // env.setParallelism(2);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("192.168.254.128", 7777).map(new WaterSensorMapFunction())
             .assignTimestampsAndWatermarks(

                     WatermarkStrategy.<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3)) //延迟3S
                             .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                                 @Override
                                 public long extractTimestamp(WaterSensor element, long recordTimestamp) {
                                     // 返回的时间戳，要 毫秒
                                     // System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                                     return element.getTs() * 1000L;
                                 }
                             })


             );



        sensorDS.keyBy(r -> r.getId())
                .process(
                        new KeyedProcessFunction<String, WaterSensor, String>() {
                            //定义初始化
                            //key传一个水位值，value传次数count
                            ReducingState<Integer> vcSumReducingState;

                            @Override
                            public void open(Configuration parameters) throws Exception {
                                super.open(parameters);
                                vcSumReducingState=getRuntimeContext().getReducingState(new ReducingStateDescriptor<Integer>("vcSumReducingState",
                                        new ReduceFunction<Integer>() {
                                            @Override
                                            public Integer reduce(Integer value1, Integer value2) throws Exception {
                                                return value1+value2;
                                            }
                                        }
                                        , Types.INT));


                            }

                            @Override
                            public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                //来一条数据添加到reduceing状态里面
                                vcSumReducingState.add(value.getVc());

                                out.collect("传感器id为"+value.getId()+"水位值的总和="+vcSumReducingState.get());

                            }
                        }
                ).print();



        env.execute();


    }


}
