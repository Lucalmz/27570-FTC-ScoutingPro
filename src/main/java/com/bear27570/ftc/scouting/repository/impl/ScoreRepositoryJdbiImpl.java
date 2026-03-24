package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.ScoreRepository;
import com.bear27570.ftc.scouting.repository.dao.ScoreDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 现代企业级实现：使用 JDBI 彻底接管数据持久层
 */
public class ScoreRepositoryJdbiImpl implements ScoreRepository {
    private static final Logger log = LoggerFactory.getLogger(ScoreRepositoryJdbiImpl.class);
    private ScoreDao getDao() {
        return DatabaseManager.getJdbi().onDemand(ScoreDao.class);
    }

    @Override
    public void save(String competitionName, ScoreEntry entry) {
        try {
            int generatedId = getDao().insert(entry, competitionName);
            entry.setId(generatedId);
        } catch (Exception e) {
            log.error("[DAO Error] Failed to save score: " + e.getMessage());
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
        DatabaseManager.getJdbi().useTransaction(handle -> {
            ScoreDao dao = handle.attach(ScoreDao.class);

            List<ScoreEntry> localPending = dao.findPendingExports(competitionName);

            // 💡 架构师改造：废弃全量删除重建，改用优雅的 UPSERT 增量更新机制
            for (ScoreEntry hs : hostData) {
                hs.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);

                // 依据 FTC 业务逻辑：同一赛事、同一场次、同一联盟的数据具有唯一性
                ScoreEntry existing = dao.findByMatchAndAlliance(competitionName, hs.getMatchNumber(), hs.getAlliance());

                if (existing != null) {
                    hs.setId(existing.getId()); // 绑定本地 ID
                    dao.update(hs);             // 覆盖更新本地数据
                } else {
                    dao.insert(hs, competitionName); // 新数据直接插入
                }
            }

            // 补录本地未同步的数据 (防冲突)
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