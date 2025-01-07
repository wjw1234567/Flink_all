package org.state;

import org.Partition.WaterSensorMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.List;


/**
 *
 *  TODO 在map算子中计算数据的个数
 *
 *
 */



public class OperatorListStateDemo {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        env.setParallelism(1);
        // env.setParallelism(2);

        env
                .socketTextStream("192.168.254.128", 7777)
                .map(new MapCountFunction()).print();





        env.execute();


    }


    public static class MapCountFunction implements MapFunction<String,Long>
    , CheckpointedFunction
    {

        private Long count =0L;
        private ListState<Long> state;



        /**
         *
         * 2.本地变量持久化，将本地变量拷贝到算子中
         * @param context
         * @throws Exception
         */

        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {

            System.out.println("snapshotState.....");

            //2.1 清空算子状态
            state.clear();
            //2.2 将本地变量添加到算子状态中
            state.add(count);

        }


        /**
         *
         *
         * 3.初始化本地变量，程序恢复时，从状态中，把数据添加到本地变量，每个子任务调用一次
         * @param context
         * @throws Exception
         */

        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {


            System.out.println("initializeState....");

            //3.1 从上下文初始化算子状态
            state=context
                    .getOperatorStateStore()
                    .getListState(new ListStateDescriptor<Long>("state", Types.LONG));

            //3.2 从算子状态中拷贝到本地变量

            if (context.isRestored()) {
                for (Long c : state.get()) {
                    count +=c;

                }
            }
        }



        @Override
        public Long map(String value) throws Exception {
            return ++count;
        }

    }





}
