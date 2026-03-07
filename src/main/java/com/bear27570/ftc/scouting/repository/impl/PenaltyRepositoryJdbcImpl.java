package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import org.intellij.lang.annotations.Language;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PenaltyRepositoryJdbcImpl implements PenaltyRepository {

    private final String dbUrl;

    public PenaltyRepositoryJdbcImpl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public void savePenaltyEntry(String competitionName, PenaltyEntry entry) {
        // 修复：利用 H2 的事务机制和原子判断，防止网络高并发提交下发生的数据竞争和相互覆盖
        try (Connection conn = DatabaseManager.getConnection()) {
            // 设置手动提交以保证事务安全
            conn.setAutoCommit(false);

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
                sql = "INSERT INTO penalties (competitionName, matchNumber, redMajor, redMinor, blueMajor, blueMinor) VALUES (?, ?, ?, ?, ?, ?)";
            } else {
                if (entry.getAlliance().equalsIgnoreCase("RED")) {
                    sql = "UPDATE penalties SET redMajor=?, redMinor=? WHERE competitionName=? AND matchNumber=?";
                } else {
                    sql = "UPDATE penalties SET blueMajor=?, blueMinor=? WHERE competitionName=? AND matchNumber=?";
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
                    } else {
                        pstmt.setInt(3, 0);
                        pstmt.setInt(4, 0);
                        pstmt.setInt(5, entry.getMajorCount());
                        pstmt.setInt(6, entry.getMinorCount());
                    }
                } else {
                    pstmt.setInt(1, entry.getMajorCount());
                    pstmt.setInt(2, entry.getMinorCount());
                    pstmt.setString(3, competitionName);
                    pstmt.setInt(4, entry.getMatchNumber());
                }
                pstmt.executeUpdate();
            }
            // 提交事务
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Database Error saving penalty: " + e.getMessage());
            e.printStackTrace();
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
                            rs.getInt("blueMajor"), rs.getInt("blueMinor")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error fetching penalties: " + e.getMessage());
        }
        return map;
    }
}