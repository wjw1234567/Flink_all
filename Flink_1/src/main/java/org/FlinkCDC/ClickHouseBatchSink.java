package org.FlinkCDC;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.eclipse.jetty.util.ajax.JSON;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class ClickHouseBatchSink extends RichSinkFunction<List<String>> {
    private transient Connection conn;
    private transient PreparedStatement insertStmt;
    private transient PreparedStatement deleteStmt;

    private transient PreparedStatement optimize;


    @Override
    public void open(Configuration parameters) throws SQLException {
        conn = DriverManager.getConnection("jdbc:clickhouse://node1:8123/db_ck1","ck_u1","ck_u1");
        conn.setAutoCommit(false);  // 关闭自动提交[1][5]

        insertStmt = conn.prepareStatement(
                "INSERT INTO db_ck1.ws(id,ts,vc) VALUES (?, ?, ?)"); // 按字段顺序

        // 预编译不同操作的SQL
        deleteStmt = conn.prepareStatement(
                "alter table   db_ck1.ws delete  WHERE id = ?");


       // optimize = conn.prepareStatement("OPTIMIZE TABLE db_ck1.ws FINAL");


    }

    @Override
    public void invoke(List<String> batch, Context context) throws Exception{
        try {
            for (String event : batch) {

                JsonNode json = new ObjectMapper().readTree(event);
                String op = json.get("op").asText();
                JsonNode after_data = json.get("after");
                JsonNode before_data = json.get("before");



                switch (op) {
                    case "c": // INSERT
                        insertStmt.setString(1, after_data.get("id").asText());
                        insertStmt.setInt(2, after_data.get("ts").asInt());
                        insertStmt.setInt(3, after_data.get("vc").asInt());
                       // System.out.println("插入的数据binlog"+after_data);
                        insertStmt.addBatch(); // 添加批处理[1]
                        break;

                    case "d": // DELETE
                        deleteStmt.setString(1, before_data.get("id").asText());
                         deleteStmt.addBatch();
                        // deleteStmt.execute();

                       // System.out.println("删除的数据binlog"+before_data);
                        // conn.commit();

                        break;
                }


            }

            executeBatch();


        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    private void executeBatch() throws SQLException {
        int[] insertCounts = insertStmt.executeBatch();
        int[] deleteCounts = deleteStmt.executeBatch();
        conn.commit(); // 提交事务[1]

        // optimize.execute();


        insertStmt.clearBatch(); // 清空批处理队列[9]
        deleteStmt.clearBatch();

        // batchCount = 0;
    }

    @Override
    public void close() throws Exception {

        insertStmt.close();
        deleteStmt.close();
        optimize.close();
        conn.close();
    }


}
