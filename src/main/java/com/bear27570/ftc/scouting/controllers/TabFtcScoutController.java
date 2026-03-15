// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabFtcScoutController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.services.network.FtcScoutApiClient;
import javafx.application.Platform;
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

        final int season;
        try {
            season = Integer.parseInt(seasonStr);
        } catch (NumberFormatException e) {
            autoFetchStatusLabel.setText("Error: Season must be a valid year.");
            return;
        }

        autoFetchStatusLabel.setText("Connecting to FTCScout...");
        autoFetchStatusLabel.setStyle("-fx-text-fill: #0A84FF;");
        boundEventNameLabel.setText("Event: Fetching...");

        ftcScoutApiClient.fetchAndSyncEventDataAsync(season, eventCode, currentCompetition.getName(), new FtcScoutApiClient.ApiCallback() {
            @Override
            public void onEventFound(String eventName, boolean hasMatches) {
                Platform.runLater(() -> boundEventNameLabel.setText("Bound Event: " + eventName));
            }

            @Override
            public void onSuccess(String eventName, int syncedMatchCount) {
                Platform.runLater(() -> {
                    mainController.updateOfficialEventName(eventName);
                    if (syncedMatchCount > 0) {
                        autoFetchStatusLabel.setText("Success! Synced penalties for " + syncedMatchCount + " matches.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #32D74B;");
                    } else {
                        autoFetchStatusLabel.setText("Event Linked. No matches found yet.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #FF9F0A;");
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    boundEventNameLabel.setText("Event: Error/Not Found");
                    autoFetchStatusLabel.setText(errorMessage);
                    autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
                });
            }
        });
    }
}