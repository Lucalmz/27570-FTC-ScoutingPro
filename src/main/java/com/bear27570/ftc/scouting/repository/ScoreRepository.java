package com.bear27570.ftc.scouting.repository;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import java.util.List;

public interface ScoreRepository {
    void save(String competitionName, ScoreEntry entry);
    void update(ScoreEntry entry);
    void delete(int id);
    List<ScoreEntry> findByCompetition(String competitionName);
    List<ScoreEntry> findByTeam(String competitionName, int teamNumber);
}