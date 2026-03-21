package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.ScoreRepository;
import com.bear27570.ftc.scouting.repository.dao.ScoreDao;

import java.util.List;

/**
 * 现代企业级实现：使用 JDBI 彻底接管数据持久层
 */
public class ScoreRepositoryJdbiImpl implements ScoreRepository {

    private ScoreDao getDao() {
        return DatabaseManager.getJdbi().onDemand(ScoreDao.class);
    }

    @Override
    public void save(String competitionName, ScoreEntry entry) {
        try {
            int generatedId = getDao().insert(entry, competitionName);
            entry.setId(generatedId);
        } catch (Exception e) {
            System.err.println("[DAO Error] Failed to save score: " + e.getMessage());
        }
    }

    @Override
    public void update(ScoreEntry entry) {
        getDao().update(entry);
    }

    @Override
    public void delete(int id) {
        getDao().delete(id);
    }

    @Override
    public List<ScoreEntry> findByCompetition(String competitionName) {
        return getDao().findByCompetition(competitionName);
    }

    @Override
    public List<ScoreEntry> findByTeam(String competitionName, int teamNumber) {
        return getDao().findByTeam(competitionName, teamNumber);
    }

    @Override
    public List<ScoreEntry> findPendingExports(String competitionName) {
        return getDao().findPendingExports(competitionName);
    }

    @Override
    public void updateStatuses(List<Integer> ids, ScoreEntry.SyncStatus status) {
        if (ids == null || ids.isEmpty()) return;
        getDao().updateStatuses(ids, status);
    }

    @Override
    public void syncWithHostData(String competitionName, List<ScoreEntry> hostData) {
        // 利用 JDBI 的声明式事务闭包，失败自动回滚，告别繁琐的 connection.commit()
        DatabaseManager.getJdbi().useTransaction(handle -> {
            ScoreDao dao = handle.attach(ScoreDao.class);

            // 1. 获取本地未同步数据
            List<ScoreEntry> localPending = dao.findPendingExports(competitionName);

            // 2. 清空该赛事的历史记录
            dao.deleteAllByCompetition(competitionName);

            // 3. 写入主机下发的数据
            for (ScoreEntry hs : hostData) {
                hs.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                dao.insert(hs, competitionName);
            }

            // 4. 补录本地未同步的数据 (防冲突)
            for (ScoreEntry pending : localPending) {
                boolean alreadyInHost = hostData.stream().anyMatch(h ->
                        h.getSubmitter().equals(pending.getSubmitter()) &&
                                h.getSubmissionTime().equals(pending.getSubmissionTime())
                );
                if (!alreadyInHost) {
                    dao.insert(pending, competitionName);
                }
            }
        });
    }
}