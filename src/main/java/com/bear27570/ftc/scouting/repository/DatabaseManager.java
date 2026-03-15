// File: src/main/java/com/bear27570/ftc/scouting/repository/DatabaseManager.java
package com.bear27570.ftc.scouting.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    public static synchronized void initialize(String dbUrl) {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setMaximumPoolSize(25);      // 维持 25 个活跃连接 (适合 FTC 本地局域网并发量)
            config.setMinimumIdle(5);           // 最少保持 5 个空闲连接
            config.setConnectionTimeout(30000); // 30 秒获取连接超时
            config.setIdleTimeout(600000);      // 10 分钟空闲断开

            dataSource = new HikariDataSource(config);
            System.out.println("[DB] HikariCP Connection Pool Initialized successfully.");

            // ★ 核心修复：在数据库连通后，立刻检查并创建所有缺失的表
            createTablesIfNotExist();
        }
    }

    private static void createTablesIfNotExist() {
        // 用户表 (包含了新版本加入的 geminiApiKey)
        String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                "username VARCHAR(255) PRIMARY KEY, " +
                "password VARCHAR(255), " +
                "geminiApiKey VARCHAR(255) DEFAULT ''" +
                ")";

        // 赛事表 (包含了新版本加入的 FTCScout 绑定信息)
        String createCompetitions = "CREATE TABLE IF NOT EXISTS competitions (" +
                "name VARCHAR(255) PRIMARY KEY, " +
                "creatorUsername VARCHAR(255), " +
                "ratingFormula VARCHAR(255) DEFAULT 'total', " +
                "eventSeason INT DEFAULT 0, " +
                "eventCode VARCHAR(50) DEFAULT '', " +
                "officialEventName VARCHAR(255) DEFAULT ''" +
                ")";

        // 成员权限表
        String createMemberships = "CREATE TABLE IF NOT EXISTS memberships (" +
                "username VARCHAR(255), " +
                "competitionName VARCHAR(255), " +
                "status VARCHAR(50), " +
                "PRIMARY KEY(username, competitionName)" +
                ")";

        // 判罚表
        String createPenalties = "CREATE TABLE IF NOT EXISTS penalties (" +
                "competitionName VARCHAR(255), " +
                "matchNumber INT, " +
                "redMajor INT DEFAULT 0, " +
                "redMinor INT DEFAULT 0, " +
                "blueMajor INT DEFAULT 0, " +
                "blueMinor INT DEFAULT 0, " +
                "redScore INT DEFAULT 0, " +
                "blueScore INT DEFAULT 0, " +
                "PRIMARY KEY(competitionName, matchNumber)" +
                ")";

        // 详细得分表 (包含所有最新的自动阶段 6 个新列)
        String createScores = "CREATE TABLE IF NOT EXISTS scores (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "competitionName VARCHAR(255), " +
                "scoreType VARCHAR(50), " +
                "matchNumber INT, " +
                "alliance VARCHAR(10), " +
                "team1 INT, " +
                "team2 INT, " +
                "team1AutoScore INT DEFAULT 0, " +
                "team2AutoScore INT DEFAULT 0, " +
                "team1AutoProj VARCHAR(20) DEFAULT 'NONE', " +
                "team2AutoProj VARCHAR(20) DEFAULT 'NONE', " +
                "team1AutoRow VARCHAR(20) DEFAULT 'NONE', " +
                "team2AutoRow VARCHAR(20) DEFAULT 'NONE', " +
                "autoArtifacts INT DEFAULT 0, " +
                "teleopArtifacts INT DEFAULT 0, " +
                "team1CanSequence BOOLEAN DEFAULT FALSE, " +
                "team2CanSequence BOOLEAN DEFAULT FALSE, " +
                "team1L2Climb BOOLEAN DEFAULT FALSE, " +
                "team2L2Climb BOOLEAN DEFAULT FALSE, " +
                "team1Ignored BOOLEAN DEFAULT FALSE, " +
                "team2Ignored BOOLEAN DEFAULT FALSE, " +
                "team1Broken BOOLEAN DEFAULT FALSE, " +
                "team2Broken BOOLEAN DEFAULT FALSE, " +
                "totalScore INT DEFAULT 0, " +
                "clickLocations VARCHAR(10000), " +
                "submitter VARCHAR(255), " +
                "submissionTime VARCHAR(100), " +
                "syncStatus VARCHAR(50) DEFAULT 'UNSYNCED'" +
                ")";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createUsers);
            stmt.execute(createCompetitions);
            stmt.execute(createMemberships);
            stmt.execute(createPenalties);
            stmt.execute(createScores);
            System.out.println("[DB] Auto-generated missing tables successfully. Database is ready.");
        } catch (SQLException e) {
            System.err.println("[DB] Error creating initial tables: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new SQLException("DatabaseManager is not initialized! Call initialize() first.");
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] HikariCP Connection Pool safely closed.");
        }
    }
}