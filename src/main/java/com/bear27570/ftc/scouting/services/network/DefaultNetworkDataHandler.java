// File: DefaultNetworkDataHandler.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;

import java.util.List;

/**
 * 实现网络层与数据层的解耦。利用依赖注入直接对接真实的 Service。
 */
public class DefaultNetworkDataHandler implements NetworkDataHandler {

    private final MembershipRepository membershipRepository;
    private final MatchDataService matchDataService;
    private final RankingService rankingService;

    public DefaultNetworkDataHandler(MembershipRepository membershipRepository,
                                     MatchDataService matchDataService,
                                     RankingService rankingService) {
        this.membershipRepository = membershipRepository;
        this.matchDataService = matchDataService;
        this.rankingService = rankingService;
    }

    @Override
    public void addPendingMembership(String username, String competitionName) {
        membershipRepository.addMembership(username, competitionName, Membership.Status.PENDING);
    }

    @Override
    public boolean isUserApprovedOrCreator(String username, String competitionName) {
        Membership.Status stat = membershipRepository.getMembershipStatus(username, competitionName);
        return stat == Membership.Status.APPROVED || stat == Membership.Status.CREATOR;
    }

    @Override
    public List<ScoreEntry> getScores(String competitionName) {
        return matchDataService.getHistory(competitionName);
    }

    @Override
    public List<TeamRanking> getRankings(String competitionName) {
        return rankingService.calculateRankings(competitionName);
    }
}