package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.services.DatabaseService;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

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
        refreshMyCompetitionsList();
    }

    @FXML
    private void selectHostMode() {
        NetworkService.getInstance().stop();
        hostPane.setVisible(true); hostPane.setManaged(true);
        joinPane.setVisible(false); joinPane.setManaged(false);
        statusLabel.setText("Select a competition to host or create a new one.");
        hostModeButton.setStyle("-fx-background-color: #007BFF;");
        joinModeButton.setStyle("");
    }

    @FXML
    private void selectJoinMode() {
        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(true); joinPane.setManaged(true);
        statusLabel.setText("Searching for competitions on the network...");
        joinModeButton.setStyle("-fx-background-color: #007BFF;");
        hostModeButton.setStyle("");
        startDiscovery();
    }

    private void startDiscovery() {
        discoveredCompetitions.clear();
        discoveredCompetitionsListView.setItems(discoveredCompetitions);
        NetworkService.getInstance().startDiscovery(discoveredCompetitions);
    }

    private void refreshMyCompetitionsList() {
        myCompetitionsListView.setItems(FXCollections.observableArrayList(
                DatabaseService.getAllCompetitions().stream()
                        .filter(c -> c.getCreatorUsername().equals(currentUsername))
                        .collect(Collectors.toList())
        ));
    }

    @FXML
    private void handleCreateButton() {
        String newName = newCompetitionField.getText();
        if (newName.isBlank()) {
            statusLabel.setText("Competition name cannot be empty.");
            return;
        }
        if (DatabaseService.createCompetition(newName, currentUsername)) {
            statusLabel.setText("Competition '" + newName + "' created!");
            newCompetitionField.clear();
            refreshMyCompetitionsList();
        } else {
            statusLabel.setText("A competition with that name already exists.");
        }
    }

    @FXML
    private void handleHostButton() throws IOException {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select one of your competitions to host.");
            return;
        }
        mainApp.showScoringView(selected, currentUsername, true);
    }

    // --- 新增：处理管理成员按钮点击 ---
    @FXML
    private void handleManageMembers() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a competition to manage.");
            return;
        }
        try {
            // 调用 MainApplication 中的方法打开弹窗
            mainApp.showCoordinatorView(selected);
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error opening manager: " + e.getMessage());
        }
    }
    // -------------------------------

    @FXML
    private void handleJoinButton() throws IOException {
        Competition selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a competition from the list to join.");
            return;
        }
        mainApp.showScoringView(selected, currentUsername, false);
    }

    @FXML
    private void handleLogout() throws IOException {
        NetworkService.getInstance().stop();
        mainApp.showLoginView();
    }
}