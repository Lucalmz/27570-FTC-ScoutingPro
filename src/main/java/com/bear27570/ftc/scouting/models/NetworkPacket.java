package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.util.List;

public class NetworkPacket implements Serializable {
    private static final long serialVersionUID = 2L; // 更新版本号

    public enum PacketType {
        JOIN_REQUEST,   // Client -> Host: "我要加入，我是用户X"
        JOIN_RESPONSE,  // Host -> Client: "你被批准了" 或 "你被拒绝了"
        SUBMIT_SCORE,   // Client -> Host: 提交分数
        UPDATE_DATA     // Host -> Client: 同步全局数据
    }

    private final PacketType type;
    private String username;        // 用于申请加入
    private boolean approved;       // 用于审批响应
    private ScoreEntry scoreEntry;
    private List<ScoreEntry> scoreHistory;
    private List<TeamRanking> teamRankings;

    // 申请加入构造
    public NetworkPacket(PacketType type, String username) {
        this.type = type;
        this.username = username;
    }

    // 审批响应构造
    public NetworkPacket(PacketType type, boolean approved) {
        this.type = type;
        this.approved = approved;
    }

    // 提交分数构造
    public NetworkPacket(ScoreEntry scoreEntry) {
        this.type = PacketType.SUBMIT_SCORE;
        this.scoreEntry = scoreEntry;
    }

    // 广播更新构造
    public NetworkPacket(List<ScoreEntry> scoreHistory, List<TeamRanking> teamRankings) {
        this.type = PacketType.UPDATE_DATA;
        this.scoreHistory = scoreHistory;
        this.teamRankings = teamRankings;
    }

    public PacketType getType() { return type; }
    public String getUsername() { return username; }
    public boolean isApproved() { return approved; }
    public ScoreEntry getScoreEntry() { return scoreEntry; }
    public List<ScoreEntry> getScoreHistory() { return scoreHistory; }
    public List<TeamRanking> getTeamRankings() { return teamRankings; }
}