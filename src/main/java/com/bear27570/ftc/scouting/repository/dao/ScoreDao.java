package com.bear27570.ftc.scouting.repository.dao;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

@RegisterBeanMapper(ScoreEntry.class) // 魔法：自动将 ResultSet 映射为 ScoreEntry 对象
public interface ScoreDao {

    @SqlUpdate("INSERT INTO scores (competitionName, scoreType, matchNumber, alliance, team1, team2, " +
            "team1AutoScore, team2AutoScore, team1AutoProj, team2AutoProj, team1AutoRow, team2AutoRow, " +
            "autoArtifacts, teleopArtifacts, team1CanSequence, team2CanSequence, team1L2Climb, team2L2Climb, " +
            "team1Ignored, team2Ignored, team1Broken, team2Broken, totalScore, clickLocations, submitter, submissionTime, syncStatus) " +
            "VALUES (:compName, :scoreType, :matchNumber, :alliance, :team1, :team2, " +
            ":team1AutoScore, :team2AutoScore, :team1AutoProj, :team2AutoProj, :team1AutoRow, :team2AutoRow, " +
            ":autoArtifacts, :teleopArtifacts, :team1CanSequence, :team2CanSequence, :team1L2Climb, :team2L2Climb, " +
            ":team1Ignored, :team2Ignored, :team1Broken, :team2Broken, :totalScore, :clickLocations, :submitter, :submissionTime, :syncStatus)")
    @GetGeneratedKeys("id")
    int insert(@BindBean ScoreEntry entry, @Bind("compName") String compName);

    @SqlUpdate("UPDATE scores SET matchNumber=:matchNumber, alliance=:alliance, team1=:team1, team2=:team2, " +
            "team1AutoScore=:team1AutoScore, team2AutoScore=:team2AutoScore, team1AutoProj=:team1AutoProj, team2AutoProj=:team2AutoProj, " +
            "team1AutoRow=:team1AutoRow, team2AutoRow=:team2AutoRow, autoArtifacts=:autoArtifacts, teleopArtifacts=:teleopArtifacts, " +
            "team1CanSequence=:team1CanSequence, team2CanSequence=:team2CanSequence, team1L2Climb=:team1L2Climb, team2L2Climb=:team2L2Climb, " +
            "team1Ignored=:team1Ignored, team2Ignored=:team2Ignored, team1Broken=:team1Broken, team2Broken=:team2Broken, " +
            "totalScore=:totalScore, clickLocations=:clickLocations, syncStatus=:syncStatus WHERE id=:id")
    void update(@BindBean ScoreEntry entry);

    @SqlUpdate("DELETE FROM scores WHERE id = :id")
    void delete(@Bind("id") int id);

    @SqlUpdate("DELETE FROM scores WHERE competitionName = :compName")
    void deleteAllByCompetition(@Bind("compName") String compName);

    @SqlQuery("SELECT * FROM scores WHERE competitionName = :compName ORDER BY matchNumber DESC, id DESC")
    List<ScoreEntry> findByCompetition(@Bind("compName") String compName);

    @SqlQuery("SELECT * FROM scores WHERE competitionName = :compName AND (team1 = :teamNum OR (scoreType = 'ALLIANCE' AND team2 = :teamNum)) ORDER BY matchNumber DESC, id DESC")
    List<ScoreEntry> findByTeam(@Bind("compName") String compName, @Bind("teamNum") int teamNum);

    @SqlQuery("SELECT * FROM scores WHERE competitionName = :compName AND syncStatus IN ('UNSYNCED', 'EXPORTED')")
    List<ScoreEntry> findPendingExports(@Bind("compName") String compName);
    @SqlQuery("SELECT * FROM scores WHERE competitionName = :compName AND matchNumber = :matchNum AND alliance = :alliance")
    ScoreEntry findByMatchAndAlliance(@Bind("compName") String compName, @Bind("matchNum") int matchNum, @Bind("alliance") String alliance);

    // 优雅的批量更新，无需手动拼接问号
    @SqlUpdate("UPDATE scores SET syncStatus = :status WHERE id IN (<ids>)")
    void updateStatuses(@BindList("ids") List<Integer> ids, @Bind("status") ScoreEntry.SyncStatus status);
}