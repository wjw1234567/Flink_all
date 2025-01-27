package org.checkPoint;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.protocol.types.Field;

import java.time.Duration;

public class kafkaEOSDemo {

    public static void main(String[] args)  throws Exception{


        Configuration configuration = new Configuration();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(configuration);


        // 代码中用到hdfs，需要导入hadoop依赖、指定访问hdfs的用户名
        // System.setProperty("HADOOP_USER_NAME", "atguigu");
        // 1、启用检查点: 默认是barrier对齐的，周期为5s, 精准一次
        env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig checkpointConfig = env.getCheckpointConfig();
        // 2、指定检查点的存储位置
        checkpointConfig.setCheckpointStorage("file:///D:\\IDEA_Project\\Flink\\CheckPoint_Flink");
        // DELETE_ON_CANCELLATION:主动cancel时，删除存在外部系统的chk-xx目录 （如果是程序突然挂掉，不会删）
        // RETAIN_ON_CANCELLATION:主动cancel时，外部系统的chk-xx目录会保存下来
        checkpointConfig.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);


        //TODO 2.读取kafka

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("192.168.254.128:9092,192.168.254.128:9093,192.168.254.128:9094")
                .setGroupId("Fkgroup")
                .setTopics("Fksource")
                .setValueOnlyDeserializer(new SimpleStringSchema()) //反序列化
                .setStartingOffsets(OffsetsInitializer.latest())
                .build();


        DataStreamSource<String> ksource = env.fromSource(kafkaSource, WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(3)), "kafkaSource");



        //TODO 3.输出到kafka

        /*
        精准一次写入kafka需要满足以下条件
        1.开启checkpoint
        2.设置事务前缀
        3.设置事务超时时间：checkpoint超时时间<事务超时时间<max的15分钟
         */

        KafkaSink<String> ksink = KafkaSink.<String>builder()
                .setBootstrapServers("192.168.254.128:9092,192.168.254.128:9093,192.168.254.128:9094")

                //指定序列化器，具体序列化
                .setRecordSerializer(

                        KafkaRecordSerializationSchema.<String>builder()
                                .setTopic("Fksink")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()


                )
                //精准一次
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                //设置事务前缀
                .setTransactionalIdPrefix("Flink_kafka-")
                //精准一次必须设置事务超时时间，大于checkpoint间隔小于max15分钟
                .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 10 * 60 * 1000 + "")
                .build();


        ksource.sinkTo(ksink);

        env.execute();



    }
}
