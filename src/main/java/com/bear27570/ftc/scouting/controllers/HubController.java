package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.services.DatabaseService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.io.IOException;

public class HubController {
    @FXML private Label welcomeLabel;
    @FXML private ListView<Competition> competitionListView;
    @FXML private Button joinButton;
    @FXML private Label statusLabel;
    @FXML private TextField newCompetitionField;

    private MainApplication mainApp;
    private String currentUsername;

    public void setMainApp(MainApplication mainApp, String username) {
        this.mainApp = mainApp;
        this.currentUsername = username;
        welcomeLabel.setText("Welcome, " + username + "!");
        refreshCompetitionList();
    }

    private void refreshCompetitionList() {
        competitionListView.setItems(FXCollections.observableArrayList(DatabaseService.getAllCompetitions()));
        // Custom cell factory to add "Edit" button
        competitionListView.setCellFactory(param -> new ListCell<>() {
            private final HBox hbox = new HBox(10);
            private final Label label = new Label();
            private final Button editButton = new Button("Edit Coordinators");
            private final Region region = new Region();

            {
                HBox.setHgrow(region, Priority.ALWAYS);
                editButton.setOnAction(event -> {
                    try {
                        mainApp.showCoordinatorView(getItem());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            protected void updateItem(Competition competition, boolean empty) {
                super.updateItem(competition, empty);
                if (empty || competition == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    label.setText(competition.toString());
                    hbox.getChildren().setAll(label, region, editButton);
                    setGraphic(hbox);
                    // Show edit button only for the creator
                    editButton.setVisible(competition.getCreatorUsername().equals(currentUsername));
                }
            }
        });
    }

    @FXML
    private void handleJoinButton() throws IOException {
        Competition selected = competitionListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a competition.");
            return;
        }

        Membership.Status status = DatabaseService.getMembershipStatus(currentUsername, selected.getName());

        if (status == Membership.Status.APPROVED || status == Membership.Status.CREATOR) {
            mainApp.showScoringView(selected, currentUsername);
        } else if (status == Membership.Status.PENDING) {
            statusLabel.setText("Your application is still pending approval.");
        } else { // status is null
            DatabaseService.addMembership(currentUsername, selected.getName(), Membership.Status.PENDING);
            statusLabel.setText("Application sent! A coordinator must approve your request.");
        }
    }

    @FXML
    private void handleCreateButton() {
        String newName = newCompetitionField.getText();
        if (newName.isEmpty()) {
            statusLabel.setText("Competition name cannot be empty.");
            return;
        }
        if (DatabaseService.createCompetition(newName, currentUsername)) {
            statusLabel.setText("Competition '" + newName + "' created!");
            newCompetitionField.clear();
            refreshCompetitionList();
        } else {
            statusLabel.setText("A competition with that name already exists.");
        }
    }

    @FXML
    private void handleLogout() throws IOException {
        mainApp.showLoginView();
    }
}