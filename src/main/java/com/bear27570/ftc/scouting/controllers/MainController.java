package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.services.DatabaseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.h2.engine.Database;

import java.io.IOException;

public class MainController {

    // Common FXML
    @FXML private Label competitionNameLabel;
    @FXML private Label submitterLabel;
    @FXML private Label errorLabel;

    // Scoring Tab FXML
    @FXML private TextField matchNumberField, team1Field, team2Field, autoArtifactsField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;
    @FXML private CheckBox team1SequenceCheck, team2SequenceCheck, team1L2ClimbCheck, team2L2ClimbCheck;
    private ToggleGroup allianceToggleGroup;

    // Rankings Tab FXML
    @FXML private TableView<TeamRanking> rankingsTableView;
    @FXML private TableColumn<TeamRanking, Integer> rankTeamCol, rankMatchesCol;
    @FXML private TableColumn<TeamRanking, Double> rankAutoCol, rankTeleopCol;
    @FXML private TableColumn<TeamRanking, String> rankSequenceCol, rankL2Col;

    // History Tab FXML
    @FXML private TextField searchField;
    @FXML private TableView<ScoreEntry> historyTableView;
    @FXML private TableColumn<ScoreEntry, Integer> histMatchCol, histTotalCol;
    @FXML private TableColumn<ScoreEntry, String> histAllianceCol, histTeamsCol, histSubmitterCol, histTimeCol;

    private MainApplication mainApp;
    private Competition currentCompetition;
    private String currentUsername;
    private ObservableList<ScoreEntry> scoreHistoryList = FXCollections.observableArrayList();

    public void setMainApp(MainApplication mainApp, Competition competition, String username) {
        this.mainApp = mainApp;
        this.currentCompetition = competition;
        this.currentUsername = username;

        competitionNameLabel.setText(competition.getName());
        submitterLabel.setText("Submitter: " + username);

        setupScoringTab();
        setupRankingsTab();
        setupHistoryTab();

        refreshAllData();
    }

    private void setupScoringTab() {
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup);
        blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);
    }

    private void setupRankingsTab() {
        rankTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        rankMatchesCol.setCellValueFactory(new PropertyValueFactory<>("matchesPlayed"));
        rankAutoCol.setCellValueFactory(new PropertyValueFactory<>("avgAutoArtifacts"));
        rankTeleopCol.setCellValueFactory(new PropertyValueFactory<>("avgTeleopArtifacts"));
        rankSequenceCol.setCellValueFactory(new PropertyValueFactory<>("canSequence"));
        rankL2Col.setCellValueFactory(new PropertyValueFactory<>("l2Capable"));
    }

    private void setupHistoryTab() {
        histMatchCol.setCellValueFactory(new PropertyValueFactory<>("matchNumber"));
        histAllianceCol.setCellValueFactory(new PropertyValueFactory<>("alliance"));
        histTeamsCol.setCellValueFactory(new PropertyValueFactory<>("teams"));
        histTotalCol.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        histSubmitterCol.setCellValueFactory(new PropertyValueFactory<>("submitter"));
        histTimeCol.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));

        // Search/Filter Logic
        FilteredList<ScoreEntry> filteredData = new FilteredList<>(scoreHistoryList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(scoreEntry -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                if (String.valueOf(scoreEntry.getMatchNumber()).contains(lowerCaseFilter)) return true;
                if (String.valueOf(scoreEntry.getTeam1()).contains(lowerCaseFilter)) return true;
                if (String.valueOf(scoreEntry.getTeam2()).contains(lowerCaseFilter)) return true;
                return scoreEntry.getSubmitter().toLowerCase().contains(lowerCaseFilter);
            });
        });
        historyTableView.setItems(filteredData);
    }

    private void refreshAllData() {
        // Refresh Rankings
        rankingsTableView.setItems(FXCollections.observableArrayList(DatabaseService.calculateTeamRankings(currentCompetition.getName())));
        // Refresh History
        scoreHistoryList.setAll(DatabaseService.getScoresForCompetition(currentCompetition.getName()));
    }

    @FXML
    private void handleSubmitButtonAction() {
        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) throw new IllegalArgumentException("Please select an alliance.");

            String alliance = selectedToggle.getText().startsWith("Red") ? "RED" : "BLUE";
            ScoreEntry newEntry = new ScoreEntry(
                    Integer.parseInt(matchNumberField.getText()), alliance, Integer.parseInt(team1Field.getText()),
                    Integer.parseInt(team2Field.getText()), Integer.parseInt(autoArtifactsField.getText()),
                    Integer.parseInt(teleopArtifactsField.getText()), team1SequenceCheck.isSelected(),
                    team2SequenceCheck.isSelected(), team1L2ClimbCheck.isSelected(),
                    team2L2ClimbCheck.isSelected(), currentUsername);

            DatabaseService.saveScoreEntry(currentCompetition.getName(), newEntry);
            refreshAllData();
            errorLabel.setText("Score submitted successfully!");
        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleBackButton() throws IOException {
        mainApp.showHubView(currentUsername);
    }

    @FXML
    private void handleLogout() throws IOException {
        mainApp.showLoginView();
    }
}