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
import com.bear27570.ftc.scouting.utils.FxThread;
import com.bear27570.ftc.scouting.viewmodels.SharedDataViewModel;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MainController {

    @FXML private TabPane mainTabPane;
    @FXML private Tab ftcScoutTab;
    @FXML private Label competitionNameLabel;
    @FXML private Button manageMembersBtn, offlineSyncBtn;
    @FXML private HBox toastCapsule;
    @FXML private Label toastLabel;
    @FXML private org.kordamp.ikonli.javafx.FontIcon toastIcon;

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
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private final SharedDataViewModel sharedViewModel = new SharedDataViewModel();

    public static class SyncPayload implements Serializable {
        public String exportId;
        public String competitionName;
        public String submitter;
        public int exportSequence;
        public int maxMatchNumber;
        public List<ScoreEntry> data;
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

        tabRankingsController.setDependencies(this, mainApp, currentCompetition, isHost, sharedViewModel);
        tabHistoryController.setDependencies(this, matchDataService, currentCompetition, username, isHost, sharedViewModel);

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

        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() != null) {
                FxThread.run(() -> AnimationUtils.playSmoothEntrance(newTab.getContent()));
            }
        });
    }

    private void updateTopLabel() {
        String role = isHost ? " [HOST]" : " [CLIENT]";
        FxThread.run(() -> {
            if (officialEventName != null && !officialEventName.trim().isEmpty()) {
                competitionNameLabel.setText(officialEventName + role);
            } else {
                competitionNameLabel.setText(currentCompetition.getName() + role);
            }
        });
    }

    private void startAsHost() {
        refreshAllDataFromDatabase();
        NetworkService.getInstance().startHost(currentCompetition, this::handleScoreReceivedFromClient);
    }

    private void startAsClient() {
        refreshAllDataFromDatabase();
        tabScoringController.setUIEnabled(true);
        tabScoringController.getViewModel().setStatus("Authenticating with Host...", "#89dceb");

        NetworkService.getInstance().connectToHost(currentCompetition.getHostAddress(), currentUsername, this::handleUpdateReceivedFromHost)
                .thenAccept(connected -> {
                    if (!connected) {
                        tabScoringController.getViewModel().setStatus("Working Offline (Caching Locally)", "#FFB340");
                    }
                })
                .exceptionally(ex -> {
                    tabScoringController.getViewModel().setStatus("Working Offline (Host unreachable)", "#FFB340");
                    return null;
                });
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

        sharedViewModel.updateData(fullHistory, newRankings, reliabilities);
        tabRankingsController.updateCompetition(currentCompetition);
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
        if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
            if (packet.getOfficialEventName() != null && !packet.getOfficialEventName().isEmpty()) {
                FxThread.run(() -> updateOfficialEventName(packet.getOfficialEventName()));
            }

            matchDataService.syncWithHostData(currentCompetition.getName(), packet.getScoreHistory());
            List<ScoreEntry> pending = matchDataService.getPendingExports(currentCompetition.getName());

            if (!pending.isEmpty()) {
                AtomicInteger syncCount = new AtomicInteger(0);
                List<CompletableFuture<Void>> futures = pending.stream().map(s -> {
                    int localId = s.getId();
                    s.setId(0);
                    return NetworkService.getInstance().sendScoreToServer(s).thenAccept(sent -> {
                        if (sent) {
                            matchDataService.deleteScore(localId);
                            syncCount.incrementAndGet();
                        }
                    });
                }).collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> FxThread.run(() -> {
                            if (syncCount.get() > 0) {
                                tabScoringController.getViewModel().setStatus("Auto-synced " + syncCount.get() + " local records to host.", "#32D74B");
                            }
                            refreshAllDataFromDatabase();
                        }));
            } else {
                tabScoringController.getViewModel().setStatus("Connected & Synced.", "#32D74B");
                refreshAllDataFromDatabase();
            }
        }
    }

    public void requestEditScore(ScoreEntry entry) {
        FxThread.run(() -> {
            mainTabPane.getSelectionModel().select(0);
            tabScoringController.loadScoreForEdit(entry);
        });
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
        try { mainApp.showFieldInputView(this, isAllianceMode, existingLocations); } catch (IOException e) {
            log.error("Failed to open field input view", e);
        }
    }

    @FXML
    private void handleOfflineSync() {
        Gson gson = new Gson();
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);

        if (isHost) {
            // 主机模式：选择目录，批量扫描导入
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Select Directory to Auto-Import Scouter Data");
            File dir = dirChooser.showDialog(competitionNameLabel.getScene().getWindow());

            if (dir != null) {
                // 自动搜索目录下所有 .ftcsync 结尾的文件
                File[] syncFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".ftcsync"));

                if (syncFiles == null || syncFiles.length == 0) {
                    showToast("No .ftcsync files found.", false);
                    tabScoringController.getViewModel().setStatus("No .ftcsync files found in directory.", "#FF9F0A");
                    return;
                }

                String importedPrefsKey = "imported_sync_ids_" + currentCompetition.getName();
                String importedIds = prefs.get(importedPrefsKey, "");

                int totalImportedRecords = 0;
                int filesProcessed = 0;
                int duplicatesSkipped = 0;

                // 遍历解析所有数据包
                for (File file : syncFiles) {
                    try (FileInputStream fis = new FileInputStream(file);
                         GZIPInputStream gis = new GZIPInputStream(fis);
                         InputStreamReader reader = new InputStreamReader(gis, StandardCharsets.UTF_8)) {

                        SyncPayload payload = gson.fromJson(reader, SyncPayload.class);

                        if (payload == null || payload.data == null || payload.data.isEmpty()) {
                            log.warn("Skipping empty or invalid file: {}", file.getName());
                            continue;
                        }

                        // 防呆拦截：利用 exportId 识别已经导入过的数据包
                        if (importedIds.contains(payload.exportId)) {
                            duplicatesSkipped++;
                            continue;
                        }

                        // 执行数据合并
                        matchDataService.syncWithHostData(currentCompetition.getName(), payload.data);
                        importedIds += payload.exportId + ";";

                        totalImportedRecords += payload.data.size();
                        filesProcessed++;

                    } catch (Exception e) {
                        log.error("Failed to import file: " + file.getName(), e);
                    }
                }

                // 统一保存已导入的记录标记
                prefs.put(importedPrefsKey, importedIds);

                // 根据处理结果更新 UI 与 Toast
                if (filesProcessed > 0) {
                    triggerDataRefreshAndBroadcast();
                    String successMsg = String.format("Imported %d records from %d files!", totalImportedRecords, filesProcessed);
                    showToast(successMsg, true);
                    tabScoringController.getViewModel().setStatus(successMsg + " (" + duplicatesSkipped + " duplicates skipped)", "#32D74B");
                } else if (duplicatesSkipped > 0) {
                    showToast("Skipped " + duplicatesSkipped + " duplicate files.", false);
                    int finalDuplicatesSkipped = duplicatesSkipped;
                    FxThread.run(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "扫描完成：目录下的 " + finalDuplicatesSkipped + " 个文件之前已成功导入，系统已自动跳过。", ButtonType.OK);
                        alert.setHeaderText("无需重复导入");
                        try {
                            alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/bear27570/ftc/scouting/styles/style.css").toExternalForm());
                            alert.getDialogPane().getStyleClass().add("mac-card");
                        } catch (Exception ignored) {}
                        alert.showAndWait();
                    });
                    tabScoringController.getViewModel().setStatus("All found files were already imported.", "#FF9F0A");
                } else {
                    showToast("Import Failed: No valid data.", false);
                    tabScoringController.getViewModel().setError("Import Failed: No valid data could be read.");
                }
            }
        } else {
            // 从机模式：收集本地未同步数据，打包导出为单个文件
            FileChooser fileChooser = new FileChooser();
            List<ScoreEntry> pending = matchDataService.getPendingExports(currentCompetition.getName());

            if (pending.isEmpty()) {
                showToast("No pending data to export.", false);
                tabScoringController.getViewModel().setStatus("No pending offline data to export.", "#FF9F0A");
                return;
            }

            String prefKey = "export_seq_" + currentCompetition.getName() + "_" + currentUsername;
            int seq = prefs.getInt(prefKey, 1);
            int maxMatch = pending.stream().mapToInt(ScoreEntry::getMatchNumber).max().orElse(0);

            SyncPayload payload = new SyncPayload();
            payload.exportId = UUID.randomUUID().toString();
            payload.competitionName = currentCompetition.getName();
            payload.submitter = currentUsername;
            payload.exportSequence = seq;
            payload.maxMatchNumber = maxMatch;
            payload.data = pending;

            fileChooser.setTitle("Export Data to Host (.ftcsync)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Scouter Sync Data (*.ftcsync)", "*.ftcsync"));

            String defaultName = String.format("[交回主机]侦查数据_%s_第%d次导出_至Match%d.ftcsync", currentUsername, seq, maxMatch);
            fileChooser.setInitialFileName(defaultName);

            File file = fileChooser.showSaveDialog(competitionNameLabel.getScene().getWindow());

            if (file != null) {
                try (FileOutputStream fos = new FileOutputStream(file);
                     GZIPOutputStream gos = new GZIPOutputStream(fos);
                     OutputStreamWriter writer = new OutputStreamWriter(gos, StandardCharsets.UTF_8)) {

                    gson.toJson(payload, writer);

                    prefs.putInt(prefKey, seq + 1);
                    matchDataService.markAsExported(pending.stream().map(ScoreEntry::getId).collect(Collectors.toList()));
                    refreshAllDataFromDatabase();

                    showToast("Exported successfully!", true);
                    tabScoringController.getViewModel().setStatus("Exported successfully! Send this to Host.", "#32D74B");
                } catch (Exception e) {
                    log.error("Export Failed", e);
                    showToast("Export Failed", false);
                    tabScoringController.getViewModel().setError("Export Failed: " + e.getMessage());
                }
            }
        }
    }
    private void showToast(String message, boolean isSuccess) {
        FxThread.run(() -> {
            toastLabel.setText(message);
            if (isSuccess) {
                toastCapsule.setStyle("-fx-background-color: #32D74B;"); // 成功绿
                if (toastIcon != null) toastIcon.setIconLiteral("fth-check-circle");
            } else {
                toastCapsule.setStyle("-fx-background-color: #F87171;"); // 错误红
                if (toastIcon != null) toastIcon.setIconLiteral("fth-alert-triangle");
            }
            // 复用 AnimationUtils 里的进退场动画
            AnimationUtils.playWelcomeToast(toastCapsule);
        });
    }

    @FXML private void handleExport() {
        final Stage dialog = new Stage();
        dialog.initOwner(competitionNameLabel.getScene().getWindow());
        dialog.setTitle("Export Data");

        VBox dialogVbox = new VBox(20);
        dialogVbox.setAlignment(javafx.geometry.Pos.CENTER);
        dialogVbox.setPadding(new javafx.geometry.Insets(30));
        dialogVbox.getStyleClass().add("mac-card");

        Label headerLabel = new Label("Export CSV Data");
        headerLabel.getStyleClass().add("title-3");

        Button btnHistory = new Button("Export Match History");
        btnHistory.getStyleClass().add("button");
        btnHistory.setOnAction(e -> { dialog.close(); exportData("Match History"); });

        Button btnRankings = new Button("Export Team Rankings");
        btnRankings.getStyleClass().add("button");
        btnRankings.setOnAction(e -> { dialog.close(); exportData("Team Rankings"); });

        Button btnCancel = new Button("Cancel");
        btnCancel.getStyleClass().addAll("button", "danger");
        btnCancel.setOnAction(e -> dialog.close());

        dialogVbox.getChildren().addAll(headerLabel, btnHistory, btnRankings, btnCancel);

        Scene scene = new Scene(dialogVbox, 400, 300);
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
                tabScoringController.getViewModel().setStatus("Exported successfully to " + file.getName(), "#32D74B");
            } catch (IOException e) { tabScoringController.getViewModel().setError("Export Failed: " + e.getMessage()); }
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
    @FXML private void handleBackButton() throws IOException { mainApp.showHubView(currentUsername,false); }
    @FXML private void handleLogout() throws IOException { NetworkService.getInstance().stop(); mainApp.showLoginView(); }
}