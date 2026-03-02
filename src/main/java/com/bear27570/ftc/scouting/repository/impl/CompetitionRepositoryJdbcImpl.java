package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CompetitionRepositoryJdbcImpl implements CompetitionRepository {

    private final String dbUrl;

    public CompetitionRepositoryJdbcImpl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public List<Competition> findAll() {
        List<Competition> competitions = new ArrayList<>();
        String sql = "SELECT * FROM competitions";
        try (Connection conn = DriverManager.getConnection(dbUrl); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Competition c = new Competition(rs.getString("name"), rs.getString("creatorUsername"), rs.getString("ratingFormula"));
                // 新增：从数据库读取官方赛事信息
                c.setEventSeason(rs.getInt("eventSeason"));
                c.setEventCode(rs.getString("eventCode"));
                c.setOfficialEventName(rs.getString("officialEventName"));
                competitions.add(c);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return competitions;
    }

    @Override
    public Competition findByName(String name) {
        return findAll().stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public void updateFormula(String competitionName, String newFormula) {
        String sql = "UPDATE competitions SET ratingFormula = ? WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFormula);
            pstmt.setString(2, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean create(String name, String creatorUsername, String ratingFormula) {
        String sql = "INSERT INTO competitions(name, creatorUsername, ratingFormula) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, creatorUsername);
            pstmt.setString(3, ratingFormula);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // 新增：将绑定的赛事信息写入数据库
    @Override
    public void updateEventInfo(String competitionName, int season, String eventCode, String officialName) {
        String sql = "UPDATE competitions SET eventSeason = ?, eventCode = ?, officialEventName = ? WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, season);
            pstmt.setString(2, eventCode);
            pstmt.setString(3, officialName);
            pstmt.setString(4, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}