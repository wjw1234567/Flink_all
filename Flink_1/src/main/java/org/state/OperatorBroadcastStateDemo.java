package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.*;
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
import org.apache.flink.util.Collector;
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

        // TODO 1.将配置流广播出去,配置流的状态描述器
        // 配置流的key写死thresold，value是Integer类型
        MapStateDescriptor<String, Integer> broadcastMapstate = new MapStateDescriptor<>("broadcast-state", Types.STRING, Types.INT);

        // 得到带有广播状态的广播流
        BroadcastStream<String> configBs = configDs.broadcast(broadcastMapstate);


        // TODO 2.将数据流 和 广播后配置流 connect

        BroadcastConnectedStream<WaterSensor, String> SensorBCs = SensorDs.connect(configBs);

        // TODO 3.调用process
        SensorBCs.process(
                new BroadcastProcessFunction<WaterSensor, String, String>() {

                    /**
                     * 数据流的处理方法
                     * @param value The stream element.
                     * @param ctx A {@link ReadOnlyContext} that allows querying the timestamp of the element,
                     *     querying the current processing/event time and updating the broadcast state. The context
                     *     is only valid during the invocation of this method, do not store it.
                     * @param out The collector to emit resulting elements to
                     * @throws Exception
                     */

                    @Override
                    public void processElement(WaterSensor value, BroadcastProcessFunction<WaterSensor, String, String>.ReadOnlyContext ctx, Collector<String> out) throws Exception {

                        // TODO 5.通过上下文获取广播状态，取出里面的数据,只能读不能修改
                        ReadOnlyBroadcastState<String, Integer> broadcastState = ctx.getBroadcastState(broadcastMapstate);
                        Integer thresold = broadcastState.get("thresold");
                        //考虑到数据流的先来后到
                        thresold= thresold==null?0:thresold;

                        if (value.getVc()>thresold){
                            out.collect(value+"当前水位值超过指定阈值："+thresold+"!!!!");
                        }

                    }


                    /**
                     * 广播配置流的处理方法
                     * @param value The stream element.
                     * @param ctx A {@link Context} that allows querying the timestamp of the element, querying the
                     *     current processing/event time and updating the broadcast state. The context is only valid
                     *     during the invocation of this method, do not store it.
                     * @param out The collector to emit resulting elements to
                     * @throws Exception
                     */

                    @Override
                    public void processBroadcastElement(String value, BroadcastProcessFunction<WaterSensor, String, String>.Context ctx, Collector<String> out) throws Exception {
                        // TODO 4.1 通过上下文获取了广播状态,可以当作map使用
                        BroadcastState<String, Integer> broadcastState = ctx.getBroadcastState(broadcastMapstate);

                        // TODO 4.2 往广播状态插入数据,现在是暂时写死key值，只能存一个
                        broadcastState.put("thresold", Integer.valueOf(value));


                    }
                }


        ).print();

        env.execute();

    }

}
