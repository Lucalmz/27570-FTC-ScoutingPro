package com.bear27570.ftc.scouting.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    public static synchronized void initialize(String dbUrl) {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setMaximumPoolSize(20); // 维持20个活跃连接
            config.setMinimumIdle(5);      // 最少保持5个空闲连接
            config.setConnectionTimeout(30000); // 30秒超时
            config.setIdleTimeout(600000); // 10分钟空闲断开
            dataSource = new HikariDataSource(config);
            System.out.println("[DB] HikariCP Connection Pool Initialized.");
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("DatabaseManager is not initialized!");
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}