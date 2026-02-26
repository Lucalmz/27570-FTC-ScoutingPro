package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.services.DatabaseService;

import java.util.List;

/**
 * 默认实现：在生产环境中，网络层收到数据后，调用真实的 DatabaseService。
 */
public class DefaultNetworkDataHandler implements NetworkDataHandler {

    @Override
    public void addPendingMembership(String username, String competitionName) {
        DatabaseService.addMembership(username, competitionName, Membership.Status.PENDING);
    }

    @Override
    public boolean isUserApprovedOrCreator(String username, String competitionName) {
        Membership.Status stat = DatabaseService.getMembershipStatus(username, competitionName);
        return stat == Membership.Status.APPROVED || stat == Membership.Status.CREATOR;
    }

    @Override
    public List<ScoreEntry> getScores(String competitionName) {
        return DatabaseService.getScoresForCompetition(competitionName);
    }

    @Override
    public List<TeamRanking> getRankings(String competitionName) {
        return DatabaseService.calculateTeamRankings(competitionName);
    }
}