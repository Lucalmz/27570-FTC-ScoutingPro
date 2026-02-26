package com.bear27570.ftc.scouting.services.domain;

/**
 * 用户相关的纯业务逻辑接口
 */
public interface UserService {
    boolean login(String username, String password);
    boolean register(String username, String password);
}