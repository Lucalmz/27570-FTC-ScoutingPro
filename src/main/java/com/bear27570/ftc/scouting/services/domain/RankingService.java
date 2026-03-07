package com.bear27570.ftc.scouting.services.domain;

import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.repository.*;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankingService {

    private static final int PENALTY_MAJOR_PTS = 15;
    private static final int PENALTY_MINOR_PTS = 5;

    private final ScoreRepository scoreRepository;
    private final PenaltyRepository penaltyRepository;
    private final CompetitionRepository competitionRepository;

    public RankingService(ScoreRepository scoreRepository,
                          PenaltyRepository penaltyRepository,
                          CompetitionRepository competitionRepository) {
        this.scoreRepository = scoreRepository;
        this.penaltyRepository = penaltyRepository;
        this.competitionRepository = competitionRepository;
    }

    // ★ 新增：对外暴露获取侦察员信誉度的接口
    public Map<String, Double> getSubmitterReliabilities(String competitionName) {
        List<ScoreEntry> allScores = scoreRepository.findByCompetition(competitionName);
        Map<Integer, PenaltyRepository.FullPenaltyRow> penaltyMap = penaltyRepository.getFullPenalties(competitionName);
        return calculateScouterWeights(allScores, penaltyMap);
    }

    public List<TeamRanking> calculateRankings(String competitionName) {
        List<ScoreEntry> allScores = scoreRepository.findByCompetition(competitionName);
        Map<Integer, PenaltyRepository.FullPenaltyRow> penaltyMap = penaltyRepository.getFullPenalties(competitionName);
        Competition comp = competitionRepository.findByName(competitionName);
        String formulaStr = (comp != null && comp.getRatingFormula() != null) ? comp.getRatingFormula() : "total";

        // 1. 计算每个侦察员的权重 (1.0 或 0.5)
        Map<String, Double> scouterWeights = calculateScouterWeights(allScores, penaltyMap);

        Map<Integer, TeamRanking> rankings = new HashMap<>();
        Map<Integer, Double> totalRatingPoints = new HashMap<>();

        for (ScoreEntry score : allScores) {
            boolean isAllianceMode = (score.getScoreType() == ScoreEntry.Type.ALLIANCE);
            double divisor = isAllianceMode ? 2.0 : 1.0;

            double adjAuto = score.getAutoArtifacts() / divisor;
            double adjTeleop = score.getTeleopArtifacts() / divisor;

            int[] shotStats = parseShotStats(score.getClickLocations());
            int t1Hits = shotStats[0], t1Shots = shotStats[1];
            int t2Hits = shotStats[2], t2Shots = shotStats[3];

            int penCommitted = 0, penReceived = 0;
            PenaltyRepository.FullPenaltyRow pe = penaltyMap.get(score.getMatchNumber());

            if (pe != null) {
                int redGaveAway = (pe.rMaj * PENALTY_MAJOR_PTS) + (pe.rMin * PENALTY_MINOR_PTS);
                int blueGaveAway = (pe.bMaj * PENALTY_MAJOR_PTS) + (pe.bMin * PENALTY_MINOR_PTS);
                if (score.getAlliance().equalsIgnoreCase("RED")) {
                    penCommitted = redGaveAway;
                    penReceived = blueGaveAway;
                } else {
                    penCommitted = blueGaveAway;
                    penReceived = redGaveAway;
                }
                if (isAllianceMode) {
                    penCommitted /= 2;
                    penReceived /= 2;
                }
            }

            double matchRating = evaluateFormula(formulaStr, score, divisor);

            // ★ 获取当前提交者的权重 (默认为 1.0)
            double weight = scouterWeights.getOrDefault(score.getSubmitter(), 1.0);

            // ★ 传入权重到 TeamRanking
            processTeam(rankings, totalRatingPoints, score.getTeam1(), score, adjAuto, adjTeleop, matchRating, true, t1Hits, t1Shots, score.isTeam1Broken(), penCommitted, penReceived, weight);

            if (isAllianceMode) {
                processTeam(rankings, totalRatingPoints, score.getTeam2(), score, adjAuto, adjTeleop, matchRating, false, t2Hits, t2Shots, score.isTeam2Broken(), penCommitted, penReceived, weight);
            }
        }

        for (TeamRanking rank : rankings.values()) {
            double sum = totalRatingPoints.getOrDefault(rank.getTeamNumber(), 0.0);
            if (rank.getMatchesPlayed() > 0) {
                // Rating 也可以考虑加权，这里为了简单起见，仍使用算术平均
                rank.setRating(sum / rank.getMatchesPlayed());
            }
        }
        return new ArrayList<>(rankings.values());
    }

    /**
     * 核心算法：对比官方总分和侦察员记录，计算误差并赋予权重
     */
    private Map<String, Double> calculateScouterWeights(List<ScoreEntry> scores, Map<Integer, PenaltyRepository.FullPenaltyRow> officialData) {
        Map<String, List<Double>> userErrors = new HashMap<>();

        for (ScoreEntry score : scores) {
            // 只有联盟模式的总分才有对比意义
            if (score.getScoreType() != ScoreEntry.Type.ALLIANCE) continue;

            PenaltyRepository.FullPenaltyRow official = officialData.get(score.getMatchNumber());
            if (official == null) continue;

            boolean isRed = score.getAlliance().equalsIgnoreCase("RED");
            int officialTotal = isRed ? official.rScore : official.bScore;

            // 官方分数为0说明可能还没上传或没开始
            if (officialTotal <= 0) continue;

            // 逻辑推导：
            // Scouted Score = 纯机器得分 (不含罚分)
            // Official Score = 机器得分 + 对方送的罚分
            // 预测总分 = Scouted Score + Official Opponent Penalty
            int penaltyGained = isRed ? (official.bMaj * 15 + official.bMin * 5) : (official.rMaj * 15 + official.rMin * 5);
            int scoutPredictedTotal = score.getTotalScore() + penaltyGained;

            // 计算相对误差
            double error = Math.abs(scoutPredictedTotal - officialTotal) / (double) officialTotal;

            userErrors.computeIfAbsent(score.getSubmitter(), k -> new ArrayList<>()).add(error);
        }

        Map<String, Double> weights = new HashMap<>();
        // 遍历所有侦察员，计算平均误差
        for (Map.Entry<String, List<Double>> entry : userErrors.entrySet()) {
            double avgError = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            // 如果平均误差超过 20%，权重降为 0.5，否则为 1.0
            if (avgError > 0.20) {
                weights.put(entry.getKey(), 0.5);
            } else {
                weights.put(entry.getKey(), 1.0);
            }
        }
        return weights;
    }

    private int[] parseShotStats(String clickLocations) {
        int[] stats = new int[4];
        if (clickLocations == null || clickLocations.isEmpty()) return stats;
        String[] points = clickLocations.split(";");
        for (String p : points) {
            try {
                if (p.trim().isEmpty()) continue;
                String[] mainParts = p.split(":");
                if (mainParts.length < 2) continue;
                int teamIdx = Integer.parseInt(mainParts[0]);
                String[] coords = mainParts[1].split(",");
                int state = 0;
                if (coords.length >= 3) state = Integer.parseInt(coords[2]);
                if (teamIdx == 1) {
                    stats[1]++;
                    if (state == 0) stats[0]++;
                } else if (teamIdx == 2) {
                    stats[3]++;
                    if (state == 0) stats[2]++;
                }
            } catch (Exception ignored) {}
        }
        return stats;
    }

    // ★ 修改：增加 weight 参数
    private void processTeam(Map<Integer, TeamRanking> rankings, Map<Integer, Double> totalRatingPoints,
                             int teamNum, ScoreEntry score, double adjAuto, double adjTeleop, double matchRating,
                             boolean isTeam1, int hits, int shots, boolean isBroken, int penComm, int penRec, double weight) {
        rankings.putIfAbsent(teamNum, new TeamRanking(teamNum));
        if (isBroken) return;
        TeamRanking tr = rankings.get(teamNum);
        boolean seq = isTeam1 ? score.isTeam1CanSequence() : score.isTeam2CanSequence();
        boolean climb = isTeam1 ? score.isTeam1L2Climb() : score.isTeam2L2Climb();

        // 调用加权版本的 addMatchResult
        tr.addMatchResult(adjAuto, adjTeleop, seq, climb, hits, shots, penComm, penRec, weight);

        totalRatingPoints.put(teamNum, totalRatingPoints.getOrDefault(teamNum, 0.0) + matchRating);
    }

    private double evaluateFormula(String formula, ScoreEntry score, double divisor) {
        try {
            int seqAny = (score.isTeam1CanSequence() || score.isTeam2CanSequence()) ? 1 : 0;
            int climbAny = (score.isTeam1L2Climb() || score.isTeam2L2Climb()) ? 1 : 0;
            Expression e = new ExpressionBuilder(formula).variables("auto", "teleop", "total", "seq", "climb").build()
                    .setVariable("auto", score.getAutoArtifacts() / divisor)
                    .setVariable("teleop", score.getTeleopArtifacts() / divisor)
                    .setVariable("total", score.getTotalScore() / divisor)
                    .setVariable("seq", seqAny)
                    .setVariable("climb", climbAny);
            return e.evaluate();
        } catch (Exception e) {
            return score.getTotalScore() / divisor;
        }
    }
}