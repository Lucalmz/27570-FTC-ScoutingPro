// File: DefaultNetworkDataHandler.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;

import java.util.List;

public class DefaultNetworkDataHandler implements NetworkDataHandler {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository; // ★ 建议改成 final
    private final MatchDataService matchDataService;
    private final RankingService rankingService;

    // ★ 修改构造函数，添加 UserRepository 参数
    public DefaultNetworkDataHandler(MembershipRepository membershipRepository,
                                     UserRepository userRepository, // <--- 新增参数
                                     MatchDataService matchDataService,
                                     RankingService rankingService) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository; // <--- ★★★ 必须在这里赋值！之前漏了这行 ★★★
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
        // 现在 userRepository 不会是 null 了
        if (userRepository != null) {
            userRepository.ensureUserExists(username);
        } else {
            System.err.println("CRITICAL ERROR: UserRepository is null in DataHandler!");
        }
    }

    @Override
    public List<TeamRanking> getRankings(String competitionName) {
        return rankingService.calculateRankings(competitionName);
    }
}