package org.FlinkCDC;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class JdbcBatchSinkFunction extends RichSinkFunction<List<String>> {


    private transient Connection connection;
    private transient PreparedStatement insertStmt;
    private transient PreparedStatement updateStmt;
    private transient PreparedStatement deleteStmt;
    private int batchSize = 5; // 批处理阈值[1][5]
    private int batchCount = 0;
    private final ObjectMapper objectMapper = new ObjectMapper();





    @Override
    public void open(Configuration parameters) throws Exception {
        // 初始化数据库连接
        connection = DriverManager.getConnection("jdbc:mysql://node2:3306/Parking", "root", "root");
        connection.setAutoCommit(false); // 关闭自动提交[1][5]

        // 预编译不同操作的SQL
        insertStmt = connection.prepareStatement(
                "replace INTO ws (id, ts,vc) VALUES (?, ?,?)");
        updateStmt = connection.prepareStatement(
                "UPDATE ws SET ts = ?,vc=? WHERE id = ?");
        deleteStmt = connection.prepareStatement(
                "DELETE FROM ws WHERE id = ?");


    }

    @Override
    public void invoke(List<String> batch, Context context) throws Exception {

        try{
        for (String event : batch) {
            // 解析Debezium JSON格式[6]
            JsonNode json = new ObjectMapper().readTree(event);
            String op = json.get("op").asText();
            JsonNode after_data = json.get("after");
            JsonNode before_data = json.get("before");

            switch (op) {
                case "c": // INSERT
                    insertStmt.setString(1, after_data.get("id").asText());
                    insertStmt.setInt(2, after_data.get("ts").asInt());
                    insertStmt.setInt(3, after_data.get("vc").asInt());
                    insertStmt.addBatch(); // 添加批处理[1]
                    break;
                case "u": // UPDATE
                    updateStmt.setInt(1, after_data.get("ts").asInt());
                    updateStmt.setInt(2, after_data.get("vc").asInt());
                    updateStmt.setString(3, after_data.get("id").asText());
                    updateStmt.addBatch();
                    break;
                case "d": // DELETE
                    deleteStmt.setString(1, before_data.get("id").asText());
                    deleteStmt.addBatch();

                    break;
            }

        }

        executeBatch();

    } catch (Exception e) {
        e.printStackTrace();

        }
    }





    private void executeBatch() throws SQLException {
        int[] insertCounts = insertStmt.executeBatch();
        int[] updateCounts = updateStmt.executeBatch();
        int[] deleteCounts = deleteStmt.executeBatch();
        connection.commit(); // 提交事务[1]

        insertStmt.clearBatch(); // 清空批处理队列[9]
        updateStmt.clearBatch();
        deleteStmt.clearBatch();
        // batchCount = 0;
    }

    @Override
    public void close() throws Exception {
/*        if (batchCount > 0) {
            executeBatch(); // 最后一批数据提交[6]
        }*/
        insertStmt.close();
        updateStmt.close();
        deleteStmt.close();
        connection.close();
    }



}
