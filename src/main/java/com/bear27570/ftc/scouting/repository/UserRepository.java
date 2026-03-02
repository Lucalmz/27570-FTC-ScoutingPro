package com.bear27570.ftc.scouting.repository;

public interface UserRepository {
    boolean createUser(String username, String password);
    boolean authenticateUser(String username, String password);
    void ensureUserExists(String username);
    void updateApiKey(String username, String apiKey); // 新增
    String getApiKey(String username);
}