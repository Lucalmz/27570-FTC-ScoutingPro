package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.repository.dao.UserDao;
import com.bear27570.ftc.scouting.utils.CryptoUtil;

public class UserRepositoryJdbiImpl implements UserRepository {
    private UserDao dao() { return DatabaseManager.getJdbi().onDemand(UserDao.class); }

    @Override
    public boolean createUser(String username, String password) {
        try {
            // 存入数据库前加密密码
            dao().insert(username, CryptoUtil.encrypt(password));
            return true;
        } catch (Exception e) {
            return false; // 主键冲突拦截
        }
    }

    @Override
    public boolean authenticateUser(String username, String password) {
        String storedEncryptedPass = dao().getPassword(username);
        if (storedEncryptedPass == null) return false;
        // 将库里的密文解密后与用户输入对比
        return CryptoUtil.decrypt(storedEncryptedPass).equals(password);
    }

    @Override
    public void ensureUserExists(String username) {
        if (dao().checkExists(username) == 0) {
            dao().insert(username, CryptoUtil.encrypt("guest_account"));
        }
    }

    @Override
    public void updateApiKey(String username, String apiKey) {
        // API Key 属于敏感信息，同样加密存储
        dao().updateApiKey(username, CryptoUtil.encrypt(apiKey));
    }

    @Override
    public String getApiKey(String username) {
        String encryptedKey = dao().getApiKey(username);
        return CryptoUtil.decrypt(encryptedKey);
    }
}