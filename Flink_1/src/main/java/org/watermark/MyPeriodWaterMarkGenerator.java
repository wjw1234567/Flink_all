package org.watermark;

import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;

public class MyPeriodWaterMarkGenerator<T> implements WatermarkGenerator<T> {

    /**
     *
     * 延迟的时间
     */
    private long delayTs ;

    // 用来保存当前为止最大的事件时间
    private long maxTs;

    public MyPeriodWaterMarkGenerator(long delayTs) {
        this.delayTs = delayTs;
         this.maxTs=Long.MIN_VALUE+this.delayTs+1;
    }


    /**
     *
     * 每条数据来，都会调用一次，用来提取最大的事件时间保存下来
     * @param event
     * @param eventTimestamp  提取到数据的事件时间
     * @param output
     */




    @Override
    public void onEvent(T event, long eventTimestamp, WatermarkOutput output) {
         maxTs = Math.max(maxTs, eventTimestamp);
        System.out.println("调用了onevent方法,获取目前最大时间戳="+maxTs);
    }


    /**
     *
     *
     * 周期性调用，发射watermark
     * @param output
     */

    @Override
    public void onPeriodicEmit(WatermarkOutput output) {
              output.emitWatermark(new Watermark(maxTs - delayTs -1));
              System.out.println("调用了onPeriodicEmit方法,生成watermark="+(maxTs - delayTs -1));
    }
}
