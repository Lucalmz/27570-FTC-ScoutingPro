package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.util.List;

// 这个类是所有网络传输数据的容器
public class NetworkPacket implements Serializable {
    private static final long serialVersionUID = 1L; // 确保两端版本一致

    public enum PacketType {
        SUBMIT_SCORE,   // 客户端 -> 主机：提交一个新的得分
        UPDATE_DATA     // 主机 -> 客户端：广播最新的数据
    }

    private final PacketType type;
    private ScoreEntry scoreEntry; // 用于 SUBMIT_SCORE
    private List<ScoreEntry> scoreHistory; // 用于 UPDATE_DATA
    private List<TeamRanking> teamRankings; // 用于 UPDATE_DATA

    // 构造函数 for 客户端提交
    public NetworkPacket(ScoreEntry scoreEntry) {
        this.type = PacketType.SUBMIT_SCORE;
        this.scoreEntry = scoreEntry;
    }

    // 构造函数 for 主机更新
    public NetworkPacket(List<ScoreEntry> scoreHistory, List<TeamRanking> teamRankings) {
        this.type = PacketType.UPDATE_DATA;
        this.scoreHistory = scoreHistory;
        this.teamRankings = teamRankings;
    }

    // Getters
    public PacketType getType() { return type; }
    public ScoreEntry getScoreEntry() { return scoreEntry; }
    public List<ScoreEntry> getScoreHistory() { return scoreHistory; }
    public List<TeamRanking> getTeamRankings() { return teamRankings; }
}