// File: MatchDataService.java
package com.bear27570.ftc.scouting.services.domain;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import java.util.List;

public interface MatchDataService {
    void submitScore(String competitionName, ScoreEntry entry);
    void deleteScore(int id);
    void submitPenalty(String competitionName, PenaltyEntry entry);
    List<ScoreEntry> getHistory(String competitionName);
    List<ScoreEntry> getTeamHistory(String competitionName, int teamNumber);
    void syncWithHostData(String competitionName, List<ScoreEntry> hostData);
    List<ScoreEntry> getPendingExports(String competitionName);
    void markAsExported(List<Integer> ids);
}