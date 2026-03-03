// File: MainController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;
import com.bear27570.ftc.scouting.services.domain.UserService;
import com.bear27570.ftc.scouting.services.network.FtcScoutApiClient; // 新引入的 API 客户端
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

public class MainController {

    @FXML private Label competitionNameLabel, submitterLabel, errorLabel, statusLabel;
    @FXML private Button manageMembersBtn, submitButton, editRatingButton, offlineSyncBtn;

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
    @FXML private TextField ftcScoutSeasonField, ftcScoutEventField;
    @FXML private Label boundEventNameLabel, autoFetchStatusLabel;

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
    @FXML private TableColumn<ScoreEntry, ScoreEntry.SyncStatus> histSyncCol;
    @FXML private TableColumn<ScoreEntry, Integer> histMatchCol, histTotalCol;
    @FXML private TableColumn<ScoreEntry, String> histAllianceCol, histTeamsCol, histSubmitterCol, histTimeCol;
    @FXML private TableColumn<ScoreEntry, Void> histActionsCol;

    private ToggleGroup allianceToggleGroup, modeToggleGroup;
    private String currentClickLocations = "";

    // --- 依赖注入的服务 ---
    private MainApplication mainApp;
    private MatchDataService matchDataService;
    private RankingService rankingService;
    private CompetitionRepository competitionRepository;
    private UserService userService;
    private FtcScoutApiClient ftcScoutApiClient;

    private Competition currentCompetition;
    private String currentUsername;
    private boolean isHost;
    private ObservableList<ScoreEntry> scoreHistoryList = FXCollections.observableArrayList();

    private int editingScoreId = -1;
    private String editingOriginalTime = null;
    private ScoreEntry.SyncStatus editingOriginalSyncStatus = null;
    private String officialEventName = null;

    private void updateTopLabel() {
        String role = isHost ? " [HOST]" : " [CLIENT]";
        if (officialEventName != null && !officialEventName.trim().isEmpty()) {
            competitionNameLabel.setText(officialEventName + role);
        } else {
            competitionNameLabel.setText(currentCompetition.getName() + role);
        }
    }

    // --- 核心注入方法 ---
    public void setDependencies(MainApplication mainApp, Competition competition, String username, boolean isHost,
                                MatchDataService matchDataService, RankingService rankingService,
                                CompetitionRepository competitionRepository, UserService userService) {
        this.mainApp = mainApp;
        this.currentCompetition = competition;
        this.currentUsername = username;
        this.isHost = isHost;
        this.matchDataService = matchDataService;
        this.rankingService = rankingService;
        this.competitionRepository = competitionRepository;
        this.userService = userService;

        // 实例化专门负责 FTC Scout API 的客户端
        this.ftcScoutApiClient = new FtcScoutApiClient(matchDataService);

        updateTopLabel();
        if (currentCompetition.getOfficialEventName() != null && !currentCompetition.getOfficialEventName().isEmpty()) {
            this.officialEventName = currentCompetition.getOfficialEventName();
            NetworkService.getInstance().setOfficialEventName(this.officialEventName);

            ftcScoutSeasonField.setText(String.valueOf(currentCompetition.getEventSeason()));
            ftcScoutEventField.setText(currentCompetition.getEventCode());
            boundEventNameLabel.setText("Bound Event: " + this.officialEventName);
            updateTopLabel();
        }

        submitterLabel.setText("Submitter: " + username);

        if (offlineSyncBtn != null) {
            offlineSyncBtn.setText(isHost ? "Import Offline Data" : "Export Offline Data");
        }

        setupScoringTab();
        setupRankingsTab();
        setupHistoryTab();

        if (isHost) {
            editRatingButton.setVisible(true);
            editRatingButton.setManaged(true);
            manageMembersBtn.setVisible(true);
            manageMembersBtn.setManaged(true);
            startAsHost();
        } else {
            // ★ 完美修复数据无法同步：从机启动前，静默创建一个同名的房主账户，确保满足 users 外键依赖
            if (this.userService != null && currentCompetition.getCreatorUsername() != null) {
                this.userService.register(currentCompetition.getCreatorUsername(), "dummy_password_for_fk");
            }
            competitionRepository.ensureLocalCompetitionSync(currentCompetition);

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
        refreshAllDataFromDatabase();
        setUIEnabled(true);
        try {
            NetworkService.getInstance().connectToHost(currentCompetition.getHostAddress(), currentUsername, this::handleUpdateReceivedFromHost);
            statusLabel.setText("Authenticating with Host...");
        } catch (IOException e) {
            statusLabel.setText("Working Offline (Caching Locally)");
        }
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
                btn.setOnAction(e -> {
                    try {
                        mainApp.showHeatmapView(currentCompetition, getTableView().getItems().get(getIndex()).getTeamNumber());
                    } catch (IOException ex) { ex.printStackTrace(); }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty); setGraphic(empty ? null : btn);
            }
        });

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

    // ★ 重构后：利用独立的 API Client 异步获取数据
    @FXML
    private void handleBindAndFetch() {
        if (!isHost) {
            autoFetchStatusLabel.setText("Error: Only Host can sync with FTCScout.");
            autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
            return;
        }

        String seasonStr = ftcScoutSeasonField.getText().trim();
        String eventCode = ftcScoutEventField.getText().trim();

        if (seasonStr.isEmpty() || eventCode.isEmpty()) {
            autoFetchStatusLabel.setText("Please enter Season (Int) and Event Code.");
            return;
        }

        final int season;
        try {
            season = Integer.parseInt(seasonStr);
        } catch (NumberFormatException e) {
            autoFetchStatusLabel.setText("Error: Season must be a valid year.");
            return;
        }

        autoFetchStatusLabel.setText("Connecting to FTCScout...");
        autoFetchStatusLabel.setStyle("-fx-text-fill: #0A84FF;");
        boundEventNameLabel.setText("Event: Fetching...");

        // 调用重构到服务层的方法
        ftcScoutApiClient.fetchAndSyncEventDataAsync(season, eventCode, currentCompetition.getName(), new FtcScoutApiClient.ApiCallback() {
            @Override
            public void onEventFound(String eventName, boolean hasMatches) {
                Platform.runLater(() -> boundEventNameLabel.setText("Bound Event: " + eventName));
            }

            @Override
            public void onSuccess(String eventName, int syncedMatchCount) {
                Platform.runLater(() -> {
                    officialEventName = eventName;
                    NetworkService.getInstance().setOfficialEventName(eventName);

                    currentCompetition.setEventSeason(season);
                    currentCompetition.setEventCode(eventCode);
                    currentCompetition.setOfficialEventName(eventName);

                    competitionRepository.updateEventInfo(currentCompetition.getName(), season, eventCode, eventName);
                    updateTopLabel();

                    refreshAllDataFromDatabase();
                    broadcastUpdate();

                    if (syncedMatchCount > 0) {
                        autoFetchStatusLabel.setText("Success! Synced penalties for " + syncedMatchCount + " matches.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #32D74B;");
                    } else {
                        autoFetchStatusLabel.setText("Event Linked. No matches found yet.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #FF9F0A;");
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    boundEventNameLabel.setText("Event: Error/Not Found");
                    autoFetchStatusLabel.setText(errorMessage);
                    autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
                });
            }
        });
    }

    @FXML
    private void handleSubmitButtonAction() {
        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) throw new IllegalArgumentException("Select an alliance.");
            String alliance = selectedToggle.getText().contains("Red") ? "RED" : "BLUE";
            boolean isSingle = singleModeRadio.isSelected();

            ScoreEntry.SyncStatus targetStatus = isHost ? ScoreEntry.SyncStatus.SYNCED :
                    (editingOriginalSyncStatus != null ? editingOriginalSyncStatus : ScoreEntry.SyncStatus.UNSYNCED);

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
                        currentClickLocations, currentUsername, editingOriginalTime, targetStatus
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
                entry.setSyncStatus(targetStatus);
            }

            matchDataService.submitScore(currentCompetition.getName(), entry);

            if (isHost) {
                broadcastUpdate();
            } else {
                NetworkService.getInstance().sendScoreToServer(entry);
            }
            refreshAllDataFromDatabase();
            errorLabel.setText("Score saved locally!");
            resetFormState();
        } catch (Exception e) { errorLabel.setText("Error: " + e.getMessage()); }
    }

    private void resetFormState() {
        editingScoreId = -1; editingOriginalTime = null; editingOriginalSyncStatus = null;
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
        List<ScoreEntry> fullHistory = matchDataService.getHistory(currentCompetition.getName());
        List<TeamRanking> newRankings = rankingService.calculateRankings(currentCompetition.getName());
        updateUIAfterDataChange(fullHistory, newRankings);
    }

    private void updateUIAfterDataChange(List<ScoreEntry> history, List<TeamRanking> rankings) {
        Platform.runLater(() -> {
            rankingsTableView.setItems(FXCollections.observableArrayList(rankings));
            scoreHistoryList.setAll(history);
            if (currentCompetition != null && isHost) {
                currentCompetition = competitionRepository.findByName(currentCompetition.getName());
                rankRatingCol.setText(currentCompetition.getRatingFormula().equals("total") ? "Rating" : "Rating *");
            }
        });
    }

    private void broadcastUpdate() {
        List<ScoreEntry> fullHistory = matchDataService.getHistory(currentCompetition.getName());
        List<TeamRanking> newRankings = rankingService.calculateRankings(currentCompetition.getName());
        NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings, officialEventName));
    }

    private void updateWeakCheckboxStatus(String teamNumberStr, CheckBox checkBox) {
        if (checkBox == null || teamNumberStr == null || teamNumberStr.trim().isEmpty() || currentCompetition == null) return;
        try {
            int teamNum = Integer.parseInt(teamNumberStr.trim());
            int matchCount = matchDataService.getTeamHistory(currentCompetition.getName(), teamNum).size();
            checkBox.setDisable(matchCount < 2 && editingScoreId == -1);
        } catch (Exception e) { checkBox.setDisable(true); }
    }

    private void handleScoreReceivedFromClient(ScoreEntry scoreEntry) {
        scoreEntry.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
        matchDataService.submitScore(currentCompetition.getName(), scoreEntry);
        refreshAllDataFromDatabase();
        broadcastUpdate();
    }

    private void handleUpdateReceivedFromHost(NetworkPacket packet) {
        Platform.runLater(() -> {
            if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {

                // ★ 修复点：确保把主机的事件名称也更新到从机本地
                if (packet.getOfficialEventName() != null && !packet.getOfficialEventName().isEmpty()) {
                    this.officialEventName = packet.getOfficialEventName();
                    currentCompetition.setOfficialEventName(this.officialEventName);
                    competitionRepository.updateEventInfo(currentCompetition.getName(), currentCompetition.getEventSeason(), currentCompetition.getEventCode(), this.officialEventName);
                    updateTopLabel();
                }

                matchDataService.syncWithHostData(currentCompetition.getName(), packet.getScoreHistory());
                refreshAllDataFromDatabase();
                statusLabel.setText("Connected & Synced.");

                List<ScoreEntry> pending = matchDataService.getPendingExports(currentCompetition.getName());
                if (!pending.isEmpty()) {
                    for (ScoreEntry s : pending) {
                        NetworkService.getInstance().sendScoreToServer(s);
                    }
                    statusLabel.setText("Auto-synced " + pending.size() + " local records to host.");
                }
            }
        });
    }

    @FXML private void handleOpenFieldInput() {
        try {
            mainApp.showFieldInputView(this, !singleModeRadio.isSelected(), currentClickLocations);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void onFieldInputConfirmed(int totalHits, String clickLocations) {
        teleopArtifactsField.setText(String.valueOf(totalHits));
        currentClickLocations = clickLocations;
    }

    private void setupHistoryTab() {
        histMatchCol.setCellValueFactory(new PropertyValueFactory<>("matchNumber"));
        histAllianceCol.setCellValueFactory(new PropertyValueFactory<>("alliance"));
        histTeamsCol.setCellValueFactory(new PropertyValueFactory<>("teams"));
        histTotalCol.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        histSubmitterCol.setCellValueFactory(new PropertyValueFactory<>("submitter"));
        histTimeCol.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));

        histSyncCol.setCellValueFactory(new PropertyValueFactory<>("syncStatus"));
        histSyncCol.setCellFactory(param -> new TableCell<>() {
            private final Circle circle = new Circle(6);
            { setAlignment(javafx.geometry.Pos.CENTER); }
            @Override protected void updateItem(ScoreEntry.SyncStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); } else {
                    switch(status) {
                        case UNSYNCED -> circle.setFill(Color.web("#FF453A"));
                        case EXPORTED -> circle.setFill(Color.web("#FF9F0A"));
                        case SYNCED -> circle.setFill(Color.web("#32D74B"));
                    }
                    setGraphic(circle);
                }
            }
        });


        histActionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(8, editBtn, delBtn);
            {
                pane.setAlignment(javafx.geometry.Pos.CENTER);
                editBtn.getStyleClass().addAll("accent", "button-small");
                delBtn.getStyleClass().addAll("danger", "button-small");
                editBtn.setOnAction(e -> handleEditAction(getTableView().getItems().get(getIndex())));
                delBtn.setOnAction(e -> handleDeleteAction(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) { setGraphic(null); } else {
                    ScoreEntry entry = getTableView().getItems().get(getIndex());
                    boolean canEdit = isHost || entry.getSubmitter().equals(currentUsername);
                    boolean canDel = isHost;

                    editBtn.setVisible(canEdit); editBtn.setManaged(canEdit);
                    delBtn.setVisible(canDel); delBtn.setManaged(canDel);

                    if(!canEdit && !canDel) setGraphic(null);
                    else setGraphic(pane);
                }
            }
        });

        FilteredList<ScoreEntry> filtered = new FilteredList<>(scoreHistoryList, p -> true);
        searchField.textProperty().addListener((o, old, newVal) -> filtered.setPredicate(s -> {
            if (newVal == null || newVal.isEmpty()) return true;
            String low = newVal.toLowerCase();
            return String.valueOf(s.getMatchNumber()).contains(low) || s.getTeams().contains(low);
        }));
        historyTableView.setItems(filtered);
    }

    private void handleEditAction(ScoreEntry selected) {
        editingScoreId = selected.getId();
        editingOriginalTime = selected.getSubmissionTime();
        editingOriginalSyncStatus = selected.getSyncStatus();
        submitterLabel.setText("EDITING RECORD ID: " + editingScoreId);
        submitterLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
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

    private void handleDeleteAction(ScoreEntry selected) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Match " + selected.getMatchNumber() + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (isHost) {
                    matchDataService.deleteScore(selected.getId());
                    refreshAllDataFromDatabase();
                    broadcastUpdate();
                }
                else errorLabel.setText("Only Host can delete records for safety.");
            }
        });
    }

    @FXML
    private void handleOfflineSync() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("FTC Sync Data (*.ftcsync)", "*.ftcsync"));

        if (isHost) {
            fileChooser.setTitle("Import Client Offline Data");
            File file = fileChooser.showOpenDialog(competitionNameLabel.getScene().getWindow());
            if (file != null) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                    @SuppressWarnings("unchecked")
                    List<ScoreEntry> importedScores = (List<ScoreEntry>) in.readObject();
                    for(ScoreEntry s : importedScores) {
                        s.setId(0);
                        s.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                        matchDataService.submitScore(currentCompetition.getName(), s);
                    }
                    refreshAllDataFromDatabase();
                    broadcastUpdate();
                    statusLabel.setText("Imported " + importedScores.size() + " records!");
                } catch (Exception e) { errorLabel.setText("Import Failed."); }
            }
        } else {
            List<ScoreEntry> pending = matchDataService.getPendingExports(currentCompetition.getName());
            if (pending.isEmpty()) { statusLabel.setText("No pending offline data to export."); return; }

            fileChooser.setTitle("Export Offline Data");
            fileChooser.setInitialFileName("OfflineSync_" + currentUsername + "_" + System.currentTimeMillis() + ".ftcsync");
            File file = fileChooser.showSaveDialog(competitionNameLabel.getScene().getWindow());

            if (file != null) {
                try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                    out.writeObject(pending);
                    matchDataService.markAsExported(pending.stream().map(ScoreEntry::getId).collect(Collectors.toList()));
                    refreshAllDataFromDatabase();
                    statusLabel.setText("Exported successfully!");
                } catch (Exception e) { errorLabel.setText("Export Failed."); }
            }
        }
    }

    @FXML
    private void handleExport() {
        final Stage dialog = new Stage();
        dialog.initOwner(competitionNameLabel.getScene().getWindow());
        dialog.setTitle("Export Data");
        VBox dialogVbox = new VBox(20); dialogVbox.setAlignment(javafx.geometry.Pos.CENTER); dialogVbox.setPadding(new javafx.geometry.Insets(30));
        dialogVbox.setStyle("-fx-border-color: #4A5C70; -fx-border-width: 2;");
        Label headerLabel = new Label("Export CSV Data"); headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button btnHistory = new Button("Export Match History"); btnHistory.setOnAction(e -> { dialog.close(); exportData("Match History"); });
        Button btnRankings = new Button("Export Team Rankings"); btnRankings.setOnAction(e -> { dialog.close(); exportData("Team Rankings"); });
        Button btnCancel = new Button("Cancel"); btnCancel.setOnAction(e -> dialog.close());
        dialogVbox.getChildren().addAll(headerLabel, btnHistory, btnRankings, btnCancel);
        dialog.setScene(new Scene(dialogVbox, 400, 300)); dialog.show();
    }

    private void exportData(String type) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());
        fileChooser.setInitialFileName(currentCompetition.getName() + "_" + type.replace(" ", "") + "_" + timestamp + ".csv");
        java.io.File file = fileChooser.showSaveDialog(competitionNameLabel.getScene().getWindow());
        if (file != null) {
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {
                if (type.equals("Match History")) writeMatchHistoryCSV(writer); else writeRankingsCSV(writer);
                statusLabel.setText("Exported successfully to " + file.getName());
            } catch (IOException e) { errorLabel.setText("Export Failed: " + e.getMessage()); }
        }
    }

    private void writeMatchHistoryCSV(java.io.BufferedWriter writer) throws IOException {
        writer.write("Match,Alliance,T1,T2,Auto,Teleop,Total,T1Seq,T1Climb,T2Seq,T2Climb,Submitter,Time,SyncStatus"); writer.newLine();
        for (ScoreEntry s : scoreHistoryList) {
            writer.write(String.format("%d,%s,%d,%d,%d,%d,%d,%b,%b,%b,%b,%s,%s,%s",
                    s.getMatchNumber(), s.getAlliance(), s.getTeam1(), s.getTeam2(), s.getAutoArtifacts(), s.getTeleopArtifacts(),
                    s.getTotalScore(), s.isTeam1CanSequence(), s.isTeam1L2Climb(), s.isTeam2CanSequence(), s.isTeam2L2Climb(), s.getSubmitter(), s.getSubmissionTime(), s.getSyncStatus().name()));
            writer.newLine();
        }
    }

    private void writeRankingsCSV(java.io.BufferedWriter writer) throws IOException {
        writer.write("Rank,Team,Rating,Matches,Auto,Teleop,Accuracy,PenComm,PenOpp,L2Climb,Sequence"); writer.newLine();
        int rank = 1;
        for (TeamRanking r : rankingsTableView.getItems()) {
            writer.write(String.format("%d,%d,%s,%d,%s,%s,%s,%s,%s,%s,%s", rank++, r.getTeamNumber(), r.getRatingFormatted(), r.getMatchesPlayed(), r.getAvgAutoArtifactsFormatted(), r.getAvgTeleopArtifactsFormatted(), r.getAccuracyFormatted(), r.getAvgPenaltyCommittedFormatted(), r.getAvgOpponentPenaltyFormatted(), r.getL2Capable(), r.getCanSequence()));
            writer.newLine();
        }
    }

    private void setUIEnabled(boolean e) { scoringFormVBox.setDisable(!e); }

    @FXML private void handleEditRating() throws IOException { if(isHost) mainApp.showFormulaEditView(currentCompetition); refreshAllDataFromDatabase(); }
    @FXML private void handleManageMembers() throws IOException { mainApp.showCoordinatorView(currentCompetition); }
    @FXML private void handleAllianceAnalysis() throws IOException { mainApp.showAllianceAnalysisView(currentCompetition, currentUsername); }
    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername); }
    @FXML private void handleLogout() throws IOException { mainApp.showLoginView(); }
}