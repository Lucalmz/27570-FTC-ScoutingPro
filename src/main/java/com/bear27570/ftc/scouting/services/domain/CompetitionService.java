package com.bear27570.ftc.scouting.services.domain;

import com.bear27570.ftc.scouting.models.Competition;
import java.util.List;

/**
 * 赛事相关的纯业务逻辑接口
 */
public interface CompetitionService {
    boolean createCompetition(String name, String creatorUsername);
    List<Competition> getCompetitionsCreatedByUser(String username);
}