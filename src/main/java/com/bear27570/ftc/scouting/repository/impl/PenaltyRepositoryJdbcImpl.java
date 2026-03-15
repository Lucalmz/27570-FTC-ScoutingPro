package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PenaltyRepositoryJdbcImpl implements PenaltyRepository {

    public PenaltyRepositoryJdbcImpl(String dbUrl) {
        // ★ 初始化时检查并在旧数据库中无缝添加新列
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE penalties ADD COLUMN IF NOT EXISTS redScore INT DEFAULT 0");
            stmt.execute("ALTER TABLE penalties ADD COLUMN IF NOT EXISTS blueScore INT DEFAULT 0");
        } catch (SQLException e) {
            System.err.println("Database Update Info: penalties table columns might already exist. " + e.getMessage());
        }
    }

    @Override
    public void savePenaltyEntry(String competitionName, PenaltyEntry entry) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false); // ★ 开启事务

            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM penalties WHERE competitionName=? AND matchNumber=?")) {
                checkStmt.setString(1, competitionName);
                checkStmt.setInt(2, entry.getMatchNumber());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            String sql;
            if (!exists) {
                sql = "INSERT INTO penalties (competitionName, matchNumber, redMajor, redMinor, blueMajor, blueMinor, redScore, blueScore) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                if (entry.getAlliance().equalsIgnoreCase("RED")) {
                    sql = "UPDATE penalties SET redMajor=?, redMinor=?, redScore=? WHERE competitionName=? AND matchNumber=?";
                } else {
                    sql = "UPDATE penalties SET blueMajor=?, blueMinor=?, blueScore=? WHERE competitionName=? AND matchNumber=?";
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (!exists) {
                    pstmt.setString(1, competitionName);
                    pstmt.setInt(2, entry.getMatchNumber());
                    if (entry.getAlliance().equalsIgnoreCase("RED")) {
                        pstmt.setInt(3, entry.getMajorCount());
                        pstmt.setInt(4, entry.getMinorCount());
                        pstmt.setInt(5, 0);
                        pstmt.setInt(6, 0);
                        pstmt.setInt(7, entry.getOfficialScore()); // redScore
                        pstmt.setInt(8, 0);                        // blueScore
                    } else {
                        pstmt.setInt(3, 0);
                        pstmt.setInt(4, 0);
                        pstmt.setInt(5, entry.getMajorCount());
                        pstmt.setInt(6, entry.getMinorCount());
                        pstmt.setInt(7, 0);                        // redScore
                        pstmt.setInt(8, entry.getOfficialScore()); // blueScore
                    }
                } else {
                    pstmt.setInt(1, entry.getMajorCount());
                    pstmt.setInt(2, entry.getMinorCount());
                    pstmt.setInt(3, entry.getOfficialScore());
                    pstmt.setString(4, competitionName);
                    pstmt.setInt(5, entry.getMatchNumber());
                }
                pstmt.executeUpdate();
            }
            conn.commit(); // ★ 提交事务
        } catch (SQLException e) {
            System.err.println("Database Error saving penalty: " + e.getMessage());
            // ★ 安全网：如果发生错误，立刻回滚事务，防止数据不一致或锁表
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        } finally {
            // 恢复 autoCommit 状态并释放回连接池
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    @Override
    public Map<Integer, FullPenaltyRow> getFullPenalties(String competitionName) {
        Map<Integer, FullPenaltyRow> map = new HashMap<>();
        String sql = "SELECT * FROM penalties WHERE competitionName = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("matchNumber"), new FullPenaltyRow(
                            rs.getInt("redMajor"), rs.getInt("redMinor"),
                            rs.getInt("blueMajor"), rs.getInt("blueMinor"),
                            rs.getInt("redScore"), rs.getInt("blueScore") // ★ 成功读取分数
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error fetching penalties: " + e.getMessage());
        }
        return map;
    }
}