package com.bear27570.ftc.scouting.models;

import java.io.Serializable;

public class TeamRanking implements Serializable {
    private static final long serialVersionUID = 6L; // Version updated

    private final int teamNumber;
    private int matchesPlayed = 0;
    private double avgAutoArtifacts = 0;
    private double avgTeleopArtifacts = 0;
    private boolean canSequence = false;
    private boolean l2Capable = false;
    private double rating = 0;

    public TeamRanking(int teamNumber) {
        this.teamNumber = teamNumber;
    }

    // 修改参数为 double
    public void addMatchResult(double auto, double teleop, boolean sequence, boolean climb) {
        double totalAuto = avgAutoArtifacts * matchesPlayed;
        double totalTeleop = avgTeleopArtifacts * matchesPlayed;
        matchesPlayed++;
        avgAutoArtifacts = (totalAuto + auto) / matchesPlayed;
        avgTeleopArtifacts = (totalTeleop + teleop) / matchesPlayed;
        if (sequence) this.canSequence = true;
        if (climb) this.l2Capable = true;
    }

    // Getters ... (保持不变)
    public int getTeamNumber() { return teamNumber; }
    public int getMatchesPlayed() { return matchesPlayed; }
    // 保留两位小数显示
    public String getAvgAutoArtifactsFormatted() { return String.format("%.1f", avgAutoArtifacts); }
    public String getAvgTeleopArtifactsFormatted() { return String.format("%.1f", avgTeleopArtifacts); }

    // 为了 TableView 兼容，原 getter 返回 double 即可，FXML CellFactory 会处理显示，或者使用上面的 Formatted 方法
    public double getAvgAutoArtifacts() { return avgAutoArtifacts; }
    public double getAvgTeleopArtifacts() { return avgTeleopArtifacts; }

    public String getCanSequence() { return canSequence ? "Yes" : "No"; }
    public String getL2Capable() { return l2Capable ? "Yes" : "No"; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public String getRatingFormatted() { return String.format("%.2f", rating); }
}