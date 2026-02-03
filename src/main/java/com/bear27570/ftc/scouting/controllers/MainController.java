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

    // Checkboxes
    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck;
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;

    @FXML private VBox team2IgnoreBox;
    @FXML private Label lblTeam2;
    @FXML private VBox team2CapsBox;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck;
    @FXML private Label team1WarningLabel, team2WarningLabel;

    // --- Penalty Tab (UI Modified) ---
    @FXML private TextField penMatchField;
    @FXML private ToggleButton penRedToggle, penBlueToggle; // NEW: 选择哪一方犯规
    @FXML private TextField penMajor, penMinor;             // NEW: 通用输入框
    @FXML private Label penStatusLabel;

    // Rankings Tab
    @FXML private Button editRatingButton;
    @FXML private Label rankingLegendLabel;
    @FXML private TableView<TeamRanking> rankingsTableView;
    @FXML private TableColumn<TeamRanking, Integer> rankTeamCol, rankMatchesCol;
    @FXML private TableColumn<TeamRanking, Double> rankAutoCol, rankTeleopCol;
    @FXML private TableColumn<TeamRanking, String> rankSequenceCol, rankL2Col, rankRatingCol, rankAccuracyCol;
    @FXML private TableColumn<TeamRanking, String> rankPenCommCol, rankPenOppCol;
    @FXML private TableColumn<TeamRanking, Void> rankHeatmapCol;

    // History Tab
    @FXML private TextField searchField;
    @FXML private TableView<ScoreEntry> historyTableView;
    @FXML private TableColumn<ScoreEntry, Integer> histMatchCol, histTotalCol;
    @FXML private TableColumn<ScoreEntry, String> histAllianceCol, histTeamsCol, histSubmitterCol, histTimeCol;

    private ToggleGroup allianceToggleGroup;
    private ToggleGroup penaltyAllianceGroup; // NEW
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
        setupPenaltyTab(); // NEW init method

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
        } catch (IOException e) {
            statusLabel.setText("Failed to connect: " + e.getMessage());
        }
    }

    private void handleScoreReceivedFromClient(ScoreEntry scoreEntry) {
        DatabaseService.saveScoreEntry(currentCompetition.getName(), scoreEntry);
        List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
        List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
        Platform.runLater(() -> updateUIAfterDataChange(fullHistory, newRankings));
        NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings));
    }

    private void handleUpdateReceivedFromHost(NetworkPacket packet) {
        if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
            Platform.runLater(() -> {
                updateUIAfterDataChange(packet.getScoreHistory(), packet.getTeamRankings());
                setUIEnabled(true);
                statusLabel.setText("Data updated from host.");
                checkTeamHistory(team1Field.getText(), 1);
                checkTeamHistory(team2Field.getText(), 2);
            });
        }
    }

    // --- 新增：打开联盟分析页面的入口 ---
    @FXML private void handleAllianceAnalysis() {
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/AllianceAnalysisView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Alliance Analysis - " + currentCompetition.getName());
            stage.setScene(new Scene(loader.load()));
            AllianceAnalysisController controller = loader.getController();
            controller.setDialogStage(stage, currentCompetition);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error opening analysis: " + e.getMessage());
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

            if (currentClickLocations != null && !currentClickLocations.isEmpty()) {
                controller.loadExistingPoints(currentClickLocations);
            }

            stage.showAndWait();

            if (controller.isConfirmed()) {
                int count = controller.getTotalHitCount();
                teleopArtifactsField.setText(String.valueOf(count));
                currentClickLocations = controller.getLocationsString();
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

            // Checkbox Null Check
            boolean t1Seq = team1SequenceCheck != null && team1SequenceCheck.isSelected();
            boolean t1Climb = team1L2ClimbCheck != null && team1L2ClimbCheck.isSelected();
            boolean t1Ign = team1IgnoreCheck != null && team1IgnoreCheck.isSelected();
            boolean t1Brk = team1BrokenCheck != null && team1BrokenCheck.isSelected();

            boolean t2Seq = !isSingle && (team2SequenceCheck != null && team2SequenceCheck.isSelected());
            boolean t2Climb = !isSingle && (team2L2ClimbCheck != null && team2L2ClimbCheck.isSelected());
            boolean t2Ign = !isSingle && (team2IgnoreCheck != null && team2IgnoreCheck.isSelected());
            boolean t2Brk = !isSingle && (team2BrokenCheck != null && team2BrokenCheck.isSelected());

            ScoreEntry newEntry = new ScoreEntry(
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    Integer.parseInt(matchNumberField.getText()),
                    alliance, t1, t2,
                    Integer.parseInt(autoArtifactsField.getText()),
                    Integer.parseInt(teleopArtifactsField.getText()),
                    t1Seq, t2Seq, t1Climb, t2Climb,
                    t1Ign, t2Ign, t1Brk, t2Brk,
                    currentClickLocations, currentUsername); // Uses Constructor 1 (auto timestamp)

            if (isHost) {
                handleScoreReceivedFromClient(newEntry);
            } else {
                NetworkService.getInstance().sendScoreToServer(newEntry);
                statusLabel.setText("Score sent to host.");
            }

            errorLabel.setText("Score submitted successfully!");
            currentClickLocations = "";
            autoArtifactsField.setText("0");
            teleopArtifactsField.setText("0");

            // Reset fields
            if(team1SequenceCheck != null) team1SequenceCheck.setSelected(false);
            if(team1L2ClimbCheck != null) team1L2ClimbCheck.setSelected(false);
            if(team2SequenceCheck != null) team2SequenceCheck.setSelected(false);
            if(team2L2ClimbCheck != null) team2L2ClimbCheck.setSelected(false);
            if(team1IgnoreCheck != null) team1IgnoreCheck.setSelected(false);
            if(team2IgnoreCheck != null) team2IgnoreCheck.setSelected(false);
            if(team1BrokenCheck != null) team1BrokenCheck.setSelected(false);
            if(team2BrokenCheck != null) team2BrokenCheck.setSelected(false);

            try {
                int nextMatch = Integer.parseInt(matchNumberField.getText()) + 1;
                matchNumberField.setText(String.valueOf(nextMatch));
                penMatchField.setText(String.valueOf(nextMatch));
            } catch (NumberFormatException ignored) {}

        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    // --- 修复后的 Penalty 提交逻辑 ---
    @FXML private void handleSubmitPenalty() {
        try {
            int matchNum = Integer.parseInt(penMatchField.getText());

            ToggleButton selectedPenAlliance = (ToggleButton) penaltyAllianceGroup.getSelectedToggle();
            if (selectedPenAlliance == null) {
                throw new IllegalArgumentException("Select Alliance (Red/Blue)");
            }
            // 根据按钮颜色/文字判断
            String alliance = selectedPenAlliance.getText().contains("Red") ? "RED" : "BLUE";

            int maj = penMajor.getText().isEmpty() ? 0 : Integer.parseInt(penMajor.getText());
            int min = penMinor.getText().isEmpty() ? 0 : Integer.parseInt(penMinor.getText());

            // 创建新的单边判罚对象
            PenaltyEntry entry = new PenaltyEntry(matchNum, alliance, maj, min);

            // 存入数据库 (DB Service 会处理是 Update 还是 Insert)
            DatabaseService.savePenaltyEntry(currentCompetition.getName(), entry);

            refreshAllDataFromDatabase();

            // Host 广播更新
            if (isHost) {
                List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
                List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
                NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings));
            }

            penStatusLabel.setText("Saved: " + alliance + " M:" + matchNum);
            penStatusLabel.setStyle("-fx-text-fill: lightgreen;");

            penMajor.clear();
            penMinor.clear();
            // 不自动跳下一场，因为可能要录另一边的判罚

        } catch (Exception e) {
            penStatusLabel.setText("Error: " + e.getMessage());
            penStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML private void handleEditRating() throws IOException {
        if (!isHost) return;
        mainApp.showFormulaEditView(currentCompetition);
        refreshAllDataFromDatabase();
    }

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

    private void setUIEnabled(boolean enabled) {
        scoringFormVBox.setDisable(!enabled);
        if(!enabled) statusLabel.setText("Connecting to host...");
    }

    private void setupScoringTab() {
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup);
        blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);

        modeToggleGroup = new ToggleGroup();
        allianceModeRadio.setToggleGroup(modeToggleGroup);
        singleModeRadio.setToggleGroup(modeToggleGroup);

        modeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingle = singleModeRadio.isSelected();
            if(lblTeam2 != null) { lblTeam2.setVisible(!isSingle); lblTeam2.setManaged(!isSingle); }
            if(team2Field != null) { team2Field.setVisible(!isSingle); team2Field.setManaged(!isSingle); }
            if(team2CapsBox != null) { team2CapsBox.setVisible(!isSingle); team2CapsBox.setManaged(!isSingle); }
            if(team2IgnoreBox != null) { team2IgnoreBox.setVisible(!isSingle); team2IgnoreBox.setManaged(!isSingle); }

            if (isSingle && team2Field != null) team2Field.clear();
        });

        if(team1IgnoreCheck != null) team1IgnoreCheck.setVisible(false);
        if(team2IgnoreCheck != null) team2IgnoreCheck.setVisible(false);

        team1Field.textProperty().addListener((observable, oldValue, newValue) -> checkTeamHistory(newValue, 1));
        team2Field.textProperty().addListener((observable, oldValue, newValue) -> checkTeamHistory(newValue, 2));
    }

    // NEW: 设置判罚 Tab 的 ToggleGroup
    private void setupPenaltyTab() {
        penaltyAllianceGroup = new ToggleGroup();
        if (penRedToggle != null && penBlueToggle != null) {
            penRedToggle.setToggleGroup(penaltyAllianceGroup);
            penBlueToggle.setToggleGroup(penaltyAllianceGroup);
        }
    }

    private void checkTeamHistory(String teamStr, int teamSlot) {
        if (teamStr == null || teamStr.isBlank()) {
            if (teamSlot == 1) {
                if(team1WarningLabel!=null) team1WarningLabel.setVisible(false);
                if(team1IgnoreCheck!=null) team1IgnoreCheck.setVisible(false);
            } else {
                if(team2WarningLabel!=null) team2WarningLabel.setVisible(false);
                if(team2IgnoreCheck!=null) team2IgnoreCheck.setVisible(false);
            }
            return;
        }
        try {
            int teamNum = Integer.parseInt(teamStr.trim());
            long matchCount = scoreHistoryList.stream().filter(e -> e.getTeam1() == teamNum || e.getTeam2() == teamNum).count();
            boolean showIgnoreOption = (matchCount >= 2);
            boolean wasIgnored = scoreHistoryList.stream().anyMatch(entry ->
                    (entry.getTeam1() == teamNum && entry.isTeam1Ignored()) || (entry.getTeam2() == teamNum && entry.isTeam2Ignored()));

            if (teamSlot == 1) {
                if(team1IgnoreCheck!=null) team1IgnoreCheck.setVisible(showIgnoreOption);
                if(team1WarningLabel!=null) team1WarningLabel.setVisible(wasIgnored);
            } else {
                if(team2IgnoreCheck!=null) team2IgnoreCheck.setVisible(showIgnoreOption);
                if(team2WarningLabel!=null) team2WarningLabel.setVisible(wasIgnored);
            }
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
        rankingLegendLabel.setText("Penalty: Major=15, Minor=5. 'Avg Pen Given' = Pts given to opp due to fouls.");
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