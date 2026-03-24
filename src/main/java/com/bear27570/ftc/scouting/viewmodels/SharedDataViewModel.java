// File: src/main/java/com/bear27570/ftc/scouting/viewmodels/SharedDataViewModel.java
package com.bear27570.ftc.scouting.viewmodels;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.utils.FxThread;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SharedDataViewModel {
    private final ObservableList<ScoreEntry> historyList = FXCollections.observableArrayList();
    private final ObservableList<TeamRanking> rankingsList = FXCollections.observableArrayList();
    private Map<String, Double> reliabilities = new HashMap<>();

    public ObservableList<ScoreEntry> getHistoryList() { return historyList; }
    public ObservableList<TeamRanking> getRankingsList() { return rankingsList; }
    public Map<String, Double> getReliabilities() { return reliabilities; }

    public void updateData(List<ScoreEntry> newHistory, List<TeamRanking> newRankings, Map<String, Double> newReliabilities) {
        FxThread.run(() -> {
            this.reliabilities = newReliabilities;
            this.historyList.setAll(newHistory);
            this.rankingsList.setAll(newRankings);
        });
    }
}