package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.MembershipRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MembershipRepositoryJdbcImpl implements MembershipRepository {

    private final String dbUrl;

    public MembershipRepositoryJdbcImpl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public void addMembership(String username, String competitionName, Membership.Status status) {
        String checkSql = "SELECT status FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username);
            checkStmt.setString(2, competitionName);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                if (rs.getString("status").equals("PENDING") && status == Membership.Status.APPROVED) {
                    updateMembershipStatus(username, competitionName, status);
                }
                return;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String sql = "INSERT INTO memberships(username, competitionName, status) VALUES(?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, competitionName);
            pstmt.setString(3, status.name());
            pstmt.executeUpdate();
            System.out.println("DEBUG: 成功插入成员关系 -> User: " + username + ", Comp: " + competitionName);
        } catch (SQLException ignored) {
            System.err.println("CRITICAL DB ERROR: 无法添加成员 " + username);
        }
    }

    @Override
    public Membership.Status getMembershipStatus(String username, String competitionName) {
        String sql = "SELECT status FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, competitionName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Membership.Status.valueOf(rs.getString("status"));
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    @Override
    public void removeMembership(String username, String competitionName) {
        String sql = "DELETE FROM memberships WHERE username = ? AND competitionName = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public List<String> getMembersByStatus(String competitionName, Membership.Status status) {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM memberships WHERE competitionName = ? AND status = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, competitionName);
            pstmt.setString(2, status.name());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException ignored) {
        }
        return users;
    }

    @Override
    public void updateMembershipStatus(String username, String competitionName, Membership.Status newStatus) {
        String sql = "UPDATE memberships SET status = ? WHERE username = ? AND competitionName = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name());
            pstmt.setString(2, username);
            pstmt.setString(3, competitionName);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}