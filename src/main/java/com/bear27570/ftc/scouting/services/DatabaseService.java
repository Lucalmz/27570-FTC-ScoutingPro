package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.*;
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

    private static final int PENALTY_MAJOR_PTS = 15;
    private static final int PENALTY_MINOR_PTS = 5;

    public static void initializeMasterDatabase() {
        try {
            File dbFolder = new File(DB_FOLDER_PATH);
            if (!dbFolder.exists()) dbFolder.mkdirs();

            try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
                // 1. Users Table
                stmt.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");

                // 2. Competitions Table (Auto-migration for ratingFormula)
                stmt.execute("CREATE TABLE IF NOT EXISTS competitions (name VARCHAR(255) PRIMARY KEY, creatorUsername VARCHAR(255), ratingFormula VARCHAR(500) DEFAULT 'total', FOREIGN KEY (creatorUsername) REFERENCES users(username))");
                try { stmt.execute("ALTER TABLE competitions ADD COLUMN IF NOT EXISTS ratingFormula VARCHAR(500) DEFAULT 'total'"); } catch (SQLException ignore) {}

                // 3. Memberships Table
                stmt.execute("CREATE TABLE IF NOT EXISTS memberships (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), competitionName VARCHAR(255), status VARCHAR(50), FOREIGN KEY (username) REFERENCES users(username), FOREIGN KEY (competitionName) REFERENCES competitions(name), UNIQUE(username, competitionName))");

                // 4. Scores Table
                // Updated definition for new installs
                stmt.execute("CREATE TABLE IF NOT EXISTS scores (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), scoreType VARCHAR(20) DEFAULT 'ALLIANCE', matchNumber INT, alliance VARCHAR(10), team1 INT, team2 INT, autoArtifacts INT, teleopArtifacts INT, team1CanSequence BOOLEAN, team2CanSequence BOOLEAN, team1L2Climb BOOLEAN, team2L2Climb BOOLEAN, team1Ignored BOOLEAN DEFAULT FALSE, team2Ignored BOOLEAN DEFAULT FALSE, team1Broken BOOLEAN DEFAULT FALSE, team2Broken BOOLEAN DEFAULT FALSE, totalScore INT, clickLocations TEXT, submitter VARCHAR(255), submissionTime VARCHAR(255), FOREIGN KEY (competitionName) REFERENCES competitions(name))");

                // *** CRITICAL FIX: Schema Migration for Existing Databases ***
                // This adds the missing columns if they don't exist
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Ignored BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}
                try { stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2Broken BOOLEAN DEFAULT FALSE"); } catch (SQLException ignore) {}

                // 5. Penalties Table
                stmt.execute("CREATE TABLE IF NOT EXISTS penalties (id INT AUTO_INCREMENT PRIMARY KEY, competitionName VARCHAR(255), matchNumber INT, redMajor INT DEFAULT 0, redMinor INT DEFAULT 0, blueMajor INT DEFAULT 0, blueMinor INT DEFAULT 0, UNIQUE(competitionName, matchNumber))");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("CRITICAL: Failed to initialize master database", e);
        }
    }

    // ... (User, Membership, Competition methods) ...

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
    public static Competition getCompetition(String name) {
        return getAllCompetitions().stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }
    public static void updateCompetitionFormula(String competitionName, String newFormula) {
        String sql = "UPDATE competitions SET ratingFormula = ? WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFormula); pstmt.setString(2, competitionName); pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void addMembership(String username, String competitionName, Membership.Status status) {
        ensureUserExists(username);
        String checkSql = "SELECT status FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username); checkStmt.setString(2, competitionName);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                String currentStatus = rs.getString("status");
                if (currentStatus.equals("PENDING") && status == Membership.Status.APPROVED) {
                    updateMembershipStatus(username, competitionName, status);
                }
                return;
            }
        } catch (SQLException e) { e.printStackTrace(); }

        String sql = "INSERT INTO memberships(username, competitionName, status) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, competitionName); pstmt.setString(3, status.name());
            pstmt.executeUpdate();
        } catch (SQLException e) { System.err.println("Database Add Membership Error: " + e.getMessage()); }
    }

    private static void ensureUserExists(String username) {
        String checkSql = "SELECT 1 FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users(username, password) VALUES(?, 'guest_account')";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, username);
                if (checkStmt.executeQuery().next()) return;
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, username); insertStmt.executeUpdate();
                System.out.println("Host DB auto-created guest user: " + username);
            }
        } catch (SQLException e) { e.printStackTrace(); }
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

    // --- Penalties ---
    public static void savePenaltyEntry(String competitionName, PenaltyEntry entry) {
        boolean exists = false;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM penalties WHERE competitionName=? AND matchNumber=?")) {
            checkStmt.setString(1, competitionName); checkStmt.setInt(2, entry.getMatchNumber());
            exists = checkStmt.executeQuery().next();
        } catch (SQLException e) { e.printStackTrace(); return; }

        String sql;
        if (!exists) {
            sql = "INSERT INTO penalties (competitionName, matchNumber, redMajor, redMinor, blueMajor, blueMinor) VALUES (?, ?, ?, ?, ?, ?)";
        } else {
            if (entry.getAlliance().equalsIgnoreCase("RED")) sql = "UPDATE penalties SET redMajor=?, redMinor=? WHERE competitionName=? AND matchNumber=?";
            else sql = "UPDATE penalties SET blueMajor=?, blueMinor=? WHERE competitionName=? AND matchNumber=?";
        }

        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (!exists) {
                pstmt.setString(1, competitionName);
                pstmt.setInt(2, entry.getMatchNumber());
                if (entry.getAlliance().equalsIgnoreCase("RED")) {
                    pstmt.setInt(3, entry.getMajorCount()); pstmt.setInt(4, entry.getMinorCount());
                    pstmt.setInt(5, 0); pstmt.setInt(6, 0);
                } else {
                    pstmt.setInt(3, 0); pstmt.setInt(4, 0);
                    pstmt.setInt(5, entry.getMajorCount()); pstmt.setInt(6, entry.getMinorCount());
                }
                pstmt.executeUpdate();
            } else {
                pstmt.setInt(1, entry.getMajorCount());
                pstmt.setInt(2, entry.getMinorCount());
                pstmt.setString(3, competitionName);
                pstmt.setInt(4, entry.getMatchNumber());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static Map<Integer, FullPenaltyRow> getFullPenalties(String competitionName) {
        Map<Integer, FullPenaltyRow> map = new HashMap<>();
        String sql = "SELECT * FROM penalties WHERE competitionName = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getInt("matchNumber"), new FullPenaltyRow(
                        rs.getInt("redMajor"), rs.getInt("redMinor"),
                        rs.getInt("blueMajor"), rs.getInt("blueMinor")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public static class FullPenaltyRow {
        public int rMaj, rMin, bMaj, bMin;
        public FullPenaltyRow(int rMaj, int rMin, int bMaj, int bMin) {
            this.rMaj = rMaj; this.rMin = rMin; this.bMaj = bMaj; this.bMin = bMin;
        }
    }

    // --- Scoring & Ranking ---
    public static void saveScoreEntry(String competitionName, ScoreEntry entry) {
        String sql = "INSERT INTO scores(competitionName, scoreType, matchNumber, alliance, team1, team2, " +
                "autoArtifacts, teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb, " +
                "team1Ignored, team2Ignored, team1Broken, team2Broken, " +
                "totalScore, clickLocations, submitter, submissionTime) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            pstmt.setBoolean(13, entry.isTeam1Ignored());
            pstmt.setBoolean(14, entry.isTeam2Ignored());
            pstmt.setBoolean(15, entry.isTeam1Broken());
            pstmt.setBoolean(16, entry.isTeam2Broken());
            pstmt.setInt(17, entry.getTotalScore());
            pstmt.setString(18, entry.getClickLocations());
            pstmt.setString(19, entry.getSubmitter());
            pstmt.setString(20, entry.getSubmissionTime());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Make sure your DB schema includes team1Ignored, etc. Try restarting to trigger migration.");
        }
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
                boolean t1Ign = false, t2Ign = false, t1Brk = false, t2Brk = false;
                try { t1Ign = rs.getBoolean("team1Ignored"); } catch (SQLException ignore) {}
                try { t2Ign = rs.getBoolean("team2Ignored"); } catch (SQLException ignore) {}
                try { t1Brk = rs.getBoolean("team1Broken"); } catch (SQLException ignore) {}
                try { t2Brk = rs.getBoolean("team2Broken"); } catch (SQLException ignore) {}

                entries.add(new ScoreEntry(
                        type,
                        rs.getInt("matchNumber"), rs.getString("alliance"), rs.getInt("team1"), rs.getInt("team2"),
                        rs.getInt("autoArtifacts"), rs.getInt("teleopArtifacts"), rs.getBoolean("team1CanSequence"),
                        rs.getBoolean("team2CanSequence"), rs.getBoolean("team1L2Climb"), rs.getBoolean("team2L2Climb"),
                        t1Ign, t2Ign, t1Brk, t2Brk,
                        rs.getString("clickLocations"),
                        rs.getString("submitter"),
                        rs.getString("submissionTime")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return entries;
    }

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

    public static List<TeamRanking> calculateTeamRankings(String competitionName) {
        List<ScoreEntry> allScores = getScoresForCompetition(competitionName);
        Map<Integer, FullPenaltyRow> penaltyMap = getFullPenalties(competitionName);
        Competition comp = getCompetition(competitionName);
        String formulaStr = (comp != null && comp.getRatingFormula() != null) ? comp.getRatingFormula() : "total";

        Map<Integer, TeamRanking> rankings = new HashMap<>();
        Map<Integer, Double> totalRatingPoints = new HashMap<>();

        for (ScoreEntry score : allScores) {
            boolean isAllianceMode = (score.getScoreType() == ScoreEntry.Type.ALLIANCE);
            double divisor = isAllianceMode ? 2.0 : 1.0;

            double adjAuto = score.getAutoArtifacts() / divisor;
            double adjTeleop = score.getTeleopArtifacts() / divisor;

            int[] shotStats = parseShotStats(score.getClickLocations());
            int t1Hits = shotStats[0], t1Shots = shotStats[1];
            int t2Hits = shotStats[2], t2Shots = shotStats[3];

            int penCommitted = 0, penReceived = 0;
            FullPenaltyRow pe = penaltyMap.get(score.getMatchNumber());

            if (pe != null) {
                int redGaveAway = (pe.rMaj * PENALTY_MAJOR_PTS) + (pe.rMin * PENALTY_MINOR_PTS);
                int blueGaveAway = (pe.bMaj * PENALTY_MAJOR_PTS) + (pe.bMin * PENALTY_MINOR_PTS);
                if (score.getAlliance().equalsIgnoreCase("RED")) {
                    penCommitted = redGaveAway; penReceived = blueGaveAway;
                } else {
                    penCommitted = blueGaveAway; penReceived = redGaveAway;
                }
                if (isAllianceMode) { penCommitted /= 2; penReceived /= 2; }
            }

            double matchRating = evaluateFormula(formulaStr, score, divisor);

            // Process Team 1
            // IMPORTANT: If team1Ignored is true, we treat it similarly to broken but specific to logic
            // Assuming Ignored means "Don't count for stats but keep record" or "Treat as 0 rating"?
            // Usually "Weak" means we want to adjust how we view them, but for Ranking Calculation:
            // If the user checked "Ignore", we likely want to exclude this specific match performance from their average
            // OR simply flag it. Based on previous context, it's for "Set Weak".
            // If Team is Broken -> We typically skip adding stats.

            // Logic: processTeam adds to the ranking object.
            processTeam(rankings, totalRatingPoints, score.getTeam1(), score, adjAuto, adjTeleop, matchRating, true, t1Hits, t1Shots, score.isTeam1Broken(), penCommitted, penReceived);

            if (isAllianceMode) {
                processTeam(rankings, totalRatingPoints, score.getTeam2(), score, adjAuto, adjTeleop, matchRating, false, t2Hits, t2Shots, score.isTeam2Broken(), penCommitted, penReceived);
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

    public static List<Double> getValidMatchScores(String competitionName, int teamNumber) {
        List<ScoreEntry> all = getScoresForTeam(competitionName, teamNumber);
        List<Double> scores = new ArrayList<>();
        for (ScoreEntry s : all) {
            boolean p1 = (s.getTeam1() == teamNumber && !s.isTeam1Broken());
            boolean p2 = (s.getTeam2() == teamNumber && !s.isTeam2Broken());
            if (p1 || p2) {
                double divisor = (s.getScoreType() == ScoreEntry.Type.ALLIANCE) ? 2.0 : 1.0;
                scores.add(s.getTotalScore() / divisor);
            }
        }
        return scores;
    }
    public static double calculateStdDev(List<Double> values) {
        if (values.isEmpty() || values.size() == 1) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        double mean = sum / values.size();
        double temp = 0;
        for (double v : values) temp += (mean - v) * (mean - v);
        return Math.sqrt(temp / values.size());
    }
    private static int[] parseShotStats(String clickLocations) {
        int[] stats = new int[4];
        if (clickLocations == null || clickLocations.isEmpty()) return stats;
        String[] points = clickLocations.split(";");
        for (String p : points) {
            try {
                if (p.trim().isEmpty()) continue;
                String[] mainParts = p.split(":");
                if (mainParts.length < 2) continue;
                int teamIdx = Integer.parseInt(mainParts[0]);
                String[] coords = mainParts[1].split(",");
                int state = 0;
                if (coords.length >= 3) state = Integer.parseInt(coords[2]);
                if (teamIdx == 1) {
                    stats[1]++; if (state == 0) stats[0]++;
                } else if (teamIdx == 2) {
                    stats[3]++; if (state == 0) stats[2]++;
                }
            } catch (Exception ignored) {}
        }
        return stats;
    }
    private static void processTeam(Map<Integer, TeamRanking> rankings, Map<Integer, Double> totalRatingPoints,
                                    int teamNum, ScoreEntry score, double adjAuto, double adjTeleop, double matchRating,
                                    boolean isTeam1, int hits, int shots, boolean isBroken, int penComm, int penRec) {
        rankings.putIfAbsent(teamNum, new TeamRanking(teamNum));
        if (isBroken) return;

        TeamRanking tr = rankings.get(teamNum);
        boolean seq = isTeam1 ? score.isTeam1CanSequence() : score.isTeam2CanSequence();
        boolean climb = isTeam1 ? score.isTeam1L2Climb() : score.isTeam2L2Climb();
        tr.addMatchResult(adjAuto, adjTeleop, seq, climb, hits, shots, penComm, penRec);

        // Handle "Ignored/Weak" logic here if needed:
        // Currently, even if ignored (weak), we add rating points unless broken.
        // If you want "Ignored" to NOT count towards average rating, wrap this in an if(!isIgnored) check.
        // Assuming current requirement is just to store the flag.

        totalRatingPoints.put(teamNum, totalRatingPoints.getOrDefault(teamNum, 0.0) + matchRating);
    }
    private static double evaluateFormula(String formula, ScoreEntry score, double divisor) {
        try {
            int seqAny = (score.isTeam1CanSequence() || score.isTeam2CanSequence()) ? 1 : 0;
            int climbAny = (score.isTeam1L2Climb() || score.isTeam2L2Climb()) ? 1 : 0;
            Expression e = new ExpressionBuilder(formula).variables("auto", "teleop", "total", "seq", "climb").build()
                    .setVariable("auto", score.getAutoArtifacts() / divisor)
                    .setVariable("teleop", score.getTeleopArtifacts() / divisor)
                    .setVariable("total", score.getTotalScore() / divisor)
                    .setVariable("seq", seqAny).setVariable("climb", climbAny);
            return e.evaluate();
        } catch (Exception e) { return score.getTotalScore() / divisor; }
    }
}