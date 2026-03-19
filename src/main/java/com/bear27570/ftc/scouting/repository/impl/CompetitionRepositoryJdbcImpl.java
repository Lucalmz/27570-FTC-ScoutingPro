package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.repository.DatabaseManager;

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
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newFormula);
            pstmt.setString(2, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void ensureLocalCompetitionSync(Competition competition) {
        String checkSql = "SELECT COUNT(*) FROM competitions WHERE name = ?";
        boolean exists = false;
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, competition.getName());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                exists = true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (!exists) {
            // 1. 本地没有同名比赛，安全插入
            String insertSql = "INSERT INTO competitions(name, creatorUsername, ratingFormula, eventSeason, eventCode, officialEventName) VALUES(?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, competition.getName());
                pstmt.setString(2, competition.getCreatorUsername() != null ? competition.getCreatorUsername() : "HOST_SYNC");
                pstmt.setString(3, competition.getRatingFormula() != null ? competition.getRatingFormula() : "total");
                pstmt.setInt(4, competition.getEventSeason());
                pstmt.setString(5, competition.getEventCode());
                pstmt.setString(6, competition.getOfficialEventName());
                pstmt.executeUpdate();
                System.out.println("DEBUG: [从机] 本地无同名赛事，已自动创建: " + competition.getName());
            } catch (SQLException e) {
                System.err.println("DEBUG: [从机] 同步赛事信息时发生并发冲突(安全忽略)");
            }
        } else {
            // 2. 本地已经有同名比赛，不要报错，直接更新官方 API 信息以保持与主机一致
            updateEventInfo(competition.getName(), competition.getEventSeason(), competition.getEventCode(), competition.getOfficialEventName());
            System.out.println("DEBUG: [从机] 本地已有同名赛事，已更新赛事API元数据: " + competition.getName());
        }
    }
    @Override
    public boolean create(String name, String creatorUsername, String ratingFormula) {
        String sql = "INSERT INTO competitions(name, creatorUsername, ratingFormula) VALUES(?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, creatorUsername);
            pstmt.setString(3, ratingFormula);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void updateEventInfo(String competitionName, int season, String eventCode, String officialName) {
        String sql = "UPDATE competitions SET eventSeason = ?, eventCode = ?, officialEventName = ? WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, season);
            pstmt.setString(2, eventCode);
            pstmt.setString(3, officialName);
            pstmt.setString(4, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean deleteByName(String name) {
        // SQL 语句：清理所有与该比赛相关的数据表
        String delScores = "DELETE FROM scores WHERE competitionName = ?";
        String delPenalties = "DELETE FROM penalties WHERE competitionName = ?";
        String delMemberships = "DELETE FROM memberships WHERE competitionName = ?";
        String delComp = "DELETE FROM competitions WHERE name = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // 开启事务

            try (PreparedStatement ps1 = conn.prepareStatement(delScores);
                 PreparedStatement ps2 = conn.prepareStatement(delPenalties);
                 PreparedStatement ps3 = conn.prepareStatement(delMemberships);
                 PreparedStatement ps4 = conn.prepareStatement(delComp)) {

                // 1. 删成绩
                ps1.setString(1, name);
                ps1.executeUpdate();

                // 2. 删判罚
                ps2.setString(1, name);
                ps2.executeUpdate();

                // 3. 删成员
                ps3.setString(1, name);
                ps3.executeUpdate();

                // 4. 最后删比赛本体
                ps4.setString(1, name);
                int rows = ps4.executeUpdate();

                conn.commit(); // 提交事务
                return rows > 0;

            } catch (SQLException e) {
                conn.rollback(); // 发生任何错误立刻回滚，保护数据安全
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true); // 恢复默认自动提交状态
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}