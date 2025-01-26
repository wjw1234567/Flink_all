package org.kafka;

import org.apache.kafka.clients.producer.*;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class AvroUserProducer {


    /*
     生产者
     */


     private static final String BOOTSTRAP_SERVERS = "192.168.254.128:9092,192.168.254.128:9093,192.168.254.128:9094";
    // private static final String BOOTSTRAP_SERVERS = "192.168.254.128:9092";
    private static final String TOPIC = "disTopic";



    public static void main(String[] args)  throws ExecutionException, InterruptedException {

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        // 配置key的序列化类
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
       // 配置value的自定义序列化类
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.kafka.UserSerializer");


        //kafka生产者
        Producer<String, User> producer = new KafkaProducer<>(props);

        //用于协同控制一个或多个线程等待在其他线程中执行的一组操作完成，然后再继续执
        CountDownLatch latch = new CountDownLatch(5);


        for(int i = 0; i < 5; i++) {

            //Part2:构建消息
            ProducerRecord<String, User> record = new ProducerRecord<>(TOPIC, Integer.toString(i),new User("a"+i,"s"+i,i+10));


            //异步发送：消息发送后不阻塞，服务端有应答后会触发回调函数
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception e) {

                    if(null != e){
                        System.out.println("消息发送失败,"+e.getMessage());
                        e.printStackTrace();
                    }else{
                        String topic = metadata.topic();
                        long offset = metadata.offset();
                        String message = metadata.toString();
                        System.out.println("message:["+ message+"] sended with topic:"+topic+";offset:"+offset);
                    }
                    latch.countDown();


                }
            });





        }


        //消息处理完才停⽌发送者。
        latch.await();
        producer.close();




    }


}
