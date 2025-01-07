package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;


/**
 *
 * 检测每种传感器的水位值，统计每种传感器每种水位值出现的次数
 *
 */


public class keyedMapStateDemo {

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
                            MapState<Integer,Integer> vcCountMapState;

                            @Override
                            public void open(Configuration parameters) throws Exception {
                                super.open(parameters);
                                vcCountMapState=getRuntimeContext().getMapState(new MapStateDescriptor<Integer,Integer>("vcCountMapState",Types.INT,Types.INT));


                            }

                            @Override
                            public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {

                                //判断是否存在vc对应的key

                                Integer vc = value.getVc();
                                if (vcCountMapState.contains(vc)) {
                                    Integer count= vcCountMapState.get(vc);
                                    vcCountMapState.put(vc,++count);

                                }else {
                                    //如果不包含vc,则存进去
                                    vcCountMapState.put(vc,1);
                                }


                                StringBuilder outStr = new StringBuilder();
                                outStr.append("传感器id为"+value.getId()+"\n");
                                for (Map.Entry<Integer, Integer> vcCount : vcCountMapState.entries()) {
                                   outStr.append(vcCount.toString()+"\n");

                                }

                                outStr.append("vcCountMapState的迭代器为"+vcCountMapState.iterator().next()+"\n");
                                outStr.append("vcCountMapState的entries为"+vcCountMapState.entries().toString()+"\n");
                                outStr.append("===================>");


                                out.collect(outStr.toString());


                                /*
                                vcCountMapState.entries();
                                vcCountMapState.put();
                                vcCountMapState.contains();
                                vcCountMapState.entries();
                                vcCountMapState.keys();
                                vcCountMapState.iterator();
                                vcCountMapState.isEmpty();
                                vcCountMapState.remove();
                                vcCountMapState.clear();
                                */




                            }
                        }
                ).print();



        env.execute();


    }


}
