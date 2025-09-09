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
    @FXML private Button hostModeButton, joinModeButton; // 改为 Button
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
        // 停止所有网络活动，以防万一
        NetworkService.getInstance().stop();

        hostPane.setVisible(true); hostPane.setManaged(true);
        joinPane.setVisible(false); joinPane.setManaged(false);
        statusLabel.setText("Select a competition to host or create a new one.");

        // 更新按钮视觉样式
        hostModeButton.setStyle("-fx-background-color: #007BFF;"); // 选中样式
        joinModeButton.setStyle(""); // 恢复默认样式
    }

    @FXML
    private void selectJoinMode() {
        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(true); joinPane.setManaged(true);
        statusLabel.setText("Searching for competitions on the network...");

        // 更新按钮视觉样式
        joinModeButton.setStyle("-fx-background-color: #007BFF;"); // 选中样式
        hostModeButton.setStyle(""); // 恢复默认样式

        // 开始服务发现
        startDiscovery();
    }

    private void startDiscovery() {
        discoveredCompetitions.clear();
        discoveredCompetitionsListView.setItems(discoveredCompetitions);
        NetworkService.getInstance().startDiscovery(discoveredCompetitions); // 使用单例
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
        mainApp.showScoringView(selected, currentUsername, true); // true for isHost
    }

    @FXML
    private void handleJoinButton() throws IOException {
        Competition selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a competition from the list to join.");
            return;
        }
        // 我们不再需要在这里 stop，因为 connectToHost 内部会处理
        mainApp.showScoringView(selected, currentUsername, false);
    }

    @FXML
    private void handleLogout() throws IOException {
        NetworkService.getInstance().stop(); // 使用单例
        mainApp.showLoginView();
    }
}