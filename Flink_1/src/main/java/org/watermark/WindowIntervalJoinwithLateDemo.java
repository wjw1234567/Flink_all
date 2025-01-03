package org.watermark;

import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

/**
 * TODO
 *
 * @author cjp
 * @version 1.0
 */
public class WindowIntervalJoinwithLateDemo {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<Tuple2<String, Integer>> ds1 = env
                .socketTextStream("192.168.254.128", 7777)
                .map(new MapFunction<String, Tuple2<String, Integer>>() {
                    @Override
                    public Tuple2<String, Integer> map(String value) throws Exception {
                        String[] data = value.split(",");
                        return Tuple2.of(data[0],Integer.parseInt(data[1]));
                    }
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple2<String, Integer>>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner((value, ts) -> value.f1 * 1000L)
                );


        SingleOutputStreamOperator<Tuple3<String, Integer,Integer>> ds2 = env
                .socketTextStream("192.168.254.128", 8888)
                .map(new MapFunction<String, Tuple3<String, Integer,Integer>>() {
                    @Override
                    public Tuple3<String, Integer, Integer> map(String value) throws Exception {
                        String[] data = value.split(",");
                        return Tuple3.of(data[0],Integer.parseInt(data[1]),Integer.parseInt(data[2]));

                    }
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple3<String, Integer,Integer>>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner((value, ts) -> value.f1 * 1000L)
                );



        //定义侧输出流

        OutputTag<Tuple2<String, Integer>> ks1LateTag = new OutputTag<Tuple2<String, Integer>>("Ks1-late", Types.TUPLE(Types.STRING, Types.INT));

        OutputTag<Tuple3<String, Integer, Integer>> ks2LateTag = new OutputTag<Tuple3<String, Integer, Integer>>("Ks2-late", Types.TUPLE(Types.STRING, Types.INT,Types.INT));



        KeyedStream<Tuple2<String, Integer>, String> ks1 = ds1.keyBy(r -> r.f0);
        KeyedStream<Tuple3<String, Integer, Integer>, String> ks2 = ds2.keyBy(r -> r.f0);

        /**
         * 只支持事件事件
         * 指定上界和下界
         * process处理join的数据
         * sideOutputLeftLateData  将左流迟到的数据放入侧输出流
         */

        ks1.intervalJoin(ks2)
                .between(Time.seconds(-2),Time.seconds(2))
                .sideOutputLeftLateData(ks1LateTag) //将KS1迟到的数据放到侧输出流
                .process(new ProcessJoinFunction<Tuple2<String, Integer>, Tuple3<String, Integer, Integer>, Object>() {

                    /**
                     *

                     * @param left   ks1的数据
                     * @param right  ks2的数据
                     * @param ctx    上下文
                     * @param out    采集器
                     * @throws Exception
                     */


                    @Override
                    public void processElement(Tuple2<String, Integer> left, Tuple3<String, Integer, Integer> right, Context ctx, Collector<Object> out) throws Exception {

                        out.collect(left+"<----->"+right);

                    }
                }).print();




        env.execute();
    }
}
