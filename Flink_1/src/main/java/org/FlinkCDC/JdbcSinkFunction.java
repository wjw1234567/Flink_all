package org.FlinkCDC;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;


import java.sql.DriverManager;

public class JdbcSinkFunction extends RichSinkFunction<String> {


    private transient Connection connection;
    private PreparedStatement insertStmt;
    private PreparedStatement updateStmt;
    private PreparedStatement deleteStmt;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 初始化数据库连接（目标库）<span class="tags-container"><span class="little-tags">3</span></span>
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(
                "jdbc:mysql://node1:3306/Parking?useSSL=false",
                "root",
                "root"
        );
        connection.setAutoCommit(false); // 配合检查点机制
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        // 解析CDC事件（示例为JSON格式，需根据实际反序列化结果调整）
        JsonNode json = new ObjectMapper().readTree(value);
        String op = json.get("op").asText(); // 操作类型
        JsonNode before = json.get("before");
        JsonNode after = json.get("after");

        switch(op) {
            case "c": // INSERT
                insertStmt = connection.prepareStatement(
                        "INSERT INTO table (id,name,age) VALUES (?,?,?)");
                insertStmt.setInt(1, after.get("id").asInt());
                insertStmt.setString(2, after.get("name").asText());
                insertStmt.setInt(3, after.get("age").asInt());
                insertStmt.executeUpdate();
                break;

            case "u": // UPDATE<span class="tags-container"><span class="little-tags">7</span></span>
                updateStmt = connection.prepareStatement(
                        "UPDATE table SET name=?,age=? WHERE id=?");
                updateStmt.setString(1, after.get("name").asText());
                updateStmt.setInt(2, after.get("age").asInt());
                updateStmt.setInt(3, before.get("id").asInt());
                updateStmt.executeUpdate();
                break;

            case "d": // DELETE<span class="tags-container"><span class="little-tags">2</span></span>
                deleteStmt = connection.prepareStatement(
                        "DELETE FROM table WHERE id=?");
                deleteStmt.setInt(1, before.get("id").asInt());
                deleteStmt.executeUpdate();
                break;
        }
    }

    @Override
    public void close() throws Exception {
        if (insertStmt != null) insertStmt.close();
        if (updateStmt != null) updateStmt.close();
        if (deleteStmt != null) deleteStmt.close();
        if (connection != null) connection.close();
    }



}
