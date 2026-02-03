package com.bear27570.ftc.scouting.models;

import java.io.Serializable;

public class TeamRanking implements Serializable {
    private static final long serialVersionUID = 8L;

    private final int teamNumber;
    private int matchesPlayed = 0;

    private double avgAutoArtifacts = 0;
    private double avgTeleopArtifacts = 0;
    private boolean canSequence = false;
    private boolean l2Capable = false;

    private int totalShots = 0;
    private int totalHits = 0;

    // 新增：判罚统计
    private double avgPenaltyCommitted = 0; // 平均犯规送分 (越低越好)
    private double avgOpponentPenalty = 0;  // 平均对手犯规获利 (越高越好)

    private double rating = 0;

    public TeamRanking(int teamNumber) {
        this.teamNumber = teamNumber;
    }

    public void addMatchResult(double auto, double teleop, boolean sequence, boolean climb, int hits, int shots,
                               int penaltyCommitted, int penaltyFromOpponent) {
        double totalAuto = avgAutoArtifacts * matchesPlayed;
        double totalTeleop = avgTeleopArtifacts * matchesPlayed;
        double totalPenCom = avgPenaltyCommitted * matchesPlayed;
        double totalOppPen = avgOpponentPenalty * matchesPlayed;

        matchesPlayed++;

        avgAutoArtifacts = (totalAuto + auto) / matchesPlayed;
        avgTeleopArtifacts = (totalTeleop + teleop) / matchesPlayed;

        // 判罚平均值更新
        avgPenaltyCommitted = (totalPenCom + penaltyCommitted) / matchesPlayed;
        avgOpponentPenalty = (totalOppPen + penaltyFromOpponent) / matchesPlayed;

        if (sequence) this.canSequence = true;
        if (climb) this.l2Capable = true;

        this.totalHits += hits;
        this.totalShots += shots;
    }

    public int getTeamNumber() { return teamNumber; }
    public int getMatchesPlayed() { return matchesPlayed; }
    public String getAvgAutoArtifactsFormatted() { return String.format("%.1f", avgAutoArtifacts); }
    public String getAvgTeleopArtifactsFormatted() { return String.format("%.1f", avgTeleopArtifacts); }

    // 新增 getter 用于 TableView
    public String getAvgPenaltyCommittedFormatted() { return String.format("%.1f", avgPenaltyCommitted); }
    public String getAvgOpponentPenaltyFormatted() { return String.format("%.1f", avgOpponentPenalty); }

    public double getAvgAutoArtifacts() { return avgAutoArtifacts; }
    public double getAvgTeleopArtifacts() { return avgTeleopArtifacts; }

    public String getCanSequence() { return canSequence ? "Yes" : "No"; }
    public String getL2Capable() { return l2Capable ? "Yes" : "No"; }

    public String getAccuracyFormatted() {
        if (totalShots == 0) return "N/A";
        double acc = (double) totalHits / totalShots * 100.0;
        return String.format("%.1f%%", acc);
    }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public String getRatingFormatted() { return String.format("%.2f", rating); }
}