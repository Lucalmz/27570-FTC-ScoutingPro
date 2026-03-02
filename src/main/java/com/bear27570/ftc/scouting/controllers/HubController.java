// File: HubController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.CompetitionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class HubController {

    @FXML private Label welcomeLabel, statusLabel;
    @FXML private Button hostModeButton, joinModeButton;
    @FXML private VBox hostPane, joinPane;
    @FXML private ListView<Competition> myCompetitionsListView;
    @FXML private ListView<Competition> discoveredCompetitionsListView;
    @FXML private TextField newCompetitionField;

    private MainApplication mainApp;
    private String currentUsername;
    private CompetitionService competitionService;

    private ObservableList<Competition> discoveredCompetitions = FXCollections.observableArrayList();

    public void setDependencies(MainApplication mainApp, String username, CompetitionService competitionService) {
        this.mainApp = mainApp;
        this.currentUsername = username;
        this.competitionService = competitionService;

        welcomeLabel.setText("Welcome, " + username + "!");
        discoveredCompetitionsListView.setItems(discoveredCompetitions);

        // 初始状态优化：隐藏面板
        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(false); joinPane.setManaged(false);

        refreshMyCompetitionsList();
    }

    @FXML
    private void selectHostMode() {
        hostPane.setVisible(true); hostPane.setManaged(true);
        joinPane.setVisible(false); joinPane.setManaged(false);
        statusLabel.setStyle("-fx-text-fill: #a6adc8;");
        statusLabel.setText("Host Mode: Select a competition to start.");
        hostModeButton.setStyle("-fx-background-color: rgba(212, 175, 55, 0.25) !important; -fx-border-color: #FFDF73 !important; -fx-text-fill: #FFDF73 !important; -fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.65), 15, 0.5, 0, 0) !important;");
        joinModeButton.setStyle("");
    }

    @FXML
    private void selectJoinMode() {
        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(true); joinPane.setManaged(true);
        statusLabel.setStyle("-fx-text-fill: #f9e2af;");
        statusLabel.setText("Searching for local competitions via UDP...");
        joinModeButton.setStyle("-fx-background-color: rgba(212, 175, 55, 0.25) !important; -fx-border-color: #FFDF73 !important; -fx-text-fill: #FFDF73 !important; -fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.65), 15, 0.5, 0, 0) !important;");
        hostModeButton.setStyle("");
        discoveredCompetitions.clear();
        NetworkService.getInstance().startDiscovery(discoveredCompetitions);
    }

    private void refreshMyCompetitionsList() {
        myCompetitionsListView.setItems(FXCollections.observableArrayList(
                competitionService.getCompetitionsCreatedByUser(currentUsername)
        ));
    }

    @FXML
    private void handleCreateButton() {
        String newName = newCompetitionField.getText();

        if (competitionService.createCompetition(newName, currentUsername)) {
            newCompetitionField.clear();
            refreshMyCompetitionsList();
            statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
            statusLabel.setText("Created successfully: " + newName);
        } else {
            statusLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
            statusLabel.setText("Error: Name is empty or already exists.");
        }
    }

    @FXML
    private void handleHostButton() throws IOException {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Please select a competition first!");
            return;
        }
        mainApp.showScoringView(selected, currentUsername, true);
    }

    @FXML
    private void handleJoinButton() throws IOException {
        Competition selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Please select a competition from the discovery list.");
            return;
        }

        statusLabel.setStyle("-fx-text-fill: #89dceb;");
        statusLabel.setText("Connecting to " + selected.getName() + "...");
        NetworkService.getInstance().connectToHost(selected.getHostAddress(), currentUsername, (packet) -> {
            if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE) {
                if (packet.isApproved()) {
                    try {
                        mainApp.showScoringView(selected, currentUsername, false);
                    } catch (IOException e) { e.printStackTrace(); }
                } else {
                    statusLabel.setStyle("-fx-text-fill: #f38ba8;");
                    statusLabel.setText("Join request denied by host.");
                }
            }
        });
    }

    @FXML
    private void handleAllianceAnalysis() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null && discoveredCompetitionsListView.isVisible()) {
            selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        }

        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Select a competition to analyze.");
            return;
        }

        try {
            // 核心修复：委托给 MainApplication 去进行实例化依赖注入
            mainApp.showAllianceAnalysisView(selected);
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Failed to open analysis window.");
        }
    }

    @FXML
    private void handleManageMembers() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Select your competition first.");
            return;
        }
        try {
            mainApp.showCoordinatorView(selected);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    private void handleLogout() throws IOException {
        NetworkService.getInstance().stop();
        mainApp.showLoginView();
    }
}