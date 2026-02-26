package com.bear27570.ftc.scouting.repository;

import com.bear27570.ftc.scouting.models.Competition;
import java.util.List;

public interface CompetitionRepository {
    List<Competition> findAll();
    Competition findByName(String name);
    void updateFormula(String competitionName, String newFormula);
    boolean create(String name, String creatorUsername, String ratingFormula);
}