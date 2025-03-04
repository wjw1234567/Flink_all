package org.FlinkCDC;

import org.apache.commons.collections4.IterableUtils;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.curator5.com.google.common.collect.Lists;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.List;


/**
 *
 * 动态分组规则
 *
 * 规则1：当连续操作类型一致且数量≥10时，触发批处理（如连续10条INSERT）。
 * 规则2：当操作类型变化时（如DELETE→INSERT），立即触发当前批次处理，并开启新批次。
 * 动态分组批次加上批次定时更新
 *
 */

public class BatchProcessor_pg extends KeyedProcessFunction<Integer, String, List<String>> {
    private transient ListState<String> bufferState;
    private transient ValueState<Long> timerState; //用于记录是否有新的定时器，过了5秒执行就清空需要重新定义一个定时器，如果为空，就需要初始化定时器。定义定时器填入时间

    private transient ValueState<String> opTypeState;//用于记录上一次操作类型，判断操作类型是否连续相等


    private final int batchSize ;
    private final long timeout ; // 5秒

    private  String curOptype="";
    private  String lastOptype="";

    public BatchProcessor_pg(int batchSize, long timeout) {
        this.batchSize = batchSize;
        this.timeout = timeout;
    }

    @Override
    public void open(Configuration parameters) {
        // 初始化批量缓冲状态
        bufferState = getRuntimeContext().getListState(
                new ListStateDescriptor<String>("buffer", Types.STRING));


        // 初始化定时器状态
        timerState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("timer", Long.class));

        opTypeState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("opType", Types.STRING));

    }

    @Override
    public void processElement(String value, Context ctx,
                               Collector<List<String>> out) throws Exception {

/*        System.out.println("binlog插入的数据"+new ObjectMapper().readTree(value).get("after").toString());
        System.out.println("binlog删除的数据"+new ObjectMapper().readTree(value).get("before").toString());
        System.out.println("binlog操作类型"+new ObjectMapper().readTree(value).get("op").toString());*/


        lastOptype = opTypeState.value();
        curOptype=new ObjectMapper().readTree(value).get("op").asText();


        opTypeState.update(curOptype);
        bufferState.add(value);

/*        System.out.println("lastOptype="+lastOptype);
        System.out.println("curOptype="+curOptype);*/

       if (lastOptype !=null && !lastOptype.equals(curOptype)) {
            flushBuffer(out, ctx.timerService());
        }


        Iterable<String> value_it = bufferState.get();
        // System.out.println("迭代器累积的数据量"+IterableUtils.size(value_it));



        // 首次记录时注册定时器
        if (timerState.value() == null) {
            long nextTimer = ctx.timerService().currentProcessingTime() + timeout;
            ctx.timerService().registerProcessingTimeTimer(nextTimer);
            timerState.update(nextTimer);
        }

        // 数量达到阈值时提前触发向下游发送数据
        if (IterableUtils.size(bufferState.get()) >= batchSize
            && lastOptype.equals(curOptype)
        ) {
            flushBuffer(out, ctx.timerService());
        }


    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx,
                        Collector<List<String>> out) throws Exception {

        // 超时触发提交向下游发送数据
        if (!IterableUtils.isEmpty(bufferState.get())
               // &IterableUtils.size(bufferState.get()) < batchSize
               // &lastOptype.equals(curOptype)
        ) {
            flushBuffer(out, ctx.timerService());
        }
        timerState.clear(); // 清除当前定时器
    }

    private void flushBuffer(Collector<List<String>> out, TimerService timerService) throws Exception {
        List<String> batch = Lists.newArrayList(bufferState.get());
        out.collect(batch); // 向下游发送批量数据
        bufferState.clear();

        // 注册新定时器
        long nextTimer = timerService.currentProcessingTime() + timeout;
        timerService.registerProcessingTimeTimer(nextTimer);
        timerState.update(nextTimer);
    }

}



