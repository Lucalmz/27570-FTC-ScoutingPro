package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.CompetitionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
    private CompetitionService competitionService; // 核心解耦：依赖接口

    private ObservableList<Competition> discoveredCompetitions = FXCollections.observableArrayList();

    /**
     * 依赖注入点：加载 FXML 后调用
     */
    public void setDependencies(MainApplication mainApp, String username, CompetitionService competitionService) {
        this.mainApp = mainApp;
        this.currentUsername = username;
        this.competitionService = competitionService;

        welcomeLabel.setText("Welcome, " + username + "!");
        discoveredCompetitionsListView.setItems(discoveredCompetitions);

        refreshMyCompetitionsList();
    }

    @FXML
    private void selectHostMode() {
        hostPane.setVisible(true); hostPane.setManaged(true);
        joinPane.setVisible(false); joinPane.setManaged(false);
        statusLabel.setText("Host Mode: Select a competition to start.");
        hostModeButton.setStyle("-fx-background-color: #007BFF;");
        joinModeButton.setStyle("");
    }

    @FXML
    private void selectJoinMode() {
        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(true); joinPane.setManaged(true);
        statusLabel.setText("Searching for local competitions...");
        joinModeButton.setStyle("-fx-background-color: #007BFF;");
        hostModeButton.setStyle("");

        discoveredCompetitions.clear();
        NetworkService.getInstance().startDiscovery(discoveredCompetitions);
    }

    private void refreshMyCompetitionsList() {
        // 改造后：直接调用业务逻辑层的专门方法，不再从全表里手动过滤
        myCompetitionsListView.setItems(FXCollections.observableArrayList(
                competitionService.getCompetitionsCreatedByUser(currentUsername)
        ));
    }

    @FXML
    private void handleCreateButton() {
        String newName = newCompetitionField.getText();

        // 改造后：调用业务层方法，将复杂的建表和权限绑定逻辑交由后端处理
        if (competitionService.createCompetition(newName, currentUsername)) {
            newCompetitionField.clear();
            refreshMyCompetitionsList();
            statusLabel.setText("Created: " + newName);
        } else {
            statusLabel.setText("Error: Name is empty or already exists.");
        }
    }

    @FXML
    private void handleHostButton() throws IOException {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a competition first!");
            return;
        }
        mainApp.showScoringView(selected, currentUsername, true);
    }

    @FXML
    private void handleJoinButton() throws IOException {
        Competition selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a competition from the list.");
            return;
        }

        statusLabel.setText("Connecting to " + selected.getName() + "...");
        NetworkService.getInstance().connectToHost(selected.getHostAddress(), currentUsername, (packet) -> {
            if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE) {
                if (packet.isApproved()) {
                    try {
                        mainApp.showScoringView(selected, currentUsername, false);
                    } catch (IOException e) { e.printStackTrace(); }
                } else {
                    statusLabel.setText("Join request denied.");
                }
            }
        });
    }

    @FXML
    private void handleAllianceAnalysis() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            statusLabel.setText("Select a competition to analyze.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/AllianceAnalysisView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Analysis - " + selected.getName());
            stage.setScene(new Scene(loader.load()));
            AllianceAnalysisController controller = loader.getController();
            controller.setDialogStage(stage, selected);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    private void handleManageMembers() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
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