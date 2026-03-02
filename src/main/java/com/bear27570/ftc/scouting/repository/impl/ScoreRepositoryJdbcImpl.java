// File: ScoreRepositoryJdbcImpl.java
package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.ScoreRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ScoreRepositoryJdbcImpl implements ScoreRepository {

    private final String dbUrl;

    public ScoreRepositoryJdbcImpl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public void save(String competitionName, ScoreEntry entry) {
        String sql = "INSERT INTO scores(competitionName, scoreType, matchNumber, alliance, team1, team2, " +
                "autoArtifacts, teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb, " +
                "team1Ignored, team2Ignored, team1Broken, team2Broken, " +
                "totalScore, clickLocations, submitter, submissionTime, syncStatus) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            pstmt.setString(21, entry.getSyncStatus().name());
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
        String sql = "UPDATE scores SET matchNumber=?, alliance=?, team1=?, team2=?, autoArtifacts=?, teleopArtifacts=?, " +
                "team1CanSequence=?, team2CanSequence=?, team1L2Climb=?, team2L2Climb=?, " +
                "team1Ignored=?, team2Ignored=?, team1Broken=?, team2Broken=?, totalScore=?, clickLocations=?, syncStatus=? " +
                "WHERE id=?";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, entry.getMatchNumber());
            pstmt.setString(2, entry.getAlliance());
            pstmt.setInt(3, entry.getTeam1());
            pstmt.setInt(4, entry.getTeam2());
            pstmt.setInt(5, entry.getAutoArtifacts());
            pstmt.setInt(6, entry.getTeleopArtifacts());
            pstmt.setBoolean(7, entry.isTeam1CanSequence());
            pstmt.setBoolean(8, entry.isTeam2CanSequence());
            pstmt.setBoolean(9, entry.isTeam1L2Climb());
            pstmt.setBoolean(10, entry.isTeam2L2Climb());
            pstmt.setBoolean(11, entry.isTeam1Ignored());
            pstmt.setBoolean(12, entry.isTeam2Ignored());
            pstmt.setBoolean(13, entry.isTeam1Broken());
            pstmt.setBoolean(14, entry.isTeam2Broken());
            pstmt.setInt(15, entry.getTotalScore());
            pstmt.setString(16, entry.getClickLocations());
            pstmt.setString(17, entry.getSyncStatus().name());
            pstmt.setInt(18, entry.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating score (ID: " + entry.getId() + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void syncWithHostData(String competitionName, List<ScoreEntry> hostData) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false);

            // 1. 获取本地目前所有尚未同步的记录
            List<ScoreEntry> localPending = findPendingExports(competitionName);

            // 2. 清空本地对应赛事的记录
            try(PreparedStatement del = conn.prepareStatement("DELETE FROM scores WHERE competitionName = ?")) {
                del.setString(1, competitionName);
                del.executeUpdate();
            }

            // 3. 把主机数据全部插回去 (此时已经是完全同步的状态)
            for (ScoreEntry hs : hostData) {
                hs.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                save(competitionName, hs);
            }

            // 4. 将刚才保留的未同步数据比对，如果主机里没有，则插回本地
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
                rs.getInt("autoArtifacts"),
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