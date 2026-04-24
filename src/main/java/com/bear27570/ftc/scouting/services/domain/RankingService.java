// File: RankingService.java
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

        Map<String, Double> scouterWeights = calculateScouterWeights(allScores, penaltyMap);

        Map<Integer, TeamRanking> rankings = new HashMap<>();
        Map<Integer, Double> totalRatingPoints = new HashMap<>();

        for (ScoreEntry score : allScores) {
            boolean isAllianceMode = (score.getScoreType() == ScoreEntry.Type.ALLIANCE);
            double divisor = isAllianceMode ? 2.0 : 1.0;

            // ★ 新逻辑：直接取各自独立的真实自动阶段得分
            double t1Auto = score.getTeam1AutoScore();
            double t2Auto = score.getTeam2AutoScore();

            // TeleOp 仍然共享
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
            double weight = scouterWeights.getOrDefault(score.getSubmitter(), 1.0);

            // ★ 传入 t1Auto 替代原本平摊的 adjAuto
            processTeam(rankings, totalRatingPoints, score.getTeam1(), score, t1Auto, adjTeleop, matchRating, true, t1Hits, t1Shots, score.isTeam1Broken(), penCommitted, penReceived, weight);

            if (isAllianceMode) {
                // ★ 传入 t2Auto
                processTeam(rankings, totalRatingPoints, score.getTeam2(), score, t2Auto, adjTeleop, matchRating, false, t2Hits, t2Shots, score.isTeam2Broken(), penCommitted, penReceived, weight);
            }
        }

        for (TeamRanking rank : rankings.values()) {
            double sum = totalRatingPoints.getOrDefault(rank.getTeamNumber(), 0.0);
            if (rank.getMatchesPlayed() > 0) {
                rank.setRating(sum / rank.getMatchesPlayed());
            }
        }
        return new ArrayList<>(rankings.values());
    }

    private Map<String, Double> calculateScouterWeights(List<ScoreEntry> scores, Map<Integer, PenaltyRepository.FullPenaltyRow> officialData) {
        Map<String, List<Double>> userErrors = new HashMap<>();

        // 1. 按比赛场次和联盟颜色对分数进行分组 (MatchNum_Alliance -> List<Score>)
        Map<String, List<ScoreEntry>> allianceGroups = new HashMap<>();
        for (ScoreEntry score : scores) {
            String key = score.getMatchNumber() + "_" + score.getAlliance().toUpperCase();
            allianceGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(score);
        }

        // 2. 遍历每一个联队的数据
        for (Map.Entry<String, List<ScoreEntry>> entry : allianceGroups.entrySet()) {
            List<ScoreEntry> groupScores = entry.getValue();
            if (groupScores.isEmpty()) continue;

            ScoreEntry firstEntry = groupScores.get(0);
            PenaltyRepository.FullPenaltyRow official = officialData.get(firstEntry.getMatchNumber());
            if (official == null) continue;

            boolean isRed = firstEntry.getAlliance().equalsIgnoreCase("RED");
            int officialTotal = isRed ? official.rScore : official.bScore;
            if (officialTotal <= 0) continue;

            int penaltyGained = isRed ? (official.bMaj * 15 + official.bMin * 5) : (official.rMaj * 15 + official.rMin * 5);

            // 3. 计算联队侦查总分！无论是 ALLIANCE 模式(单人记2队) 还是 两个 SINGLE 模式(两人各记1队)，全加起来
            int combinedScoutTotal = 0;
            for (ScoreEntry s : groupScores) {
                combinedScoutTotal += s.getTotalScore();
            }

            // 注意：如果是两个 SINGLE 组成了一个联盟，加上犯规得分
            // 如果是 ALLIANCE 模式，本身已经是总分了
            int scoutPredictedTotal = combinedScoutTotal + penaltyGained;

            // 4. 计算出联盟层面的总误差
            double error = Math.abs(scoutPredictedTotal - officialTotal) / (double) officialTotal;

            // 5. 误差分摊：把这笔账记到所有参与记录这个联盟的人头上（连坐）
            for (ScoreEntry s : groupScores) {
                userErrors.computeIfAbsent(s.getSubmitter(), k -> new ArrayList<>()).add(error);
            }
        }

        // 6. 统计所有人的平均误差，揪出内鬼（大数定律生效的地方）
        Map<String, Double> weights = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : userErrors.entrySet()) {
            double avgError = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            // 只要平均误差超过 20%，说明这个人就是不准，他的历史权重降低到 0.5 (也可以调得更低)
            if (avgError > 0.20) {
                weights.put(entry.getKey(), 0.5);
            } else {
                weights.put(entry.getKey(), 1.0); // 好人一生平安
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

    private void processTeam(Map<Integer, TeamRanking> rankings, Map<Integer, Double> totalRatingPoints,
                             int teamNum, ScoreEntry score, double autoVal, double teleopVal, double matchRating,
                             boolean isTeam1, int hits, int shots, boolean isBroken, int penComm, int penRec, double weight) {
        rankings.putIfAbsent(teamNum, new TeamRanking(teamNum));
        if (isBroken) return;
        TeamRanking tr = rankings.get(teamNum);
        boolean seq = isTeam1 ? score.isTeam1CanSequence() : score.isTeam2CanSequence();
        boolean climb = isTeam1 ? score.isTeam1L2Climb() : score.isTeam2L2Climb();

        tr.addMatchResult(autoVal, teleopVal, seq, climb, hits, shots, penComm, penRec, weight, matchRating);

        totalRatingPoints.put(teamNum, totalRatingPoints.getOrDefault(teamNum, 0.0) + matchRating);
    }

    private double evaluateFormula(String formula, ScoreEntry score, double divisor) {
        try {
            int seqAny = (score.isTeam1CanSequence() || score.isTeam2CanSequence()) ? 1 : 0;
            int climbAny = (score.isTeam1L2Climb() || score.isTeam2L2Climb()) ? 1 : 0;
            Expression e = new ExpressionBuilder(formula).variables("auto", "teleop", "total", "seq", "climb").build()
                    .setVariable("auto", score.getAutoArtifacts() / divisor) // 整体评分依然使用汇总值
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