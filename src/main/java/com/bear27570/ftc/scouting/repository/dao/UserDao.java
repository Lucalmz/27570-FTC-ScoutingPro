package com.bear27570.ftc.scouting.repository.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface UserDao {
    @SqlUpdate("INSERT INTO users(username, password) VALUES(:username, :password)")
    void insert(@Bind("username") String username, @Bind("password") String password);

    @SqlQuery("SELECT password FROM users WHERE username = :username")
    String getPassword(@Bind("username") String username);

    @SqlQuery("SELECT COUNT(*) FROM users WHERE username = :username")
    int checkExists(@Bind("username") String username);

    @SqlUpdate("UPDATE users SET geminiApiKey = :apiKey WHERE username = :username")
    void updateApiKey(@Bind("username") String username, @Bind("apiKey") String apiKey);

    @SqlQuery("SELECT geminiApiKey FROM users WHERE username = :username")
    String getApiKey(@Bind("username") String username);
}