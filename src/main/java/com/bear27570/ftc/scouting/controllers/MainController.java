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
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck; // Set Weak 功能对应这里的 IgnoreCheck
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;
    @FXML private VBox team2IgnoreBox, team2CapsBox;
    @FXML private Label lblTeam2, team1WarningLabel, team2WarningLabel;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck;

    // Penalty Tab
    @FXML private TextField penMatchField, penMajor, penMinor;
    @FXML private ToggleButton penRedToggle, penBlueToggle;
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

    private ToggleGroup allianceToggleGroup, penaltyAllianceGroup, modeToggleGroup;
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
        setupPenaltyTab();
        if (isHost) {
            editRatingButton.setVisible(true);
            editRatingButton.setManaged(true);
            manageMembersBtn.setVisible(true);
            manageMembersBtn.setManaged(true);
            startAsHost();
        } else {
            manageMembersBtn.setVisible(false);
            manageMembersBtn.setManaged(false);
            startAsClient();
        }
    }
    private void startAsHost() {
        refreshAllDataFromDatabase();
        // 健壮性：此处调用 startHost 不会导致已连接的客户端断开
        NetworkService.getInstance().startHost(currentCompetition, this::handleScoreReceivedFromClient);
    }
    private void startAsClient() {
        setUIEnabled(false);
        try {
            NetworkService.getInstance().connectToHost(currentCompetition.getHostAddress(), currentUsername, this::handleUpdateReceivedFromHost);
            statusLabel.setText("Connected to host. Waiting for data...");
        } catch (IOException e) {
            statusLabel.setText("Failed to connect: " + e.getMessage());
        }
    }
    @FXML
    private void handleManageMembers() {
        try {
            mainApp.showCoordinatorView(currentCompetition);
        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
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
            });
        }
    }

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
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML private void handleOpenFieldInput() {
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/FieldInputView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(loader.load()));
            FieldInputController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setAllianceMode(!singleModeRadio.isSelected());
            if (currentClickLocations != null && !currentClickLocations.isEmpty()) controller.loadExistingPoints(currentClickLocations);
            stage.showAndWait();
            if (controller.isConfirmed()) {
                teleopArtifactsField.setText(String.valueOf(controller.getTotalHitCount()));
                currentClickLocations = controller.getLocationsString();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML private void handleSubmitButtonAction() {
        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) throw new IllegalArgumentException("Select an alliance.");
            String alliance = selectedToggle.getText().contains("Red") ? "RED" : "BLUE";
            boolean isSingle = singleModeRadio.isSelected();

            ScoreEntry newEntry = new ScoreEntry(
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    Integer.parseInt(matchNumberField.getText()), alliance,
                    Integer.parseInt(team1Field.getText()),
                    isSingle ? 0 : Integer.parseInt(team2Field.getText()),
                    Integer.parseInt(autoArtifactsField.getText()),
                    Integer.parseInt(teleopArtifactsField.getText()),
                    team1SequenceCheck.isSelected(), isSingle ? false : team2SequenceCheck.isSelected(),
                    team1L2ClimbCheck.isSelected(), isSingle ? false : team2L2ClimbCheck.isSelected(),
                    team1IgnoreCheck.isSelected(), isSingle ? false : team2IgnoreCheck.isSelected(),
                    team1BrokenCheck.isSelected(), isSingle ? false : team2BrokenCheck.isSelected(),
                    currentClickLocations, currentUsername);

            if (isHost) handleScoreReceivedFromClient(newEntry);
            else NetworkService.getInstance().sendScoreToServer(newEntry);

            errorLabel.setText("Score submitted!");
            currentClickLocations = "";
            autoArtifactsField.setText("0");
            teleopArtifactsField.setText("0");

            // 提交后重置状态
            team1IgnoreCheck.setSelected(false);
            team1IgnoreCheck.setDisable(true);
            team2IgnoreCheck.setSelected(false);
            team2IgnoreCheck.setDisable(true);

        } catch (Exception e) { errorLabel.setText("Error: " + e.getMessage()); }
    }

    @FXML private void handleSubmitPenalty() {
        try {
            int matchNum = Integer.parseInt(penMatchField.getText());
            ToggleButton selected = (ToggleButton) penaltyAllianceGroup.getSelectedToggle();
            if (selected == null) throw new IllegalArgumentException("Select Alliance");
            String alliance = selected.getText().contains("Red") ? "RED" : "BLUE";

            PenaltyEntry entry = new PenaltyEntry(matchNum, alliance,
                    Integer.parseInt(penMajor.getText()), Integer.parseInt(penMinor.getText()));
            DatabaseService.savePenaltyEntry(currentCompetition.getName(), entry);

            refreshAllDataFromDatabase();
            if (isHost) {
                NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(
                        DatabaseService.getScoresForCompetition(currentCompetition.getName()),
                        DatabaseService.calculateTeamRankings(currentCompetition.getName())));
            }
            penStatusLabel.setText("Saved: " + alliance + " Match " + matchNum);
        } catch (Exception e) { penStatusLabel.setText("Error: " + e.getMessage()); }
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
            if (currentCompetition != null && isHost) {
                currentCompetition = DatabaseService.getCompetition(currentCompetition.getName());
                rankRatingCol.setText(currentCompetition.getRatingFormula().equals("total") ? "Rating" : "Rating *");
            }
            // 每次数据更新时，也尝试刷新当前输入框对应的复选框状态（防止多机同步后数据变化）
            updateWeakCheckboxStatus(team1Field.getText(), team1IgnoreCheck);
            updateWeakCheckboxStatus(team2Field.getText(), team2IgnoreCheck);
        });
    }

    private void setupScoringTab() {
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup);
        blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);

        modeToggleGroup = new ToggleGroup();
        allianceModeRadio.setToggleGroup(modeToggleGroup);
        singleModeRadio.setToggleGroup(modeToggleGroup);
        modeToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            boolean isS = singleModeRadio.isSelected();
            lblTeam2.setVisible(!isS); team2Field.setVisible(!isS);
            team2CapsBox.setVisible(!isS); team2IgnoreBox.setVisible(!isS);
        });

        // --- 新增：仅在第三场开放 Set Weak 功能的逻辑 ---
        team1IgnoreCheck.setDisable(true);
        team2IgnoreCheck.setDisable(true);

        team1Field.textProperty().addListener((obs, old, newVal) -> updateWeakCheckboxStatus(newVal, team1IgnoreCheck));
        team2Field.textProperty().addListener((obs, old, newVal) -> updateWeakCheckboxStatus(newVal, team2IgnoreCheck));
    }

    /**
     * 核心逻辑：检查数据库中该队的比赛次数
     */
    private void updateWeakCheckboxStatus(String teamNumberStr, CheckBox checkBox) {
        if (teamNumberStr == null || teamNumberStr.trim().isEmpty() || currentCompetition == null) {
            checkBox.setDisable(true);
            checkBox.setSelected(false);
            return;
        }
        try {
            int teamNum = Integer.parseInt(teamNumberStr.trim());
            // 获取已有的比赛场数
            int matchCount = DatabaseService.getScoresForTeam(currentCompetition.getName(), teamNum).size();

            // 如果已经打了 2 场，说明当前是第 3 场
            if (matchCount == 2) {
                checkBox.setDisable(false);
            } else {
                checkBox.setDisable(true);
                checkBox.setSelected(false);
            }
        } catch (NumberFormatException e) {
            checkBox.setDisable(true);
            checkBox.setSelected(false);
        }
    }

    private void setupPenaltyTab() {
        penaltyAllianceGroup = new ToggleGroup();
        penRedToggle.setToggleGroup(penaltyAllianceGroup);
        penBlueToggle.setToggleGroup(penaltyAllianceGroup);
        penRedToggle.setSelected(true);
    }

    private void setupRankingsTab() {
        rankTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        rankRatingCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRatingFormatted()));
        rankMatchesCol.setCellValueFactory(new PropertyValueFactory<>("matchesPlayed"));
        rankAutoCol.setCellValueFactory(new PropertyValueFactory<>("avgAutoArtifactsFormatted"));
        rankTeleopCol.setCellValueFactory(new PropertyValueFactory<>("avgTeleopArtifactsFormatted"));
        rankAccuracyCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAccuracyFormatted()));
        rankPenCommCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAvgPenaltyCommittedFormatted()));
        rankPenOppCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAvgOpponentPenaltyFormatted()));
        rankSequenceCol.setCellValueFactory(new PropertyValueFactory<>("canSequence"));
        rankL2Col.setCellValueFactory(new PropertyValueFactory<>("l2Capable"));

        rankHeatmapCol.setCellFactory(p -> new TableCell<>() {
            private final Button btn = new Button("View");
            {
                btn.setStyle("-fx-font-size: 10px; -fx-background-color: #007BFF; -fx-text-fill: white;");
                btn.setOnAction(e -> showHeatmap(getTableView().getItems().get(getIndex()).getTeamNumber()));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : btn);
            }
        });
        rankingLegendLabel.setText("Penalty: Major=15, Minor=5. 'Avg Pen Given' = Points given to opponent.");
    }

    private void showHeatmap(int teamNum) {
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/HeatmapView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Heatmap - Team " + teamNum);
            stage.setScene(new Scene(loader.load()));
            ((HeatmapController)loader.getController()).setData(teamNum, DatabaseService.getScoresForTeam(currentCompetition.getName(), teamNum));
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void setupHistoryTab() {
        histMatchCol.setCellValueFactory(new PropertyValueFactory<>("matchNumber"));
        histAllianceCol.setCellValueFactory(new PropertyValueFactory<>("alliance"));
        histTeamsCol.setCellValueFactory(new PropertyValueFactory<>("teams"));
        histTotalCol.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        histSubmitterCol.setCellValueFactory(new PropertyValueFactory<>("submitter"));
        histTimeCol.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));
        FilteredList<ScoreEntry> filtered = new FilteredList<>(scoreHistoryList, p -> true);
        searchField.textProperty().addListener((o, old, newVal) -> filtered.setPredicate(s -> {
            if (newVal == null || newVal.isEmpty()) return true;
            String low = newVal.toLowerCase();
            return String.valueOf(s.getMatchNumber()).contains(low) || s.getTeams().contains(low);
        }));
        historyTableView.setItems(filtered);
    }

    private void setUIEnabled(boolean e) { scoringFormVBox.setDisable(!e); }
    @FXML private void handleEditRating() throws IOException { if(isHost) mainApp.showFormulaEditView(currentCompetition); refreshAllDataFromDatabase(); }
    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername); }
    @FXML private void handleLogout() throws IOException { mainApp.showLoginView(); }
}