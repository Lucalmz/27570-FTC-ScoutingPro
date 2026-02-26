package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
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
        boolean exists = false;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM penalties WHERE competitionName=? AND matchNumber=?")) {
            checkStmt.setString(1, competitionName);
            checkStmt.setInt(2, entry.getMatchNumber());
            exists = checkStmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        @Language("SQL")
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

        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
                pstmt.executeUpdate();
            } else {
                pstmt.setInt(1, entry.getMajorCount());
                pstmt.setInt(2, entry.getMinorCount());
                pstmt.setString(3, competitionName);
                pstmt.setInt(4, entry.getMatchNumber());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<Integer, FullPenaltyRow> getFullPenalties(String competitionName) {
        Map<Integer, FullPenaltyRow> map = new HashMap<>();
        @Language("SQL")
        String sql = "SELECT * FROM penalties WHERE competitionName = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                map.put(rs.getInt("matchNumber"), new FullPenaltyRow(
                        rs.getInt("redMajor"), rs.getInt("redMinor"),
                        rs.getInt("blueMajor"), rs.getInt("blueMinor")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }
}