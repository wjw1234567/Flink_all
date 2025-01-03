package org.flink;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class WordCountStream {

    public static void main(String[] args) throws Exception {
        
        // 准备环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStreamSource<String> lineDs = env.readTextFile("input/words.txt");


        SingleOutputStreamOperator<Tuple2<String, Long>> WordAndOne = lineDs.flatMap(new FlatMapFunction<String, Tuple2<String, Long>>() {

            // 实现 FlatMapFunction接口的方法
            @Override
            public void flatMap(String value, Collector<Tuple2<String, Long>> collector) throws Exception {

                // 切分数据
                String[] words = value.split(" ");
                for (String word : words) {
                    //通过采集器向下游发送数据
                    collector.collect(Tuple2.of(word,1L));
                }

            }
        });

        //分组
        KeyedStream<Tuple2<String, Long>, String> WordAndOneks = WordAndOne.keyBy(new KeySelector<Tuple2<String, Long>, String>() {

            @Override
            public String getKey(Tuple2<String, Long> value) throws Exception {
                return value.f0;
            }
        });

        //聚合
        //WordAndOneks.print();
        SingleOutputStreamOperator<Tuple2<String, Long>> sumDs = WordAndOneks.sum(1);
        //输出数据
        sumDs.print();

        //执行
        env.execute();

    }



}
