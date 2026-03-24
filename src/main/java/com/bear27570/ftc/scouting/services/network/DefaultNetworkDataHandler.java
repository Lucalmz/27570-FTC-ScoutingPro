// File: DefaultNetworkDataHandler.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultNetworkDataHandler implements NetworkDataHandler {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository; // ★ 新增字段
    private final MatchDataService matchDataService;
    private final RankingService rankingService;
    private static final Logger log = LoggerFactory.getLogger(DefaultNetworkDataHandler.class);
    // ★ 修改构造函数：增加 UserRepository 参数
    public DefaultNetworkDataHandler(MembershipRepository membershipRepository,
                                     UserRepository userRepository,
                                     MatchDataService matchDataService,
                                     RankingService rankingService) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository; // ★ 必须在这里赋值！
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
    public void ensureUserExists(String username) {
        if (userRepository != null) {
            userRepository.ensureUserExists(username);
        } else {
            log.error("CRITICAL: UserRepository is null in DataHandler");
        }
    }

    @Override
    public List<TeamRanking> getRankings(String competitionName) {
        return rankingService.calculateRankings(competitionName);
    }
}