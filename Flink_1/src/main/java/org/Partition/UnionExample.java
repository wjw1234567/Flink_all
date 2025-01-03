package org.Partition;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class UnionExample {


    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(1);

        DataStreamSource<Integer> ds1 = env.fromElements(1, 2, 3);
        DataStreamSource<Integer> ds2 = env.fromElements(2, 2, 3);
        DataStreamSource<String>  ds3 = env.fromElements("2", "2", "3");
        DataStreamSource<Integer>  ds4 = env.fromElements(4, 5, 6,7);


       /* ds1.union(ds2,ds3.map(new MapFunction<String, Integer>() {
            @Override
            public Integer map(String value) throws Exception {
                return Integer.parseInt(value);
            }
        }),ds4).print();

        */

        ds1.union(ds2,ds3.map(value -> Integer.parseInt(value)),ds4).print();


        env.execute();
    }

}
