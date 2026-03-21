package com.bear27570.ftc.scouting.repository.impl;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import java.util.HashMap;
import java.util.Map;

public class PenaltyRepositoryJdbiImpl implements PenaltyRepository {

    @Override
    public void savePenaltyEntry(String compName, PenaltyEntry entry) {
        DatabaseManager.getJdbi().useTransaction(handle -> {
            boolean exists = handle.createQuery("SELECT 1 FROM penalties WHERE competitionName=? AND matchNumber=?")
                    .bind(0, compName).bind(1, entry.getMatchNumber())
                    .mapTo(Boolean.class).findFirst().orElse(false);

            if (!exists) {
                handle.execute("INSERT INTO penalties (competitionName, matchNumber, redMajor, redMinor, blueMajor, blueMinor, redScore, blueScore) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        compName, entry.getMatchNumber(),
                        entry.getAlliance().equalsIgnoreCase("RED") ? entry.getMajorCount() : 0,
                        entry.getAlliance().equalsIgnoreCase("RED") ? entry.getMinorCount() : 0,
                        entry.getAlliance().equalsIgnoreCase("BLUE") ? entry.getMajorCount() : 0,
                        entry.getAlliance().equalsIgnoreCase("BLUE") ? entry.getMinorCount() : 0,
                        entry.getAlliance().equalsIgnoreCase("RED") ? entry.getOfficialScore() : 0,
                        entry.getAlliance().equalsIgnoreCase("BLUE") ? entry.getOfficialScore() : 0);
            } else {
                if (entry.getAlliance().equalsIgnoreCase("RED")) {
                    handle.execute("UPDATE penalties SET redMajor=?, redMinor=?, redScore=? WHERE competitionName=? AND matchNumber=?",
                            entry.getMajorCount(), entry.getMinorCount(), entry.getOfficialScore(), compName, entry.getMatchNumber());
                } else {
                    handle.execute("UPDATE penalties SET blueMajor=?, blueMinor=?, blueScore=? WHERE competitionName=? AND matchNumber=?",
                            entry.getMajorCount(), entry.getMinorCount(), entry.getOfficialScore(), compName, entry.getMatchNumber());
                }
            }
        });
    }

    @Override
    public Map<Integer, FullPenaltyRow> getFullPenalties(String compName) {
        return DatabaseManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT * FROM penalties WHERE competitionName = :compName")
                        .bind("compName", compName)
                        .reduceResultSet(new HashMap<Integer, FullPenaltyRow>(), (acc, rs, ctx) -> {
                            acc.put(rs.getInt("matchNumber"), new FullPenaltyRow(
                                    rs.getInt("redMajor"), rs.getInt("redMinor"),
                                    rs.getInt("blueMajor"), rs.getInt("blueMinor"),
                                    rs.getInt("redScore"), rs.getInt("blueScore")
                            ));
                            return acc;
                        })
        );
    }
}