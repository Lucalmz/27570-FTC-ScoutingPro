package com.bear27570.ftc.scouting.repository;

import com.bear27570.ftc.scouting.models.Membership;
import java.util.List;

public interface MembershipRepository {
    void addMembership(String username, String competitionName, Membership.Status status);
    Membership.Status getMembershipStatus(String username, String competitionName);
    void removeMembership(String username, String competitionName);
    List<String> getMembersByStatus(String competitionName, Membership.Status status);
    void updateMembershipStatus(String username, String competitionName, Membership.Status newStatus);
}