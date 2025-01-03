package org.process;

import org.Partition.WaterSensorMapFunction;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.math3.fitting.leastsquares.EvaluationRmsChecker;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.bean.WaterSensor;

import java.time.Duration;
import java.util.*;

public class KeyedProcessTopNDemo {

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
                       // System.out.println("数据=" + element + ",recordTs=" + recordTimestamp);
                        return element.getTs() * 1000L;
                    }
                });//设置定义的水位线

        // TODO 2.指定Watermark策略
        SingleOutputStreamOperator<WaterSensor> sensorDSwithWatermark = sensorDS.assignTimestampsAndWatermarks(watermarkStrategy);




        // 最近10秒=窗口长度，每5s输出=滑动步长
      // todo 思路2 : 使用keyProcessFunction实现

        /**
         *
         * 步骤：
         * 1.按照vc做keyby，开窗，count起来结果
         *    ---》增量聚合，计算count
         *    ---》全窗口，对计算结果count封装，带上窗口结束时间的标签，为了让同个窗口的时间范围计算结果到一起去
         *
         * 2.对同一个窗口范围的count值进去处理，排序，取N个
         *    ---》按照windowEnd做keyby
         *    ---》使用process，来一条调用一次，需要分开存HashMap，key=windowEnd，value=List
         *    ---》使用定时器，对存起来的结果进行排序，取N个
         *
         */








        // 1.按照vc分组，开窗，聚合（增量计算+全量打标签）
        // 开窗聚合后就是普通的流，没有了窗口的信息，需要自己打窗口的标记windowEnd
        SingleOutputStreamOperator<Tuple3<Integer, Integer, Long>> windowAgg = sensorDSwithWatermark.keyBy(sensor -> sensor.getVc())
                .window(SlidingEventTimeWindows.of(Time.seconds(10), Time.seconds(5)))
                .aggregate(
                        new VcCountAgg(),
                        new WindowResult()
                );



        //2.按照窗口结束时间做keyby，保证窗口的同一个时间范围的结果到一起去，排序，取topN
        windowAgg.keyBy(r->r.f2)
                .process(new TopN(2))
                 .print()
        ;

        env.execute();
    }



    public static class VcCountAgg implements AggregateFunction<WaterSensor,Integer,Integer>{

        @Override
        public Integer createAccumulator() {
            return 0;
        }

        @Override
        public Integer add(WaterSensor value, Integer accumulator) {
            return accumulator+1;
        }

        @Override
        public Integer getResult(Integer accumulator) {
            return accumulator;
        }

        @Override
        public Integer merge(Integer a, Integer b) {
            return null;
        }
    }


    /**
     * in:VcCountAgg累加器的输出类型，保持一致
     * out:输出自定义类型   Tuple3<Integer,Integer,Long> vc,累加值，和窗口结束时间
     * key:vc是Integer类型
     * W：时间窗口类型TimeWindow
     *
     *
     *
     *
     *
     */
    public static class WindowResult extends ProcessWindowFunction<Integer, Tuple3<Integer,Integer,Long>,Integer,TimeWindow>{

        @Override
        public void process(Integer key, Context context, Iterable<Integer> elements, Collector<Tuple3<Integer, Integer, Long>> out) throws Exception {

            //迭代器里面只有一条数据，next一次即可
            Integer count = elements.iterator().next();
            //上下文获取窗口的结束时间
            long windowEnd = context.window().getEnd();
            //封装成Tuple3
            out.collect(Tuple3.of(key,count,windowEnd));
        }
    }







    // 根据窗口的结束时间进行keyby process处理
    public static class TopN extends KeyedProcessFunction<Long,Tuple3<Integer, Integer, Long>,String>{

        private Map<Long, List<Tuple3<Integer, Integer, Long>>> dataListMap;

        // 要取top的数量
        private int threshlod;


        public TopN(int threshlod){
            this.threshlod = threshlod;
            this.dataListMap = new HashMap<>();
        }


        @Override
        public void processElement(Tuple3<Integer, Integer, Long> value, Context ctx, Collector<String> out) throws Exception {
            //进入这个方法，只是一条数据，得集齐====》存起来，不同窗口分开存
            //1.存到hashMap中
            Long WindowEnd = value.f2;
            if (dataListMap.containsKey(WindowEnd)) {
                //包含vc，不是该vc的第一条，直接添加到list
                List<Tuple3<Integer, Integer, Long>> datalist = dataListMap.get(WindowEnd);
                datalist.add(value);

            }else {
                // 不包含vc，是改vc的第一条，直接初始化list
                List<Tuple3<Integer, Integer, Long>> datalist =new ArrayList<>();
                datalist.add(value);
                dataListMap.put(WindowEnd,datalist);
            }


            //2.注册一个定时器，windowEnd+1ms即可
            //同一个窗口范围，应该同时输出，只不过是一条一条调用processElement方法，只需要延迟1ms即可

            ctx.timerService().registerEventTimeTimer(WindowEnd+1);

        }


        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            //定时器触发，同一个窗口的计算结果攒齐了，开始排序取topN

            Long WindowEnd = ctx.getCurrentKey();
            //1.排序
            List<Tuple3<Integer, Integer, Long>> dataList = dataListMap.get(WindowEnd);


            dataList.sort(new Comparator<Tuple3<Integer, Integer, Long>>() {
                @Override
                public int compare(Tuple3<Integer, Integer, Long> o1, Tuple3<Integer, Integer, Long> o2) {
                    return o2.f1-o1.f1;
                }
            });

            //2.取topN

            StringBuilder outStr = new StringBuilder();

            outStr.append("================================\n");

            for (int i = 0; i < Math.min(threshlod, dataList.size()); i++) {

                Tuple3<Integer, Integer, Long> vcCount = dataList.get(i);
                outStr.append("Top" + (i + 1) + "\n");
                outStr.append("vc=" + vcCount.f0 + "\n");
                outStr.append("count=" + vcCount.f1 + "\n");
                outStr.append("窗口结束时间=" + DateFormatUtils.format(vcCount.f2, "yyyy-MM-dd HH:mm:ss.SSS") + "\n");
                outStr.append("================================\n");

            }
            //用完的list及时清理
            dataList.clear();
            out.collect(outStr.toString());
        }
    }





}



