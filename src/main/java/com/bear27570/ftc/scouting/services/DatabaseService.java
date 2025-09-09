package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseService {

    private static final String DB_URL = "jdbc:h2:./ftc_scouting_master_db"; // 统一的主数据库

    public static void initializeMasterDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
            // 用户表
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");
            // 比赛表
            stmt.execute("CREATE TABLE IF NOT EXISTS competitions (name VARCHAR(255) PRIMARY KEY, creatorUsername VARCHAR(255), FOREIGN KEY (creatorUsername) REFERENCES users(username))");
            // 成员资格表
            stmt.execute("CREATE TABLE IF NOT EXISTS memberships (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), competitionName VARCHAR(255), status VARCHAR(50), FOREIGN KEY (username) REFERENCES users(username), FOREIGN KEY (competitionName) REFERENCES competitions(name), UNIQUE(username, competitionName))");
            // 计分记录表
            stmt.execute("CREATE TABLE IF NOT EXISTS scores (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), matchNumber INT, alliance VARCHAR(10), team1 INT, team2 INT, autoArtifacts INT, teleopArtifacts INT, team1CanSequence BOOLEAN, team2CanSequence BOOLEAN, team1L2Climb BOOLEAN, team2L2Climb BOOLEAN, totalScore INT, submitter VARCHAR(255), submissionTime VARCHAR(255), FOREIGN KEY (competitionName) REFERENCES competitions(name))");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize master database", e);
        }
    }

    // --- User Methods ---
    public static boolean createUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("User creation failed, likely username exists: " + e.getMessage());
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                return storedPassword.equals(password);
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Competition Methods (all static) ---
    public static boolean createCompetition(String name, String creatorUsername) {
        String sql = "INSERT INTO competitions(name, creatorUsername) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, creatorUsername);
            pstmt.executeUpdate();
            addMembership(creatorUsername, name, Membership.Status.CREATOR);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static List<Competition> getAllCompetitions() {
        List<Competition> competitions = new ArrayList<>();
        String sql = "SELECT * FROM competitions";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                competitions.add(new Competition(rs.getString("name"), rs.getString("creatorUsername")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return competitions;
    }

    // --- Membership Methods (all static) ---
    public static void addMembership(String username, String competitionName, Membership.Status status) {
        String sql = "INSERT INTO memberships(username, competitionName, status) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, competitionName);
            pstmt.setString(3, status.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            // Fail silently
        }
    }

    public static Membership.Status getMembershipStatus(String username, String competitionName) {
        String sql = "SELECT status FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, competitionName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Membership.Status.valueOf(rs.getString("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<String> getMembersByStatus(String competitionName, Membership.Status status) {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM memberships WHERE competitionName = ? AND status = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            pstmt.setString(2, status.name());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public static void updateMembershipStatus(String username, String competitionName, Membership.Status newStatus) {
        String sql = "UPDATE memberships SET status = ? WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name());
            pstmt.setString(2, username);
            pstmt.setString(3, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeMembership(String username, String competitionName) {
        String sql = "DELETE FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- Scoring Methods (all static) ---
    public static void saveScoreEntry(String competitionName, ScoreEntry entry) {
        String sql = "INSERT INTO scores(competitionName, matchNumber, alliance, team1, team2, autoArtifacts, teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb, totalScore, submitter, submissionTime) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            pstmt.setInt(2, entry.getMatchNumber());
            pstmt.setString(3, entry.getAlliance());
            pstmt.setInt(4, entry.getTeam1());
            pstmt.setInt(5, entry.getTeam2());
            pstmt.setInt(6, entry.getAutoArtifacts());
            pstmt.setInt(7, entry.getTeleopArtifacts());
            pstmt.setBoolean(8, entry.isTeam1CanSequence());
            pstmt.setBoolean(9, entry.isTeam2CanSequence());
            pstmt.setBoolean(10, entry.isTeam1L2Climb());
            pstmt.setBoolean(11, entry.isTeam2L2Climb());
            pstmt.setInt(12, entry.getTotalScore());
            pstmt.setString(13, entry.getSubmitter());
            pstmt.setString(14, entry.getSubmissionTime());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<ScoreEntry> getScoresForCompetition(String competitionName) {
        List<ScoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM scores WHERE competitionName = ? ORDER BY id DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                entries.add(new ScoreEntry(
                        rs.getInt("matchNumber"), rs.getString("alliance"), rs.getInt("team1"), rs.getInt("team2"),
                        rs.getInt("autoArtifacts"), rs.getInt("teleopArtifacts"), rs.getBoolean("team1CanSequence"),
                        rs.getBoolean("team2CanSequence"), rs.getBoolean("team1L2Climb"), rs.getBoolean("team2L2Climb"),
                        rs.getString("submitter")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return entries;
    }

    public static List<TeamRanking> calculateTeamRankings(String competitionName) {
        List<ScoreEntry> allScores = getScoresForCompetition(competitionName);
        Map<Integer, TeamRanking> rankings = new HashMap<>();

        for (ScoreEntry score : allScores) {
            // Process team 1
            int team1Num = score.getTeam1();
            rankings.putIfAbsent(team1Num, new TeamRanking(team1Num));
            // For alliance-based scores, we can't easily assign them to one team, so we assign to both
            rankings.get(team1Num).addMatchResult(score.getAutoArtifacts(), score.getTeleopArtifacts(), score.isTeam1CanSequence(), score.isTeam1L2Climb());

            // Process team 2
            int team2Num = score.getTeam2();
            rankings.putIfAbsent(team2Num, new TeamRanking(team2Num));
            rankings.get(team2Num).addMatchResult(score.getAutoArtifacts(), score.getTeleopArtifacts(), score.isTeam2CanSequence(), score.isTeam2L2Climb());
        }
        return new ArrayList<>(rankings.values());
    }
}