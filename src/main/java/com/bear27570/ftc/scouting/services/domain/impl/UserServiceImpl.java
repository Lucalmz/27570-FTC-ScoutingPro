package com.bear27570.ftc.scouting.services.domain.impl;

import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.services.domain.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        return userRepository.authenticateUser(username, password);
    }

    @Override
    public boolean register(String username, String password) {
        // 核心修改：移除了密码长度限制，只校验非空
        if (username == null || username.trim().isEmpty()) {
            log.error("DEBUG: Register failed - Username is empty");
            return false;
        }
        if (password == null || password.isEmpty()) {
            log.error("DEBUG: Register failed - Password is empty");
            return false;
        }

        // 尝试写入数据库，如果返回 true 代表写入成功，false 代表用户名重复或SQL错误
        return userRepository.createUser(username, password);
    }
    @Override
    public void updateApiKey(String username, String apiKey) {
        userRepository.updateApiKey(username, apiKey);
    }

    @Override
    public String getApiKey(String username) {
        return userRepository.getApiKey(username);
    }
}