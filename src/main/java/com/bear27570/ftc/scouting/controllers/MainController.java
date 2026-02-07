package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.services.DatabaseService;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
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
    @FXML private Button manageMembersBtn, submitButton, editRatingButton;

    // Scoring Tab
    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private TextField autoArtifactsField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;

    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck;
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;

    @FXML private VBox team2IgnoreBox, team2CapsBox;
    @FXML private Label lblTeam2;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck;

    // Penalty Tab
    @FXML private TextField penMatchField, penMajor, penMinor;
    @FXML private ToggleButton penRedToggle, penBlueToggle;
    @FXML private Label penStatusLabel;

    // Rankings Tab
    @FXML private Label rankingLegendLabel;
    @FXML private TableView<TeamRanking> rankingsTableView;
    @FXML private TableColumn<TeamRanking, Integer> rankTeamCol, rankMatchesCol;
    @FXML private TableColumn<TeamRanking, Double> rankAutoCol, rankTeleopCol;
    @FXML private TableColumn<TeamRanking, Double> rankRatingCol;
    @FXML private TableColumn<TeamRanking, Double> rankAccuracyCol;
    @FXML private TableColumn<TeamRanking, Double> rankPenCommCol, rankPenOppCol;
    @FXML private TableColumn<TeamRanking, String> rankSequenceCol, rankL2Col;
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
    private int editingScoreId = -1;
    private String editingOriginalTime = null;

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
        setupPenaltyTab(); // 确保加载罚分选项卡初始化

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

    // --- 罚分选项卡初始化 ---
    private void setupPenaltyTab() {
        penaltyAllianceGroup = new ToggleGroup();
        penRedToggle.setToggleGroup(penaltyAllianceGroup);
        penBlueToggle.setToggleGroup(penaltyAllianceGroup);
        penRedToggle.setSelected(true);
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
            if (team2CapsBox != null) team2CapsBox.setVisible(!isS);
            if (team2IgnoreBox != null) team2IgnoreBox.setVisible(!isS);
            if (team2IgnoreCheck != null) team2IgnoreCheck.setVisible(!isS);
            if (team2BrokenCheck != null) team2BrokenCheck.setVisible(!isS);
            if (isS) {
                if (team2IgnoreCheck != null) team2IgnoreCheck.setSelected(false);
                if (team2BrokenCheck != null) team2BrokenCheck.setSelected(false);
                if (team2SequenceCheck != null) team2SequenceCheck.setSelected(false);
                if (team2L2ClimbCheck != null) team2L2ClimbCheck.setSelected(false);
            }
        });

        if (team1IgnoreCheck != null) team1IgnoreCheck.setDisable(true);
        if (team2IgnoreCheck != null) team2IgnoreCheck.setDisable(true);

        team1Field.textProperty().addListener((obs, old, newVal) -> updateWeakCheckboxStatus(newVal, team1IgnoreCheck));
        team2Field.textProperty().addListener((obs, old, newVal) -> updateWeakCheckboxStatus(newVal, team2IgnoreCheck));
    }

    private void setupRankingsTab() {
        // --- 核心修复：排行榜数值排序与居中对齐 ---
        rankTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        rankTeamCol.setStyle("-fx-alignment: CENTER;");

        rankMatchesCol.setCellValueFactory(new PropertyValueFactory<>("matchesPlayed"));
        rankMatchesCol.setStyle("-fx-alignment: CENTER;");

        rankRatingCol.setCellValueFactory(new PropertyValueFactory<>("rating"));
        rankRatingCol.setCellFactory(createNumberCellFactory("%.2f"));
        rankRatingCol.setStyle("-fx-alignment: CENTER;");

        rankAutoCol.setCellValueFactory(new PropertyValueFactory<>("avgAutoArtifacts"));
        rankAutoCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankAutoCol.setStyle("-fx-alignment: CENTER;");

        rankTeleopCol.setCellValueFactory(new PropertyValueFactory<>("avgTeleopArtifacts"));
        rankTeleopCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankTeleopCol.setStyle("-fx-alignment: CENTER;");

        rankAccuracyCol.setCellValueFactory(cellData -> {
            String accStr = cellData.getValue().getAccuracyFormatted().replace("%", "");
            double val = accStr.equals("N/A") ? -1.0 : Double.parseDouble(accStr);
            return new SimpleObjectProperty<>(val);
        });
        rankAccuracyCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item < 0) setText(empty ? null : "N/A");
                else setText(String.format("%.1f%%", item));
            }
        });
        rankAccuracyCol.setStyle("-fx-alignment: CENTER;");

        rankPenCommCol.setCellValueFactory(new PropertyValueFactory<>("avgPenaltyCommitted"));
        rankPenCommCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankPenCommCol.setStyle("-fx-alignment: CENTER;");

        rankPenOppCol.setCellValueFactory(new PropertyValueFactory<>("avgOpponentPenalty"));
        rankPenOppCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankPenOppCol.setStyle("-fx-alignment: CENTER;");

        rankSequenceCol.setCellValueFactory(new PropertyValueFactory<>("canSequence"));
        rankSequenceCol.setStyle("-fx-alignment: CENTER;");
        rankL2Col.setCellValueFactory(new PropertyValueFactory<>("l2Capable"));
        rankL2Col.setStyle("-fx-alignment: CENTER;");

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

        // 规则更新：Into The Deep 赛季不执行自动翻倍
        rankingLegendLabel.setText("Penalty: Major=15, Minor=5. Auto scores are NOT doubled per Into The Deep rules.");
    }

    private <T> Callback<TableColumn<T, Double>, TableCell<T, Double>> createNumberCellFactory(String format) {
        return tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(format, item));
            }
        };
    }

    @FXML
    private void handleSubmitPenalty() {
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

    @FXML
    private void handleSubmitButtonAction() {
        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) throw new IllegalArgumentException("Select an alliance.");
            String alliance = selectedToggle.getText().contains("Red") ? "RED" : "BLUE";
            boolean isSingle = singleModeRadio.isSelected();

            ScoreEntry entry;
            if (editingScoreId != -1) {
                entry = new ScoreEntry(
                        editingScoreId, isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                        Integer.parseInt(matchNumberField.getText()), alliance,
                        Integer.parseInt(team1Field.getText()), isSingle ? 0 : Integer.parseInt(team2Field.getText()),
                        Integer.parseInt(autoArtifactsField.getText()), Integer.parseInt(teleopArtifactsField.getText()),
                        team1SequenceCheck.isSelected(), isSingle ? false : team2SequenceCheck.isSelected(),
                        team1L2ClimbCheck.isSelected(), isSingle ? false : team2L2ClimbCheck.isSelected(),
                        team1IgnoreCheck.isSelected(), isSingle ? false : team2IgnoreCheck.isSelected(),
                        team1BrokenCheck.isSelected(), isSingle ? false : team2BrokenCheck.isSelected(),
                        currentClickLocations, currentUsername, editingOriginalTime
                );
            } else {
                entry = new ScoreEntry(
                        isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                        Integer.parseInt(matchNumberField.getText()), alliance,
                        Integer.parseInt(team1Field.getText()), isSingle ? 0 : Integer.parseInt(team2Field.getText()),
                        Integer.parseInt(autoArtifactsField.getText()), Integer.parseInt(teleopArtifactsField.getText()),
                        team1SequenceCheck.isSelected(), isSingle ? false : team2SequenceCheck.isSelected(),
                        team1L2ClimbCheck.isSelected(), isSingle ? false : team2L2ClimbCheck.isSelected(),
                        team1IgnoreCheck.isSelected(), isSingle ? false : team2IgnoreCheck.isSelected(),
                        team1BrokenCheck.isSelected(), isSingle ? false : team2BrokenCheck.isSelected(),
                        currentClickLocations, currentUsername);
            }

            if (isHost) {
                DatabaseService.saveOrUpdateScoreEntry(currentCompetition.getName(), entry);
                refreshAllDataFromDatabase();
                broadcastUpdate();
            } else {
                NetworkService.getInstance().sendScoreToServer(entry);
            }
            errorLabel.setText("Score saved!");
            resetFormState();
        } catch (Exception e) { errorLabel.setText("Error: " + e.getMessage()); }
    }

    private void resetFormState() {
        editingScoreId = -1; editingOriginalTime = null;
        if(submitButton != null) { submitButton.setText("Submit Score"); submitButton.setStyle(""); }
        submitterLabel.setText("Submitter: " + currentUsername); submitterLabel.setStyle("");
        currentClickLocations = ""; autoArtifactsField.setText("0"); teleopArtifactsField.setText("0");
        if(team1IgnoreCheck != null) { team1IgnoreCheck.setSelected(false); team1IgnoreCheck.setDisable(true); }
        if(team2IgnoreCheck != null) { team2IgnoreCheck.setSelected(false); team2IgnoreCheck.setDisable(true); }
        team1BrokenCheck.setSelected(false); team2BrokenCheck.setSelected(false);
        team1SequenceCheck.setSelected(false); team1L2ClimbCheck.setSelected(false);
        if(team2SequenceCheck != null) team2SequenceCheck.setSelected(false);
        if(team2L2ClimbCheck != null) team2L2ClimbCheck.setSelected(false);
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
        });
    }

    private void broadcastUpdate() {
        List<ScoreEntry> fullHistory = DatabaseService.getScoresForCompetition(currentCompetition.getName());
        List<TeamRanking> newRankings = DatabaseService.calculateTeamRankings(currentCompetition.getName());
        NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings));
    }

    private void updateWeakCheckboxStatus(String teamNumberStr, CheckBox checkBox) {
        if (checkBox == null || teamNumberStr == null || teamNumberStr.trim().isEmpty() || currentCompetition == null) return;
        try {
            int teamNum = Integer.parseInt(teamNumberStr.trim());
            int matchCount = DatabaseService.getScoresForTeam(currentCompetition.getName(), teamNum).size();
            checkBox.setDisable(matchCount < 2 && editingScoreId == -1);
        } catch (Exception e) { checkBox.setDisable(true); }
    }

    private void handleScoreReceivedFromClient(ScoreEntry scoreEntry) {
        DatabaseService.saveOrUpdateScoreEntry(currentCompetition.getName(), scoreEntry);
        refreshAllDataFromDatabase();
        broadcastUpdate();
    }

    private void handleUpdateReceivedFromHost(NetworkPacket packet) {
        Platform.runLater(() -> {
            if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
                updateUIAfterDataChange(packet.getScoreHistory(), packet.getTeamRankings());
                setUIEnabled(true);
                statusLabel.setText("Connected & Synced.");
            }
        });
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

        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit This Entry");
        editItem.setOnAction(e -> handleEditAction());
        MenuItem deleteItem = new MenuItem("Delete Entry (Danger)");
        deleteItem.setStyle("-fx-text-fill: red;");
        deleteItem.setOnAction(e -> handleDeleteAction());
        contextMenu.getItems().addAll(editItem, deleteItem);
        historyTableView.setRowFactory(tv -> {
            TableRow<ScoreEntry> row = new TableRow<>();
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(contextMenu));
            return row;
        });
    }

    private void handleEditAction() {
        ScoreEntry selected = historyTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        editingScoreId = selected.getId(); editingOriginalTime = selected.getSubmissionTime();
        submitterLabel.setText("EDITING RECORD ID: " + editingScoreId); submitterLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        if(submitButton != null) { submitButton.setText("Update Record"); submitButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;"); }
        matchNumberField.setText(String.valueOf(selected.getMatchNumber()));
        team1Field.setText(String.valueOf(selected.getTeam1())); team2Field.setText(String.valueOf(selected.getTeam2()));
        autoArtifactsField.setText(String.valueOf(selected.getAutoArtifacts())); teleopArtifactsField.setText(String.valueOf(selected.getTeleopArtifacts()));
        if ("RED".equalsIgnoreCase(selected.getAlliance())) redAllianceToggle.setSelected(true); else blueAllianceToggle.setSelected(true);
        if (selected.getScoreType() == ScoreEntry.Type.SINGLE) singleModeRadio.setSelected(true); else allianceModeRadio.setSelected(true);
        team1SequenceCheck.setSelected(selected.isTeam1CanSequence()); team2SequenceCheck.setSelected(selected.isTeam2CanSequence());
        team1L2ClimbCheck.setSelected(selected.isTeam1L2Climb()); team2L2ClimbCheck.setSelected(selected.isTeam2L2Climb());
        if(team1IgnoreCheck != null) { team1IgnoreCheck.setDisable(false); team1IgnoreCheck.setSelected(selected.isTeam1Ignored()); }
        if(team2IgnoreCheck != null) { team2IgnoreCheck.setDisable(false); team2IgnoreCheck.setSelected(selected.isTeam2Ignored()); }
        team1BrokenCheck.setSelected(selected.isTeam1Broken()); team2BrokenCheck.setSelected(selected.isTeam2Broken());
        currentClickLocations = selected.getClickLocations(); errorLabel.setText("Editing record loaded.");
    }

    private void handleDeleteAction() {
        ScoreEntry selected = historyTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Match " + selected.getMatchNumber() + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (isHost) { DatabaseService.deleteScoreEntry(selected.getId()); refreshAllDataFromDatabase(); broadcastUpdate(); }
                else errorLabel.setText("Only Host can delete records for safety.");
            }
        });
    }

    @FXML
    private void handleExport() {
        final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(competitionNameLabel.getScene().getWindow());
        dialog.setTitle("Export Data");
        VBox dialogVbox = new VBox(20); dialogVbox.setAlignment(javafx.geometry.Pos.CENTER); dialogVbox.setPadding(new javafx.geometry.Insets(30));
        dialogVbox.setStyle("-fx-border-color: #4A5C70; -fx-border-width: 2;");
        Label headerLabel = new Label("Export Data"); headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button btnHistory = new Button("Export Match History"); btnHistory.setOnAction(e -> { dialog.close(); exportData("Match History"); });
        Button btnRankings = new Button("Export Team Rankings"); btnRankings.setOnAction(e -> { dialog.close(); exportData("Team Rankings"); });
        Button btnCancel = new Button("Cancel"); btnCancel.setOnAction(e -> dialog.close());
        dialogVbox.getChildren().addAll(headerLabel, btnHistory, btnRankings, btnCancel);
        dialog.setScene(new Scene(dialogVbox, 400, 300)); dialog.show();
    }

    private void exportData(String type) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());
        fileChooser.setInitialFileName(currentCompetition.getName() + "_" + type.replace(" ", "") + "_" + timestamp + ".csv");
        java.io.File file = fileChooser.showSaveDialog((Stage) competitionNameLabel.getScene().getWindow());
        if (file != null) {
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {
                if (type.equals("Match History")) writeMatchHistoryCSV(writer); else writeRankingsCSV(writer);
                statusLabel.setText("Exported successfully to " + file.getName());
            } catch (IOException e) { errorLabel.setText("Export Failed: " + e.getMessage()); }
        }
    }

    private void writeMatchHistoryCSV(java.io.BufferedWriter writer) throws IOException {
        writer.write("Match,Alliance,T1,T2,Auto,Teleop,Total,T1Seq,T1Climb,T2Seq,T2Climb,Submitter,Time"); writer.newLine();
        for (ScoreEntry s : scoreHistoryList) {
            writer.write(String.format("%d,%s,%d,%d,%d,%d,%d,%b,%b,%b,%b,%s,%s",
                    s.getMatchNumber(), s.getAlliance(), s.getTeam1(), s.getTeam2(), s.getAutoArtifacts(), s.getTeleopArtifacts(),
                    s.getTotalScore(), s.isTeam1CanSequence(), s.isTeam1L2Climb(), s.isTeam2CanSequence(), s.isTeam2L2Climb(), s.getSubmitter(), s.getSubmissionTime()));
            writer.newLine();
        }
    }

    private void writeRankingsCSV(java.io.BufferedWriter writer) throws IOException {
        writer.write("Rank,Team,Rating,Matches,Auto,Teleop,Accuracy"); writer.newLine();
        int rank = 1;
        for (TeamRanking r : rankingsTableView.getItems()) {
            writer.write(String.format("%d,%d,%s,%d,%s,%s,%s", rank++, r.getTeamNumber(), r.getRatingFormatted(), r.getMatchesPlayed(), r.getAvgAutoArtifactsFormatted(), r.getAvgTeleopArtifactsFormatted(), r.getAccuracyFormatted()));
            writer.newLine();
        }
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

    private void setUIEnabled(boolean e) { scoringFormVBox.setDisable(!e); }
    @FXML private void handleEditRating() throws IOException { if(isHost) mainApp.showFormulaEditView(currentCompetition); refreshAllDataFromDatabase(); }
    @FXML private void handleManageMembers() throws IOException { mainApp.showCoordinatorView(currentCompetition); }
    @FXML private void handleAllianceAnalysis() { /* 逻辑已通过按钮跳转到 FXML */ }
    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername); }
    @FXML private void handleLogout() throws IOException { mainApp.showLoginView(); }
}
