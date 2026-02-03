package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.services.DatabaseService;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.util.List;

public class MainController {

    @FXML private Label competitionNameLabel, submitterLabel, errorLabel, statusLabel;

    // Scoring Tab
    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private TextField autoArtifactsField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;
    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck;
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;
    @FXML private Label team1WarningLabel, team2WarningLabel;
    @FXML private VBox team2IgnoreBox;
    @FXML private Label lblTeam2;
    @FXML private VBox team2CapsBox;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck;

    // --- 新增: Penalty Tab ---
    @FXML private TextField penMatchField;
    @FXML private TextField penRedMajor, penRedMinor;
    @FXML private TextField penBlueMajor, penBlueMinor;
    @FXML private Label penStatusLabel;

    // Rankings Tab
    @FXML private Button editRatingButton;
    @FXML private Label rankingLegendLabel;
    @FXML private TableView<TeamRanking> rankingsTableView;
    @FXML private TableColumn<TeamRanking, Integer> rankTeamCol, rankMatchesCol;
    @FXML private TableColumn<TeamRanking, Double> rankAutoCol, rankTeleopCol;
    @FXML private TableColumn<TeamRanking, String> rankSequenceCol, rankL2Col, rankRatingCol, rankAccuracyCol;
    @FXML private TableColumn<TeamRanking, String> rankPenCommCol, rankPenOppCol; // 新增列
    @FXML private TableColumn<TeamRanking, Void> rankHeatmapCol;

    // History Tab
    @FXML private TextField searchField;
    @FXML private TableView<ScoreEntry> historyTableView;
    @FXML private TableColumn<ScoreEntry, Integer> histMatchCol, histTotalCol;
    @FXML private TableColumn<ScoreEntry, String> histAllianceCol, histTeamsCol, histSubmitterCol, histTimeCol;

    private ToggleGroup allianceToggleGroup;
    private ToggleGroup modeToggleGroup;
    private String currentClickLocations = "";
    private MainApplication mainApp;
    private Competition currentCompetition;
    private String currentUsername;
    private boolean isHost;
    private ObservableList<ScoreEntry> scoreHistoryList = FXCollections.observableArrayList();

    public void setMainApp(MainApplication mainApp, Competition competition, String username, boolean isHost) {
        this.mainApp = mainApp;
        this.currentCompetition = competition;
        this.currentUsername = username;
        this.isHost = isHost;

        competitionNameLabel.setText(competition.getName() + (isHost ? " (HOSTING)" : " (CLIENT)"));
        submitterLabel.setText("Submitter: " + username);

        setupScoringTab();
        setupRankingsTab();
        setupHistoryTab();

        if (isHost) {
            editRatingButton.setVisible(true);
            editRatingButton.setManaged(true);
            startAsHost();
        } else {
            startAsClient();
        }
    }

    private void startAsHost() {
        refreshAllDataFromDatabase();
        NetworkService.getInstance().startHost(currentCompetition, this::handleScoreReceivedFromClient);
    }
    private void startAsClient() {
        setUIEnabled(false);
        try {
            NetworkService.getInstance().connectToHost(currentCompetition.getHostAddress(), this::handleUpdateReceivedFromHost);
            statusLabel.setText("Connected to host. Waiting for data...");
        } catch (IOException e) { statusLabel.setText("Failed to connect: " + e.getMessage()); }
    }
    private void handleScoreReceivedFromClient(ScoreEntry scoreEntry) {
        DatabaseService.saveScoreEntry(currentCompetition.getName(), scoreEntry);
        refreshAllDataFromDatabase();
        List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
        List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
        NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings));
    }
    private void handleUpdateReceivedFromHost(NetworkPacket packet) {
        if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
            updateUIAfterDataChange(packet.getScoreHistory(), packet.getTeamRankings());
            setUIEnabled(true);
            statusLabel.setText("Data updated from host.");
            Platform.runLater(() -> {
                checkTeamHistory(team1Field.getText(), 1);
                checkTeamHistory(team2Field.getText(), 2);
            });
        }
    }

    @FXML private void handleOpenFieldInput() {
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/FieldInputView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Record Scoring Locations");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(loader.load()));
            FieldInputController controller = loader.getController();
            controller.setDialogStage(stage);
            boolean isSingle = singleModeRadio.isSelected();
            controller.setAllianceMode(!isSingle);
            stage.showAndWait();
            if (controller.isConfirmed()) {
                int count = controller.getTotalHitCount();
                teleopArtifactsField.setText(String.valueOf(count));
                String newLocs = controller.getLocationsString();
                if (!newLocs.isEmpty()) {
                    if (currentClickLocations.length() > 0 && !currentClickLocations.endsWith(";")) currentClickLocations += ";";
                    currentClickLocations += newLocs;
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML private void handleSubmitButtonAction() {
        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) throw new IllegalArgumentException("Please select an alliance.");

            String alliance = selectedToggle.getText().startsWith("Red") ? "RED" : "BLUE";
            boolean isSingle = singleModeRadio.isSelected();

            if (team1Field.getText().isEmpty()) throw new IllegalArgumentException("Team 1 is required.");
            int t1 = Integer.parseInt(team1Field.getText());
            int t2 = 0;
            if (!isSingle) {
                if (team2Field.getText().isEmpty()) throw new IllegalArgumentException("Team 2 is required in Alliance Mode");
                t2 = Integer.parseInt(team2Field.getText());
            }

            ScoreEntry newEntry = new ScoreEntry(
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    Integer.parseInt(matchNumberField.getText()),
                    alliance, t1, t2,
                    Integer.parseInt(autoArtifactsField.getText()),
                    Integer.parseInt(teleopArtifactsField.getText()),
                    team1SequenceCheck.isSelected(), !isSingle && team2SequenceCheck.isSelected(),
                    team1L2ClimbCheck.isSelected(), !isSingle && team2L2ClimbCheck.isSelected(),
                    team1IgnoreCheck.isSelected(), !isSingle && team2IgnoreCheck.isSelected(),
                    team1BrokenCheck.isSelected(), !isSingle && team2BrokenCheck.isSelected(),
                    currentClickLocations, currentUsername);

            if (isHost) { handleScoreReceivedFromClient(newEntry); }
            else { NetworkService.getInstance().sendScoreToServer(newEntry); statusLabel.setText("Score sent to host."); }

            errorLabel.setText("Score submitted successfully!");
            currentClickLocations = "";
            autoArtifactsField.setText("0"); teleopArtifactsField.setText("0");
            team1SequenceCheck.setSelected(false); team1L2ClimbCheck.setSelected(false);
            team2SequenceCheck.setSelected(false); team2L2ClimbCheck.setSelected(false);
            team1IgnoreCheck.setSelected(false); team2IgnoreCheck.setSelected(false);
            team1BrokenCheck.setSelected(false); team2BrokenCheck.setSelected(false);
            team1WarningLabel.setVisible(false); team2WarningLabel.setVisible(false);

            try {
                int nextMatch = Integer.parseInt(matchNumberField.getText()) + 1;
                matchNumberField.setText(String.valueOf(nextMatch));
                penMatchField.setText(String.valueOf(nextMatch)); // 同步更新Penalty页面的场次
            } catch (NumberFormatException ignored) {}

        } catch (Exception e) { errorLabel.setText("Error: " + e.getMessage()); }
    }

    // --- 新增: Penalty Handling ---
    @FXML
    private void handleSubmitPenalty() {
        try {
            int matchNum = Integer.parseInt(penMatchField.getText());
            int rMaj = penRedMajor.getText().isEmpty() ? 0 : Integer.parseInt(penRedMajor.getText());
            int rMin = penRedMinor.getText().isEmpty() ? 0 : Integer.parseInt(penRedMinor.getText());
            int bMaj = penBlueMajor.getText().isEmpty() ? 0 : Integer.parseInt(penBlueMajor.getText());
            int bMin = penBlueMinor.getText().isEmpty() ? 0 : Integer.parseInt(penBlueMinor.getText());

            PenaltyEntry entry = new PenaltyEntry(matchNum, rMaj, rMin, bMaj, bMin);
            DatabaseService.savePenaltyEntry(currentCompetition.getName(), entry);

            // 提交后刷新数据 (判罚会影响排名统计)
            refreshAllDataFromDatabase();
            if (isHost) {
                // 如果是Host，还得广播一下 (这里简化逻辑，通常也发Update包)
                List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
                List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
                NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings));
            }

            penStatusLabel.setText("Penalty saved for Match " + matchNum);
            penStatusLabel.setStyle("-fx-text-fill: lightgreen;");

            // 清空
            penRedMajor.clear(); penRedMinor.clear();
            penBlueMajor.clear(); penBlueMinor.clear();
            penMatchField.setText(String.valueOf(matchNum + 1));

        } catch (Exception e) {
            penStatusLabel.setText("Error: " + e.getMessage());
            penStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML private void handleEditRating() throws IOException { if (!isHost) return; mainApp.showFormulaEditView(currentCompetition); refreshAllDataFromDatabase(); }

    private void showHeatmap(int teamNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/HeatmapView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Heatmap - Team " + teamNumber);
            stage.setScene(new Scene(loader.load()));
            HeatmapController controller = loader.getController();
            List<ScoreEntry> matches = DatabaseService.getScoresForTeam(currentCompetition.getName(), teamNumber);
            controller.setData(teamNumber, matches);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void refreshAllDataFromDatabase() {
        List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
        List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
        updateUIAfterDataChange(fullHistory, newRankings);
    }

    private void updateUIAfterDataChange(List<ScoreEntry> history, List<TeamRanking> rankings) {
        Platform.runLater(() -> {
            rankingsTableView.setItems(FXCollections.observableArrayList(rankings));
            scoreHistoryList.setAll(history);
            if (currentCompetition != null) {
                if (isHost) currentCompetition = DatabaseService.getCompetition(currentCompetition.getName());
                String formula = currentCompetition.getRatingFormula();
                rankRatingCol.setText((formula != null && !formula.equals("total")) ? "Rating *" : "Rating");
            }
        });
    }

    private void setUIEnabled(boolean enabled) { scoringFormVBox.setDisable(!enabled); if(!enabled) statusLabel.setText("Connecting to host..."); }

    private void setupScoringTab() {
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup); blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);
        modeToggleGroup = new ToggleGroup();
        allianceModeRadio.setToggleGroup(modeToggleGroup); singleModeRadio.setToggleGroup(modeToggleGroup);
        modeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingle = singleModeRadio.isSelected();
            lblTeam2.setVisible(!isSingle); lblTeam2.setManaged(!isSingle);
            team2Field.setVisible(!isSingle); team2Field.setManaged(!isSingle);
            team2CapsBox.setVisible(!isSingle); team2CapsBox.setManaged(!isSingle);
            team2IgnoreBox.setVisible(!isSingle); team2IgnoreBox.setManaged(!isSingle);
            if (isSingle) { team2Field.clear(); team2BrokenCheck.setSelected(false); }
        });
        team1IgnoreCheck.setVisible(false); team2IgnoreCheck.setVisible(false);
        team1Field.textProperty().addListener((observable, oldValue, newValue) -> checkTeamHistory(newValue, 1));
        team2Field.textProperty().addListener((observable, oldValue, newValue) -> checkTeamHistory(newValue, 2));
    }

    private void checkTeamHistory(String teamStr, int teamSlot) {
        if (teamStr == null || teamStr.isBlank()) {
            if (teamSlot == 1) { team1WarningLabel.setVisible(false); team1IgnoreCheck.setVisible(false); }
            else { team2WarningLabel.setVisible(false); team2IgnoreCheck.setVisible(false); }
            return;
        }
        try {
            int teamNum = Integer.parseInt(teamStr.trim());
            long matchCount = scoreHistoryList.stream().filter(e -> e.getTeam1() == teamNum || e.getTeam2() == teamNum).count();
            boolean showIgnoreOption = (matchCount >= 2);
            boolean wasIgnored = scoreHistoryList.stream().anyMatch(entry ->
                    (entry.getTeam1() == teamNum && entry.isTeam1Ignored()) || (entry.getTeam2() == teamNum && entry.isTeam2Ignored()));
            if (teamSlot == 1) { team1IgnoreCheck.setVisible(showIgnoreOption); team1WarningLabel.setVisible(wasIgnored); }
            else { team2IgnoreCheck.setVisible(showIgnoreOption); team2WarningLabel.setVisible(wasIgnored); }
        } catch (NumberFormatException e) {}
    }

    private void setupRankingsTab() {
        rankTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        rankRatingCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRatingFormatted()));
        rankMatchesCol.setCellValueFactory(new PropertyValueFactory<>("matchesPlayed"));
        rankAutoCol.setCellValueFactory(new PropertyValueFactory<>("avgAutoArtifactsFormatted"));
        rankTeleopCol.setCellValueFactory(new PropertyValueFactory<>("avgTeleopArtifactsFormatted"));
        rankSequenceCol.setCellValueFactory(new PropertyValueFactory<>("canSequence"));
        rankL2Col.setCellValueFactory(new PropertyValueFactory<>("l2Capable"));

        TableColumn<TeamRanking, String> accCol = new TableColumn<>("Accuracy");
        accCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAccuracyFormatted()));
        rankingsTableView.getColumns().add(5, accCol);

        // --- 新增：判罚列 ---
        TableColumn<TeamRanking, String> penComm = new TableColumn<>("Avg Pen Given");
        penComm.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAvgPenaltyCommittedFormatted()));

        TableColumn<TeamRanking, String> penOpp = new TableColumn<>("Avg Pen Got");
        penOpp.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAvgOpponentPenaltyFormatted()));

        rankingsTableView.getColumns().add(rankingsTableView.getColumns().size() - 1, penComm);
        rankingsTableView.getColumns().add(rankingsTableView.getColumns().size() - 1, penOpp);

        rankHeatmapCol.setCellFactory(new Callback<>() {
            @Override
            public TableCell<TeamRanking, Void> call(TableColumn<TeamRanking, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("View Map");
                    {
                        btn.setStyle("-fx-font-size: 10px; -fx-padding: 3 8 3 8; -fx-background-color: #007BFF; -fx-text-fill: white;");
                        btn.setOnAction(event -> {
                            TeamRanking data = getTableView().getItems().get(getIndex());
                            if(data != null) showHeatmap(data.getTeamNumber());
                        });
                    }
                    @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); if (empty) setGraphic(null); else setGraphic(btn); }
                };
            }
        });
        rankingLegendLabel.setText("Avg Pen Given = Points given to opponent (Lower better). Avg Pen Got = Points received from opponent.");
    }

    private void setupHistoryTab() {
        histMatchCol.setCellValueFactory(new PropertyValueFactory<>("matchNumber"));
        histAllianceCol.setCellValueFactory(new PropertyValueFactory<>("alliance"));
        histTeamsCol.setCellValueFactory(new PropertyValueFactory<>("teams"));
        histTotalCol.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        histSubmitterCol.setCellValueFactory(new PropertyValueFactory<>("submitter"));
        histTimeCol.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));
        FilteredList<ScoreEntry> filteredData = new FilteredList<>(scoreHistoryList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(scoreEntry -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lower = newValue.toLowerCase();
                return String.valueOf(scoreEntry.getMatchNumber()).contains(lower) ||
                        String.valueOf(scoreEntry.getTeam1()).contains(lower) ||
                        String.valueOf(scoreEntry.getTeam2()).contains(lower) ||
                        scoreEntry.getSubmitter().toLowerCase().contains(lower);
            });
        });
        historyTableView.setItems(filteredData);
    }

    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername); }
    @FXML private void handleLogout() throws IOException { mainApp.showLoginView(); }
}