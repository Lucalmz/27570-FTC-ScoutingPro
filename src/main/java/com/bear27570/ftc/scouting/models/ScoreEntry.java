// File: src/main/java/com/bear27570/ftc/scouting/models/ScoreEntry.java
package com.bear27570.ftc.scouting.models;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScoreEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 14L;

    public enum Type { ALLIANCE, SINGLE }
    public enum SyncStatus { UNSYNCED, EXPORTED, SYNCED }

    private int id;
    private Type scoreType;
    private int matchNumber;
    private String alliance;
    private int team1;
    private int team2;

    private int team1AutoScore;
    private int team2AutoScore;
    private String team1AutoProj;
    private String team2AutoProj;
    private String team1AutoRow;
    private String team2AutoRow;

    private int autoArtifacts;
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

    // ==========================================
    // 1. 必须新增的无参构造函数 (供 JDBI 反射使用)
    // ==========================================
    public ScoreEntry() {
    }

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

    // 用于带历史数据的完整构造函数
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
        this.autoArtifacts = team1AutoScore + team2AutoScore;

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
        this.submissionTime = (existingTimestamp == null || existingTimestamp.trim().isEmpty())
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : existingTimestamp;
        this.syncStatus = syncStatus != null ? syncStatus : SyncStatus.UNSYNCED;
        this.totalScore = calculateTotalScore();
    }

    private int calculateTotalScore() {
        int score = team1AutoScore + team2AutoScore + (teleopArtifacts * 3);
        if (team1CanSequence) score += 10;
        if (team2CanSequence) score += 10;
        if (team1L2Climb) score += 15;
        if (team2L2Climb) score += 15;
        return score;
    }

    // ==========================================
    // 2. 原有的 Getters
    // ==========================================
    public int getId() { return id; }
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

    // ==========================================
    // 3. 补齐 JDBI 反射所需的 Setters
    // ==========================================
    public void setId(int id) { this.id = id; }
    public void setScoreType(Type scoreType) { this.scoreType = scoreType; }
    public void setMatchNumber(int matchNumber) { this.matchNumber = matchNumber; }
    public void setAlliance(String alliance) { this.alliance = alliance; }
    public void setTeam1(int team1) { this.team1 = team1; }
    public void setTeam2(int team2) { this.team2 = team2; }
    public void setTeam1AutoScore(int team1AutoScore) { this.team1AutoScore = team1AutoScore; }
    public void setTeam2AutoScore(int team2AutoScore) { this.team2AutoScore = team2AutoScore; }
    public void setTeam1AutoProj(String team1AutoProj) { this.team1AutoProj = team1AutoProj; }
    public void setTeam2AutoProj(String team2AutoProj) { this.team2AutoProj = team2AutoProj; }
    public void setTeam1AutoRow(String team1AutoRow) { this.team1AutoRow = team1AutoRow; }
    public void setTeam2AutoRow(String team2AutoRow) { this.team2AutoRow = team2AutoRow; }
    public void setAutoArtifacts(int autoArtifacts) { this.autoArtifacts = autoArtifacts; }
    public void setTeleopArtifacts(int teleopArtifacts) { this.teleopArtifacts = teleopArtifacts; }
    public void setTeam1CanSequence(boolean team1CanSequence) { this.team1CanSequence = team1CanSequence; }
    public void setTeam2CanSequence(boolean team2CanSequence) { this.team2CanSequence = team2CanSequence; }
    public void setTeam1L2Climb(boolean team1L2Climb) { this.team1L2Climb = team1L2Climb; }
    public void setTeam2L2Climb(boolean team2L2Climb) { this.team2L2Climb = team2L2Climb; }
    public void setTeam1Ignored(boolean team1Ignored) { this.team1Ignored = team1Ignored; }
    public void setTeam2Ignored(boolean team2Ignored) { this.team2Ignored = team2Ignored; }
    public void setTeam1Broken(boolean team1Broken) { this.team1Broken = team1Broken; }
    public void setTeam2Broken(boolean team2Broken) { this.team2Broken = team2Broken; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public void setSubmitter(String submitter) { this.submitter = submitter; }
    public void setSubmissionTime(String submissionTime) { this.submissionTime = submissionTime; }
    public void setClickLocations(String clickLocations) { this.clickLocations = clickLocations; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
}