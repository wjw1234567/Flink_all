package org.transform;

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.bean.WaterSensor;


public class TransKeyBy {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStreamSource<WaterSensor> stream = env.fromElements(
                new WaterSensor("sensor_1", 1L, 1),
                new WaterSensor("sensor_1", 2L, 2),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_3", 3L, 3)
        );

        // 方式一：使用Lambda表达式
        KeyedStream<WaterSensor, String> keyedStream = stream.keyBy(e -> e.id);

        // 方式二：使用匿名类实现KeySelector
        KeyedStream<WaterSensor, String> keyedStream1 = stream.keyBy(new KeySelector<WaterSensor, String>() {
            @Override
            public String getKey(WaterSensor e) throws Exception {
                return e.id;

            }
        });

        SingleOutputStreamOperator<WaterSensor> vc = keyedStream.sum("vc");
        vc.print();
        env.execute();
    }


}
