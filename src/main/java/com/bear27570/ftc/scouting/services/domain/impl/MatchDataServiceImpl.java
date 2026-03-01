package com.bear27570.ftc.scouting.services.domain.impl;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import com.bear27570.ftc.scouting.repository.ScoreRepository;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;

import java.util.List;

public class MatchDataServiceImpl implements MatchDataService {

    private final ScoreRepository scoreRepository;
    private final PenaltyRepository penaltyRepository;

    public MatchDataServiceImpl(ScoreRepository scoreRepository, PenaltyRepository penaltyRepository) {
        this.scoreRepository = scoreRepository;
        this.penaltyRepository = penaltyRepository;
    }

    @Override
    public void submitScore(String competitionName, ScoreEntry entry) {
        if (entry.getId() > 0) {
            scoreRepository.update(entry);
        } else {
            scoreRepository.save(competitionName, entry);
        }
    }

    @Override
    public void deleteScore(int id) {
        scoreRepository.delete(id);
    }

    @Override
    public void submitPenalty(String competitionName, PenaltyEntry entry) {
        penaltyRepository.savePenaltyEntry(competitionName, entry);
    }

    @Override
    public List<ScoreEntry> getHistory(String competitionName) {
        return scoreRepository.findByCompetition(competitionName);
    }

    @Override
    public List<ScoreEntry> getTeamHistory(String competitionName, int teamNumber) {
        return scoreRepository.findByTeam(competitionName, teamNumber);
    }
}