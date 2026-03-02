// File: ScoreRepository.java
package com.bear27570.ftc.scouting.repository;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import java.util.List;

public interface ScoreRepository {
    void save(String competitionName, ScoreEntry entry);
    void update(ScoreEntry entry);
    void delete(int id);
    List<ScoreEntry> findByCompetition(String competitionName);
    List<ScoreEntry> findByTeam(String competitionName, int teamNumber);

    // 新增离线同步相关接口
    List<ScoreEntry> findPendingExports(String competitionName);
    void updateStatuses(List<Integer> ids, ScoreEntry.SyncStatus status);
    void syncWithHostData(String competitionName, List<ScoreEntry> hostData);
}