package org.kafka;


/**
 * 对象序列化
 *
 */

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.protocol.types.Field;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class UserDeSerializer implements Deserializer<User> {



    @Override
    public User deserialize(String topic, byte[] data) {


        try {

            if (data == null) {
                return null;
            }


            //wrap可以把字节数组包装成缓冲区ByteBuffer
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);

        /*
        get()从buffer中取出数据，每次取完之后position指向当前取出的元素的下一位，可以理解为按顺序依次读取
         */
            int Age = byteBuffer.getInt();



            /*
             * 定义一个字节数组来存储字节数组类型的name，因为在读取字节数组数据的时候需要定义一个与读取的数组长度一致的数组，要想知道每个name的值的长度
             * ，就需要把这个长度存到buffer中，这样在读取的时候可以得到数组的长度方便定义数组
             */
            int nameLength = byteBuffer.getInt();

            //第一种方式
            byte[] nameBytes = new byte[nameLength];
            byteBuffer.get(nameBytes);
            String name = new String(nameBytes, "UTF-8");

            //另一种方式
            //String name  =  new String(byteBuffer.get(data,4,nameLength).array(),"UTF-8").trim();



            int idLength = byteBuffer.getInt();
            byte[] idBytes = new byte[idLength];
            byteBuffer.get(idBytes);
            String id = new String(idBytes, "UTF-8");

            return new User(name,id,Age);




        }catch(Exception e){
            throw new SerializationException("error when deserializing..."+e);
        }


    }



    public static void main(String[] args) {


        //自定义序列化
        User user = new User("wjw", "a1234567", 26);
        UserSerializer us = new UserSerializer();
        byte[] a1s1 = us.serialize("a1", user);

        //反序列化
        UserDeSerializer ud = new UserDeSerializer();
        User a1 = ud.deserialize("a1", a1s1);
        System.out.println(a1);


    }


}
