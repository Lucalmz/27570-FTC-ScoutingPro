package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.repository.*;
import com.bear27570.ftc.scouting.repository.impl.*;
import com.bear27570.ftc.scouting.services.domain.RankingService;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 门面类 (Facade)：
 * 封装了所有底层 Repository 和 Service，保持向后的兼容性，使得 Controller 代码无需大量修改。
 * 等我们下一步重构 Controller 时，这个类就可以慢慢被替换为依赖注入(DI)。
 */
public class DatabaseService {

    private static final String DB_FOLDER_PATH = System.getProperty("user.home") + File.separator + ".ftcscoutingpro";
    private static final String DB_URL = "jdbc:h2:" + DB_FOLDER_PATH + File.separator + "ftc_scouting_master_db";

    // 静态持有所有的底层实例
    private static UserRepository userRepository;
    private static MembershipRepository membershipRepository;
    private static ScoreRepository scoreRepository;
    private static PenaltyRepository penaltyRepository;
    private static CompetitionRepository competitionRepository;
    private static RankingService rankingService;

    public static void initializeMasterDatabase() {
        try {
            File dbFolder = new File(DB_FOLDER_PATH);
            if (!dbFolder.exists()) {
                dbFolder.mkdirs();
            }

            // 初始化数据库表结构
            try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");
                stmt.execute("CREATE TABLE IF NOT EXISTS competitions (name VARCHAR(255) PRIMARY KEY, creatorUsername VARCHAR(255), ratingFormula VARCHAR(500) DEFAULT 'total', FOREIGN KEY (creatorUsername) REFERENCES users(username))");
                try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS ratingFormula VARCHAR(500) DEFAULT 'total'"); } catch (SQLException ignore) {}
                stmt.execute("CREATE TABLE IF NOT EXISTS memberships (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), competitionName VARCHAR(255), status VARCHAR(50), FOREIGN KEY (username) REFERENCES users(username), FOREIGN KEY (competitionName) REFERENCES competitions(name), UNIQUE(username, competitionName))");
                stmt.execute("CREATE TABLE IF NOT EXISTS scores (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), scoreType VARCHAR(20) DEFAULT 'ALLIANCE', matchNumber INT, alliance VARCHAR(10), team1 INT, team2 INT, autoArtifacts INT, teleopArtifacts INT, team1CanSequence BOOLEAN, team2CanSequence BOOLEAN, team1L2Climb BOOLEAN, team2L2Climb BOOLEAN, team1Ignored BOOLEAN DEFAULT FALSE, team2Ignored BOOLEAN DEFAULT FALSE, team1Broken BOOLEAN DEFAULT FALSE, team2Broken BOOLEAN DEFAULT FALSE, totalScore INT, clickLocations TEXT, submitter VARCHAR(255), submissionTime VARCHAR(255), FOREIGN KEY (competitionName) REFERENCES competitions(name))");
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                stmt.execute("CREATE TABLE IF NOT EXISTS penalties (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), matchNumber INT, redMajor INT DEFAULT 0, redMinor INT DEFAULT 0, blueMajor INT DEFAULT 0, blueMinor INT DEFAULT 0, UNIQUE(competitionName, matchNumber))");
            }

            // 实例化所有 Repository 和 Service (手动装配依赖)
            userRepository = new UserRepositoryJdbcImpl(DB_URL);
            membershipRepository = new MembershipRepositoryJdbcImpl(DB_URL);
            scoreRepository = new ScoreRepositoryJdbcImpl(DB_URL);
            penaltyRepository = new PenaltyRepositoryJdbcImpl(DB_URL);
            competitionRepository = new CompetitionRepositoryJdbcImpl(DB_URL);

            // 注入依赖到业务逻辑服务
            rankingService = new RankingService(scoreRepository, penaltyRepository, competitionRepository);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("CRITICAL: Failed to initialize master database", e);
        }
    }

    // ==========================================
    // 代理方法：转发给 UserRepository
    // ==========================================
    public static boolean createUser(String username, String password) {
        return userRepository.createUser(username, password);
    }

    public static boolean authenticateUser(String username, String password) {
        return userRepository.authenticateUser(username, password);
    }

    // ==========================================
    // 代理方法：转发给 CompetitionRepository & MembershipRepository
    // ==========================================
    public static boolean createCompetition(String name, String creatorUsername) {
        // 创建比赛直接通过原生 SQL 比较特殊，这里为了保持兼容保留原始逻辑，或者你可以放在 CompetitionRepo 中
        String sql = "INSERT INTO competitions(name, creatorUsername, ratingFormula) VALUES(?, ?, 'total')";
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
        return competitionRepository.findAll();
    }

    public static Competition getCompetition(String name) {
        return competitionRepository.findByName(name);
    }

    public static void updateCompetitionFormula(String competitionName, String newFormula) {
        competitionRepository.updateFormula(competitionName, newFormula);
    }

    public static void addMembership(String username, String competitionName, Membership.Status status) {
        userRepository.ensureUserExists(username);
        membershipRepository.addMembership(username, competitionName, status);
    }

    public static Membership.Status getMembershipStatus(String username, String competitionName) {
        return membershipRepository.getMembershipStatus(username, competitionName);
    }

    public static void removeMembership(String username, String competitionName) {
        membershipRepository.removeMembership(username, competitionName);
    }

    public static List<String> getMembersByStatus(String competitionName, Membership.Status status) {
        return membershipRepository.getMembersByStatus(competitionName, status);
    }

    public static void updateMembershipStatus(String username, String competitionName, Membership.Status newStatus) {
        membershipRepository.updateMembershipStatus(username, competitionName, newStatus);
    }

    // ==========================================
    // 代理方法：转发给 PenaltyRepository
    // ==========================================
    public static void savePenaltyEntry(String competitionName, PenaltyEntry entry) {
        penaltyRepository.savePenaltyEntry(competitionName, entry);
    }

    public static Map<Integer, PenaltyRepository.FullPenaltyRow> getFullPenalties(String competitionName) {
        return penaltyRepository.getFullPenalties(competitionName);
    }

    // ==========================================
    // 代理方法：转发给 ScoreRepository
    // ==========================================
    public static void saveOrUpdateScoreEntry(String competitionName, ScoreEntry entry) {
        if (entry.getId() > 0) {
            scoreRepository.update(entry);
        } else {
            scoreRepository.save(competitionName, entry);
        }
    }

    public static void deleteScoreEntry(int id) {
        scoreRepository.delete(id);
    }

    public static List<ScoreEntry> getScoresForCompetition(String competitionName) {
        return scoreRepository.findByCompetition(competitionName);
    }

    public static List<ScoreEntry> getScoresForTeam(String competitionName, int teamNumber) {
        return scoreRepository.findByTeam(competitionName, teamNumber);
    }

    // ==========================================
    // 代理方法：转发给核心 RankingService
    // ==========================================
    public static List<TeamRanking> calculateTeamRankings(String competitionName) {
        return rankingService.calculateRankings(competitionName);
    }
}