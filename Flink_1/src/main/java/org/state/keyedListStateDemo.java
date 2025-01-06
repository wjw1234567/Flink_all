package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;
import java.util.ArrayList;


/**
 *
 * 检测每种传感器的水位值，如果连续的两个水位值超过10，就输出报警
 *
 */


public class keyedListStateDemo {

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
                            ListState<Integer> vcListState;

                            @Override
                            public void open(Configuration parameters) throws Exception {
                                super.open(parameters);
                                vcListState=getRuntimeContext().getListState(new ListStateDescriptor<Integer>("vcListState",Types.INT));
                            }

                            @Override
                            public void processElement(WaterSensor value, Context ctx, Collector<String> out) throws Exception {

                                //来一条存在list状态
                                vcListState.add(value.getVc());

                                //从vcListState里面拷贝到一个新的list里面，排序

                                Iterable<Integer> vcList = vcListState.get();

                                ArrayList<Integer> list_tmp = new ArrayList<>();
                                for (Integer vc:vcList){
                                    list_tmp.add(vc);

                                }
                                //对list_tmp 进行降序
                                list_tmp.sort(((o1, o2) -> o2-o1));


                                // todo 重要一步，因为来一条算一条（list_tmp的个数一定是连续变大，只要去掉最后一个即可） 只保存3个最大的
                                if (list_tmp.size()>3) {
                                    list_tmp.remove(3);
                                }


                                out.collect("传感器id="+value.getId()+",最大的水位值="+list_tmp.toString());


                                // 更新list状态
                                vcListState.update(list_tmp);

                            }
                        }
                );



        env.execute();


    }


}
