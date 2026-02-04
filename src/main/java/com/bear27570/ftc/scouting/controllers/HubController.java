package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.services.DatabaseService;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.stream.Collectors;

public class HubController {

    @FXML private Label welcomeLabel, statusLabel;
    @FXML private Button hostModeButton, joinModeButton;
    @FXML private VBox hostPane, joinPane;
    @FXML private ListView<Competition> myCompetitionsListView;
    @FXML private ListView<Competition> discoveredCompetitionsListView;
    @FXML private TextField newCompetitionField;

    private MainApplication mainApp;
    private String currentUsername;
    private ObservableList<Competition> discoveredCompetitions = FXCollections.observableArrayList();

    public void setMainApp(MainApplication mainApp, String username) {
        this.mainApp = mainApp;
        this.currentUsername = username;
        welcomeLabel.setText("Welcome, " + username + "!");

        // 绑定列表
        discoveredCompetitionsListView.setItems(discoveredCompetitions);
        refreshMyCompetitionsList();
    }

    @FXML private void selectHostMode() {
        hostPane.setVisible(true); hostPane.setManaged(true);
        joinPane.setVisible(false); joinPane.setManaged(false);
        statusLabel.setText("Host Mode: Select a competition to start.");
        hostModeButton.setStyle("-fx-background-color: #007BFF;");
        joinModeButton.setStyle("");
    }

    @FXML private void selectJoinMode() {
        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(true); joinPane.setManaged(true);
        statusLabel.setText("Searching for local competitions...");
        joinModeButton.setStyle("-fx-background-color: #007BFF;");
        hostModeButton.setStyle("");

        // 启动搜索
        discoveredCompetitions.clear();
        NetworkService.getInstance().startDiscovery(discoveredCompetitions);
    }

    private void refreshMyCompetitionsList() {
        myCompetitionsListView.setItems(FXCollections.observableArrayList(
                DatabaseService.getAllCompetitions().stream()
                        .filter(c -> c.getCreatorUsername().equals(currentUsername))
                        .collect(Collectors.toList())
        ));
    }

    @FXML private void handleCreateButton() {
        String newName = newCompetitionField.getText();
        if (newName != null && !newName.isBlank()) {
            if (DatabaseService.createCompetition(newName, currentUsername)) {
                newCompetitionField.clear();
                refreshMyCompetitionsList();
                statusLabel.setText("Created: " + newName);
            } else {
                statusLabel.setText("Error: Name exists.");
            }
        }
    }

    @FXML private void handleHostButton() throws IOException {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a competition first!");
            return;
        }
        mainApp.showScoringView(selected, currentUsername, true);
    }

    @FXML private void handleJoinButton() throws IOException {
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

    @FXML private void handleAllianceAnalysis() {
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

    @FXML private void handleManageMembers() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            mainApp.showCoordinatorView(selected);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML private void handleLogout() throws IOException {
        NetworkService.getInstance().stop();
        mainApp.showLoginView();
    }
}
