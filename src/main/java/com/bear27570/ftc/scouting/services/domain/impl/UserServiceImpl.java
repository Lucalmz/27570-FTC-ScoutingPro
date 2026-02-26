package com.bear27570.ftc.scouting.services.domain.impl;

import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.services.domain.UserService;

public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // 构造函数注入：需要什么 Repository 就传什么进来，绝不在内部 new
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean login(String username, String password) {
        // 业务层可以在这里加上数据校验，比如防 SQL 注入、判空等
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        return userRepository.authenticateUser(username, password);
    }

    @Override
    public boolean register(String username, String password) {
        // 注册业务逻辑：比如校验密码长度是否大于6位
        if (username == null || username.trim().isEmpty() || password == null || password.length() < 6) {
            return false;
        }
        return userRepository.createUser(username, password);
    }
}