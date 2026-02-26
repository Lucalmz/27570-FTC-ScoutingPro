package com.bear27570.ftc.scouting.repository;

public interface UserRepository {
    boolean createUser(String username, String password);
    boolean authenticateUser(String username, String password);
    void ensureUserExists(String username);
}