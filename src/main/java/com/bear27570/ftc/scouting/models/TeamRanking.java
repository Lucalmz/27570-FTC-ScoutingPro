// File: TeamRanking.java
package com.bear27570.ftc.scouting.models;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class TeamRanking implements Serializable {
    private static final long serialVersionUID = 11L;
    private final LinkedList<Double> recentRatings = new LinkedList<>();

    private final int teamNumber;
    private int matchesPlayed = 0;

    private double totalWeight = 0;

    // ★ 针对自动得分特殊的权重计算(分母)，以实现 "无得分则跳过该局平均值" 的算法
    private double autoWeight = 0;

    private double sumAutoArtifacts = 0;
    private double sumTeleopArtifacts = 0;
    private double sumPenaltyCommitted = 0;
    private double sumOpponentPenalty = 0;

    private double avgAutoArtifacts = 0;
    private double avgTeleopArtifacts = 0;
    private double avgPenaltyCommitted = 0;
    private double avgOpponentPenalty = 0;

    private boolean canSequence = false;
    private boolean l2Capable = false;

    private int totalShots = 0;
    private int totalHits = 0;
    private double rating = 0;

    public TeamRanking(int teamNumber) {
        this.teamNumber = teamNumber;
    }

    public void addMatchResult(double autoScore, double teleop, boolean sequence, boolean climb, int hits, int shots,
                               int penaltyCommitted, int penaltyFromOpponent, double weight,double matchRating) {
        matchesPlayed++;
        totalWeight += weight;

        // TeleOp 和判罚使用总权重
        sumTeleopArtifacts += teleop * weight;
        sumPenaltyCommitted += penaltyCommitted * weight;
        sumOpponentPenalty += penaltyFromOpponent * weight;

        if (totalWeight > 0) {
            avgTeleopArtifacts = sumTeleopArtifacts / totalWeight;
            avgPenaltyCommitted = sumPenaltyCommitted / totalWeight;
            avgOpponentPenalty = sumOpponentPenalty / totalWeight;
        }

        // ★ 智能 Auto 平均分跳过机制：仅在自动得分大于 0 时，才将其计入自动阶段的权重(分母)和总分(分子)
        if (autoScore > 0) {
            sumAutoArtifacts += autoScore * weight;
            autoWeight += weight; // 只增加有有效记录场次的权重分母
        }

        if (autoWeight > 0) {
            avgAutoArtifacts = sumAutoArtifacts / autoWeight;
        } else {
            avgAutoArtifacts = 0;
        }
        recentRatings.add(matchRating);
        if (sequence) this.canSequence = true;
        if (climb) this.l2Capable = true;
        recentRatings.add(rating);
        if(recentRatings.size() > 5) recentRatings.removeFirst();
        this.totalHits += hits;
        this.totalShots += shots;
    }

    public int getTeamNumber() { return teamNumber; }
    public int getMatchesPlayed() { return matchesPlayed; }
    public List<Double> getRecentRatings() {
        return recentRatings;
    }
    public String getAvgAutoArtifactsFormatted() { return String.format("%.1f", avgAutoArtifacts); }
    public String getAvgTeleopArtifactsFormatted() { return String.format("%.1f", avgTeleopArtifacts); }
    public String getAvgPenaltyCommittedFormatted() { return String.format("%.1f", avgPenaltyCommitted); }
    public String getAvgOpponentPenaltyFormatted() { return String.format("%.1f", avgOpponentPenalty); }

    public double getAvgAutoArtifacts() { return avgAutoArtifacts; }
    public double getAvgTeleopArtifacts() { return avgTeleopArtifacts; }
    public double getAvgPenaltyCommitted() { return avgPenaltyCommitted; }
    public double getAvgOpponentPenalty() { return avgOpponentPenalty; }

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