package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.datastream.BroadcastConnectedStream;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.bean.WaterSensor;


/**
 *
 *  TODO 水位超过指定的阈值发送告警，阈值可以动态修改。
 *
 *
 */



public class OperatorBroadcastStateDemo {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        env.setParallelism(1);
        // env.setParallelism(2);

        SingleOutputStreamOperator<WaterSensor> SensorDs = env
                .socketTextStream("192.168.254.128", 7777)
                .map(new WaterSensorMapFunction());
        


        //配置流（用来广播配置）
        DataStreamSource<String> configDs = env.socketTextStream("192.168.254.128", 8888);

        // TODO 1.将配置流广播出去
        MapStateDescriptor<String, Integer> broadcastMapstate = new MapStateDescriptor<>("broadcast-state", Types.STRING, Types.INT);

        // 得到带有广播状态的广播流
        BroadcastStream<String> configBs = configDs.broadcast(broadcastMapstate);


        // TODO 2.将数据流 和 广播后配置流 connect

        BroadcastConnectedStream<WaterSensor, String> SensorBCs = SensorDs.connect(configBs);

        // TODO 3.调用process
        SensorBCs.process(
                new BroadcastProcessFunction<WaterSensor, String, Object>() {
                }


        )





        env.execute();


    }






}
