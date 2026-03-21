package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
import com.bear27570.ftc.scouting.repository.dao.MembershipDao;
import java.util.List;

public class MembershipRepositoryJdbiImpl implements MembershipRepository {
    private MembershipDao dao() { return DatabaseManager.getJdbi().onDemand(MembershipDao.class); }

    @Override
    public void addMembership(String username, String competitionName, Membership.Status status) {
        String existingStatus = dao().getStatus(username, competitionName);
        if (existingStatus == null) {
            dao().insert(username, competitionName, status.name());
        } else if (existingStatus.equals("PENDING") && status == Membership.Status.APPROVED) {
            dao().updateStatus(username, competitionName, status.name());
        }
    }

    @Override
    public Membership.Status getMembershipStatus(String username, String competitionName) {
        String status = dao().getStatus(username, competitionName);
        return status != null ? Membership.Status.valueOf(status) : null;
    }

    @Override
    public void removeMembership(String username, String compName) { dao().delete(username, compName); }

    @Override
    public List<String> getMembersByStatus(String compName, Membership.Status status) {
        return dao().findUsersByStatus(compName, status.name());
    }

    @Override
    public void updateMembershipStatus(String username, String compName, Membership.Status newStatus) {
        dao().updateStatus(username, compName, newStatus.name());
    }
}