package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.dao.CompetitionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CompetitionRepositoryJdbiImpl implements CompetitionRepository {
    private static final Logger log = LoggerFactory.getLogger(CompetitionRepositoryJdbiImpl.class);

    private CompetitionDao dao() { return DatabaseManager.getJdbi().onDemand(CompetitionDao.class); }

    @Override
    public List<Competition> findAll() { return dao().findAll(); }

    @Override
    public Competition findByName(String name) { return dao().findByName(name); }

    @Override
    public void updateFormula(String compName, String newFormula) { dao().updateFormula(compName, newFormula); }

    @Override
    public boolean create(String name, String creator, String formula) {
        try {
            dao().insert(name, creator, formula, 0, "", "", "");
            return true;
        } catch (Exception e) {
            log.error("Failed to create competition: {}", name, e);
            return false;
        }
    }

    @Override
    public void updateBannedTeams(String compName, String bannedTeams) {
        dao().updateBannedTeams(compName, bannedTeams);
    }

    @Override
    public void updateEventInfo(String compName, int season, String code, String officialName) {
        dao().updateEventInfo(compName, season, code, officialName);
    }

    @Override
    public void ensureLocalCompetitionSync(Competition comp) {
        if (dao().checkExists(comp.getName()) == 0) {
            String creator = comp.getCreatorUsername() != null ? comp.getCreatorUsername() : "HOST_SYNC";
            String formula = comp.getRatingFormula() != null ? comp.getRatingFormula() : "total";
            String banned = comp.getBannedTeams() != null ? comp.getBannedTeams() : "";
            try {
                dao().insert(comp.getName(), creator, formula, comp.getEventSeason(), comp.getEventCode(), comp.getOfficialEventName(), banned);
            } catch (Exception e) {
                log.warn("Failed to sync local competition (might already exist): {}", comp.getName());
            }
        } else {
            updateEventInfo(comp.getName(), comp.getEventSeason(), comp.getEventCode(), comp.getOfficialEventName());
            // 同步主机传来的黑名单
            updateBannedTeams(comp.getName(), comp.getBannedTeams());
        }
    }

    @Override
    public boolean deleteByName(String name) {
        try {
            // 企业级安全级联删除，使用闭包控制事务
            DatabaseManager.getJdbi().useTransaction(handle -> {
                handle.execute("DELETE FROM scores WHERE competitionName = ?", name);
                handle.execute("DELETE FROM penalties WHERE competitionName = ?", name);
                handle.execute("DELETE FROM memberships WHERE competitionName = ?", name);
                handle.execute("DELETE FROM competitions WHERE name = ?", name);
            });
            return true;
        } catch (Exception e) {
            log.error("Failed to delete competition", e);
            return false;
        }
    }
}