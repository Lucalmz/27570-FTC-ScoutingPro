package com.bear27570.ftc.scouting.services.domain;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import com.bear27570.ftc.scouting.repository.ScoreRepository;
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

    public List<TeamRanking> calculateRankings(String competitionName) {
        List<ScoreEntry> allScores = scoreRepository.findByCompetition(competitionName);
        Map<Integer, PenaltyRepository.FullPenaltyRow> penaltyMap = penaltyRepository.getFullPenalties(competitionName);
        Competition comp = competitionRepository.findByName(competitionName);
        String formulaStr = (comp != null && comp.getRatingFormula() != null) ? comp.getRatingFormula() : "total";

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

            processTeam(rankings, totalRatingPoints, score.getTeam1(), score, adjAuto, adjTeleop, matchRating, true, t1Hits, t1Shots, score.isTeam1Broken(), penCommitted, penReceived);

            if (isAllianceMode) {
                processTeam(rankings, totalRatingPoints, score.getTeam2(), score, adjAuto, adjTeleop, matchRating, false, t2Hits, t2Shots, score.isTeam2Broken(), penCommitted, penReceived);
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
                             int teamNum, ScoreEntry score, double adjAuto, double adjTeleop, double matchRating,
                             boolean isTeam1, int hits, int shots, boolean isBroken, int penComm, int penRec) {
        rankings.putIfAbsent(teamNum, new TeamRanking(teamNum));
        if (isBroken) return;
        TeamRanking tr = rankings.get(teamNum);
        boolean seq = isTeam1 ? score.isTeam1CanSequence() : score.isTeam2CanSequence();
        boolean climb = isTeam1 ? score.isTeam1L2Climb() : score.isTeam2L2Climb();
        tr.addMatchResult(adjAuto, adjTeleop, seq, climb, hits, shots, penComm, penRec);
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