// File: src/main/java/com/bear27570/ftc/scouting/controllers/HubController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.CompetitionService;
import com.bear27570.ftc.scouting.utils.FxThread;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.prefs.Preferences;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class HubController {

    @FXML private Label welcomeLabel, statusLabel;
    @FXML private Button hostModeButton, joinModeButton;
    @FXML private VBox hostPane, joinPane;
    @FXML private ListView<Competition> myCompetitionsListView;
    @FXML private ListView<Competition> discoveredCompetitionsListView;
    @FXML private TextField newCompetitionField;
    @FXML private HBox toastCapsule;
    @FXML private Label toastLabel;
    @FXML private Button startHostBtn;

    private MainApplication mainApp;
    private String currentUsername;
    private CompetitionService competitionService;

    private static final Logger log = LoggerFactory.getLogger(HubController.class);

    private ObservableList<Competition> discoveredCompetitions = FXCollections.observableArrayList();

    public static class CompPayload implements Serializable {
        public String exportId;
        public int exportSequence;
        public Competition competition;
    }

    // File: src/main/java/com/bear27570/ftc/scouting/controllers/HubController.java

    // 修改方法签名，增加 boolean isFromLogin
    public void setDependencies(MainApplication mainApp, String username, CompetitionService competitionService, boolean isFromLogin) {
        this.mainApp = mainApp;
        this.currentUsername = username;
        this.competitionService = competitionService;

        welcomeLabel.setText("Welcome, " + username + "!");
        discoveredCompetitionsListView.setItems(discoveredCompetitions);

        hostPane.setVisible(false); hostPane.setManaged(false);
        joinPane.setVisible(false); joinPane.setManaged(false);

        refreshMyCompetitionsList();

        if (isFromLogin) {
            toastLabel.setText("Welcome back " + username);
            Platform.runLater(() -> AnimationUtils.playWelcomeToast(toastCapsule));
        }
        myCompetitionsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && startHostBtn != null) {
                if (newVal.getCreatorUsername().equals(currentUsername)) {
                    startHostBtn.setText("Start Hosting");
                } else {
                    startHostBtn.setText("Offline Scoring");
                }
            }
        });
    }

    @FXML
    private void selectHostMode() {
        if (!hostPane.isVisible()) {
            NetworkService.getInstance().stop();

            hostPane.setVisible(true); hostPane.setManaged(true);
            joinPane.setVisible(false); joinPane.setManaged(false);

            statusLabel.setStyle("-fx-text-fill: #A1A1AA;"); // 改用新的暗色
            statusLabel.setText("Host Mode: Select a competition to start.");

            // 【关键修复】不要写死 style，改用移除和添加 class
            hostModeButton.getStyleClass().add("mode-button-active");
            joinModeButton.getStyleClass().remove("mode-button-active");

            AnimationUtils.playSmoothEntrance(hostPane);
        }
    }

    @FXML
    private void selectJoinMode() {
        if (!joinPane.isVisible()) {
            NetworkService.getInstance().stop();
            joinModeButton.setDisable(false);
            joinModeButton.setText("Join Competition");

            hostPane.setVisible(false); hostPane.setManaged(false);
            joinPane.setVisible(true); joinPane.setManaged(true);

            statusLabel.setStyle("-fx-text-fill: #A1A1AA;");
            statusLabel.setText("Searching for local competitions via UDP...");

            joinModeButton.getStyleClass().add("mode-button-active");
            hostModeButton.getStyleClass().remove("mode-button-active");

            discoveredCompetitions.clear();
            NetworkService.getInstance().startDiscovery(discoveredCompetitions);

            AnimationUtils.playSmoothEntrance(joinPane);
        }
    }

    private void refreshMyCompetitionsList() {
        myCompetitionsListView.setItems(FXCollections.observableArrayList(
                competitionService.getCompetitionsCreatedByUser(currentUsername)
        ));
    }

    @FXML
    private void handleCreateButton() {
        String newName = newCompetitionField.getText();

        if (competitionService.createCompetition(newName, currentUsername)) {
            newCompetitionField.clear();
            refreshMyCompetitionsList();
            statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
            statusLabel.setText("Created successfully: " + newName);
        } else {
            statusLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
            statusLabel.setText("Error: Name is empty or already exists.");
        }
    }

    @FXML
    private void handleHostButton() throws IOException {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Please select a competition first!");
            return;
        }

        // 💡 核心修复：根据创建者动态判断身份角色
        // 如果是你创建的，你是 Host (true)
        // 如果不是你创建的（说明是从文件导入的），你是离线 Scouter (false)
        boolean isHostRole = selected.getCreatorUsername().equals(currentUsername);

        mainApp.showScoringView(selected, currentUsername, isHostRole);
    }

    @FXML
    private void handleJoinButton() {
        Competition selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Please select a competition from the discovery list.");
            return;
        }

        statusLabel.setStyle("-fx-text-fill: #89dceb;");
        statusLabel.setText("Connecting to " + selected.getName() + "...");

        joinModeButton.setDisable(true);
        joinModeButton.setText("Connecting...");

        NetworkService.getInstance().connectToHost(selected.getHostAddress(), currentUsername, (packet) -> {
            if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
                FxThread.run(() -> {
                    try { mainApp.showScoringView(selected, currentUsername, false); } catch (Exception e) {}
                });
            }
        }).thenAccept(isApproved -> FxThread.run(() -> {
            if (isApproved) {
                try { mainApp.showScoringView(selected, currentUsername, false); } catch (Exception e) {}
            } else {
                statusLabel.setStyle("-fx-text-fill: #FBBF24; -fx-font-weight: bold;");
                statusLabel.setText("Request sent! Waiting for Host to approve...");
                joinModeButton.setText("Waiting...");
            }
        })).exceptionally(ex -> {
            FxThread.run(() -> {
                joinModeButton.setDisable(false);
                joinModeButton.setText("Request to Join");
                statusLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
                statusLabel.setText("Connection failed: Host may be offline.");
            });
            return null;
        });
    }

    @FXML
    private void handleExportCompetition() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Please select a competition to export!");
            return;
        }

        Preferences prefs = Preferences.userNodeForPackage(HubController.class);
        String prefKey = "export_comp_seq_" + selected.getName();
        int seq = prefs.getInt(prefKey, 1);

        CompPayload payload = new CompPayload();
        payload.exportId = UUID.randomUUID().toString();
        payload.exportSequence = seq;
        payload.competition = selected;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Competition Setup for Scouters");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Competition Setup (*.ftccomp)", "*.ftccomp"));
        fileChooser.setInitialFileName(String.format("[发给从机]赛事初始化_%s_第%d次.ftccomp", selected.getName(), seq));

        File file = fileChooser.showSaveDialog(welcomeLabel.getScene().getWindow());
        if (file != null) {
            try (FileOutputStream fos = new FileOutputStream(file);
                 GZIPOutputStream gos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gos, StandardCharsets.UTF_8)) {

                new Gson().toJson(payload, writer);
                prefs.putInt(prefKey, seq + 1);

                statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
                statusLabel.setText("Competition exported. Airdrop this to scouters.");
            } catch (Exception e) {
                statusLabel.setStyle("-fx-text-fill: #f38ba8;");
                statusLabel.setText("Export Failed.");
            }
        }
    }

    @FXML
    private void handleImportCompetition() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Offline Competition (.ftccomp)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Competition Setup (*.ftccomp)", "*.ftccomp"));

        File file = fileChooser.showOpenDialog(welcomeLabel.getScene().getWindow());
        if (file != null) {
            try (FileInputStream fis = new FileInputStream(file);
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 InputStreamReader reader = new InputStreamReader(gis, StandardCharsets.UTF_8)) {

                CompPayload payload = new Gson().fromJson(reader, CompPayload.class);

                if (payload == null || payload.competition == null) {
                    statusLabel.setStyle("-fx-text-fill: #f38ba8;");
                    statusLabel.setText("Invalid .ftccomp file.");
                    return;
                }

                Preferences prefs = Preferences.userNodeForPackage(HubController.class);
                String importedKey = "imported_comp_ids";
                if (prefs.get(importedKey, "").contains(payload.exportId)) {
                    FxThread.run(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING, "防呆拦截：此比赛初始化文件已经导入过啦！", ButtonType.OK);
                        alert.setHeaderText("重复导入警告");
                        try { alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/bear27570/ftc/scouting/styles/style.css").toExternalForm()); alert.getDialogPane().getStyleClass().add("mac-card"); } catch (Exception ignored) {}
                        alert.showAndWait();
                    });
                    return;
                }

                if (competitionService.createCompetition(payload.competition.getName(), payload.competition.getCreatorUsername())) {
                    prefs.put(importedKey, prefs.get(importedKey, "") + payload.exportId + ";");

                    statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
                    statusLabel.setText("Offline Competition Imported!");
                    refreshMyCompetitionsList();
                } else {
                    statusLabel.setStyle("-fx-text-fill: #f38ba8;");
                    statusLabel.setText("Competition already exists or invalid.");
                }
            } catch (Exception e) {
                statusLabel.setStyle("-fx-text-fill: #f38ba8;");
                statusLabel.setText("Import Failed. Corrupted file?");
            }
        }
    }

    @FXML
    private void handleAllianceAnalysis() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null && discoveredCompetitionsListView.isVisible()) {
            selected = discoveredCompetitionsListView.getSelectionModel().getSelectedItem();
        }

        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Select a competition to analyze.");
            return;
        }

        try {
            mainApp.showAllianceAnalysisView(selected, currentUsername);
        } catch (IOException e) {
            log.error("Failed to open analysis window", e);
            statusLabel.setText("Failed to open analysis window.");
        }
    }

    @FXML
    private void handleManageMembers() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Select your competition first.");
            return;
        }
        try {
            mainApp.showCoordinatorView(selected);
        } catch (IOException e) {log.error("Failed to open coordinator window", e); }
    }

    @FXML
    private void handleLogout() throws IOException {
        NetworkService.getInstance().stop();
        mainApp.showLoginView();
    }

    @FXML
    private void handleDeleteCompetition() {
        Competition selected = myCompetitionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setStyle("-fx-text-fill: #fab387;");
            statusLabel.setText("Please select a competition to delete!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to permanently delete '" + selected.getName() + "' and ALL its data? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Delete Competition");
        alert.setHeaderText("Warning: Data Deletion");

        DialogPane dialogPane = alert.getDialogPane();
        try {
            dialogPane.getStylesheets().add(getClass().getResource("/com/bear27570/ftc/scouting/styles/style.css").toExternalForm());
            dialogPane.getStyleClass().add("mac-card");
        } catch (Exception e) {
            log.error("CSS 加载失败，采用降级样式");
        }

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (competitionService.deleteCompetition(selected.getName(), currentUsername)) {
                    statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
                    statusLabel.setText("Deleted successfully: " + selected.getName());
                    refreshMyCompetitionsList();
                } else {
                    statusLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
                    statusLabel.setText("Error: Failed to delete competition.");
                }
            }
        });
    }
}