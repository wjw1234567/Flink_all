package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.math3.fitting.leastsquares.EvaluationRmsChecker;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;


/**
 *
 * 检测每种传感器的水位值，如果连续的两个水位值超过10，就输出报警
 *
 */


public class keyedStateTTLDemo {

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

                        // todo 创建stateTTLconfig
                        StateTtlConfig stateTtlConfig =
                                StateTtlConfig.newBuilder(Time.seconds(5))// 过期时间5S
                                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite) // 状态创建和写入(更新)更新过期时间
                                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired) // 不返回过期状态值
                                .build();

                        // 状态描述器，第一个参数：取个名字；第二个参数：存储类型，
                        ValueStateDescriptor<Integer> stateDescriptor = new ValueStateDescriptor<>("lastVcState", Types.INT);
                        //状态描述器启用TTL
                        stateDescriptor.enableTimeToLive(stateTtlConfig);

                        this.lastVcState =getRuntimeContext().getState(stateDescriptor);

                    }

                    @Override
                    public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {

                        // 先获取状态值
                        Integer lastVc = lastVcState.value();
                        out.collect("key="+value.getId()+",状态值="+lastVc);

                        //更新状态值
                        lastVcState.update(value.getVc());


                    }
                }).print();



        env.execute();


    }


}
