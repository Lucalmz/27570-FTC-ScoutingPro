package com.bear27570.ftc.scouting.services.domain;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import com.bear27570.ftc.scouting.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private ScoreRepository mockScoreRepo;
    private PenaltyRepository mockPenaltyRepo;
    private CompetitionRepository mockCompRepo;
    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        // 1. 初始化 Mock 对象 (使用 Mockito 伪造数据库层)
        mockScoreRepo = Mockito.mock(ScoreRepository.class);
        mockPenaltyRepo = Mockito.mock(PenaltyRepository.class);
        mockCompRepo = Mockito.mock(CompetitionRepository.class);

        // 2. 将 Mock 对象注入我们要测试的核心业务类中
        rankingService = new RankingService(mockScoreRepo, mockPenaltyRepo, mockCompRepo);
    }

    @Test
    void testCalculateRankings_WithCustomFormula() {
        // --- Arrange (准备假数据) ---
        String compName = "TestChampionship";

        // 模拟赛事表，返回包含自定义公式的赛事
        Competition comp = new Competition(compName, "AdminUser", "(auto * 2) + teleop + (seq * 10)");
        when(mockCompRepo.findByName(compName)).thenReturn(comp);

        // 模拟分数表，返回一场比赛的单人测试记录
        ScoreEntry entry = new ScoreEntry(
                ScoreEntry.Type.SINGLE, 1, "RED", 1234, 0,
                10,   // autoArtifacts
                5,    // teleopArtifacts
                true, // team1CanSequence (代入公式 seq=1)
                false, false, false, false, false, false, false,
                "", "Tester"
        );
        when(mockScoreRepo.findByCompetition(compName)).thenReturn(List.of(entry));

        // 模拟罚分表，返回空
        when(mockPenaltyRepo.getFullPenalties(compName)).thenReturn(new HashMap<>());

        // --- Act (执行业务逻辑) ---
        List<TeamRanking> rankings = rankingService.calculateRankings(compName);

        // --- Assert (验证排名和分数的计算是否正确) ---
        assertEquals(1, rankings.size(), "排行榜应该只有一个队伍");
        TeamRanking tr = rankings.get(0);

        assertEquals(1234, tr.getTeamNumber());
        assertEquals(1, tr.getMatchesPlayed());

        // 验证公式是否准确执行：(10 * 2) + 5 + (1 * 10) = 20 + 5 + 10 = 35.0
        assertEquals(35.0, tr.getRating(), 0.01, "评分公式计算结果不匹配");

        // 验证场均自动阶段得分是否正确读取
        assertEquals(10.0, tr.getAvgAutoArtifacts());
    }

    @Test
    void testCalculateRankings_BrokenRobotIgnored() {
        // --- Arrange ---
        String compName = "BrokenRobotComp";
        when(mockCompRepo.findByName(compName)).thenReturn(new Competition(compName, "Admin", "total"));

        // 标记这台机器在比赛中损坏了 (team1Broken = true)
        ScoreEntry entry = new ScoreEntry(
                ScoreEntry.Type.SINGLE, 1, "RED", 9999, 0,
                10, 5, false, false, false, false, false, false,
                true, // <-- team1Broken 设置为 true
                false, "", "Tester"
        );
        when(mockScoreRepo.findByCompetition(compName)).thenReturn(List.of(entry));
        when(mockPenaltyRepo.getFullPenalties(compName)).thenReturn(new HashMap<>());

        // --- Act ---
        List<TeamRanking> rankings = rankingService.calculateRankings(compName);

        // --- Assert ---
        // 业务逻辑规定，损坏的机器不应当被计入该场的场均数据，因此对该队伍的有效参赛场次为 0
        assertEquals(1, rankings.size());
        assertEquals(0, rankings.get(0).getMatchesPlayed(), "损坏的机器不应计入总场次");
    }
}