package org.udf;


import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.bean.WaterSensor;


public class RichFunctionExample {


    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        env.fromElements(1,2,3,4)
                .map(new RichMapFunction<Integer, Integer>() {

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        System.out.println("索引是：" + getRuntimeContext().getIndexOfThisSubtask() + " 的任务的生命周期开始");
                    }

                    @Override
                    public Integer map(Integer integer) throws Exception {
                        return integer + 1;
                    }

                    @Override
                    public void close() throws Exception {
                        super.close();
                        System.out.println("索引是：" + getRuntimeContext().getIndexOfThisSubtask() + " 的任务的生命周期结束");
                    }

                })
                .print();

        env.execute();
    }

}
