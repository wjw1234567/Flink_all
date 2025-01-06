package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;


import java.time.Duration;


/**
 *
 * 检测每种传感器的水位值，如果连续的两个水位值超过10，就输出报警
 *
 */


public class keyedValueStateDemo {

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
                .process(new KeyedProcessFunction<String, WaterSensor, String>() {

                    //TODO 定义状态,是会根据key值分开的，如果S1跟S2水位相差10，是不会输出
                    ValueState<Integer> lastVcState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 状态描述器，第一个参数：取个名字；第二个参数：存储类型
                        lastVcState=getRuntimeContext().getState(new ValueStateDescriptor<Integer>("lastVcState", Types.INT));

                    }

                    @Override
                    public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {
                        // 1.取出上一条数据的水位值
                        int lastVc = lastVcState.value() == null ? 0 : lastVcState.value();
                        //当前水位值
                        int Vc = value.getVc();

                        // 2.求差值绝对值，判断是否超过10
                        if (Math.abs(Vc - lastVc)>10) {
                            out.collect("传感器=" + value.getId() + "==>当前水位值=" + Vc + ",与上一条水位值=" + lastVc + ",相差超过10！！！！");
                        };


                        // 3.保存更新自己的水位值
                        lastVcState.update(Vc);

                    }
                }).print();



        env.execute();


    }


}
