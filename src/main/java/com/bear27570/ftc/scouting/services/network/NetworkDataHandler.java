package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;

import java.util.List;

/**
 * 网络层与数据层交互的契约。
 * 网络模块不需要知道底层是 MySQL、H2 还是假数据，只要能存取就行。
 */
public interface NetworkDataHandler {
    void addPendingMembership(String username, String competitionName);
    boolean isUserApprovedOrCreator(String username, String competitionName);
    void ensureUserExists(String username);

    List<ScoreEntry> getScores(String competitionName);
    List<TeamRanking> getRankings(String competitionName);
}