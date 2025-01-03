package org.Partition;

import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoMapFunction;
import org.bean.WaterSensor;


public class ConnectDemo {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        DataStreamSource<WaterSensor> source1 = env.fromElements(

                new WaterSensor("sensor_1", 1L, 1),
                new WaterSensor("sensor_2", 2L, 2),
                new WaterSensor("sensor_3", 2L, 2)
        );
        DataStreamSource<String> source2 = env.fromElements("a", "b", "c");

      /* SingleOutputStreamOperator<Integer> source1 = env
                .socketTextStream("192.168.133.5", 7777)
                .map(i -> Integer.parseInt(i));

        DataStreamSource<String> source2 = env.socketTextStream("192.168.133.5", 8888);

       */

        /**
         * TODO 使用 connect 合流
         * 1、一次只能连接 2条流
         * 2、流的数据类型可以不一样
         * 3、 连接后可以调用 map、flatmap、process来处理，但是各处理各的
         */
        ConnectedStreams<WaterSensor, String> connect = source1.connect(source2);

        SingleOutputStreamOperator<String> result = connect.map(new CoMapFunction<WaterSensor, String, String>() {
            @Override
            public String map1(WaterSensor value) throws Exception {
                return "来源于WaterSensor流:" + value.toString();
            }

            @Override
            public String map2(String value) throws Exception {
                return "来源于字母流:" + value;
            }
        });

        result.print();

        env.execute();

    }

}
