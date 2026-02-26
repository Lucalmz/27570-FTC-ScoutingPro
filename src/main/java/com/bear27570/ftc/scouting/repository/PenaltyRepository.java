package com.bear27570.ftc.scouting.repository;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import java.util.Map;

public interface PenaltyRepository {
    void savePenaltyEntry(String competitionName, PenaltyEntry entry);
    Map<Integer, FullPenaltyRow> getFullPenalties(String competitionName);

    class FullPenaltyRow {
        public int rMaj, rMin, bMaj, bMin;
        public FullPenaltyRow(int rMaj, int rMin, int bMaj, int bMin) {
            this.rMaj = rMaj;
            this.rMin = rMin;
            this.bMaj = bMaj;
            this.bMin = bMin;
        }
    }
}