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

import java.io.IOException;
import java.util.List;

public class MainController {

    @FXML private Label competitionNameLabel, submitterLabel, errorLabel, statusLabel;
    @FXML private Button manageMembersBtn;
    // Scoring Tab
    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private TextField autoArtifactsField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;

    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck;
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;

    // 这些组件可能在FXML里没定义ID，导致为空，所以后面必须加判空处理
    @FXML private VBox team2IgnoreBox, team2CapsBox;

    @FXML private Label lblTeam2;
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
        NetworkService.getInstance().startHost(currentCompetition, this::handleScoreReceivedFromClient);
    }
    private void startAsClient() {
        setUIEnabled(false);
        try {
            NetworkService.getInstance().connectToHost(currentCompetition.getHostAddress(), currentUsername, this::handleUpdateReceivedFromHost);
            statusLabel.setText("Authenticating with Host...");
        } catch (IOException e) {
            statusLabel.setText("Connection Failed.");
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
        Platform.runLater(() -> {
            if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
                updateUIAfterDataChange(packet.getScoreHistory(), packet.getTeamRankings());
                setUIEnabled(true);
                statusLabel.setText("Connected & Synced.");
            } else if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE) {
                if (packet.isApproved()) {
                    statusLabel.setText("Access Granted. Waiting for data...");
                } else {
                    statusLabel.setText("Access Denied.");
                }
            }
        });
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
            if(team1IgnoreCheck != null) {
                team1IgnoreCheck.setSelected(false);
                team1IgnoreCheck.setDisable(true);
            }
            if(team2IgnoreCheck != null) {
                team2IgnoreCheck.setSelected(false);
                team2IgnoreCheck.setDisable(true);
            }

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

        // --- 修复点：这里加了非空判断，避免空指针异常 ---
        modeToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            boolean isS = singleModeRadio.isSelected();

            if (lblTeam2 != null) lblTeam2.setVisible(!isS);
            if (team2Field != null) team2Field.setVisible(!isS);

            // 安全判断：如果FXML里没加这个VBox，就不会崩
            if (team2CapsBox != null) team2CapsBox.setVisible(!isS);
            if (team2IgnoreBox != null) team2IgnoreBox.setVisible(!isS);
        });

        if (team1IgnoreCheck != null) team1IgnoreCheck.setDisable(true);
        if (team2IgnoreCheck != null) team2IgnoreCheck.setDisable(true);

        // 绑定监听器时也判断组件是否存在
        team1Field.textProperty().addListener((obs, old, newVal) -> {
            if (team1IgnoreCheck != null) updateWeakCheckboxStatus(newVal, team1IgnoreCheck);
        });
        team2Field.textProperty().addListener((obs, old, newVal) -> {
            if (team2IgnoreCheck != null) updateWeakCheckboxStatus(newVal, team2IgnoreCheck);
        });
    }

    private void updateWeakCheckboxStatus(String teamNumberStr, CheckBox checkBox) {
        if (checkBox == null) return; // 安全退出

        if (teamNumberStr == null || teamNumberStr.trim().isEmpty() || currentCompetition == null) {
            checkBox.setDisable(true);
            checkBox.setSelected(false);
            return;
        }
        try {
            int teamNum = Integer.parseInt(teamNumberStr.trim());
            int matchCount = DatabaseService.getScoresForTeam(currentCompetition.getName(), teamNum).size();
            // 逻辑：只有当已经打了2场（当前是第3场）时，允许设置为 Weak (Ignored)
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
// ================= EXPORT FUNCTIONALITY =================

    @FXML
    private void handleExport() {
        // 1. 创建 Stage
        final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(competitionNameLabel.getScene().getWindow());
        dialog.setTitle("Export Data");

        // 2. 创建容器 VBox
        VBox dialogVbox = new VBox(20); // 间距 20
        dialogVbox.setAlignment(javafx.geometry.Pos.CENTER);
        dialogVbox.setPadding(new javafx.geometry.Insets(30));

        // --- 关键修改：直接应用 CSS 类 ---
        // 使用 "sidebar" 类作为背景 (#2A3B4F)，这与你的主界面侧边栏一致
        dialogVbox.getStyleClass().add("sidebar");
        // 加一点边框让它更像一个独立的窗口
        dialogVbox.setStyle("-fx-border-color: #4A5C70; -fx-border-width: 2;");

        // 3. 标题
        Label headerLabel = new Label("Export Data");
        // 使用 CSS 中的 "header-label" (Cyan色, 粗体, 24px)
        headerLabel.getStyleClass().add("header-label");

        Label subLabel = new Label("Select format (.csv):");
        // 使用 CSS 中的普通 "label" (浅灰色)
        subLabel.getStyleClass().add("label");

        // 4. 按钮样式
        // 我们希望导出按钮是绿色的，所以引用基础 .button 类后，覆盖背景色
        // 这样可以保留 CSS 定义的字体、圆角和鼠标悬停逻辑，只改变颜色
        String exportBtnStyle = "-fx-background-color: #2E7D32; -fx-pref-width: 220;";
        String exportHoverStyle = "-fx-background-color: #1B5E20; -fx-pref-width: 220;";

        // 按钮 1: Match History
        Button btnHistory = new Button("Export Match History");
        btnHistory.getStyleClass().add("button"); // 加载基础样式
        btnHistory.setStyle(exportBtnStyle);      // 覆盖为绿色
        // 手动处理 Hover 变色 (因为 override 了背景色，CSS 的 :hover 可能被覆盖)
        btnHistory.setOnMouseEntered(e -> btnHistory.setStyle(exportHoverStyle));
        btnHistory.setOnMouseExited(e -> btnHistory.setStyle(exportBtnStyle));
        btnHistory.setOnAction(e -> {
            dialog.close();
            exportData("Match History");
        });

        // 按钮 2: Rankings
        Button btnRankings = new Button("Export Team Rankings");
        btnRankings.getStyleClass().add("button");
        btnRankings.setStyle(exportBtnStyle);
        btnRankings.setOnMouseEntered(e -> btnRankings.setStyle(exportHoverStyle));
        btnRankings.setOnMouseExited(e -> btnRankings.setStyle(exportBtnStyle));
        btnRankings.setOnAction(e -> {
            dialog.close();
            exportData("Team Rankings");
        });

        // 按钮 3: Cancel
        Button btnCancel = new Button("Cancel");
        // 直接使用 CSS 里定义的 .logout-button (红色)，非常适合做取消/关闭按钮
        btnCancel.getStyleClass().add("logout-button");
        btnCancel.setStyle("-fx-pref-width: 220; -fx-font-size: 14px; -fx-padding: 8;"); // 调整宽度和内边距以匹配上方按钮
        btnCancel.setOnAction(e -> dialog.close());

        // 5. 分割线
        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        sep1.setOpacity(0.3); // 让分割线暗一点
        sep2.setOpacity(0.3);

        // 6. 组装
        dialogVbox.getChildren().addAll(headerLabel, subLabel, sep1, btnHistory, btnRankings, sep2, btnCancel);

        Scene dialogScene = new Scene(dialogVbox, 400, 380);

        // --- 关键步骤：加载你的 CSS 文件 ---
        // 假设 style.css 在 resources/com/bear27570/ftc/scouting/styles/style.css
        // 使用 mainApp.getClass() 确保路径解析正确
        try {
            dialogScene.getStylesheets().add(mainApp.getClass().getResource("styles/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load CSS for dialog: " + e.getMessage());
            // 如果找不到 CSS，保持默认样式，不崩溃
        }

        dialog.setScene(dialogScene);
        dialog.setResizable(false);
        dialog.show();
    }

    private void exportData(String type) {
        // 2. 配置文件选择器
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save Export File");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        // 生成默认文件名: CompetitionName_Type_Timestamp.csv
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());
        String cleanCompName = currentCompetition.getName().replaceAll("[^a-zA-Z0-9]", "");
        String defaultName = cleanCompName + "_" + type.replace(" ", "") + "_" + timestamp + ".csv";
        fileChooser.setInitialFileName(defaultName);

        // 获取当前 Stage
        Stage stage = (Stage) competitionNameLabel.getScene().getWindow();
        java.io.File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {
                if (type.equals("Match History")) {
                    writeMatchHistoryCSV(writer);
                } else {
                    writeRankingsCSV(writer);
                }
                statusLabel.setText("Exported successfully to " + file.getName());
            } catch (IOException e) {
                errorLabel.setText("Export Failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void writeMatchHistoryCSV(java.io.BufferedWriter writer) throws IOException {
        // 写入表头
        writer.write("Match Number,Alliance,Team 1,Team 2,Auto Artifacts,Teleop Artifacts,Total Score,T1 Sequence,T1 Climb,T2 Sequence,T2 Climb,Submitter,Time,Ignored/Broken Flags");
        writer.newLine();

        // 写入数据
        // 直接使用 scoreHistoryList，因为它包含当前所有数据
        for (ScoreEntry s : scoreHistoryList) {
            String flags = "";
            if(s.isTeam1Ignored()) flags += "T1_Weak ";
            if(s.isTeam2Ignored()) flags += "T2_Weak ";
            if(s.isTeam1Broken()) flags += "T1_Broken ";
            if(s.isTeam2Broken()) flags += "T2_Broken ";

            String line = String.format("%d,%s,%d,%d,%d,%d,%d,%b,%b,%b,%b,%s,%s,%s",
                    s.getMatchNumber(),
                    s.getAlliance(),
                    s.getTeam1(),
                    s.getTeam2(),
                    s.getAutoArtifacts(),
                    s.getTeleopArtifacts(),
                    s.getTotalScore(),
                    s.isTeam1CanSequence(),
                    s.isTeam1L2Climb(),
                    s.isTeam2CanSequence(),
                    s.isTeam2L2Climb(),
                    escapeCsv(s.getSubmitter()),
                    s.getSubmissionTime(),
                    flags.trim()
            );
            writer.write(line);
            writer.newLine();
        }
    }

    private void writeRankingsCSV(java.io.BufferedWriter writer) throws IOException {
        // 写入表头
        writer.write("Rank,Team Number,Rating,Matches Played,Avg Auto,Avg Teleop,Accuracy,Avg Pen Committed,Avg Pen Received,Sequence %,L2 Climb %");
        writer.newLine();

        // 获取当前表格中的排序数据
        List<TeamRanking> rankings = rankingsTableView.getItems();

        int rank = 1;
        for (TeamRanking r : rankings) {
            String line = String.format("%d,%d,%s,%d,%s,%s,%s,%s,%s,%s,%s",
                    rank++,
                    r.getTeamNumber(),
                    r.getRatingFormatted(), // 假设 TeamRanking 有这个getter，如果没有就用 r.getRating()
                    r.getMatchesPlayed(),
                    r.getAvgAutoArtifactsFormatted(),
                    r.getAvgTeleopArtifactsFormatted(),
                    r.getAccuracyFormatted(),
                    r.getAvgPenaltyCommittedFormatted(),
                    r.getAvgOpponentPenaltyFormatted(),
                    r.getCanSequence(), // 假设返回的是格式化后的百分比字符串
                    r.getL2Capable()
            );
            writer.write(line);
            writer.newLine();
        }
    }

    // 简单的 CSV 转义工具，防止数据中包含逗号导致格式错乱
    private String escapeCsv(String data) {
        if (data == null) return "";
        if (data.contains(",") || data.contains("\"") || data.contains("\n")) {
            return "\"" + data.replace("\"", "\"\"") + "\"";
        }
        return data;
    }
    private void setUIEnabled(boolean e) { scoringFormVBox.setDisable(!e); }
    @FXML private void handleEditRating() throws IOException { if(isHost) mainApp.showFormulaEditView(currentCompetition); refreshAllDataFromDatabase(); }
    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername); }
    @FXML private void handleLogout() throws IOException { mainApp.showLoginView(); }
}