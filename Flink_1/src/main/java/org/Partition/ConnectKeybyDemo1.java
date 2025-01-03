package org.Partition;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.ConnectedStreams;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ConnectKeybyDemo1 {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStreamSource<Tuple2<Integer, String>> source1 = env.fromElements(
                Tuple2.of(1, "a1"),
                Tuple2.of(1, "a2"),
                Tuple2.of(1, "a3"),
                Tuple2.of(2, "b"),
                Tuple2.of(3, "c")
        );
        DataStreamSource<Tuple3<Integer, String, Integer>> source2 = env.fromElements(
                Tuple3.of(1, "aa1", 1),
                Tuple3.of(1, "aa2", 2),
                Tuple3.of(1, "aa4", 2),
                Tuple3.of(2, "bb", 1),
                Tuple3.of(3, "cc", 1)
        );


        ConnectedStreams<Tuple2<Integer, String>, Tuple3<Integer, String, Integer>> connect = source1.connect(source2);

        // 将2个元组的第一个元素作为id
        ConnectedStreams<Tuple2<Integer, String>, Tuple3<Integer, String, Integer>> connectkeyby = connect.keyBy(s1 -> s1.f0, s2 -> s2.f0);


        SingleOutputStreamOperator<String>  result = connectkeyby.process(new CoProcessFunction<Tuple2<Integer, String>, Tuple3<Integer, String, Integer>, String>() {



            Map<Integer, List<Tuple2<Integer, String>>> s1Cache =new HashMap<>();
            Map<Integer, List<Tuple3<Integer, String,Integer>>> s2Cache =new HashMap<>();



            @Override
            public void processElement1(Tuple2<Integer, String> value, CoProcessFunction<Tuple2<Integer, String>, Tuple3<Integer, String, Integer>, String>.Context ctx, Collector<String> out) throws Exception {
                Integer i =value.f0;
                if (!s1Cache.containsKey(i)){
                    // 如果map不含改元组
                    ArrayList<Tuple2<Integer, String>> list1 = new ArrayList<>();
                    list1.add(value);
                    s1Cache.put(i,list1);
                } else {
                    s1Cache.get(i).add(value);
                }

                out.collect("s1Cache:"+s1Cache.toString());

                if (s2Cache.containsKey(i)) {

                    for (Tuple3<Integer, String, Integer> s2element : s2Cache.get(i)){

                    //    out.collect("s2:" + s2element + "<--------->s1:" + value);
                    }

                }

            }

            @Override
            public void processElement2(Tuple3<Integer, String, Integer> value, Context ctx, Collector<String> out) throws Exception {


                Integer i = value.f0;
                if (!s2Cache.containsKey(i)) {
                    // 如果map不含元组
                    ArrayList<Tuple3<Integer, String, Integer>> list2 = new ArrayList<>();
                    list2.add(value);
                    s2Cache.put(i, list2);
                } else {
                    s2Cache.get(i).add(value);
                }

                 out.collect("s2Cache:"+s2Cache.toString());

                if (s1Cache.containsKey(i)) {

                    for (Tuple2<Integer, String> s1element : s1Cache.get(i)){

                        out.collect("s1:" + s1element + "<--------->s2:" + value);
                    }

                }


            }



        });

        result.print();

        env.execute();
    }


}
