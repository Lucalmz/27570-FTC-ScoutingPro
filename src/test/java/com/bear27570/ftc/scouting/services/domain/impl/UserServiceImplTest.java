package com.bear27570.ftc.scouting.services.domain.impl;

import com.bear27570.ftc.scouting.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class UserServiceImplTest {

    private UserRepository mockUserRepository;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // 创建假的 Repository
        mockUserRepository = Mockito.mock(UserRepository.class);
        // 注入到 Service 中
        userService = new UserServiceImpl(mockUserRepository);
    }

    @Test
    void testRegister_PasswordTooShort_ShouldFailWithoutCallingDB() {
        // Act: 尝试注册一个密码只有 3 位的账号
        boolean result = userService.register("TestUser", "123");

        // Assert: 业务层应该直接拦截并返回 false
        assertFalse(result, "密码太短应该注册失败");

        // 关键断言：验证 UserRepository.createUser 方法 "从来没有(never)" 被调用过！
        // 证明我们的业务拦截生效了，没有产生无意义的数据库查询压力。
        verify(mockUserRepository, never()).createUser(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void testLogin_Success() {
        // Arrange: 假设数据库里有这个人，密码是 admin123
        when(mockUserRepository.authenticateUser("admin", "admin123")).thenReturn(true);

        // Act: 执行登录
        boolean result = userService.login("admin", "admin123");

        // Assert: 登录应该成功
        assertTrue(result);
    }

    @Test
    void testLogin_EmptyUsername_ShouldFail() {
        // Act: 用户名为空格
        boolean result = userService.login("   ", "password");

        // Assert: 拦截失败
        assertFalse(result);
        verify(mockUserRepository, never()).authenticateUser(Mockito.anyString(), Mockito.anyString());
    }
}