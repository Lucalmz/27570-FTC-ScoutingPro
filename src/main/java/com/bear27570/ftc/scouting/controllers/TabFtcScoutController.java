// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabFtcScoutController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.services.network.FtcScoutApiClient;
import com.bear27570.ftc.scouting.utils.FxThread;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class TabFtcScoutController {

    @FXML private TextField ftcScoutSeasonField;
    @FXML private TextField ftcScoutEventField;
    @FXML private Label boundEventNameLabel;
    @FXML private Label autoFetchStatusLabel;

    private MainController mainController;
    private FtcScoutApiClient ftcScoutApiClient;
    private Competition currentCompetition;
    private boolean isHost;

    public void setDependencies(MainController mainController, FtcScoutApiClient apiClient, Competition competition, boolean isHost) {
        this.mainController = mainController;
        this.ftcScoutApiClient = apiClient;
        this.currentCompetition = competition;
        this.isHost = isHost;

        if (currentCompetition.getOfficialEventName() != null && !currentCompetition.getOfficialEventName().isEmpty()) {
            ftcScoutSeasonField.setText(String.valueOf(currentCompetition.getEventSeason()));
            ftcScoutEventField.setText(currentCompetition.getEventCode());
            boundEventNameLabel.setText("Bound Event: " + currentCompetition.getOfficialEventName());
        }
    }

    @FXML
    private void handleBindAndFetch() {
        if (!isHost) {
            autoFetchStatusLabel.setText("Error: Only Host can sync with FTCScout.");
            autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
            return;
        }

        String seasonStr = ftcScoutSeasonField.getText().trim();
        String eventCode = ftcScoutEventField.getText().trim();
        if (seasonStr.isEmpty() || eventCode.isEmpty()) {
            autoFetchStatusLabel.setText("Please enter Season (Int) and Event Code.");
            return;
        }

        int season;
        try { season = Integer.parseInt(seasonStr); }
        catch (NumberFormatException e) { autoFetchStatusLabel.setText("Error: Season must be a valid year."); return; }

        autoFetchStatusLabel.setText("Connecting to FTCScout...");
        autoFetchStatusLabel.setStyle("-fx-text-fill: #0A84FF;");
        boundEventNameLabel.setText("Event: Fetching...");

        // 💥 链式调用
        ftcScoutApiClient.fetchAndSyncEventDataAsync(season, eventCode, currentCompetition.getName(),
                        progress -> FxThread.run(() -> boundEventNameLabel.setText("Bound Event: " + progress.name())))
                .thenAccept(syncedCount -> FxThread.run(() -> {
                    // UI 更新
                    if (syncedCount > 0) {
                        autoFetchStatusLabel.setText("Success! Synced penalties for " + syncedCount + " matches.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #32D74B;");
                    } else {
                        autoFetchStatusLabel.setText("Event Linked. No matches found yet.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #FF9F0A;");
                    }
                    // 获取到名字后全局更新
                    String officialName = boundEventNameLabel.getText().replace("Bound Event: ", "");
                    mainController.updateOfficialEventName(officialName);
                }))
                .exceptionally(ex -> {
                    FxThread.run(() -> {
                        boundEventNameLabel.setText("Event: Error/Not Found");
                        autoFetchStatusLabel.setText(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
                    });
                    return null;
                });
    }
}