package org.process;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class TopNDemo1 {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


         env.setParallelism(1);
        // env.setParallelism(2);


        SingleOutputStreamOperator<WaterSensor> sensorDS = env.socketTextStream("192.168.254.128", 7777).map(new WaterSensorMapFunction());


        // TODO 1.定义Watermark策略
        // forBoundedOutOfOrderness表示乱序水位线，延迟3S
        WatermarkStrategy<WaterSensor> watermarkStrategy = WatermarkStrategy.<WaterSensor>forBoundedOutOfOrderness(Duration.ofSeconds(3)) //延迟3S
                .withTimestampAssigner(new SerializableTimestampAssigner<WaterSensor>() {
                    @Override
                    public long extractTimestamp(WaterSensor element, long recordTimestamp) {
                        // 返回的时间戳，要 毫秒
                        System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                        return element.getTs() * 1000L;
                    }
                });//设置定义的水位线

        // TODO 2.指定Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDSwithWatermark = sensorDS.assignTimestampsAndWatermarks(watermarkStrategy);


        //根据id分组
        // KeyedStream<WaterSensor, String> sensorDs = sensorDSwithWatermark.keyBy(sensor -> sensor.getId());


      // todo 思路一：所有数据到一起，用hashmap才存，key=vc，value=count值
        // 最近10秒=窗口长度，每5s输出=滑动步长

        sensorDSwithWatermark.windowAll(SlidingEventTimeWindows.of(Time.seconds(10),Time.seconds(5)))
                .process(new MyTopNPAWF())
                .print();

        env.execute();
    }



    public static class MyTopNPAWF extends ProcessAllWindowFunction<WaterSensor,String, TimeWindow>{

        @Override
        public void process(Context context, Iterable<WaterSensor> elements, Collector<String> out) throws Exception {
            // 定义一个hashmap用来存，key=vc，value=count值
            HashMap<Integer, Integer> vcCountMap = new HashMap<>();
            // 1.遍历数据, 统计 各个vc出现的次数
            for (WaterSensor element:elements){
                Integer vc = element.getVc();
               if (vcCountMap.containsKey(vc)){
                   //1.key存在，不是这个key的第一条数据，直接累加
                   vcCountMap.put(vc,vcCountMap.get(vc)+1);

               }
               else {
                   vcCountMap.put(vc,1);
               }
            }

            // 2.对 count值进行排序: 利用List来实现排序
            ArrayList<Tuple2<Integer,Integer>> datas = new ArrayList<>();
            //遍历vcCountMap存入lists
            for (Integer vc : vcCountMap.keySet()){
                datas.add(Tuple2.of(vc,vcCountMap.get(vc)));
            }

            // 对List进行排序，根据count值 降序
            datas.sort(new Comparator<Tuple2<Integer, Integer>>() {
                @Override
                public int compare(Tuple2<Integer, Integer> o1, Tuple2<Integer, Integer> o2) {
                    return o2.f1-o1.f1;
                }
            });



            // 3.取出 count最大的2个 vc
            StringBuilder outStr = new StringBuilder();

            outStr.append("================================\n");

            for (int i = 0; i < Math.min(2, datas.size()); i++) {
                Tuple2<Integer, Integer> vcCount = datas.get(i);
                outStr.append("Top" + (i + 1) + "\n");
                outStr.append("vc=" + vcCount.f0 + "\n");
                outStr.append("count=" + vcCount.f1 + "\n");
                outStr.append("窗口结束时间=" + DateFormatUtils.format(context.window().getEnd(), "yyyy-MM-dd HH:mm:ss.SSS") + "\n");
                outStr.append("================================\n");
            }


           //输出
            out.collect(outStr.toString());



        }
    }



}



