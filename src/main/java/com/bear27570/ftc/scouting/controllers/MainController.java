// File: src/main/java/com/bear27570/ftc/scouting/controllers/MainController.java
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
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainController {

    @FXML private TabPane mainTabPane;
    @FXML private Tab ftcScoutTab;
    @FXML private Label competitionNameLabel;
    @FXML private Button manageMembersBtn, offlineSyncBtn;

    @FXML private TabScoringController tabScoringController;
    @FXML private TabFtcScoutController tabFtcScoutController;
    @FXML private TabRankingsController tabRankingsController;
    @FXML private TabHistoryController tabHistoryController;

    private MainApplication mainApp;
    private Competition currentCompetition;
    private String currentUsername;
    private boolean isHost;

    private MatchDataService matchDataService;
    private RankingService rankingService;
    private CompetitionRepository competitionRepository;
    private UserService userService;
    private FtcScoutApiClient ftcScoutApiClient;

    private String officialEventName = null;

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

        if (currentCompetition.getOfficialEventName() != null && !currentCompetition.getOfficialEventName().isEmpty()) {
            this.officialEventName = currentCompetition.getOfficialEventName();
            NetworkService.getInstance().setOfficialEventName(this.officialEventName);
        }
        updateTopLabel();

        if (offlineSyncBtn != null) {
            offlineSyncBtn.setText(isHost ? "Import Offline Data" : "Export Offline Data");
        }

        tabScoringController.setDependencies(this, matchDataService, currentCompetition, username, isHost);
        tabFtcScoutController.setDependencies(this, ftcScoutApiClient, currentCompetition, isHost);
        tabRankingsController.setDependencies(this, mainApp, currentCompetition, isHost);
        tabHistoryController.setDependencies(this, matchDataService, currentCompetition, username, isHost);

        if (isHost) {
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

            Platform.runLater(() -> mainTabPane.getTabs().remove(ftcScoutTab));
            startAsClient();
        }

        AnimationUtils.attachLightBarAnimation(mainTabPane);

        // ★ 这里是处理 Tab 切换的入口，包装在 runLater 里
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() != null) {
                Platform.runLater(() -> AnimationUtils.playSmoothEntrance(newTab.getContent()));
            }
        });
    }

    private void updateTopLabel() {
        String role = isHost ? " [HOST]" : " [CLIENT]";
        if (officialEventName != null && !officialEventName.trim().isEmpty()) {
            competitionNameLabel.setText(officialEventName + role);
        } else {
            competitionNameLabel.setText(currentCompetition.getName() + role);
        }
    }

    private void startAsHost() {
        refreshAllDataFromDatabase();
        NetworkService.getInstance().startHost(currentCompetition, this::handleScoreReceivedFromClient);
    }

    private void startAsClient() {
        refreshAllDataFromDatabase();
        tabScoringController.setUIEnabled(true);
        try {
            NetworkService.getInstance().connectToHost(currentCompetition.getHostAddress(), currentUsername, this::handleUpdateReceivedFromHost);
            tabScoringController.setStatusText("Authenticating with Host...", "#89dceb");
        } catch (IOException e) {
            tabScoringController.setStatusText("Working Offline (Caching Locally)", "#FFB340");
        }
    }

    public void triggerDataRefreshAndBroadcast() {
        refreshAllDataFromDatabase();
        if (isHost) {
            broadcastUpdate();
        }
    }

    private void refreshAllDataFromDatabase() {
        List<ScoreEntry> fullHistory = matchDataService.getHistory(currentCompetition.getName());
        List<TeamRanking> newRankings = rankingService.calculateRankings(currentCompetition.getName());
        Map<String, Double> reliabilities = rankingService.getSubmitterReliabilities(currentCompetition.getName());

        if (currentCompetition != null && isHost) {
            currentCompetition = competitionRepository.findByName(currentCompetition.getName());
        }

        Platform.runLater(() -> {
            tabHistoryController.updateData(fullHistory, reliabilities);
            tabRankingsController.updateData(newRankings, currentCompetition);
        });
    }

    private void broadcastUpdate() {
        List<ScoreEntry> fullHistory = matchDataService.getHistory(currentCompetition.getName());
        List<TeamRanking> newRankings = rankingService.calculateRankings(currentCompetition.getName());
        NetworkService.getInstance().broadcastUpdateToClients(new NetworkPacket(fullHistory, newRankings, officialEventName));
    }

    private void handleScoreReceivedFromClient(ScoreEntry scoreEntry) {
        scoreEntry.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
        matchDataService.submitScore(currentCompetition.getName(), scoreEntry);
        triggerDataRefreshAndBroadcast();
    }

    private void handleUpdateReceivedFromHost(NetworkPacket packet) {
        Platform.runLater(() -> {
            if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
                if (packet.getOfficialEventName() != null && !packet.getOfficialEventName().isEmpty()) {
                    updateOfficialEventName(packet.getOfficialEventName());
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
                        tabScoringController.setStatusText("Auto-synced " + syncCount + " local records to host.", "#32D74B");
                    }
                } else {
                    tabScoringController.setStatusText("Connected & Synced.", "#32D74B");
                }
                refreshAllDataFromDatabase();
            }
        });
    }

    public void requestEditScore(ScoreEntry entry) {
        mainTabPane.getSelectionModel().select(0);
        tabScoringController.loadScoreForEdit(entry);
    }

    public void onFieldInputConfirmed(int totalHits, String clickLocations) {
        tabScoringController.onFieldInputConfirmed(totalHits, clickLocations);
    }

    public void updateOfficialEventName(String eventName) {
        this.officialEventName = eventName;
        currentCompetition.setOfficialEventName(eventName);
        NetworkService.getInstance().setOfficialEventName(eventName);
        competitionRepository.updateEventInfo(currentCompetition.getName(), currentCompetition.getEventSeason(), currentCompetition.getEventCode(), eventName);
        updateTopLabel();
        triggerDataRefreshAndBroadcast();
    }

    public void openFieldInputView(boolean isAllianceMode, String existingLocations) {
        try {
            mainApp.showFieldInputView(this, isAllianceMode, existingLocations);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML private void handleOfflineSync() {
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
                    triggerDataRefreshAndBroadcast();
                    tabScoringController.setStatusText("Imported " + importedScores.size() + " records!", "#32D74B");
                } catch (Exception e) { tabScoringController.setErrorText("Import Failed."); }
            }
        } else {
            List<ScoreEntry> pending = matchDataService.getPendingExports(currentCompetition.getName());
            if (pending.isEmpty()) { tabScoringController.setStatusText("No pending offline data to export.", "#FF9F0A"); return; }

            fileChooser.setTitle("Export Offline Data");
            fileChooser.setInitialFileName("OfflineSync_" + currentUsername + "_" + System.currentTimeMillis() + ".ftcsync");
            File file = fileChooser.showSaveDialog(competitionNameLabel.getScene().getWindow());

            if (file != null) {
                try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                    out.writeObject(pending);
                    matchDataService.markAsExported(pending.stream().map(ScoreEntry::getId).collect(Collectors.toList()));
                    refreshAllDataFromDatabase();
                    tabScoringController.setStatusText("Exported successfully!", "#32D74B");
                } catch (Exception e) { tabScoringController.setErrorText("Export Failed."); }
            }
        }
    }

    @FXML private void handleExport() {
        final Stage dialog = new Stage();
        dialog.initOwner(competitionNameLabel.getScene().getWindow());
        dialog.setTitle("Export Data");

        // 使用 mac-card 统一风格
        VBox dialogVbox = new VBox(20);
        dialogVbox.setAlignment(javafx.geometry.Pos.CENTER);
        dialogVbox.setPadding(new javafx.geometry.Insets(30));
        dialogVbox.getStyleClass().add("mac-card"); // ✨ 替换掉原来的硬编码 style

        Label headerLabel = new Label("Export CSV Data");
        headerLabel.getStyleClass().add("title-3"); // ✨ 应用 Audiowide 标题字体

        Button btnHistory = new Button("Export Match History");
        btnHistory.getStyleClass().add("button"); // ✨ 应用按钮光效
        btnHistory.setOnAction(e -> { dialog.close(); exportData("Match History"); });

        Button btnRankings = new Button("Export Team Rankings");
        btnRankings.getStyleClass().add("button");
        btnRankings.setOnAction(e -> { dialog.close(); exportData("Team Rankings"); });

        Button btnCancel = new Button("Cancel");
        btnCancel.getStyleClass().addAll("button", "danger"); // ✨ 取消按钮用红色
        btnCancel.setOnAction(e -> dialog.close());

        dialogVbox.getChildren().addAll(headerLabel, btnHistory, btnRankings, btnCancel);

        Scene scene = new Scene(dialogVbox, 400, 300);
        // ✨ UI 补丁：注入全局 CSS
        try {
            scene.getStylesheets().add(getClass().getResource("/com/bear27570/ftc/scouting/styles/style.css").toExternalForm());
        } catch (Exception e) {}

        dialog.setScene(scene);
        dialog.show();
    }

    private void exportData(String type) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());
        fileChooser.setInitialFileName(currentCompetition.getName() + "_" + type.replace(" ", "") + "_" + timestamp + ".csv");
        File file = fileChooser.showSaveDialog(competitionNameLabel.getScene().getWindow());
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                if (type.equals("Match History")) writeMatchHistoryCSV(writer); else writeRankingsCSV(writer);
                tabScoringController.setStatusText("Exported successfully to " + file.getName(), "#32D74B");
            } catch (IOException e) { tabScoringController.setErrorText("Export Failed: " + e.getMessage()); }
        }
    }

    private void writeMatchHistoryCSV(BufferedWriter writer) throws IOException {
        writer.write("Match,Alliance,T1,T2,T1Auto,T2Auto,T1Proj,T2Proj,T1Row,T2Row,Teleop,Total,T1Seq,T1Climb,T2Seq,T2Climb,Submitter,Time,SyncStatus");
        writer.newLine();
        for (ScoreEntry s : tabHistoryController.getScoreHistoryList()) {
            writer.write(String.format("%d,%s,%d,%d,%d,%d,%s,%s,%s,%s,%d,%d,%b,%b,%b,%b,%s,%s,%s",
                    s.getMatchNumber(), s.getAlliance(), s.getTeam1(), s.getTeam2(),
                    s.getTeam1AutoScore(), s.getTeam2AutoScore(), s.getTeam1AutoProj(), s.getTeam2AutoProj(), s.getTeam1AutoRow(), s.getTeam2AutoRow(),
                    s.getTeleopArtifacts(), s.getTotalScore(),
                    s.isTeam1CanSequence(), s.isTeam1L2Climb(), s.isTeam2CanSequence(), s.isTeam2L2Climb(),
                    s.getSubmitter(), s.getSubmissionTime(), s.getSyncStatus().name()));
            writer.newLine();
        }
    }

    private void writeRankingsCSV(BufferedWriter writer) throws IOException {
        writer.write("Rank,Team,Rating,Matches,Auto,Teleop,Accuracy,PenComm,PenOpp,L2Climb,Sequence"); writer.newLine();
        int rank = 1;
        for (TeamRanking r : tabRankingsController.getRankingsList()) {
            writer.write(String.format("%d,%d,%s,%d,%s,%s,%s,%s,%s,%s,%s", rank++, r.getTeamNumber(), r.getRatingFormatted(), r.getMatchesPlayed(), r.getAvgAutoArtifactsFormatted(), r.getAvgTeleopArtifactsFormatted(), r.getAccuracyFormatted(), r.getAvgPenaltyCommittedFormatted(), r.getAvgOpponentPenaltyFormatted(), r.getL2Capable(), r.getCanSequence()));
            writer.newLine();
        }
    }

    @FXML private void handleManageMembers() throws IOException { mainApp.showCoordinatorView(currentCompetition); }
    @FXML private void handleAllianceAnalysis() throws IOException { mainApp.showAllianceAnalysisView(currentCompetition, currentUsername); }
    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername); }
    @FXML private void handleLogout() throws IOException { NetworkService.getInstance().stop(); mainApp.showLoginView(); }
}