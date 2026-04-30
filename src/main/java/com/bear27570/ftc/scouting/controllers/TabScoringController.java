// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabScoringController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.utils.FxThread;
import com.bear27570.ftc.scouting.viewmodels.ScoringViewModel;
import javafx.animation.*;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class TabScoringController {

    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field, t1AutoScoreField, t2AutoScoreField, teleopArtifactsField;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;
    @FXML private ToggleButton t1ProjNearBtn, t1ProjFarBtn, t2ProjNearBtn, t2ProjFarBtn;
    @FXML private ToggleButton t1Row1Btn, t1Row2Btn, t1Row3Btn, t2Row1Btn, t2Row2Btn, t2Row3Btn;
    @FXML private CheckBox team1SequenceCheck, team1L2ClimbCheck, team1IgnoreCheck, team1BrokenCheck;
    @FXML private CheckBox team2SequenceCheck, team2L2ClimbCheck, team2IgnoreCheck, team2BrokenCheck;
    @FXML private VBox team2AutoBox, team2IgnoreBox, team2CapsBox;
    @FXML private Label lblTeam2, submitterLabel, statusLabel, errorLabel;
    @FXML private Button submitButton;

    private ToggleGroup allianceToggleGroup, modeToggleGroup, t1ProjGroup, t2ProjGroup;
    private MainController mainController;
    private MatchDataService matchDataService;
    private Competition currentCompetition;
    private String currentUsername;
    private boolean isHost;

    private String currentClickLocations = "";
    private int editingScoreId = -1;
    private String editingOriginalTime = null;
    private ScoreEntry.SyncStatus editingOriginalSyncStatus = null;

    private final ObjectProperty<Color> themeColor = new SimpleObjectProperty<>(Color.WHITE);
    private final ScoringViewModel viewModel = new ScoringViewModel();

    @FXML
    public void initialize() {
        initToggleGroups();
        setupModeListeners();

        team1IgnoreCheck.setDisable(true);
        team2IgnoreCheck.setDisable(true);
        team1Field.focusedProperty().addListener((obs, o, n) -> { if (!n) validateTeamInput(team1Field, team1IgnoreCheck); });
        team2Field.focusedProperty().addListener((obs, o, n) -> { if (!n) validateTeamInput(team2Field, team2IgnoreCheck); });
        AnimationUtils.attachCyberpunkGlow(scoringFormVBox, themeColor);

        // 监听红蓝联盟 ToggleGroup 的变化
        allianceToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal == redAllianceToggle) {
                animateThemeColor(Color.web("#EF4444")); // 切换到红色
            } else if (newVal == blueAllianceToggle) {
                animateThemeColor(Color.web("#0A84FF")); // 切换到蓝色
            } else {
                animateThemeColor(Color.WHITE); // 没有选中时恢复白色
            }
        });
        statusLabel.textProperty().bind(viewModel.statusTextProperty());
        statusLabel.styleProperty().bind(Bindings.concat("-fx-text-fill: ", viewModel.statusColorProperty(), ";"));
        errorLabel.textProperty().bind(viewModel.errorTextProperty());
    }
    private void validateTeamInput(TextField field, CheckBox weakCheck) {
        String txt = field.getText().trim();
        if (txt.isEmpty()) return;

        // 1. 检查是否为 Weak 队伍
        updateWeakStatus(txt, weakCheck);

        // 2. 检查是否为 Banned 队伍
        try {
            int teamNum = Integer.parseInt(txt);
            if (currentCompetition != null && currentCompetition.isTeamBanned(teamNum)) {
                // 💡 核心修改：改为“软警告”
                // 不调用 showFieldError()，避免 requestFocus() 强推光标，允许侦查员继续填写其他框
                viewModel.setError("🚫 Warning: Team " + teamNum + " is BANNED!");
                field.setStyle("-fx-border-color: #F87171; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-color: rgba(248, 113, 113, 0.1);");
                AnimationUtils.playShakeAnimation(field);
            } else {
                // 如果队伍合法，且当前正显示 Ban 警告，则清除错误状态
                if (errorLabel.getText().contains("BANNED")) {
                    field.setStyle("");
                    viewModel.clear();
                }
            }
        } catch (NumberFormatException ignored) {}
    }
    private void animateThemeColor(Color targetColor) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(350),
                        new KeyValue(themeColor, targetColor, Interpolator.EASE_OUT)
                )
        );
        timeline.play();
    }

    public void setDependencies(MainController mainController, MatchDataService matchDataService, Competition competition, String username, boolean isHost) {
        this.mainController = mainController;
        this.matchDataService = matchDataService;
        this.currentCompetition = competition;
        this.currentUsername = username;
        this.isHost = isHost;
        submitterLabel.setText("Submitter: " + username);
    }

    public ScoringViewModel getViewModel() { return viewModel; }
    public void setUIEnabled(boolean enabled) { scoringFormVBox.setDisable(!enabled); }
    public void onFieldInputConfirmed(int totalHits, String locations) { teleopArtifactsField.setText(String.valueOf(totalHits)); currentClickLocations = locations; }
    @FXML private void handleOpenFieldInput() { mainController.openFieldInputView(!singleModeRadio.isSelected(), currentClickLocations); }

    private int parseField(TextField field, String errorMsg) {
        try {
            int val = Integer.parseInt(field.getText().trim());
            if (val < 0) throw new NumberFormatException();
            return val;
        } catch (Exception e) {
            showFieldError(field, errorMsg);
            throw new RuntimeException("VALIDATION_FAILED");
        }
    }

    @FXML
    private void handleSubmitButtonAction() {
        clearErrors();
        submitButton.setDisable(true);
        submitButton.setText("Saving...");

        try {
            boolean isSingle = singleModeRadio.isSelected();
            ToggleButton selectedAlliance = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedAlliance == null) { viewModel.setError("Select an Alliance (Red/Blue)."); throw new RuntimeException("VALIDATION_FAILED"); }

            String alliance = selectedAlliance.getText().toUpperCase();
            int matchNum = parseField(matchNumberField, "Valid Match Number required.");
            int team1 = parseField(team1Field, "Valid Team 1 required.");
            int team2 = isSingle ? 0 : parseField(team2Field, "Valid Team 2 required.");
            if (!isSingle && team1 == team2) { showFieldError(team2Field, "Teams cannot be same."); throw new RuntimeException("VALIDATION_FAILED"); }

            boolean t1Banned = currentCompetition.isTeamBanned(team1);
            boolean t2Banned = !isSingle && currentCompetition.isTeamBanned(team2);

            if (t1Banned || t2Banned) {
                String bannedTeamStr = t1Banned ? String.valueOf(team1) : String.valueOf(team2);
                if (t1Banned && t2Banned) bannedTeamStr = team1 + " & " + team2;

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Banned Team Detected");
                alert.setHeaderText("Team " + bannedTeamStr + " is Banned!");
                alert.setContentText("This team's data will NOT be recorded. \n\nDo you want to DISCARD this entry? \n• 'Yes' to throw it away and clear the form. \n• 'No' to go back and fix the team number.");

                try {
                    alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/bear27570/ftc/scouting/styles/style.css").toExternalForm());
                    alert.getDialogPane().getStyleClass().add("mac-card");
                } catch (Exception ignored) {}

                ButtonType btnYes = new ButtonType("Yes (Discard)", ButtonBar.ButtonData.YES);
                ButtonType btnNo = new ButtonType("No (Edit)", ButtonBar.ButtonData.NO);
                alert.getButtonTypes().setAll(btnYes, btnNo);

                // 因为此方法是由按钮点击触发，处于 UI 线程，可以直接使用 showAndWait() 阻塞等待用户选择
                alert.showAndWait().ifPresent(type -> {
                    if (type == btnYes) {
                        // 选择了“是”：直接丢弃，重置表单
                        resetFormState();
                        restoreSubmitState();
                        viewModel.setStatus("Entry discarded (Banned Team).", "#FBBF24"); // 亮个黄灯提示丢弃成功
                    } else {
                        // 选择了“否”：标红填错的框，恢复按钮状态让用户继续编辑
                        if (t1Banned) showFieldError(team1Field, "Please correct the team number.");
                        if (t2Banned) showFieldError(team2Field, "Please correct the team number.");
                        restoreSubmitState();
                    }
                });
                return; // ⚠️ 拦截成功，直接退出整个提交流程
            }
            // ==========================================

            int t1Auto = parseField(t1AutoScoreField, "Team 1 Auto must be a number.");
            int t2Auto = isSingle ? 0 : parseField(t2AutoScoreField, "Team 2 Auto must be a number.");
            int teleop = parseField(teleopArtifactsField, "TeleOp Hits must be a valid number.");

            ScoreEntry entry = new ScoreEntry(
                    editingScoreId == -1 ? 0 : editingScoreId,
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    matchNum, alliance, team1, team2, t1Auto, t2Auto,
                    getSelectedToggleText(t1ProjGroup), isSingle ? "NONE" : getSelectedToggleText(t2ProjGroup),
                    getSelectedRows(t1Row1Btn, t1Row2Btn, t1Row3Btn), isSingle ? "NONE" : getSelectedRows(t2Row1Btn, t2Row2Btn, t2Row3Btn),
                    teleop,
                    team1SequenceCheck.isSelected(), !isSingle && team2SequenceCheck.isSelected(),
                    team1L2ClimbCheck.isSelected(), !isSingle && team2L2ClimbCheck.isSelected(),
                    team1IgnoreCheck.isSelected(), !isSingle && team2IgnoreCheck.isSelected(),
                    team1BrokenCheck.isSelected(), !isSingle && team2BrokenCheck.isSelected(),
                    currentClickLocations, currentUsername,
                    editingScoreId == -1 ? null : editingOriginalTime,
                    editingScoreId == -1 ? ScoreEntry.SyncStatus.UNSYNCED : editingOriginalSyncStatus
            );

            processSubmission(entry, matchNum, editingScoreId != -1);

        } catch (RuntimeException e) {
            if (!"VALIDATION_FAILED".equals(e.getMessage())) viewModel.setError("Error: " + e.getMessage());
            restoreSubmitState();
        }
    }

    private void processSubmission(ScoreEntry entry, int currentMatchNum, boolean wasEditing) {
        if (isHost) {
            entry.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
            matchDataService.submitScore(currentCompetition.getName(), entry);
            viewModel.setStatus("✔ Score saved and broadcasted!", "#34D399");
            finalizeSubmissionUI(currentMatchNum, wasEditing);
        } else {
            NetworkService.getInstance().sendScoreToServer(entry)
                    .thenAccept(sent -> FxThread.run(() -> {
                        if (sent) {
                            viewModel.setStatus("✔ Score sent to host!", "#34D399");
                        } else {
                            entry.setSyncStatus(ScoreEntry.SyncStatus.UNSYNCED);
                            matchDataService.submitScore(currentCompetition.getName(), entry);
                            viewModel.setStatus("⚠ Score saved locally (Offline mode).", "#FBBF24");
                        }
                        finalizeSubmissionUI(currentMatchNum, wasEditing);
                    }));
        }
    }

    private void finalizeSubmissionUI(int currentMatchNum, boolean wasEditing) {
        mainController.triggerDataRefreshAndBroadcast();
        resetFormState();
        if (!wasEditing) matchNumberField.setText(String.valueOf(currentMatchNum + 1));

        submitButton.setText("✔ Success!");
        submitButton.setStyle("-fx-background-color: #34D399; -fx-text-fill: #18181B; -fx-font-weight: bold;");
        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(e -> restoreSubmitState());
        pause.play();
    }

    private void resetFormState() {
        editingScoreId = -1; editingOriginalTime = null; editingOriginalSyncStatus = null;
        submitterLabel.setText("Submitter: " + currentUsername); submitterLabel.setStyle("");
        currentClickLocations = "";

        Stream.of(team1Field, team2Field).forEach(TextField::clear);
        Stream.of(t1AutoScoreField, t2AutoScoreField, teleopArtifactsField).forEach(f -> f.setText("0"));

        t1ProjGroup.selectToggle(null); t2ProjGroup.selectToggle(null);
        Stream.of(t1Row1Btn, t1Row2Btn, t1Row3Btn, t2Row1Btn, t2Row2Btn, t2Row3Btn).forEach(b -> b.setSelected(false));

        Stream.of(team1SequenceCheck, team1L2ClimbCheck, team1IgnoreCheck, team1BrokenCheck,
                        team2SequenceCheck, team2L2ClimbCheck, team2IgnoreCheck, team2BrokenCheck)
                .filter(Objects::nonNull).forEach(cb -> {
                    cb.setSelected(false);
                    cb.setDisable(cb == team1IgnoreCheck || cb == team2IgnoreCheck);
                });
        clearErrors();
    }

    public void loadScoreForEdit(ScoreEntry s) {
        editingScoreId = s.getId(); editingOriginalTime = s.getSubmissionTime(); editingOriginalSyncStatus = s.getSyncStatus();
        submitterLabel.setText("EDITING RECORD ID: " + editingScoreId); submitterLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        submitButton.setText("Update Record"); submitButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");

        matchNumberField.setText(String.valueOf(s.getMatchNumber()));
        team1Field.setText(String.valueOf(s.getTeam1())); team2Field.setText(String.valueOf(s.getTeam2()));
        t1AutoScoreField.setText(String.valueOf(s.getTeam1AutoScore())); t2AutoScoreField.setText(String.valueOf(s.getTeam2AutoScore()));
        teleopArtifactsField.setText(String.valueOf(s.getTeleopArtifacts()));

        setToggleByText(t1ProjGroup, s.getTeam1AutoProj()); setToggleByText(t2ProjGroup, s.getTeam2AutoProj());
        setRowsByText(s.getTeam1AutoRow(), t1Row1Btn, t1Row2Btn, t1Row3Btn); setRowsByText(s.getTeam2AutoRow(), t2Row1Btn, t2Row2Btn, t2Row3Btn);

        (s.getAlliance().equalsIgnoreCase("RED") ? redAllianceToggle : blueAllianceToggle).setSelected(true);
        (s.getScoreType() == ScoreEntry.Type.SINGLE ? singleModeRadio : allianceModeRadio).setSelected(true);

        team1SequenceCheck.setSelected(s.isTeam1CanSequence()); team2SequenceCheck.setSelected(s.isTeam2CanSequence());
        team1L2ClimbCheck.setSelected(s.isTeam1L2Climb()); team2L2ClimbCheck.setSelected(s.isTeam2L2Climb());
        team1IgnoreCheck.setDisable(false); team1IgnoreCheck.setSelected(s.isTeam1Ignored());
        team2IgnoreCheck.setDisable(false); team2IgnoreCheck.setSelected(s.isTeam2Ignored());
        team1BrokenCheck.setSelected(s.isTeam1Broken()); team2BrokenCheck.setSelected(s.isTeam2Broken());

        currentClickLocations = s.getClickLocations() != null ? s.getClickLocations() : "";
        viewModel.setError("Editing record loaded.");
    }

    private void initToggleGroups() {
        allianceToggleGroup = new ToggleGroup(); redAllianceToggle.setToggleGroup(allianceToggleGroup); blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        t1ProjGroup = new ToggleGroup(); t1ProjNearBtn.setToggleGroup(t1ProjGroup); t1ProjFarBtn.setToggleGroup(t1ProjGroup);
        t2ProjGroup = new ToggleGroup(); t2ProjNearBtn.setToggleGroup(t2ProjGroup); t2ProjFarBtn.setToggleGroup(t2ProjGroup);
        t1Row1Btn.selectedProperty().addListener((o, old, n) -> { if(n) t2Row1Btn.setSelected(false); });
        t2Row1Btn.selectedProperty().addListener((o, old, n) -> { if(n) t1Row1Btn.setSelected(false); });
        t1Row2Btn.selectedProperty().addListener((o, old, n) -> { if(n) t2Row2Btn.setSelected(false); });
        t2Row2Btn.selectedProperty().addListener((o, old, n) -> { if(n) t1Row2Btn.setSelected(false); });
        t1Row3Btn.selectedProperty().addListener((o, old, n) -> { if(n) t2Row3Btn.setSelected(false); });
        t2Row3Btn.selectedProperty().addListener((o, old, n) -> { if(n) t1Row3Btn.setSelected(false); });
        modeToggleGroup = new ToggleGroup(); allianceModeRadio.setToggleGroup(modeToggleGroup); singleModeRadio.setToggleGroup(modeToggleGroup);
    }

    private void setupModeListeners() {
        modeToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            boolean isS = singleModeRadio.isSelected();
            Stream.of(lblTeam2, team2Field, team2CapsBox, team2IgnoreBox, team2IgnoreCheck, team2BrokenCheck).filter(Objects::nonNull).forEach(n -> n.setVisible(!isS));
            if (team2AutoBox != null) { team2AutoBox.setVisible(!isS); team2AutoBox.setManaged(!isS); }
            if (isS) {
                t2AutoScoreField.setText("0"); t2ProjGroup.selectToggle(null);
                Stream.of(t2Row1Btn, t2Row2Btn, t2Row3Btn, team2IgnoreCheck, team2BrokenCheck, team2SequenceCheck, team2L2ClimbCheck)
                        .filter(Objects::nonNull).forEach(b -> { if(b instanceof ToggleButton) ((ToggleButton)b).setSelected(false); else ((CheckBox)b).setSelected(false); });
            }
        });
    }

    private void clearErrors() {
        viewModel.clear();
        Stream.of(matchNumberField, team1Field, team2Field, t1AutoScoreField, t2AutoScoreField, teleopArtifactsField).forEach(f -> f.setStyle(""));
    }

    private void showFieldError(Control f, String msg) {
        viewModel.setError(msg);
        f.setStyle("-fx-border-color: #F87171; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-color: rgba(248, 113, 113, 0.1);");
        f.requestFocus();
        f.focusedProperty().addListener((o, old, n) -> { if (n) { f.setStyle(""); viewModel.clear(); } });
    }

    private void restoreSubmitState() {
        submitButton.setDisable(false); submitButton.setStyle("");
        submitButton.setText(editingScoreId == -1 ? "Submit Match Entry" : "Update Record");
        if (editingScoreId != -1) submitButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
    }

    private void updateWeakStatus(String txt, CheckBox cb) {
        if (cb == null || txt == null || txt.trim().isEmpty() || currentCompetition == null) return;
        try { cb.setDisable(matchDataService.getTeamHistory(currentCompetition.getName(), Integer.parseInt(txt.trim())).size() < 2 && editingScoreId == -1); }
        catch (Exception e) { cb.setDisable(true); }
    }

    private String getSelectedToggleText(ToggleGroup g) { ToggleButton t = (ToggleButton) g.getSelectedToggle(); return t == null ? "NONE" : t.getText().toUpperCase().replace(" ", ""); }
    private String getSelectedRows(ToggleButton r1, ToggleButton r2, ToggleButton r3) {
        List<String> s = new ArrayList<>(); if (r1.isSelected()) s.add("R1"); if (r2.isSelected()) s.add("R2"); if (r3.isSelected()) s.add("R3");
        return s.isEmpty() ? "NONE" : String.join(" ", s);
    }
    private void setToggleByText(ToggleGroup g, String t) {
        g.selectToggle(null); if (t == null || t.equals("NONE")) return;
        g.getToggles().stream().filter(tg -> ((ToggleButton) tg).getText().toUpperCase().replace(" ", "").equals(t)).findFirst().ifPresent(g::selectToggle);
    }
    private void setRowsByText(String t, ToggleButton r1, ToggleButton r2, ToggleButton r3) {
        r1.setSelected(t != null && t.contains("R1")); r2.setSelected(t != null && t.contains("R2")); r3.setSelected(t != null && t.contains("R3"));
    }
    public void updateCompetition(Competition competition) {
        this.currentCompetition = competition;
    }
}