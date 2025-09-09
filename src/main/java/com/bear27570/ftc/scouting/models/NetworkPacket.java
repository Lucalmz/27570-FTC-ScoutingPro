package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.util.List;

public class NetworkPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum PacketType {
        SUBMIT_SCORE,   // Client -> Host: A new score to be saved.
        UPDATE_DATA     // Host -> Client: A full refresh of all data.
    }

    private final PacketType type;
    private ScoreEntry scoreEntry;
    private List<ScoreEntry> scoreHistory;
    private List<TeamRanking> teamRankings;

    // Constructor for a client submitting a score
    public NetworkPacket(ScoreEntry scoreEntry) {
        this.type = PacketType.SUBMIT_SCORE;
        this.scoreEntry = scoreEntry;
    }

    // Constructor for a host broadcasting an update
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