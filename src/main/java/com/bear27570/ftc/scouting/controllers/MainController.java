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
    @FXML private Button submitButton; // 确保在 FXML 中给提交按钮加上 fx:id="submitButton"

    // Scoring Tab
    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private TextField autoArtifactsField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;

    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck;
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;

    // These components might not be defined in FXML, so null checks are required
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

    // === 编辑功能状态变量 ===
    private int editingScoreId = -1; // -1 表示新建模式，>0 表示编辑模式
    private String editingOriginalTime = null; // 用于在编辑时保留原始提交时间

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
        // 使用支持更新的方法
        DatabaseService.saveOrUpdateScoreEntry(currentCompetition.getName(), scoreEntry);
        refreshAllDataFromDatabase();
        broadcastUpdate();
    }

    private void broadcastUpdate() {
        List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
        List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
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

            ScoreEntry entry;

            if (editingScoreId != -1) {
                // === 更新模式：保留 ID 和 原始时间戳 ===
                entry = new ScoreEntry(
                        editingScoreId, // 传入 ID
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
                        currentClickLocations,
                        currentUsername, // 这里可以选择保留原提交人，或更新为当前修改人
                        editingOriginalTime // 保留原始时间
                );
            } else {
                // === 新建模式：ID=0, 自动生成时间 ===
                entry = new ScoreEntry(
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
            }

            if (isHost) {
                // Host 自动判断 Save 或 Update
                DatabaseService.saveOrUpdateScoreEntry(currentCompetition.getName(), entry);
                refreshAllDataFromDatabase();
                broadcastUpdate();
            } else {
                // Client 发送给 Host，Host 端也需要用 saveOrUpdateScoreEntry 处理
                NetworkService.getInstance().sendScoreToServer(entry);
            }

            errorLabel.setText("Score saved!");
            resetFormState(); // 清空或重置表单

        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetFormState() {
        // 重置编辑状态
        editingScoreId = -1;
        editingOriginalTime = null;
        if(submitButton != null) {
            submitButton.setText("Submit Score");
            submitButton.setStyle("");
        }
        submitterLabel.setText("Submitter: " + currentUsername);
        submitterLabel.setStyle("");

        // 清空字段
        currentClickLocations = "";
        autoArtifactsField.setText("0");
        teleopArtifactsField.setText("0");
        matchNumberField.clear(); // 可选：是否清空比赛号取决于你的使用习惯

        // 重置勾选框
        if(team1IgnoreCheck != null) { team1IgnoreCheck.setSelected(false); team1IgnoreCheck.setDisable(true); }
        if(team2IgnoreCheck != null) { team2IgnoreCheck.setSelected(false); team2IgnoreCheck.setDisable(true); }
        if(team1BrokenCheck != null) team1BrokenCheck.setSelected(false);
        if(team2BrokenCheck != null) team2BrokenCheck.setSelected(false);
        team1SequenceCheck.setSelected(false);
        team1L2ClimbCheck.setSelected(false);
        if(team2SequenceCheck != null) team2SequenceCheck.setSelected(false);
        if(team2L2ClimbCheck != null) team2L2ClimbCheck.setSelected(false);
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
            if (isHost) broadcastUpdate();
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

        modeToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            boolean isS = singleModeRadio.isSelected();

            if (lblTeam2 != null) lblTeam2.setVisible(!isS);
            if (team2Field != null) team2Field.setVisible(!isS);

            // Hide capability container if it exists
            if (team2CapsBox != null) team2CapsBox.setVisible(!isS);

            // Hide Ignore/Broken if wrapped in a box
            if (team2IgnoreBox != null) team2IgnoreBox.setVisible(!isS);

            // Explicitly hide the specific checkboxes if they aren't in the hidden box
            if (team2IgnoreCheck != null) team2IgnoreCheck.setVisible(!isS);
            if (team2BrokenCheck != null) team2BrokenCheck.setVisible(!isS);

            // Clear checkboxes when switching to Single Mode
            if (isS) {
                if (team2IgnoreCheck != null) team2IgnoreCheck.setSelected(false);
                if (team2BrokenCheck != null) team2BrokenCheck.setSelected(false);
                if (team2SequenceCheck != null) team2SequenceCheck.setSelected(false);
                if (team2L2ClimbCheck != null) team2L2ClimbCheck.setSelected(false);
            }
        });

        if (team1IgnoreCheck != null) team1IgnoreCheck.setDisable(true);
        if (team2IgnoreCheck != null) team2IgnoreCheck.setDisable(true);

        team1Field.textProperty().addListener((obs, old, newVal) -> {
            if (team1IgnoreCheck != null) updateWeakCheckboxStatus(newVal, team1IgnoreCheck);
        });
        team2Field.textProperty().addListener((obs, old, newVal) -> {
            if (team2IgnoreCheck != null) updateWeakCheckboxStatus(newVal, team2IgnoreCheck);
        });
    }

    private void updateWeakCheckboxStatus(String teamNumberStr, CheckBox checkBox) {
        if (checkBox == null) return;
        // 如果正在编辑模式，允许保留原有状态，或者根据逻辑判断
        // 这里保持原逻辑：只有场次够了才能设为 Weak
        if (teamNumberStr == null || teamNumberStr.trim().isEmpty() || currentCompetition == null) {
            checkBox.setDisable(true);
            checkBox.setSelected(false);
            return;
        }
        try {
            int teamNum = Integer.parseInt(teamNumberStr.trim());
            int matchCount = DatabaseService.getScoresForTeam(currentCompetition.getName(), teamNum).size();
            // Allow "Weak" flag only after 2 matches played
            if (matchCount >= 2) {
                checkBox.setDisable(false);
            } else {
                // 如果是编辑回填，且原本就是 Weak，那么允许它保持 Weak 状态（即使计算出来小于2场）
                // 或者是新建时，小于2场禁用
                if (editingScoreId == -1) {
                    checkBox.setDisable(true);
                    checkBox.setSelected(false);
                } else {
                    // 编辑模式下，如果原本选了，就允许选
                    if (!checkBox.isSelected()) {
                        checkBox.setDisable(true);
                    } else {
                        checkBox.setDisable(false);
                    }
                }
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

        // === 右键菜单：编辑与删除 ===
        ContextMenu contextMenu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit This Entry");
        editItem.setOnAction(e -> handleEditAction());

        MenuItem deleteItem = new MenuItem("Delete Entry (Danger)");
        deleteItem.setStyle("-fx-text-fill: red;");
        deleteItem.setOnAction(e -> handleDeleteAction());

        contextMenu.getItems().addAll(editItem, deleteItem);

        historyTableView.setRowFactory(tv -> {
            TableRow<ScoreEntry> row = new TableRow<>();
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );
            return row;
        });
    }

    // 处理编辑动作：数据回填
    private void handleEditAction() {
        ScoreEntry selected = historyTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // 设置状态
        editingScoreId = selected.getId();
        editingOriginalTime = selected.getSubmissionTime();

        // 提示用户
        submitterLabel.setText("EDITING RECORD ID: " + editingScoreId);
        submitterLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        if(submitButton != null) {
            submitButton.setText("Update Record");
            submitButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        }

        // 回填表单
        matchNumberField.setText(String.valueOf(selected.getMatchNumber()));
        team1Field.setText(String.valueOf(selected.getTeam1()));
        team2Field.setText(String.valueOf(selected.getTeam2()));

        autoArtifactsField.setText(String.valueOf(selected.getAutoArtifacts()));
        teleopArtifactsField.setText(String.valueOf(selected.getTeleopArtifacts()));

        if ("RED".equalsIgnoreCase(selected.getAlliance())) {
            redAllianceToggle.setSelected(true);
        } else {
            blueAllianceToggle.setSelected(true);
        }

        if (selected.getScoreType() == ScoreEntry.Type.SINGLE) {
            singleModeRadio.setSelected(true);
        } else {
            allianceModeRadio.setSelected(true);
        }

        team1SequenceCheck.setSelected(selected.isTeam1CanSequence());
        team2SequenceCheck.setSelected(selected.isTeam2CanSequence());
        team1L2ClimbCheck.setSelected(selected.isTeam1L2Climb());
        team2L2ClimbCheck.setSelected(selected.isTeam2L2Climb());

        // 处理 Weak/Broken (先启用再勾选)
        if(team1IgnoreCheck != null) { team1IgnoreCheck.setDisable(false); team1IgnoreCheck.setSelected(selected.isTeam1Ignored()); }
        if(team2IgnoreCheck != null) { team2IgnoreCheck.setDisable(false); team2IgnoreCheck.setSelected(selected.isTeam2Ignored()); }
        if(team1BrokenCheck != null) team1BrokenCheck.setSelected(selected.isTeam1Broken());
        if(team2BrokenCheck != null) team2BrokenCheck.setSelected(selected.isTeam2Broken());

        currentClickLocations = selected.getClickLocations();
        errorLabel.setText("Editing record loaded.");
    }

    // 处理删除动作
    private void handleDeleteAction() {
        ScoreEntry selected = historyTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Match " + selected.getMatchNumber() + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // 如果是 Client，无法直接删库，这需要网络协议支持 DeletePacket。
                // 暂时假设 Client 只能让 Host 删，或者如果是 Host 才能删。
                if (isHost) {
                    DatabaseService.deleteScoreEntry(selected.getId());
                    refreshAllDataFromDatabase();
                    broadcastUpdate();
                } else {
                    // Client Delete TODO: 需要在 NetworkService 增加 sendDeleteRequest
                    errorLabel.setText("Only Host can delete records for safety.");
                }
            }
        });
    }

    @FXML
    private void handleExport() {
        final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(competitionNameLabel.getScene().getWindow());
        dialog.setTitle("Export Data");

        VBox dialogVbox = new VBox(20);
        dialogVbox.setAlignment(javafx.geometry.Pos.CENTER);
        dialogVbox.setPadding(new javafx.geometry.Insets(30));
        dialogVbox.getStyleClass().add("sidebar");
        dialogVbox.setStyle("-fx-border-color: #4A5C70; -fx-border-width: 2;");

        Label headerLabel = new Label("Export Data");
        headerLabel.getStyleClass().add("header-label");

        Label subLabel = new Label("Select format (.csv):");
        subLabel.getStyleClass().add("label");

        String exportBtnStyle = "-fx-background-color: #2E7D32; -fx-pref-width: 220;";
        String exportHoverStyle = "-fx-background-color: #1B5E20; -fx-pref-width: 220;";

        Button btnHistory = new Button("Export Match History");
        btnHistory.getStyleClass().add("button");
        btnHistory.setStyle(exportBtnStyle);
        btnHistory.setOnMouseEntered(e -> btnHistory.setStyle(exportHoverStyle));
        btnHistory.setOnMouseExited(e -> btnHistory.setStyle(exportBtnStyle));
        btnHistory.setOnAction(e -> {
            dialog.close();
            exportData("Match History");
        });

        Button btnRankings = new Button("Export Team Rankings");
        btnRankings.getStyleClass().add("button");
        btnRankings.setStyle(exportBtnStyle);
        btnRankings.setOnMouseEntered(e -> btnRankings.setStyle(exportHoverStyle));
        btnRankings.setOnMouseExited(e -> btnRankings.setStyle(exportBtnStyle));
        btnRankings.setOnAction(e -> {
            dialog.close();
            exportData("Team Rankings");
        });

        Button btnCancel = new Button("Cancel");
        btnCancel.getStyleClass().add("logout-button");
        btnCancel.setStyle("-fx-pref-width: 220; -fx-font-size: 14px; -fx-padding: 8;");
        btnCancel.setOnAction(e -> dialog.close());

        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        sep1.setOpacity(0.3);
        sep2.setOpacity(0.3);

        dialogVbox.getChildren().addAll(headerLabel, subLabel, sep1, btnHistory, btnRankings, sep2, btnCancel);

        Scene dialogScene = new Scene(dialogVbox, 400, 380);

        try {
            dialogScene.getStylesheets().add(mainApp.getClass().getResource("styles/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load CSS for dialog: " + e.getMessage());
        }

        dialog.setScene(dialogScene);
        dialog.setResizable(false);
        dialog.show();
    }

    private void exportData(String type) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save Export File");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());
        String cleanCompName = currentCompetition.getName().replaceAll("[^a-zA-Z0-9]", "");
        String defaultName = cleanCompName + "_" + type.replace(" ", "") + "_" + timestamp + ".csv";
        fileChooser.setInitialFileName(defaultName);

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
        writer.write("Match Number,Alliance,Team 1,Team 2,Auto Artifacts,Teleop Artifacts,Total Score,T1 Sequence,T1 Climb,T2 Sequence,T2 Climb,Submitter,Time,Ignored/Broken Flags");
        writer.newLine();

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
        writer.write("Rank,Team Number,Rating,Matches Played,Avg Auto,Avg Teleop,Accuracy,Avg Pen Committed,Avg Pen Received,Sequence %,L2 Climb %");
        writer.newLine();

        List<TeamRanking> rankings = rankingsTableView.getItems();

        int rank = 1;
        for (TeamRanking r : rankings) {
            String line = String.format("%d,%d,%s,%d,%s,%s,%s,%s,%s,%s,%s",
                    rank++,
                    r.getTeamNumber(),
                    r.getRatingFormatted(),
                    r.getMatchesPlayed(),
                    r.getAvgAutoArtifactsFormatted(),
                    r.getAvgTeleopArtifactsFormatted(),
                    r.getAccuracyFormatted(),
                    r.getAvgPenaltyCommittedFormatted(),
                    r.getAvgOpponentPenaltyFormatted(),
                    r.getCanSequence(),
                    r.getL2Capable()
            );
            writer.write(line);
            writer.newLine();
        }
    }

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