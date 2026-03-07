package com.bear27570.ftc.scouting.repository;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import java.util.Map;

public interface PenaltyRepository {
    void savePenaltyEntry(String competitionName, PenaltyEntry entry);
    Map<Integer, FullPenaltyRow> getFullPenalties(String competitionName);

    class FullPenaltyRow {
        public int rMaj, rMin, bMaj, bMin;
        // ★ 新增：红蓝官方总分
        public int rScore, bScore;

        public FullPenaltyRow(int rMaj, int rMin, int bMaj, int bMin, int rScore, int bScore) {
            this.rMaj = rMaj;
            this.rMin = rMin;
            this.bMaj = bMaj;
            this.bMin = bMin;
            this.rScore = rScore;
            this.bScore = bScore;
        }
    }
}