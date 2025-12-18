package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
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

    @FXML private Label competitionNameLabel;
    @FXML private Label submitterLabel;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;
    @FXML private VBox scoringFormVBox;

    // --- Scoring Tab Inputs ---
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private TextField autoArtifactsField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;
    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;

    // UI elements to hide in Single Team Mode
    @FXML private Label lblTeam2;
    @FXML private VBox team2CapsBox;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck;

    private ToggleGroup allianceToggleGroup;
    private ToggleGroup modeToggleGroup;

    // 暂存当前录入的坐标数据 (格式: "1:x,y;2:x,y;...")
    private String currentClickLocations = "";

    // --- Rankings Tab Inputs ---
    @FXML private Button editRatingButton;
    @FXML private Label rankingLegendLabel; // Legend for explanation
    @FXML private TableView<TeamRanking> rankingsTableView;
    @FXML private TableColumn<TeamRanking, Integer> rankTeamCol, rankMatchesCol;
    @FXML private TableColumn<TeamRanking, Double> rankAutoCol, rankTeleopCol;
    @FXML private TableColumn<TeamRanking, String> rankSequenceCol, rankL2Col, rankRatingCol;
    @FXML private TableColumn<TeamRanking, Void> rankHeatmapCol; // Column for "View Map" buttons

    // --- History Tab Inputs ---
    @FXML private TextField searchField;
    @FXML private TableView<ScoreEntry> historyTableView;
    @FXML private TableColumn<ScoreEntry, Integer> histMatchCol, histTotalCol;
    @FXML private TableColumn<ScoreEntry, String> histAllianceCol, histTeamsCol, histSubmitterCol, histTimeCol;

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

    // --- Networking Setup ---
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
        }
    }

    // --- UI Handlers ---

    @FXML
    private void handleOpenFieldInput() {
        try {
            FXMLLoader loader = new FXMLLoader(mainApp.getClass().getResource("fxml/FieldInputView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Record Scoring Locations");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(loader.load()));

            FieldInputController controller = loader.getController();
            controller.setDialogStage(stage);

            // --- 核心逻辑：设置单/双队模式 ---
            boolean isSingle = singleModeRadio.isSelected();
            controller.setAllianceMode(!isSingle); // 这里的 !isSingle 即为 isAllianceMode
            // -----------------------------

            stage.showAndWait();

            if (controller.isConfirmed()) {
                // 1. 获取计数并填入 Teleop 框 (累加或者覆盖，这里选择覆盖当前输入框以简化逻辑，用户可以手动改)
                int count = controller.getTotalCount();
                teleopArtifactsField.setText(String.valueOf(count));

                // 2. 追加坐标字符串
                String newLocs = controller.getLocationsString();
                if (!newLocs.isEmpty()) {
                    if (currentClickLocations.length() > 0 && !currentClickLocations.endsWith(";")) {
                        currentClickLocations += ";";
                    }
                    currentClickLocations += newLocs;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("Error opening map input: " + e.getMessage());
        }
    }

    @FXML
    private void handleSubmitButtonAction() {
        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) throw new IllegalArgumentException("Please select an alliance.");

            String alliance = selectedToggle.getText().startsWith("Red") ? "RED" : "BLUE";
            boolean isSingle = singleModeRadio.isSelected();

            // 验证并解析队伍号
            if (team1Field.getText().isEmpty()) throw new IllegalArgumentException("Team 1 is required.");
            int t1 = Integer.parseInt(team1Field.getText());
            int t2 = 0;
            if (!isSingle) {
                if (team2Field.getText().isEmpty()) throw new IllegalArgumentException("Team 2 is required in Alliance Mode");
                t2 = Integer.parseInt(team2Field.getText());
            }

            // 创建 ScoreEntry
            ScoreEntry newEntry = new ScoreEntry(
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    Integer.parseInt(matchNumberField.getText()),
                    alliance,
                    t1,
                    t2,
                    Integer.parseInt(autoArtifactsField.getText()),
                    Integer.parseInt(teleopArtifactsField.getText()),
                    team1SequenceCheck.isSelected(),
                    !isSingle && team2SequenceCheck.isSelected(), // 单队模式强制 false
                    team1L2ClimbCheck.isSelected(),
                    !isSingle && team2L2ClimbCheck.isSelected(), // 单队模式强制 false
                    currentClickLocations, // 传入坐标数据
                    currentUsername);

            // 发送/保存
            if (isHost) {
                handleScoreReceivedFromClient(newEntry);
            } else {
                NetworkService.getInstance().sendScoreToServer(newEntry);
                statusLabel.setText("Score sent to host.");
            }

            // 提交成功后的清理工作
            errorLabel.setText("Score submitted successfully!");
            currentClickLocations = ""; // 清空坐标缓存

            // 重置输入框，为下一场做准备
            autoArtifactsField.setText("0");
            teleopArtifactsField.setText("0");
            team1SequenceCheck.setSelected(false); team1L2ClimbCheck.setSelected(false);
            team2SequenceCheck.setSelected(false); team2L2ClimbCheck.setSelected(false);

            // 增加场次号方便录入
            try {
                int nextMatch = Integer.parseInt(matchNumberField.getText()) + 1;
                matchNumberField.setText(String.valueOf(nextMatch));
            } catch (NumberFormatException ignored) {}

        } catch (NumberFormatException e) {
            errorLabel.setText("Error: Please enter valid numbers.");
        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditRating() throws IOException {
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
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("Error opening heatmap.");
        }
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
                // 如果是Host，重新获取对象以获得最新公式
                if (isHost) currentCompetition = DatabaseService.getCompetition(currentCompetition.getName());

                String formula = currentCompetition.getRatingFormula();
                if (formula != null && !formula.equals("total")) {
                    rankRatingCol.setText("Rating *");
                } else {
                    rankRatingCol.setText("Rating");
                }
            }
        });
    }

    private void setUIEnabled(boolean enabled) {
        scoringFormVBox.setDisable(!enabled);
        if(!enabled) statusLabel.setText("Connecting to host...");
    }

    private void setupScoringTab() {
        // 联盟选择
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup);
        blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);

        // 模式选择
        modeToggleGroup = new ToggleGroup();
        allianceModeRadio.setToggleGroup(modeToggleGroup);
        singleModeRadio.setToggleGroup(modeToggleGroup);

        // 监听模式切换
        modeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSingle = singleModeRadio.isSelected();
            // 切换 Team 2 相关控件的显示/隐藏
            lblTeam2.setVisible(!isSingle); lblTeam2.setManaged(!isSingle);
            team2Field.setVisible(!isSingle); team2Field.setManaged(!isSingle);
            team2CapsBox.setVisible(!isSingle); team2CapsBox.setManaged(!isSingle);

            if (isSingle) {
                team2Field.clear();
                team2SequenceCheck.setSelected(false);
                team2L2ClimbCheck.setSelected(false);
            }
        });
    }

    private void setupRankingsTab() {
        rankTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        rankRatingCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRatingFormatted()));
        rankMatchesCol.setCellValueFactory(new PropertyValueFactory<>("matchesPlayed"));
        rankAutoCol.setCellValueFactory(new PropertyValueFactory<>("avgAutoArtifactsFormatted")); // 使用 Formatted getter
        rankTeleopCol.setCellValueFactory(new PropertyValueFactory<>("avgTeleopArtifactsFormatted"));
        rankSequenceCol.setCellValueFactory(new PropertyValueFactory<>("canSequence"));
        rankL2Col.setCellValueFactory(new PropertyValueFactory<>("l2Capable"));

        // 设置 "Shot Chart" 按钮列
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
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) setGraphic(null);
                        else setGraphic(btn);
                    }
                };
            }
        });

        // 设置说明文字
        if (rankingLegendLabel != null) {
            rankingLegendLabel.setText("LEGEND: 'Avg Auto/Teleop' = Avg per match (In Alliance Mode, total is halved). 'Rating' = Calculated score based on formula. '*' indicates custom formula. View 'Shot Chart' for density heatmap.");
        }
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

    @FXML
    private void handleBackButton() throws IOException {
        mainApp.showHubView(currentUsername);
    }

    @FXML
    private void handleLogout() throws IOException {
        mainApp.showLoginView();
    }
}