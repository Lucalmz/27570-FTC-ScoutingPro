package com.bear27570.ftc.scouting.repository.dao;

import com.bear27570.ftc.scouting.models.Competition;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import java.util.List;

@RegisterBeanMapper(Competition.class)
public interface CompetitionDao {
    @SqlQuery("SELECT * FROM competitions")
    List<Competition> findAll();

    @SqlQuery("SELECT * FROM competitions WHERE name = :name")
    Competition findByName(@Bind("name") String name);

    @SqlQuery("SELECT COUNT(*) FROM competitions WHERE name = :name")
    int checkExists(@Bind("name") String name);

    @SqlUpdate("INSERT INTO competitions(name, creatorUsername, ratingFormula, eventSeason, eventCode, officialEventName) VALUES(:name, :creator, :formula, :season, :code, :officialName)")
    void insert(@Bind("name") String name, @Bind("creator") String creator, @Bind("formula") String formula, @Bind("season") int season, @Bind("code") String code, @Bind("officialName") String officialName);

    @SqlUpdate("UPDATE competitions SET ratingFormula = :formula WHERE name = :name")
    void updateFormula(@Bind("name") String name, @Bind("formula") String formula);

    @SqlUpdate("UPDATE competitions SET eventSeason = :season, eventCode = :code, officialEventName = :officialName WHERE name = :name")
    void updateEventInfo(@Bind("name") String name, @Bind("season") int season, @Bind("code") String code, @Bind("officialName") String officialName);
}