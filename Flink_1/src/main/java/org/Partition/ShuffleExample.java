package org.Partition;
import org.apache.flink.streaming.api.datastream.DataStreamSource;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
public class ShuffleExample {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(3);

        DataStreamSource<String> stream = env.socketTextStream("192.168.133.5", 7777);;

        //随机分区
        //stream.shuffle().print();

        //轮询分区
        stream.rebalance().print();

        env.execute();
    }


}
