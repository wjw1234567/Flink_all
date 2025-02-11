package org.FlinkSql;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class sqlDemo {
    public static void main(String[] args) {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        //TODO 1. 写法1 创建表环境
/*        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inStreamingMode()    // 使用流处理模式
                .build();

        TableEnvironment tableEnv = TableEnvironment.create(settings);
        */


        //TODO 1. 写法2 创建表环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);


        //TODO 2.创建表
        tableEnv.executeSql("CREATE TABLE source ( \n" +
                "    id INT, \n" +
                "    ts BIGINT, \n" +
                "    vc INT\n" +
                ") WITH ( \n" +
                "    'connector' = 'datagen', \n" +
                "    'rows-per-second'='1', \n" +
                "    'fields.id.kind'='random', \n" +
                "    'fields.id.min'='1', \n" +
                "    'fields.id.max'='10', \n" +
                "    'fields.ts.kind'='sequence', \n" +
                "    'fields.ts.start'='1', \n" +
                "    'fields.ts.end'='1000000', \n" +
                "    'fields.vc.kind'='random', \n" +
                "    'fields.vc.min'='1', \n" +
                "    'fields.vc.max'='100'\n" +
                ");");

        tableEnv.executeSql("CREATE TABLE sink (\n" +
                "    id INT, \n" +
                "    ts BIGINT, \n" +
                "    vc INT\n" +
                ") WITH (\n" +
                "'connector' = 'print'\n" +
                ");");


        //todo 3.执行查询


        //Table table = tableEnv.sqlQuery("select * from source where id >5");

        //todo 4.输出表

        tableEnv.executeSql("insert into sink select * from source");






    }

}
