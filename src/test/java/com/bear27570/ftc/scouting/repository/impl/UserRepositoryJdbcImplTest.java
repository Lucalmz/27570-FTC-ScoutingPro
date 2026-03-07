package com.bear27570.ftc.scouting.repository.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryJdbcImplTest {

    // 使用 h2 的 mem 模式，只有在测试运行时存在于内存中
    private static final String TEST_DB_URL = "jdbc:h2:mem:test_users_db;DB_CLOSE_DELAY=-1";
    private UserRepositoryJdbcImpl userRepository;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = new UserRepositoryJdbcImpl(TEST_DB_URL);

        // 每次测试前，在内存中建表
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (username VARCHAR(255) PRIMARY KEY, password VARCHAR(255))");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // 每次测试后，销毁内存表，保证测试之间互不干扰
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE users");
        }
    }

    @Test
    void testCreateUser_Success() {
        boolean result = userRepository.createUser("TestUser", "Password123");
        assertTrue(result, "新建用户应该返回 true");
    }

    @Test
    void testCreateUser_DuplicateUsername() {
        userRepository.createUser("TestUser", "Password123");
        boolean result = userRepository.createUser("TestUser", "AnotherPassword");
        assertFalse(result, "用户名重复时应该返回 false");
    }

    @Test
    void testAuthenticateUser_Success() {
        userRepository.createUser("Alice", "SecretPass");
        boolean authResult = userRepository.authenticateUser("Alice", "SecretPass");
        assertTrue(authResult, "密码正确应该认证通过");
    }

    @Test
    void testAuthenticateUser_WrongPassword() {
        userRepository.createUser("Bob", "RealPass");
        boolean authResult = userRepository.authenticateUser("Bob", "FakePass");
        assertFalse(authResult, "密码错误应该认证失败");
    }
}