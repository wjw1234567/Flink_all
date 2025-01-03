package org.Partition;

import org.apache.flink.api.common.functions.MapFunction;
import org.bean.WaterSensor;

public class WaterSensorMapFunction   implements MapFunction<String, WaterSensor>
{
    @Override
    public WaterSensor map(String value) throws Exception {
        String[] datas = value.split(",");
        return new WaterSensor(datas[0]  ,Long.valueOf(datas[1]) ,Integer.valueOf(datas[2]) );
    }


   /* public static void main(String[] args) throws Exception {
        WaterSensorMapFunction waterSensorMapFunction = new WaterSensorMapFunction();
        System.out.println(waterSensorMapFunction.map( "AA,1,2" ) );
    }*/
}
