// File: ScoreEntry.java
package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScoreEntry implements Serializable {
    private static final long serialVersionUID = 13L;

    public enum Type { ALLIANCE, SINGLE }
    public enum SyncStatus { UNSYNCED, EXPORTED, SYNCED }

    private int id;
    private Type scoreType;
    private int matchNumber;
    private String alliance;
    private int team1;
    private int team2;

    // --- 新增的 Auto 精细化记录字段 ---
    private int team1AutoScore;
    private int team2AutoScore;
    private String team1AutoProj; // "NEAR", "FAR", "NONE"
    private String team2AutoProj;
    private String team1AutoRow;  // "ROW1", "ROW2", "ROW3", "NONE"
    private String team2AutoRow;

    private int autoArtifacts; // 保持兼容性，作为两者得分之和
    private int teleopArtifacts;

    private boolean team1CanSequence;
    private boolean team2CanSequence;
    private boolean team1L2Climb;
    private boolean team2L2Climb;
    private boolean team1Ignored;
    private boolean team2Ignored;
    private boolean team1Broken;
    private boolean team2Broken;
    private int totalScore;
    private String submitter;
    private String submissionTime;
    private String clickLocations;

    private SyncStatus syncStatus;

    // 用于新建提交的构造函数
    public ScoreEntry(Type scoreType, int matchNumber, String alliance, int team1, int team2,
                      int team1AutoScore, int team2AutoScore, String team1AutoProj, String team2AutoProj,
                      String team1AutoRow, String team2AutoRow, int teleopArtifacts,
                      boolean team1CanSequence, boolean team2CanSequence, boolean team1L2Climb, boolean team2L2Climb,
                      boolean team1Ignored, boolean team2Ignored,
                      boolean team1Broken, boolean team2Broken,
                      String clickLocations, String submitter) {
        this(0, scoreType, matchNumber, alliance, team1, team2,
                team1AutoScore, team2AutoScore, team1AutoProj, team2AutoProj, team1AutoRow, team2AutoRow,
                teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb,
                team1Ignored, team2Ignored, team1Broken, team2Broken,
                clickLocations, submitter,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                SyncStatus.UNSYNCED);
    }

    // 用于数据库加载的完整构造函数
    public ScoreEntry(int id, Type scoreType, int matchNumber, String alliance, int team1, int team2,
                      int team1AutoScore, int team2AutoScore, String team1AutoProj, String team2AutoProj,
                      String team1AutoRow, String team2AutoRow, int teleopArtifacts,
                      boolean team1CanSequence, boolean team2CanSequence, boolean team1L2Climb, boolean team2L2Climb,
                      boolean team1Ignored, boolean team2Ignored,
                      boolean team1Broken, boolean team2Broken,
                      String clickLocations, String submitter, String existingTimestamp, SyncStatus syncStatus) {
        this.id = id;
        this.scoreType = scoreType;
        this.matchNumber = matchNumber;
        this.alliance = alliance;
        this.team1 = team1;
        this.team2 = team2;

        this.team1AutoScore = team1AutoScore;
        this.team2AutoScore = team2AutoScore;
        this.team1AutoProj = team1AutoProj;
        this.team2AutoProj = team2AutoProj;
        this.team1AutoRow = team1AutoRow;
        this.team2AutoRow = team2AutoRow;
        this.autoArtifacts = team1AutoScore + team2AutoScore; // 聚合

        this.teleopArtifacts = teleopArtifacts;
        this.team1CanSequence = team1CanSequence;
        this.team2CanSequence = team2CanSequence;
        this.team1L2Climb = team1L2Climb;
        this.team2L2Climb = team2L2Climb;
        this.team1Ignored = team1Ignored;
        this.team2Ignored = team2Ignored;
        this.team1Broken = team1Broken;
        this.team2Broken = team2Broken;
        this.clickLocations = clickLocations;
        this.submitter = submitter;
        this.submissionTime = existingTimestamp;
        this.syncStatus = syncStatus != null ? syncStatus : SyncStatus.UNSYNCED;
        this.totalScore = calculateTotalScore();
    }

    private int calculateTotalScore() {
        // 自动得分由用户直接记录真实分数，直接相加
        int score = team1AutoScore + team2AutoScore + (teleopArtifacts * 3);
        if (team1CanSequence) score += 10;
        if (team2CanSequence) score += 10;
        if (team1L2Climb) score += 15;
        if (team2L2Climb) score += 15;
        return score;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Type getScoreType() { return scoreType; }
    public int getMatchNumber() { return matchNumber; }
    public String getAlliance() { return alliance; }
    public int getTeam1() { return team1; }
    public int getTeam2() { return team2; }
    public String getTeams() { return scoreType == Type.SINGLE ? String.valueOf(team1) : team1 + " & " + team2; }

    public int getTeam1AutoScore() { return team1AutoScore; }
    public int getTeam2AutoScore() { return team2AutoScore; }
    public String getTeam1AutoProj() { return team1AutoProj; }
    public String getTeam2AutoProj() { return team2AutoProj; }
    public String getTeam1AutoRow() { return team1AutoRow; }
    public String getTeam2AutoRow() { return team2AutoRow; }

    public int getAutoArtifacts() { return autoArtifacts; }
    public int getTeleopArtifacts() { return teleopArtifacts; }
    public int getTotalScore() { return totalScore; }
    public String getSubmitter() { return submitter; }
    public String getSubmissionTime() { return submissionTime; }
    public boolean isTeam1CanSequence() { return team1CanSequence; }
    public boolean isTeam2CanSequence() { return team2CanSequence; }
    public boolean isTeam1L2Climb() { return team1L2Climb; }
    public boolean isTeam2L2Climb() { return team2L2Climb; }
    public boolean isTeam1Ignored() { return team1Ignored; }
    public boolean isTeam2Ignored() { return team2Ignored; }
    public boolean isTeam1Broken() { return team1Broken; }
    public boolean isTeam2Broken() { return team2Broken; }
    public String getClickLocations() { return clickLocations; }
    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
}