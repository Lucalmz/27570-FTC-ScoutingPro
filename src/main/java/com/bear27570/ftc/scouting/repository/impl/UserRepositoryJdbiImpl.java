package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.repository.dao.UserDao;

public class UserRepositoryJdbiImpl implements UserRepository {
    private UserDao dao() { return DatabaseManager.getJdbi().onDemand(UserDao.class); }

    @Override
    public boolean createUser(String username, String password) {
        try {
            dao().insert(username, password);
            return true;
        } catch (Exception e) {
            return false; // 主键冲突拦截
        }
    }

    @Override
    public boolean authenticateUser(String username, String password) {
        String storedPass = dao().getPassword(username);
        return storedPass != null && storedPass.equals(password);
    }

    @Override
    public void ensureUserExists(String username) {
        if (dao().checkExists(username) == 0) {
            dao().insert(username, "guest_account");
        }
    }

    @Override
    public void updateApiKey(String username, String apiKey) { dao().updateApiKey(username, apiKey); }

    @Override
    public String getApiKey(String username) { return dao().getApiKey(username); }
}