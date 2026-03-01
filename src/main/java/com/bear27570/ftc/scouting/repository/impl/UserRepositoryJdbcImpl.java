package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.repository.UserRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRepositoryJdbcImpl implements UserRepository {

    private final String dbUrl;

    public UserRepositoryJdbcImpl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    @Override
    public boolean createUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            System.out.println("DEBUG: User created successfully -> " + username);
            return true;
        } catch (SQLException e) {
            System.err.println("DEBUG: Create User Failed for " + username + ". Reason: " + e.getMessage());
            // 通常是主键重复 (IntegrityConstraintViolationException)
            return false;
        }
    }

    @Override
    public boolean authenticateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPass = rs.getString("password");
                    boolean match = storedPass.equals(password);
                    if (!match) System.out.println("DEBUG: Password mismatch for user " + username);
                    return match;
                }
                System.out.println("DEBUG: User not found -> " + username);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("CRITICAL DB ERROR during login: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void ensureUserExists(String username) {
        String checkSql = "SELECT 1 FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users(username, password) VALUES(?, 'guest_account')";
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, username);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) exists = true;
                }
            }

            if (!exists) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, username);
                    insertStmt.executeUpdate();
                    System.out.println("DEBUG: Ensured guest user exists: " + username);
                }
            }
        } catch (SQLException e) {
            System.err.println("DEBUG: Ensure user failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}