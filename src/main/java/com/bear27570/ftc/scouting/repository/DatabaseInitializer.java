package com.bear27570.ftc.scouting.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    public static void initializeMasterDatabase(String dbUrl) {
        try (Connection conn = DriverManager.getConnection(dbUrl); Statement stmt = conn.createStatement()) {
            System.out.println("DEBUG: Initializing Database...");

            // 1. 先创建 Users 表 (无依赖)
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");

            // 2. 创建 Competitions 表 (依赖 users)
            stmt.execute("CREATE TABLE IF NOT EXISTS competitions (name VARCHAR(255) PRIMARY KEY, creatorUsername VARCHAR(255), ratingFormula VARCHAR(500) DEFAULT 'total', FOREIGN KEY (creatorUsername) REFERENCES users(username))");
            // 兼容旧数据库：尝试添加列
            try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS ratingFormula VARCHAR(500) DEFAULT 'total'"); } catch (SQLException ignore) {}

            // 3. 创建 Memberships 表 (依赖 users, competitions)
            stmt.execute("CREATE TABLE IF NOT EXISTS memberships (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), competitionName VARCHAR(255), status VARCHAR(50), FOREIGN KEY (username) REFERENCES users(username), FOREIGN KEY (competitionName) REFERENCES competitions(name), UNIQUE(username, competitionName))");

            // 4. 创建 Scores 表 (依赖 competitions)
            stmt.execute("CREATE TABLE IF NOT EXISTS scores (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), scoreType VARCHAR(20) DEFAULT 'ALLIANCE', matchNumber INT, alliance VARCHAR(10), team1 INT, team2 INT, autoArtifacts INT, teleopArtifacts INT, team1CanSequence BOOLEAN, team2CanSequence BOOLEAN, team1L2Climb BOOLEAN, team2L2Climb BOOLEAN, team1Ignored BOOLEAN DEFAULT FALSE, team2Ignored BOOLEAN DEFAULT FALSE, team1Broken BOOLEAN DEFAULT FALSE, team2Broken BOOLEAN DEFAULT FALSE, totalScore INT, clickLocations TEXT, submitter VARCHAR(255), submissionTime VARCHAR(255), FOREIGN KEY (competitionName) REFERENCES competitions(name))");

            // 兼容旧数据库：添加分数列
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}

            // 5. 创建 Penalties 表 (依赖 competitions)
            stmt.execute("CREATE TABLE IF NOT EXISTS penalties (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), matchNumber INT, redMajor INT DEFAULT 0, redMinor INT DEFAULT 0, blueMajor INT DEFAULT 0, blueMinor INT DEFAULT 0, UNIQUE(competitionName, matchNumber), FOREIGN KEY (competitionName) REFERENCES competitions(name) ON DELETE CASCADE)");

            System.out.println("DEBUG: Database Initialized Successfully.");
        } catch (SQLException e) {
            System.err.println("CRITICAL: Failed to initialize master database. Cause: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("CRITICAL: Failed to initialize master database", e);
        }
    }
}