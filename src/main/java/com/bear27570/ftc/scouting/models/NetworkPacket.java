package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.util.List;

public class NetworkPacket implements Serializable {
    private static final long serialVersionUID = 3L; // 更新版本号

    public enum PacketType {
        JOIN_REQUEST,   // Client -> Host
        JOIN_RESPONSE,  // Host -> Client
        SUBMIT_SCORE,   // Client -> Host
        UPDATE_DATA     // Host -> Client
    }

    private final PacketType type;
    private String username;
    private boolean approved;
    private ScoreEntry scoreEntry;
    private List<ScoreEntry> scoreHistory;
    private List<TeamRanking> teamRankings;

    // 新增：用于向从机广播官方赛事全称
    private String officialEventName;

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

    // 广播更新构造 (已修改：支持传入赛事全称)
    public NetworkPacket(List<ScoreEntry> scoreHistory, List<TeamRanking> teamRankings, String officialEventName) {
        this.type = PacketType.UPDATE_DATA;
        this.scoreHistory = scoreHistory;
        this.teamRankings = teamRankings;
        this.officialEventName = officialEventName;
    }

    public PacketType getType() { return type; }
    public String getUsername() { return username; }
    public boolean isApproved() { return approved; }
    public ScoreEntry getScoreEntry() { return scoreEntry; }
    public List<ScoreEntry> getScoreHistory() { return scoreHistory; }
    public List<TeamRanking> getTeamRankings() { return teamRankings; }

    // 新增 Getter
    public String getOfficialEventName() { return officialEventName; }
}