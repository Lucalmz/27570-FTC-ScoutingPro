package com.bear27570.ftc.scouting.repository.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import java.util.List;

public interface MembershipDao {
    @SqlQuery("SELECT status FROM memberships WHERE username = :username AND competitionName = :compName")
    String getStatus(@Bind("username") String username, @Bind("compName") String compName);

    @SqlUpdate("INSERT INTO memberships(username, competitionName, status) VALUES(:username, :compName, :status)")
    void insert(@Bind("username") String username, @Bind("compName") String compName, @Bind("status") String status);

    @SqlUpdate("UPDATE memberships SET status = :status WHERE username = :username AND competitionName = :compName")
    int updateStatus(@Bind("username") String username, @Bind("compName") String compName, @Bind("status") String status);

    @SqlUpdate("DELETE FROM memberships WHERE username = :username AND competitionName = :compName")
    void delete(@Bind("username") String username, @Bind("compName") String compName);

    @SqlQuery("SELECT username FROM memberships WHERE competitionName = :compName AND status = :status")
    List<String> findUsersByStatus(@Bind("compName") String compName, @Bind("status") String status);
}