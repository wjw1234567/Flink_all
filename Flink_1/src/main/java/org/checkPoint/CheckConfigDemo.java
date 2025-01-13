package org.checkPoint;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.util.Collector;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;


public class CheckConfigDemo {

    public static void main(String[] args)  throws Exception{


        Configuration conf = new Configuration();
        conf.setString(RestOptions.BIND_PORT, "9091"); // 设置WebUI端口为8081


        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);

       // StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(1);


        //TODO 检查点配置
        //1.启用检查点，默认是barrier对齐的,周期为5S,精准一次
        env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE);
        //2.指定检查点的存储位置,支持HDFS，也支持本地路径
        CheckpointConfig checkpointConfig = env.getCheckpointConfig();
        checkpointConfig.setCheckpointStorage("file:///D:\\IDEA_Project\\Flink\\CheckPoint_Flink");
        //设置超时时间
        checkpointConfig.setCheckpointTimeout(60000);
        //同时运行中的checkpoint的最大数量
        checkpointConfig.setMaxConcurrentCheckpoints(2);

        //取消作业时，checkpoint的数据是否保留在外部系统
        // DELETE_ON_CANCELLATION  取消作业时表示删除
        // RETAIN_ON_CANCELLATION  取消作业时表示保留

        checkpointConfig.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);



        








        env
                .socketTextStream("192.168.254.128", 7777)
                .flatMap(
                        (String value,Collector<Tuple2<String,Integer>> out) -> {
                         String[] words = value.split(",");
                          for (String word : words) {
                                out.collect(Tuple2.of(word,1));
                            }
                        })
                .returns(Types.TUPLE(Types.STRING,Types.INT))
                .keyBy(value -> value.f0)
                .sum(1)
        ;



        env.execute("job-" + CheckConfigDemo.class.getSimpleName());


    }


}
