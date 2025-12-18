package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScoreEntry implements Serializable {
    private static final long serialVersionUID = 7L; // Version updated

    public enum Type { ALLIANCE, SINGLE }

    private Type scoreType;
    private int matchNumber;
    private String alliance;
    private int team1;
    private int team2;
    private int autoArtifacts;
    private int teleopArtifacts;
    private boolean team1CanSequence;
    private boolean team2CanSequence;
    private boolean team1L2Climb;
    private boolean team2L2Climb;
    private int totalScore;
    private String submitter;
    private String submissionTime;

    // 新增：存储点击坐标，格式 "x,y;x,y;..."
    private String clickLocations;

    public ScoreEntry(Type scoreType, int matchNumber, String alliance, int team1, int team2, int autoArtifacts, int teleopArtifacts,
                      boolean team1CanSequence, boolean team2CanSequence, boolean team1L2Climb, boolean team2L2Climb,
                      String clickLocations, // 新增参数
                      String submitter) {
        this.scoreType = scoreType;
        this.matchNumber = matchNumber;
        this.alliance = alliance;
        this.team1 = team1;
        this.team2 = team2;
        this.autoArtifacts = autoArtifacts;
        this.teleopArtifacts = teleopArtifacts;
        this.team1CanSequence = team1CanSequence;
        this.team2CanSequence = team2CanSequence;
        this.team1L2Climb = team1L2Climb;
        this.team2L2Climb = team2L2Climb;
        this.clickLocations = clickLocations;
        this.submitter = submitter;
        this.submissionTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.totalScore = calculateTotalScore();
    }

    private int calculateTotalScore() {
        int score = (autoArtifacts * 7) + (teleopArtifacts * 3);
        if (team1CanSequence) score += 10;
        if (team2CanSequence) score += 10;
        if (team1L2Climb) score += 15;
        if (team2L2Climb) score += 15;
        return score;
    }

    // Getters
    public Type getScoreType() { return scoreType; }
    public int getMatchNumber() { return matchNumber; }
    public String getAlliance() { return alliance; }
    public int getTeam1() { return team1; }
    public int getTeam2() { return team2; }
    public String getTeams() {
        if (scoreType == Type.SINGLE) return String.valueOf(team1);
        return team1 + " & " + team2;
    }
    public int getAutoArtifacts() { return autoArtifacts; }
    public int getTeleopArtifacts() { return teleopArtifacts; }
    public int getTotalScore() { return totalScore; }
    public String getSubmitter() { return submitter; }
    public String getSubmissionTime() { return submissionTime; }
    public boolean isTeam1CanSequence() { return team1CanSequence; }
    public boolean isTeam2CanSequence() { return team2CanSequence; }
    public boolean isTeam1L2Climb() { return team1L2Climb; }
    public boolean isTeam2L2Climb() { return team2L2Climb; }
    public String getClickLocations() { return clickLocations; }
}