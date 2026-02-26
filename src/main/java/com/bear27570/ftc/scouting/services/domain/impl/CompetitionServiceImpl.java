package com.bear27570.ftc.scouting.services.domain.impl;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.services.domain.CompetitionService;

import java.util.List;
import java.util.stream.Collectors;

public class CompetitionServiceImpl implements CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    // 构造函数注入多个数据源
    public CompetitionServiceImpl(CompetitionRepository competitionRepository,
                                  MembershipRepository membershipRepository,
                                  UserRepository userRepository) {
        this.competitionRepository = competitionRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Override
    public boolean createCompetition(String name, String creatorUsername) {
        // 业务层规则 1：名字不能为空
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // 业务层规则 2：重名校验
        if (competitionRepository.findByName(name) != null) {
            return false;
        }

        // 执行创建（默认使用 'total' 作为初始评分公式）
        boolean created = competitionRepository.create(name, creatorUsername, "total");

        // 业务层规则 3：创建成功后，自动将创建者设为该赛事的 CREATOR 成员
        if (created) {
            userRepository.ensureUserExists(creatorUsername);
            membershipRepository.addMembership(creatorUsername, name, Membership.Status.CREATOR);
        }

        return created;
    }

    @Override
    public List<Competition> getCompetitionsCreatedByUser(String username) {
        // 在业务层进行过滤，而不是在 UI 控制器里写过滤代码
        return competitionRepository.findAll().stream()
                .filter(c -> c.getCreatorUsername().equals(username))
                .collect(Collectors.toList());
    }
}