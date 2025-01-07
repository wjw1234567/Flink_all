package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.state.AggregatingState;
import org.apache.flink.api.common.state.AggregatingStateDescriptor;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;


/**
 *
 * 检测每种传感器的水位值，计算每种传感器的平均水位
 *
 */


public class keyedAggregateStateDemo {

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
                            //key传一个水位值，输出是平均值
                            AggregatingState<Integer,Double> vcAvgAggregatState;

                            //定义一个tuple2存入水位值的总和 和 count次数

                            @Override
                            public void open(Configuration parameters) throws Exception {
                                super.open(parameters);
                                vcAvgAggregatState=getRuntimeContext()
                                        .getAggregatingState(

                                            new AggregatingStateDescriptor<Integer, Tuple2<Integer,Integer>, Double>(
                                                "vcAvgAggregatState",


                                                new AggregateFunction<Integer, Tuple2<Integer, Integer>, Double>() {
                                                    @Override
                                                    public Tuple2<Integer, Integer> createAccumulator() {
                                                        return Tuple2.of(0,0);
                                                    }

                                                    @Override
                                                    public Tuple2<Integer, Integer> add(Integer value, Tuple2<Integer, Integer> accumulator) {
                                                        return Tuple2.of(accumulator.f0+value,accumulator.f1+1);
                                                    }

                                                    @Override
                                                    public Double getResult(Tuple2<Integer, Integer> accumulator) {
                                                        return accumulator.f0*1D/accumulator.f1;
                                                    }

                                                    @Override
                                                    public Tuple2<Integer, Integer> merge(Tuple2<Integer, Integer> a, Tuple2<Integer, Integer> b) {
                                                        return null;
                                                    }
                                                },

                                                //ACC累加器的类型
                                                Types.TUPLE(Types.INT, Types.INT)

                                        ));


                            }

                            @Override
                            public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                                //将水位值添加到聚合状态中
                                vcAvgAggregatState.add(value.getVc());

                                Double vcAvg = vcAvgAggregatState.get();



                                out.collect("传感器id为"+value.getId()+"水位值的平均值="+vcAvg);

                            }
                        }
                ).print();



        env.execute();


    }


}
