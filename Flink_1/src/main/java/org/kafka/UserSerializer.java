package org.kafka;


/**
 * 对象序列化
 *
 */

import org.apache.kafka.common.serialization.Serializer;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class UserSerializer  implements Serializer<User> {
    @Override
    public byte[] serialize(String topic, User data) {

        if(data == null){
            return null;
        }

        byte[] nameBytes = data.getName().getBytes(StandardCharsets.UTF_8);


        byte[] idbytes = data.getId().getBytes(StandardCharsets.UTF_8);



        // 因为Int的字节数是4
        // 第一个4表示存储age的值
        // 第二个4表示存储nameBytes的属性长度Int   和nameBytes值的长度
        // 同理第三个4表示存储idbytes的属性长度Int  和idbytes值的长度
        int cap =4+4+nameBytes.length+4+idbytes.length;
        ByteBuffer buteBuffer = ByteBuffer.allocate(cap);
        //buteBuffer

        buteBuffer.putInt(data.getAge());
        buteBuffer.putInt(nameBytes.length);
        buteBuffer.put(nameBytes);
        buteBuffer.putInt(idbytes.length);
        buteBuffer.put(idbytes);

        return buteBuffer.array();
    }

    public static void main(String[] args) {


        User user = new User("wjw", "a1", 25);
        UserSerializer userSerializer = new UserSerializer();
        byte[] a1s = userSerializer.serialize("a1", user);
        System.out.println(a1s);


    }


}
