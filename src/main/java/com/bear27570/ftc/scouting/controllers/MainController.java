// File: MainController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;
import com.bear27570.ftc.scouting.services.domain.UserService;
import com.bear27570.ftc.scouting.services.network.FtcScoutApiClient;
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
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainController {

    @FXML private TabPane mainTabPane;

    @FXML private Label competitionNameLabel, submitterLabel, errorLabel, statusLabel;
    @FXML private Button manageMembersBtn, submitButton, editRatingButton, offlineSyncBtn;

    // Scoring Tab
    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;

    // 精细化 Auto 阶段面板
    @FXML private VBox team2AutoBox;
    @FXML private TextField t1AutoScoreField, t2AutoScoreField;
    @FXML private ToggleButton t1ProjNearBtn, t1ProjFarBtn, t2ProjNearBtn, t2ProjFarBtn;
    @FXML private ToggleButton t1Row1Btn, t1Row2Btn, t1Row3Btn;
    @FXML private ToggleButton t2Row1Btn, t2Row2Btn, t2Row3Btn;

    @FXML private TextField teleopArtifactsField;

    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck;
    @FXML private CheckBox team1IgnoreCheck, team2IgnoreCheck;
    @FXML private CheckBox team1BrokenCheck, team2BrokenCheck;

    @FXML private VBox team2IgnoreBox, team2CapsBox;
    @FXML private Label lblTeam2;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck;

    // Penalty / FTCScout Tab
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
    private ToggleGroup t1ProjGroup, t2ProjGroup;
    // 注意：去掉了 t1RowGroup 和 t2RowGroup，允许单队多选

    private String currentClickLocations = "";

    // 依赖注入的服务
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

    private Map<String, Double> submitterReliabilityMap = new java.util.HashMap<>();

    private void updateTopLabel() {
        String role = isHost ? " [HOST]" : " [CLIENT]";
        if (officialEventName != null && !officialEventName.trim().isEmpty()) {
            competitionNameLabel.setText(officialEventName + role);
        } else {
            competitionNameLabel.setText(currentCompetition.getName() + role);
        }
    }

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

        Competition localComp = competitionRepository.findByName(competition.getName());
        if (localComp != null && localComp.getOfficialEventName() != null && !localComp.getOfficialEventName().trim().isEmpty()) {
            this.currentCompetition.setOfficialEventName(localComp.getOfficialEventName());
            this.currentCompetition.setEventSeason(localComp.getEventSeason());
            this.currentCompetition.setEventCode(localComp.getEventCode());
        }

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
            if (this.userService != null && currentCompetition.getCreatorUsername() != null) {
                this.userService.register(currentCompetition.getCreatorUsername(), "dummy_password_for_fk");
            }
            competitionRepository.ensureLocalCompetitionSync(currentCompetition);

            manageMembersBtn.setVisible(false);
            manageMembersBtn.setManaged(false);

            Platform.runLater(() -> {
                if (ftcScoutSeasonField != null) {
                    javafx.scene.Node current = ftcScoutSeasonField;
                    TabPane foundTabPane = null;
                    while (current != null) {
                        if (current instanceof TabPane) {
                            foundTabPane = (TabPane) current;
                            break;
                        }
                        current = current.getParent();
                    }
                    if (foundTabPane != null) {
                        foundTabPane.getTabs().removeIf(tab -> tab.getContent() != null && isDescendant(ftcScoutSeasonField, tab.getContent()));
                    } else {
                        if (ftcScoutSeasonField.getParent() != null) {
                            ftcScoutSeasonField.getParent().setVisible(false);
                            ftcScoutSeasonField.getParent().setManaged(false);
                        }
                    }
                }
            });

            startAsClient();
        }
    }

    private boolean isDescendant(javafx.scene.Node node, javafx.scene.Node parent) {
        while (node != null) {
            if (node == parent) return true;
            node = node.getParent();
        }
        return false;
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

    // 核心逻辑：设置多队之间对同一个Row的互斥“抢夺”
    private void setupRowExclusivity(ToggleButton t1Btn, ToggleButton t2Btn) {
        t1Btn.selectedProperty().addListener((obs, oldVal, isSelected) -> {
            if (isSelected) t2Btn.setSelected(false);
        });
        t2Btn.selectedProperty().addListener((obs, oldVal, isSelected) -> {
            if (isSelected) t1Btn.setSelected(false);
        });
    }

    private void setupScoringTab() {
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup);
        blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);

        t1ProjGroup = new ToggleGroup(); t1ProjNearBtn.setToggleGroup(t1ProjGroup); t1ProjFarBtn.setToggleGroup(t1ProjGroup);
        t2ProjGroup = new ToggleGroup(); t2ProjNearBtn.setToggleGroup(t2ProjGroup); t2ProjFarBtn.setToggleGroup(t2ProjGroup);

        // ★ 跨队 Row 互斥逻辑
        setupRowExclusivity(t1Row1Btn, t2Row1Btn);
        setupRowExclusivity(t1Row2Btn, t2Row2Btn);
        setupRowExclusivity(t1Row3Btn, t2Row3Btn);

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
            if (team2AutoBox != null) { team2AutoBox.setVisible(!isS); team2AutoBox.setManaged(!isS); }
            if (isS) {
                if (team2IgnoreCheck != null) team2IgnoreCheck.setSelected(false);
                if (team2BrokenCheck != null) team2BrokenCheck.setSelected(false);
                if (team2SequenceCheck != null) team2SequenceCheck.setSelected(false);
                if (team2L2ClimbCheck != null) team2L2ClimbCheck.setSelected(false);
                t2AutoScoreField.setText("0");
                t2ProjGroup.selectToggle(null);

                t2Row1Btn.setSelected(false);
                t2Row2Btn.setSelected(false);
                t2Row3Btn.setSelected(false);
            }
        });

        if (team1IgnoreCheck != null) team1IgnoreCheck.setDisable(true);
        if (team2IgnoreCheck != null) team2IgnoreCheck.setDisable(true);

        team1Field.textProperty().addListener((obs, old, newVal) -> updateWeakCheckboxStatus(newVal, team1IgnoreCheck));
        team2Field.textProperty().addListener((obs, old, newVal) -> updateWeakCheckboxStatus(newVal, team2IgnoreCheck));
    }

    private String getSelectedToggleText(ToggleGroup group) {
        ToggleButton selected = (ToggleButton) group.getSelectedToggle();
        return selected == null ? "NONE" : selected.getText().toUpperCase().replace(" ", "");
    }

    // 获取多选的 Row，用空格分隔，例如 "R1 R3"
    private String getSelectedRowsString(ToggleButton r1, ToggleButton r2, ToggleButton r3) {
        List<String> selected = new ArrayList<>();
        if (r1.isSelected()) selected.add("R1");
        if (r2.isSelected()) selected.add("R2");
        if (r3.isSelected()) selected.add("R3");
        return selected.isEmpty() ? "NONE" : String.join(" ", selected);
    }

    private void setToggleGroupByText(ToggleGroup group, String text) {
        group.selectToggle(null);
        if (text == null || text.equals("NONE")) return;
        for (Toggle toggle : group.getToggles()) {
            if (((ToggleButton) toggle).getText().toUpperCase().replace(" ", "").equals(text)) {
                group.selectToggle(toggle);
                break;
            }
        }
    }

    // 恢复多选的 Row 状态
    private void setRowsFromText(String text, ToggleButton r1, ToggleButton r2, ToggleButton r3) {
        r1.setSelected(text != null && text.contains("R1"));
        r2.setSelected(text != null && text.contains("R2"));
        r3.setSelected(text != null && text.contains("R3"));
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

        rankingLegendLabel.setText("Penalty: Major=15, Minor=5. Auto Score now correctly skips matches with 0 points from average calculation.");
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

            int t1AutoScore = Integer.parseInt(t1AutoScoreField.getText().trim());
            int t2AutoScore = isSingle ? 0 : Integer.parseInt(t2AutoScoreField.getText().trim());
            String t1Proj = getSelectedToggleText(t1ProjGroup);
            String t2Proj = isSingle ? "NONE" : getSelectedToggleText(t2ProjGroup);

            // ★ 获取由空格分隔的多选 Row 字符串
            String t1Row = getSelectedRowsString(t1Row1Btn, t1Row2Btn, t1Row3Btn);
            String t2Row = isSingle ? "NONE" : getSelectedRowsString(t2Row1Btn, t2Row2Btn, t2Row3Btn);

            ScoreEntry entry = new ScoreEntry(
                    editingScoreId == -1 ? 0 : editingScoreId,
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    Integer.parseInt(matchNumberField.getText()), alliance,
                    Integer.parseInt(team1Field.getText()), isSingle ? 0 : Integer.parseInt(team2Field.getText()),
                    t1AutoScore, t2AutoScore, t1Proj, t2Proj, t1Row, t2Row,
                    Integer.parseInt(teleopArtifactsField.getText()),
                    team1SequenceCheck.isSelected(), isSingle ? false : team2SequenceCheck.isSelected(),
                    team1L2ClimbCheck.isSelected(), isSingle ? false : team2L2ClimbCheck.isSelected(),
                    team1IgnoreCheck.isSelected(), isSingle ? false : team2IgnoreCheck.isSelected(),
                    team1BrokenCheck.isSelected(), isSingle ? false : team2BrokenCheck.isSelected(),
                    currentClickLocations, currentUsername,
                    editingScoreId == -1 ? null : editingOriginalTime,
                    editingScoreId == -1 ? ScoreEntry.SyncStatus.UNSYNCED : editingOriginalSyncStatus
            );

            if (isHost) {
                entry.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                matchDataService.submitScore(currentCompetition.getName(), entry);
                broadcastUpdate();
                refreshAllDataFromDatabase();
                errorLabel.setText("Score saved and broadcasted!");
            } else {
                boolean sent = NetworkService.getInstance().sendScoreToServer(entry);
                if (sent) {
                    errorLabel.setText("Score sent to host!");
                } else {
                    entry.setSyncStatus(ScoreEntry.SyncStatus.UNSYNCED);
                    matchDataService.submitScore(currentCompetition.getName(), entry);
                    refreshAllDataFromDatabase();
                    errorLabel.setText("Score saved locally (Offline mode).");
                }
            }
            resetFormState();
        } catch (Exception e) { errorLabel.setText("Error: " + e.getMessage()); }
    }

    private void resetFormState() {
        editingScoreId = -1; editingOriginalTime = null; editingOriginalSyncStatus = null;
        if(submitButton != null) { submitButton.setText("Submit Score"); submitButton.setStyle(""); }
        submitterLabel.setText("Submitter: " + currentUsername); submitterLabel.setStyle("");
        currentClickLocations = "";
        t1AutoScoreField.setText("0"); t2AutoScoreField.setText("0"); teleopArtifactsField.setText("0");
        t1ProjGroup.selectToggle(null); t2ProjGroup.selectToggle(null);

        t1Row1Btn.setSelected(false); t1Row2Btn.setSelected(false); t1Row3Btn.setSelected(false);
        t2Row1Btn.setSelected(false); t2Row2Btn.setSelected(false); t2Row3Btn.setSelected(false);

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
        submitterReliabilityMap = rankingService.getSubmitterReliabilities(currentCompetition.getName());
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
                if (packet.getOfficialEventName() != null && !packet.getOfficialEventName().isEmpty()) {
                    this.officialEventName = packet.getOfficialEventName();
                    currentCompetition.setOfficialEventName(this.officialEventName);
                    competitionRepository.updateEventInfo(currentCompetition.getName(), currentCompetition.getEventSeason(), currentCompetition.getEventCode(), this.officialEventName);
                    updateTopLabel();
                }
                matchDataService.syncWithHostData(currentCompetition.getName(), packet.getScoreHistory());
                List<ScoreEntry> pending = matchDataService.getPendingExports(currentCompetition.getName());
                if (!pending.isEmpty()) {
                    int syncCount = 0;
                    for (ScoreEntry s : pending) {
                        int localId = s.getId();
                        s.setId(0);

                        if (NetworkService.getInstance().sendScoreToServer(s)) {
                            matchDataService.deleteScore(localId);
                            syncCount++;
                        }
                    }
                    if (syncCount > 0) {
                        statusLabel.setText("Auto-synced " + syncCount + " local records to host.");
                    }
                } else {
                    statusLabel.setText("Connected & Synced.");
                }
                refreshAllDataFromDatabase();
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

        histSubmitterCol.setCellFactory(column -> new TableCell<ScoreEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Double weight = submitterReliabilityMap.getOrDefault(item, 1.0);
                    if (weight < 1.0) {
                        setText(item + " (Low Rel)");
                        setStyle("-fx-text-fill: #F87171 !important; -fx-font-weight: bold;");
                        FontIcon warningIcon = new FontIcon("fth-alert-triangle");
                        warningIcon.setIconSize(14);
                        warningIcon.setIconColor(Color.web("#F87171"));
                        setGraphic(warningIcon);
                    } else {
                        setText(item);
                        setGraphic(null);
                        setStyle("");
                    }
                }
            }
        });
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
        team1Field.setText(String.valueOf(selected.getTeam1()));
        team2Field.setText(String.valueOf(selected.getTeam2()));

        t1AutoScoreField.setText(String.valueOf(selected.getTeam1AutoScore()));
        t2AutoScoreField.setText(String.valueOf(selected.getTeam2AutoScore()));
        setToggleGroupByText(t1ProjGroup, selected.getTeam1AutoProj());
        setToggleGroupByText(t2ProjGroup, selected.getTeam2AutoProj());

        setRowsFromText(selected.getTeam1AutoRow(), t1Row1Btn, t1Row2Btn, t1Row3Btn);
        setRowsFromText(selected.getTeam2AutoRow(), t2Row1Btn, t2Row2Btn, t2Row3Btn);

        teleopArtifactsField.setText(String.valueOf(selected.getTeleopArtifacts()));

        if ("RED".equalsIgnoreCase(selected.getAlliance())) redAllianceToggle.setSelected(true); else blueAllianceToggle.setSelected(true);
        if (selected.getScoreType() == ScoreEntry.Type.SINGLE) singleModeRadio.setSelected(true); else allianceModeRadio.setSelected(true);

        team1SequenceCheck.setSelected(selected.isTeam1CanSequence()); team2SequenceCheck.setSelected(selected.isTeam2CanSequence());
        team1L2ClimbCheck.setSelected(selected.isTeam1L2Climb()); team2L2ClimbCheck.setSelected(selected.isTeam2L2Climb());
        if(team1IgnoreCheck != null) { team1IgnoreCheck.setDisable(false); team1IgnoreCheck.setSelected(selected.isTeam1Ignored()); }
        if(team2IgnoreCheck != null) { team2IgnoreCheck.setDisable(false); team2IgnoreCheck.setSelected(selected.isTeam2Ignored()); }
        team1BrokenCheck.setSelected(selected.isTeam1Broken()); team2BrokenCheck.setSelected(selected.isTeam2Broken());

        currentClickLocations = selected.getClickLocations();
        errorLabel.setText("Editing record loaded.");

        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().select(0);
        }
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
        writer.write("Match,Alliance,T1,T2,T1Auto,T2Auto,T1Proj,T2Proj,T1Row,T2Row,Teleop,Total,T1Seq,T1Climb,T2Seq,T2Climb,Submitter,Time,SyncStatus");
        writer.newLine();
        for (ScoreEntry s : scoreHistoryList) {
            writer.write(String.format("%d,%s,%d,%d,%d,%d,%s,%s,%s,%s,%d,%d,%b,%b,%b,%b,%s,%s,%s",
                    s.getMatchNumber(), s.getAlliance(), s.getTeam1(), s.getTeam2(),
                    s.getTeam1AutoScore(), s.getTeam2AutoScore(), s.getTeam1AutoProj(), s.getTeam2AutoProj(), s.getTeam1AutoRow(), s.getTeam2AutoRow(),
                    s.getTeleopArtifacts(), s.getTotalScore(),
                    s.isTeam1CanSequence(), s.isTeam1L2Climb(), s.isTeam2CanSequence(), s.isTeam2L2Climb(),
                    s.getSubmitter(), s.getSubmissionTime(), s.getSyncStatus().name()));
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