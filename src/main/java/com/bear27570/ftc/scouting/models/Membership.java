package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.util.Objects;

// ★ 1. 必须实现 Serializable，否则无法通过网络发送
public class Membership implements Serializable {

    // ★ 2. 添加版本号，防止不同编译版本导致的序列化错误
    private static final long serialVersionUID = 1L;

    public enum Status { PENDING, APPROVED, CREATOR }

    // ★ 3. 建议去掉 final 并添加无参构造函数 (为了兼容某些序列化框架)
    private String username;
    private String competitionName;
    private Status status;

    // 无参构造 (很多库需要这个)
    public Membership() {}

    // 全参构造
    public Membership(String username, String competitionName, Status status) {
        this.username = username;
        this.competitionName = competitionName;
        this.status = status;
    }

    // Getters
    public String getUsername() { return username; }
    public String getCompetitionName() { return competitionName; }
    public Status getStatus() { return status; }

    // Setters (如果需要修改状态)
    public void setUsername(String username) { this.username = username; }
    public void setCompetitionName(String competitionName) { this.competitionName = competitionName; }
    public void setStatus(Status status) { this.status = status; }

    // ★ 4. 重写 toString，方便你在控制台看日志
    @Override
    public String toString() {
        return "Membership{" +
                "user='" + username + '\'' +
                ", comp='" + competitionName + '\'' +
                ", status=" + status +
                '}';
    }

    // ★ 5. 重写 equals 和 hashCode，方便 List.contains() 判断是否存在
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Membership that = (Membership) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(competitionName, that.competitionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, competitionName);
    }
}