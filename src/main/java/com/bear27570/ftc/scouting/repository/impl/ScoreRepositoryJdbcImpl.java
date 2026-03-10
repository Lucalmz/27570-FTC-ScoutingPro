// File: ScoreRepositoryJdbcImpl.java
package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.ScoreRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ScoreRepositoryJdbcImpl implements ScoreRepository {

    private final String dbUrl;

    public ScoreRepositoryJdbcImpl(String dbUrl) {
        this.dbUrl = dbUrl;

        // ★ 核心逻辑：应用启动时自动检测并为旧数据库无缝添加新增的 6 个自动阶段数据列
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1AutoScore INT DEFAULT 0");
            stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2AutoScore INT DEFAULT 0");
            stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1AutoProj VARCHAR(20) DEFAULT 'NONE'");
            stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2AutoProj VARCHAR(20) DEFAULT 'NONE'");
            stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team1AutoRow VARCHAR(20) DEFAULT 'NONE'");
            stmt.execute("ALTER TABLE scores ADD COLUMN IF NOT EXISTS team2AutoRow VARCHAR(20) DEFAULT 'NONE'");
        } catch (SQLException e) {
            System.err.println("Database Update Warning: Could not alter scores table. " + e.getMessage());
        }
    }

    @Override
    public void save(String competitionName, ScoreEntry entry) {
        // ★ 增加了 6 个新字段，共计 27 个参数
        String sql = "INSERT INTO scores(competitionName, scoreType, matchNumber, alliance, team1, team2, " +
                "team1AutoScore, team2AutoScore, team1AutoProj, team2AutoProj, team1AutoRow, team2AutoRow, " +
                "autoArtifacts, teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb, " +
                "team1Ignored, team2Ignored, team1Broken, team2Broken, " +
                "totalScore, clickLocations, submitter, submissionTime, syncStatus) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, competitionName);
            pstmt.setString(2, entry.getScoreType().name());
            pstmt.setInt(3, entry.getMatchNumber());
            pstmt.setString(4, entry.getAlliance());
            pstmt.setInt(5, entry.getTeam1());
            pstmt.setInt(6, entry.getTeam2());

            // --- 绑定新增字段 ---
            pstmt.setInt(7, entry.getTeam1AutoScore());
            pstmt.setInt(8, entry.getTeam2AutoScore());
            pstmt.setString(9, entry.getTeam1AutoProj());
            pstmt.setString(10, entry.getTeam2AutoProj());
            pstmt.setString(11, entry.getTeam1AutoRow());
            pstmt.setString(12, entry.getTeam2AutoRow());

            pstmt.setInt(13, entry.getAutoArtifacts()); // 依然保留聚合值以便向下兼容
            pstmt.setInt(14, entry.getTeleopArtifacts());
            pstmt.setBoolean(15, entry.isTeam1CanSequence());
            pstmt.setBoolean(16, entry.isTeam2CanSequence());
            pstmt.setBoolean(17, entry.isTeam1L2Climb());
            pstmt.setBoolean(18, entry.isTeam2L2Climb());
            pstmt.setBoolean(19, entry.isTeam1Ignored());
            pstmt.setBoolean(20, entry.isTeam2Ignored());
            pstmt.setBoolean(21, entry.isTeam1Broken());
            pstmt.setBoolean(22, entry.isTeam2Broken());
            pstmt.setInt(23, entry.getTotalScore());
            pstmt.setString(24, entry.getClickLocations());
            pstmt.setString(25, entry.getSubmitter());
            pstmt.setString(26, entry.getSubmissionTime());
            pstmt.setString(27, entry.getSyncStatus().name());

            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    entry.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving score: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void update(ScoreEntry entry) {
        String sql = "UPDATE scores SET matchNumber=?, alliance=?, team1=?, team2=?, " +
                "team1AutoScore=?, team2AutoScore=?, team1AutoProj=?, team2AutoProj=?, team1AutoRow=?, team2AutoRow=?, " +
                "autoArtifacts=?, teleopArtifacts=?, " +
                "team1CanSequence=?, team2CanSequence=?, team1L2Climb=?, team2L2Climb=?, " +
                "team1Ignored=?, team2Ignored=?, team1Broken=?, team2Broken=?, totalScore=?, clickLocations=?, syncStatus=? " +
                "WHERE id=?";

        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, entry.getMatchNumber());
            pstmt.setString(2, entry.getAlliance());
            pstmt.setInt(3, entry.getTeam1());
            pstmt.setInt(4, entry.getTeam2());

            // --- 绑定新增字段 ---
            pstmt.setInt(5, entry.getTeam1AutoScore());
            pstmt.setInt(6, entry.getTeam2AutoScore());
            pstmt.setString(7, entry.getTeam1AutoProj());
            pstmt.setString(8, entry.getTeam2AutoProj());
            pstmt.setString(9, entry.getTeam1AutoRow());
            pstmt.setString(10, entry.getTeam2AutoRow());

            pstmt.setInt(11, entry.getAutoArtifacts());
            pstmt.setInt(12, entry.getTeleopArtifacts());
            pstmt.setBoolean(13, entry.isTeam1CanSequence());
            pstmt.setBoolean(14, entry.isTeam2CanSequence());
            pstmt.setBoolean(15, entry.isTeam1L2Climb());
            pstmt.setBoolean(16, entry.isTeam2L2Climb());
            pstmt.setBoolean(17, entry.isTeam1Ignored());
            pstmt.setBoolean(18, entry.isTeam2Ignored());
            pstmt.setBoolean(19, entry.isTeam1Broken());
            pstmt.setBoolean(20, entry.isTeam2Broken());
            pstmt.setInt(21, entry.getTotalScore());
            pstmt.setString(22, entry.getClickLocations());
            pstmt.setString(23, entry.getSyncStatus().name());
            pstmt.setInt(24, entry.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating score (ID: " + entry.getId() + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void syncWithHostData(String competitionName, List<ScoreEntry> hostData) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            List<ScoreEntry> localPending = findPendingExports(competitionName);

            try(PreparedStatement del = conn.prepareStatement("DELETE FROM scores WHERE competitionName = ?")) {
                del.setString(1, competitionName);
                del.executeUpdate();
            }

            for (ScoreEntry hs : hostData) {
                hs.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                save(competitionName, hs);
            }

            for (ScoreEntry pending : localPending) {
                boolean alreadyInHost = hostData.stream().anyMatch(h ->
                        h.getSubmitter().equals(pending.getSubmitter()) &&
                                h.getSubmissionTime().equals(pending.getSubmissionTime())
                );
                if (!alreadyInHost) {
                    save(competitionName, pending);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<ScoreEntry> findPendingExports(String competitionName) {
        List<ScoreEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM scores WHERE competitionName = ? AND syncStatus IN ('UNSYNCED', 'EXPORTED')";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToScoreEntry(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void updateStatuses(List<Integer> ids, ScoreEntry.SyncStatus status) {
        if (ids == null || ids.isEmpty()) return;
        List<String> placeholdersList = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            placeholdersList.add("?");
        }
        String placeholders = String.join(",", placeholdersList);
        String sql = "UPDATE scores SET syncStatus = ? WHERE id IN (" + placeholders + ")";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            int idx = 2;
            for (int id : ids) {
                pstmt.setInt(idx++, id);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM scores WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting score: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<ScoreEntry> findByCompetition(String competitionName) {
        List<ScoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM scores WHERE competitionName = ? ORDER BY matchNumber DESC, id DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSetToScoreEntry(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching scores by competition: " + e.getMessage());
        }
        return entries;
    }

    @Override
    public List<ScoreEntry> findByTeam(String competitionName, int teamNumber) {
        List<ScoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM scores WHERE competitionName = ? AND (team1 = ? OR (scoreType = 'ALLIANCE' AND team2 = ?)) ORDER BY matchNumber DESC, id DESC";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            pstmt.setInt(2, teamNumber);
            pstmt.setInt(3, teamNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSetToScoreEntry(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching scores by team: " + e.getMessage());
        }
        return entries;
    }

    private ScoreEntry mapResultSetToScoreEntry(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("scoreType");
        ScoreEntry.Type type = ScoreEntry.Type.ALLIANCE;
        if (typeStr != null && !typeStr.trim().isEmpty()) {
            try {
                type = ScoreEntry.Type.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Unknown score type found in DB -> " + typeStr);
            }
        }

        ScoreEntry.SyncStatus sync = ScoreEntry.SyncStatus.UNSYNCED;
        String syncStr = rs.getString("syncStatus");
        if (syncStr != null && !syncStr.trim().isEmpty()) {
            try {
                sync = ScoreEntry.SyncStatus.valueOf(syncStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        // --- 安全地提取新增列（防止在首次启动刚建表时由于某些原因抛出异常） ---
        int t1AutoScore = 0, t2AutoScore = 0;
        String t1Proj = "NONE", t2Proj = "NONE", t1Row = "NONE", t2Row = "NONE";

        try { t1AutoScore = rs.getInt("team1AutoScore"); } catch (SQLException ignore) {}
        try { t2AutoScore = rs.getInt("team2AutoScore"); } catch (SQLException ignore) {}
        try { t1Proj = rs.getString("team1AutoProj"); if(t1Proj == null) t1Proj = "NONE"; } catch (SQLException ignore) {}
        try { t2Proj = rs.getString("team2AutoProj"); if(t2Proj == null) t2Proj = "NONE"; } catch (SQLException ignore) {}
        try { t1Row = rs.getString("team1AutoRow"); if(t1Row == null) t1Row = "NONE"; } catch (SQLException ignore) {}
        try { t2Row = rs.getString("team2AutoRow"); if(t2Row == null) t2Row = "NONE"; } catch (SQLException ignore) {}

        boolean t1Ign = false, t2Ign = false, t1Brk = false, t2Brk = false;
        try { t1Ign = rs.getBoolean("team1Ignored"); } catch (SQLException ignore) {}
        try { t2Ign = rs.getBoolean("team2Ignored"); } catch (SQLException ignore) {}
        try { t1Brk = rs.getBoolean("team1Broken"); } catch (SQLException ignore) {}
        try { t2Brk = rs.getBoolean("team2Broken"); } catch (SQLException ignore) {}

        return new ScoreEntry(
                rs.getInt("id"),
                type,
                rs.getInt("matchNumber"),
                rs.getString("alliance"),
                rs.getInt("team1"),
                rs.getInt("team2"),
                t1AutoScore,
                t2AutoScore,
                t1Proj,
                t2Proj,
                t1Row,
                t2Row,
                rs.getInt("teleopArtifacts"),
                rs.getBoolean("team1CanSequence"),
                rs.getBoolean("team2CanSequence"),
                rs.getBoolean("team1L2Climb"),
                rs.getBoolean("team2L2Climb"),
                t1Ign,
                t2Ign,
                t1Brk,
                t2Brk,
                rs.getString("clickLocations"),
                rs.getString("submitter"),
                rs.getString("submissionTime"),
                sync
        );
    }
}