package com.bear27570.ftc.scouting.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    public static void initializeMasterDatabase(String dbUrl) {
        try (Connection conn = DriverManager.getConnection(dbUrl); Statement stmt = conn.createStatement()) {
            System.out.println("DEBUG: Initializing Database...");

            stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");

            stmt.execute("CREATE TABLE IF NOT EXISTS competitions (name VARCHAR(255) PRIMARY KEY, creatorUsername VARCHAR(255), ratingFormula VARCHAR(500) DEFAULT 'total', FOREIGN KEY (creatorUsername) REFERENCES users(username))");

            // 兼容旧数据库：尝试添加列 (新增 FTCScout 字段)
            try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS ratingFormula VARCHAR(500) DEFAULT 'total'"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS eventSeason INT DEFAULT 0"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS eventCode VARCHAR(100)"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS officialEventName VARCHAR(255)"); } catch (SQLException ignore) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS memberships (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), competitionName VARCHAR(255), status VARCHAR(50), FOREIGN KEY (username) REFERENCES users(username), FOREIGN KEY (competitionName) REFERENCES competitions(name), UNIQUE(username, competitionName))");

            stmt.execute("CREATE TABLE IF NOT EXISTS scores (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), scoreType VARCHAR(20) DEFAULT 'ALLIANCE', matchNumber INT, alliance VARCHAR(10), team1 INT, team2 INT, autoArtifacts INT, teleopArtifacts INT, team1CanSequence BOOLEAN, team2CanSequence BOOLEAN, team1L2Climb BOOLEAN, team2L2Climb BOOLEAN, team1Ignored BOOLEAN DEFAULT FALSE, team2Ignored BOOLEAN DEFAULT FALSE, team1Broken BOOLEAN DEFAULT FALSE, team2Broken BOOLEAN DEFAULT FALSE, totalScore INT, clickLocations TEXT, submitter VARCHAR(255), submissionTime VARCHAR(255), syncStatus VARCHAR(20) DEFAULT 'UNSYNCED', FOREIGN KEY (competitionName) REFERENCES competitions(name))");
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS syncStatus VARCHAR(20) DEFAULT 'UNSYNCED'"); } catch (SQLException ignore) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS penalties (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), matchNumber INT, redMajor INT DEFAULT 0, redMinor INT DEFAULT 0, blueMajor INT DEFAULT 0, blueMinor INT DEFAULT 0, UNIQUE(competitionName, matchNumber), FOREIGN KEY (competitionName) REFERENCES competitions(name) ON DELETE CASCADE)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}