package org.window;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.bean.WaterSensor;

public class WindowReduceDemo {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        env
                .socketTextStream("192.168.254.128", 7777)
                .map(new WaterSensorMapFunction())
                .keyBy(r -> r.getId())
                // 设置滚动事件时间窗口

                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .reduce(new ReduceFunction<WaterSensor>() {

                    @Override
                    public WaterSensor reduce(WaterSensor value1, WaterSensor value2) throws Exception {
                        System.out.println("调用reduce方法，之前的结果:"+value1 + ",现在来的数据:"+value2);
                        return new WaterSensor(value1.getId(), System.currentTimeMillis(),value1.getVc()+value2.getVc());
                    }
                })
                .print();

        env.execute();
    }


}
