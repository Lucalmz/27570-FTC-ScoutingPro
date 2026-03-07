package com.bear27570.ftc.scouting.models;

import java.io.Serializable;

public class TeamRanking implements Serializable {
    private static final long serialVersionUID = 10L; // 更新序列化版本

    private final int teamNumber;
    private int matchesPlayed = 0;

    // ★ 核心改动：引入总权重，用于计算加权平均值
    private double totalWeight = 0;

    // 使用累加值(Sum)而不是动态平均值(Avg)，以支持加权计算
    private double sumAutoArtifacts = 0;
    private double sumTeleopArtifacts = 0;
    private double sumPenaltyCommitted = 0;
    private double sumOpponentPenalty = 0;

    // 对外暴露的计算后的平均值
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

    /**
     * 添加一场比赛数据
     * @param weight 数据权重 (1.0 = 正常, 0.5 = 低信誉度)
     */
    public void addMatchResult(double auto, double teleop, boolean sequence, boolean climb, int hits, int shots,
                               int penaltyCommitted, int penaltyFromOpponent, double weight) {
        matchesPlayed++;
        totalWeight += weight; // 累加权重

        // 核心：各项数据 * 权重 后累加
        sumAutoArtifacts += auto * weight;
        sumTeleopArtifacts += teleop * weight;
        sumPenaltyCommitted += penaltyCommitted * weight;
        sumOpponentPenalty += penaltyFromOpponent * weight;

        // 重新计算加权平均值
        if (totalWeight > 0) {
            avgAutoArtifacts = sumAutoArtifacts / totalWeight;
            avgTeleopArtifacts = sumTeleopArtifacts / totalWeight;
            avgPenaltyCommitted = sumPenaltyCommitted / totalWeight;
            avgOpponentPenalty = sumOpponentPenalty / totalWeight;
        }

        if (sequence) this.canSequence = true;
        if (climb) this.l2Capable = true;

        // 命中率不需要加权，因为它本身就是 totalHits / totalShots
        this.totalHits += hits;
        this.totalShots += shots;
    }

    // --- Getters (保持原有接口兼容) ---

    public int getTeamNumber() { return teamNumber; }
    public int getMatchesPlayed() { return matchesPlayed; }

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