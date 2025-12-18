package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseService {

    private static final String DB_FOLDER_PATH = System.getProperty("user.home") + File.separator + ".ftcscoutingpro";
    private static final String DB_URL = "jdbc:h2:" + DB_FOLDER_PATH + File.separator + "ftc_scouting_master_db";

    public static void initializeMasterDatabase() {
        try {
            File dbFolder = new File(DB_FOLDER_PATH);
            if (!dbFolder.exists()) dbFolder.mkdirs();

            try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");

                stmt.execute("CREATE TABLE IF NOT EXISTS competitions (name VARCHAR(255) PRIMARY KEY, creatorUsername VARCHAR(255), ratingFormula VARCHAR(500) DEFAULT 'total', FOREIGN KEY (creatorUsername) REFERENCES users(username))");
                try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS ratingFormula VARCHAR(500) DEFAULT 'total'"); } catch (SQLException ignore) {}

                stmt.execute("CREATE TABLE IF NOT EXISTS memberships (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), competitionName VARCHAR(255), status VARCHAR(50), FOREIGN KEY (username) REFERENCES users(username), FOREIGN KEY (competitionName) REFERENCES competitions(name), UNIQUE(username, competitionName))");

                // 更新 Scores 表结构
                stmt.execute("CREATE TABLE IF NOT EXISTS scores (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), scoreType VARCHAR(20) DEFAULT 'ALLIANCE', matchNumber INT, alliance VARCHAR(10), team1 INT, team2 INT, autoArtifacts INT, teleopArtifacts INT, team1CanSequence BOOLEAN, team2CanSequence BOOLEAN, team1L2Climb BOOLEAN, team2L2Climb BOOLEAN, totalScore INT, clickLocations TEXT, submitter VARCHAR(255), submissionTime VARCHAR(255), FOREIGN KEY (competitionName) REFERENCES competitions(name))");
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS scoreType VARCHAR(20) DEFAULT 'ALLIANCE'"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS clickLocations TEXT"); } catch (SQLException ignore) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("CRITICAL: Failed to initialize master database", e);
        }
    }

    // ... createUser, authenticateUser, createCompetition, getAllCompetitions, updateCompetitionFormula, getCompetition, Membership methods ...
    // (这些方法保持不变，为节省篇幅省略，请使用上一版代码)
    // ---------------------------------------------------------
    // 以下是必须替换的方法
    // ---------------------------------------------------------

    // 补全省略的方法，确保代码完整性
    public static boolean createUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, password); pstmt.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }
    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("password").equals(password); return false;
        } catch (SQLException e) { return false; }
    }
    public static boolean createCompetition(String name, String creatorUsername) {
        String sql = "INSERT INTO competitions(name, creatorUsername, ratingFormula) VALUES(?, ?, 'total')";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name); pstmt.setString(2, creatorUsername); pstmt.executeUpdate();
            addMembership(creatorUsername, name, Membership.Status.CREATOR); return true;
        } catch (SQLException e) { return false; }
    }
    public static List<Competition> getAllCompetitions() {
        List<Competition> competitions = new ArrayList<>();
        String sql = "SELECT * FROM competitions";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) competitions.add(new Competition(rs.getString("name"), rs.getString("creatorUsername"), rs.getString("ratingFormula")));
        } catch (SQLException e) { e.printStackTrace(); }
        return competitions;
    }
    public static void updateCompetitionFormula(String competitionName, String newFormula) {
        String sql = "UPDATE competitions SET ratingFormula = ? WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFormula); pstmt.setString(2, competitionName); pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public static Competition getCompetition(String name) { return getAllCompetitions().stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null); }
    public static void addMembership(String username, String competitionName, Membership.Status status) {
        String sql = "INSERT INTO memberships(username, competitionName, status) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, competitionName); pstmt.setString(3, status.name()); pstmt.executeUpdate();
        } catch (SQLException e) {}
    }
    public static Membership.Status getMembershipStatus(String username, String competitionName) {
        String sql = "SELECT status FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, competitionName); ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return Membership.Status.valueOf(rs.getString("status"));
        } catch (SQLException e) {} return null;
    }
    public static void removeMembership(String username, String competitionName) {
        String sql = "DELETE FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, competitionName); pstmt.executeUpdate();
        } catch (SQLException e) {}
    }
    public static List<String> getMembersByStatus(String competitionName, Membership.Status status) {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM memberships WHERE competitionName = ? AND status = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName); pstmt.setString(2, status.name()); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) users.add(rs.getString("username"));
        } catch (SQLException e) {} return users;
    }
    public static void updateMembershipStatus(String username, String competitionName, Membership.Status newStatus) {
        String sql = "UPDATE memberships SET status = ? WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name()); pstmt.setString(2, username); pstmt.setString(3, competitionName); pstmt.executeUpdate();
        } catch (SQLException e) {}
    }

    public static void saveScoreEntry(String competitionName, ScoreEntry entry) {
        String sql = "INSERT INTO scores(competitionName, scoreType, matchNumber, alliance, team1, team2, autoArtifacts, teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb, totalScore, clickLocations, submitter, submissionTime) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            pstmt.setString(2, entry.getScoreType().name());
            pstmt.setInt(3, entry.getMatchNumber());
            pstmt.setString(4, entry.getAlliance());
            pstmt.setInt(5, entry.getTeam1());
            pstmt.setInt(6, entry.getTeam2());
            pstmt.setInt(7, entry.getAutoArtifacts());
            pstmt.setInt(8, entry.getTeleopArtifacts());
            pstmt.setBoolean(9, entry.isTeam1CanSequence());
            pstmt.setBoolean(10, entry.isTeam2CanSequence());
            pstmt.setBoolean(11, entry.isTeam1L2Climb());
            pstmt.setBoolean(12, entry.isTeam2L2Climb());
            pstmt.setInt(13, entry.getTotalScore());
            pstmt.setString(14, entry.getClickLocations()); // 保存坐标
            pstmt.setString(15, entry.getSubmitter());
            pstmt.setString(16, entry.getSubmissionTime());
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static List<ScoreEntry> getScoresForCompetition(String competitionName) {
        List<ScoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM scores WHERE competitionName = ? ORDER BY id DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String typeStr = rs.getString("scoreType");
                ScoreEntry.Type type = (typeStr == null) ? ScoreEntry.Type.ALLIANCE : ScoreEntry.Type.valueOf(typeStr);

                entries.add(new ScoreEntry(
                        type,
                        rs.getInt("matchNumber"), rs.getString("alliance"), rs.getInt("team1"), rs.getInt("team2"),
                        rs.getInt("autoArtifacts"), rs.getInt("teleopArtifacts"), rs.getBoolean("team1CanSequence"),
                        rs.getBoolean("team2CanSequence"), rs.getBoolean("team1L2Climb"), rs.getBoolean("team2L2Climb"),
                        rs.getString("clickLocations"), // 读取坐标
                        rs.getString("submitter")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return entries;
    }

    // --- 核心修改：平均分逻辑 ---
    public static List<TeamRanking> calculateTeamRankings(String competitionName) {
        List<ScoreEntry> allScores = getScoresForCompetition(competitionName);
        Competition comp = getCompetition(competitionName);
        String formulaStr = (comp != null && comp.getRatingFormula() != null) ? comp.getRatingFormula() : "total";

        Map<Integer, TeamRanking> rankings = new HashMap<>();
        Map<Integer, Double> totalRatingPoints = new HashMap<>();

        for (ScoreEntry score : allScores) {
            // 1. 判断是否需要平分数据
            boolean isAllianceMode = (score.getScoreType() == ScoreEntry.Type.ALLIANCE);
            double divisor = isAllianceMode ? 2.0 : 1.0;

            // 2. 准备用于计算的数据（如果是联盟模式，数值除以2）
            double adjAuto = score.getAutoArtifacts() / divisor;
            double adjTeleop = score.getTeleopArtifacts() / divisor;
            double adjTotal = score.getTotalScore() / divisor;

            // 3. 计算 Rating (传入调整后的数据)
            double matchRating = evaluateFormula(formulaStr, score, divisor);

            // 4. 处理 Team 1
            processTeam(rankings, totalRatingPoints, score.getTeam1(), score, adjAuto, adjTeleop, matchRating, true);

            // 5. 处理 Team 2 (仅限联盟模式)
            if (isAllianceMode) {
                processTeam(rankings, totalRatingPoints, score.getTeam2(), score, adjAuto, adjTeleop, matchRating, false);
            }
        }

        for (TeamRanking rank : rankings.values()) {
            double sum = totalRatingPoints.getOrDefault(rank.getTeamNumber(), 0.0);
            if (rank.getMatchesPlayed() > 0) {
                rank.setRating(sum / rank.getMatchesPlayed());
            }
        }
        return new ArrayList<>(rankings.values());
    }

    private static void processTeam(Map<Integer, TeamRanking> rankings, Map<Integer, Double> totalRatingPoints,
                                    int teamNum, ScoreEntry score, double adjAuto, double adjTeleop, double matchRating, boolean isTeam1) {
        rankings.putIfAbsent(teamNum, new TeamRanking(teamNum));
        TeamRanking tr = rankings.get(teamNum);

        boolean seq = isTeam1 ? score.isTeam1CanSequence() : score.isTeam2CanSequence();
        boolean climb = isTeam1 ? score.isTeam1L2Climb() : score.isTeam2L2Climb();

        // 注意：addMatchResult 现在接收 double 类型的分数
        // 我们需要在 TeamRanking 中重载或修改 addMatchResult 接收 double，或者转 int
        // 这里为了兼容性，我们在 TeamRanking 里做微调，或者直接传 int (四舍五入)
        // 鉴于 TeamRanking 内部用 double 存 avg，我们可以传 double
        // 但 TeamRanking.addMatchResult 目前签名是 int, int。
        // 为了最少改动，我们依然传原始 int，但在计算 Avg 时除以 divisor？
        // 不，最好的方式是让 TeamRanking 接收 double。
        // 由于不能修改 TeamRanking 签名太大，我们这里强转 int 可能会丢失精度。
        // **修正策略**：TeamRanking.addMatchResult 改为接收 double

        tr.addMatchResult(adjAuto, adjTeleop, seq, climb); // 需要去修改 TeamRanking

        totalRatingPoints.put(teamNum, totalRatingPoints.getOrDefault(teamNum, 0.0) + matchRating);
    }

    private static double evaluateFormula(String formula, ScoreEntry score, double divisor) {
        try {
            int seqAny = (score.isTeam1CanSequence() || score.isTeam2CanSequence()) ? 1 : 0;
            int climbAny = (score.isTeam1L2Climb() || score.isTeam2L2Climb()) ? 1 : 0;

            Expression e = new ExpressionBuilder(formula)
                    .variables("auto", "teleop", "total", "seq", "climb")
                    .build()
                    // 变量传入平分后的值，体现个人实力
                    .setVariable("auto", score.getAutoArtifacts() / divisor)
                    .setVariable("teleop", score.getTeleopArtifacts() / divisor)
                    .setVariable("total", score.getTotalScore() / divisor)
                    .setVariable("seq", seqAny)
                    .setVariable("climb", climbAny);
            return e.evaluate();
        } catch (Exception e) {
            return score.getTotalScore() / divisor;
        }
    }

    // 获取某队所有比赛记录 (用于热点图)
    public static List<ScoreEntry> getScoresForTeam(String competitionName, int teamNumber) {
        List<ScoreEntry> all = getScoresForCompetition(competitionName);
        List<ScoreEntry> teamScores = new ArrayList<>();
        for (ScoreEntry s : all) {
            if (s.getTeam1() == teamNumber || (s.getScoreType() == ScoreEntry.Type.ALLIANCE && s.getTeam2() == teamNumber)) {
                teamScores.add(s);
            }
        }
        return teamScores;
    }
}