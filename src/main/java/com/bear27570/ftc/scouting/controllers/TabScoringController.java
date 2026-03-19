// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabScoringController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

public class TabScoringController {

    @FXML private VBox scoringFormVBox;
    @FXML private RadioButton allianceModeRadio, singleModeRadio;
    @FXML private TextField matchNumberField, team1Field, team2Field;
    @FXML private ToggleButton redAllianceToggle, blueAllianceToggle;

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

    @FXML private Label submitterLabel, statusLabel, errorLabel;
    @FXML private Button submitButton;

    private ToggleGroup allianceToggleGroup, modeToggleGroup;
    private ToggleGroup t1ProjGroup, t2ProjGroup;

    private MainController mainController;
    private MatchDataService matchDataService;
    private Competition currentCompetition;
    private String currentUsername;
    private boolean isHost;

    private String currentClickLocations = "";
    private int editingScoreId = -1;
    private String editingOriginalTime = null;
    private ScoreEntry.SyncStatus editingOriginalSyncStatus = null;

    @FXML
    public void initialize() {
        allianceToggleGroup = new ToggleGroup();
        redAllianceToggle.setToggleGroup(allianceToggleGroup);
        blueAllianceToggle.setToggleGroup(allianceToggleGroup);
        redAllianceToggle.setSelected(true);

        t1ProjGroup = new ToggleGroup(); t1ProjNearBtn.setToggleGroup(t1ProjGroup); t1ProjFarBtn.setToggleGroup(t1ProjGroup);
        t2ProjGroup = new ToggleGroup(); t2ProjNearBtn.setToggleGroup(t2ProjGroup); t2ProjFarBtn.setToggleGroup(t2ProjGroup);

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
                t2Row1Btn.setSelected(false); t2Row2Btn.setSelected(false); t2Row3Btn.setSelected(false);
            }
        });

        if (team1IgnoreCheck != null) team1IgnoreCheck.setDisable(true);
        if (team2IgnoreCheck != null) team2IgnoreCheck.setDisable(true);

        team1Field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) updateWeakCheckboxStatus(team1Field.getText(), team1IgnoreCheck);
        });
        team2Field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) updateWeakCheckboxStatus(team2Field.getText(), team2IgnoreCheck);
        });
    }

    public void setDependencies(MainController mainController, MatchDataService matchDataService,
                                Competition competition, String username, boolean isHost) {
        this.mainController = mainController;
        this.matchDataService = matchDataService;
        this.currentCompetition = competition;
        this.currentUsername = username;
        this.isHost = isHost;
        submitterLabel.setText("Submitter: " + username);
    }

    public void setUIEnabled(boolean enabled) {
        scoringFormVBox.setDisable(!enabled);
    }

    public void setStatusText(String text, String colorHex) {
        statusLabel.setText(text);
        statusLabel.setStyle("-fx-text-fill: " + colorHex + ";");
    }

    public void setErrorText(String text) {
        errorLabel.setText(text);
    }

    private void setupRowExclusivity(ToggleButton t1Btn, ToggleButton t2Btn) {
        t1Btn.selectedProperty().addListener((obs, oldVal, isSelected) -> { if (isSelected) t2Btn.setSelected(false); });
        t2Btn.selectedProperty().addListener((obs, oldVal, isSelected) -> { if (isSelected) t1Btn.setSelected(false); });
    }

    private String getSelectedToggleText(ToggleGroup group) {
        ToggleButton selected = (ToggleButton) group.getSelectedToggle();
        return selected == null ? "NONE" : selected.getText().toUpperCase().replace(" ", "");
    }

    private String getSelectedRowsString(ToggleButton r1, ToggleButton r2, ToggleButton r3) {
        List<String> selected = new ArrayList<>();
        if (r1.isSelected()) selected.add("R1");
        if (r2.isSelected()) selected.add("R2");
        if (r3.isSelected()) selected.add("R3");
        return selected.isEmpty() ? "NONE" : String.join(" ", selected);
    }

    @FXML
    private void handleOpenFieldInput() {
        mainController.openFieldInputView(!singleModeRadio.isSelected(), currentClickLocations);
    }

    public void onFieldInputConfirmed(int totalHits, String clickLocations) {
        teleopArtifactsField.setText(String.valueOf(totalHits));
        currentClickLocations = clickLocations;
    }

    // ★ 增加表单重置边框的方法
    private void clearErrors() {
        errorLabel.setText("");
        String defaultStyle = "";
        matchNumberField.setStyle(defaultStyle);
        team1Field.setStyle(defaultStyle);
        team2Field.setStyle(defaultStyle);
        t1AutoScoreField.setStyle(defaultStyle);
        t2AutoScoreField.setStyle(defaultStyle);
        teleopArtifactsField.setStyle(defaultStyle);
    }

    // ★ 精准定位红框报错
    private void showFieldError(Control field, String message) {
        errorLabel.setText("❌ " + message);
        field.setStyle("-fx-border-color: #F87171; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-color: rgba(248, 113, 113, 0.1);");
        field.requestFocus();
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                field.setStyle("");
                errorLabel.setText("");
            }
        });
    }

    @FXML
    private void handleSubmitButtonAction() {
        clearErrors();

        try {
            ToggleButton selectedToggle = (ToggleButton) allianceToggleGroup.getSelectedToggle();
            if (selectedToggle == null) {
                setErrorText("❌ Please select an Alliance (Red/Blue).");
                return;
            }
            String alliance = selectedToggle.getText().contains("Red") ? "RED" : "BLUE";
            boolean isSingle = singleModeRadio.isSelected();

            int matchNum;
            try {
                matchNum = Integer.parseInt(matchNumberField.getText().trim());
                if (matchNum <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showFieldError(matchNumberField, "Match Number must be a valid positive integer.");
                return;
            }

            int team1;
            try {
                team1 = Integer.parseInt(team1Field.getText().trim());
                if (team1 <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showFieldError(team1Field, "Team 1 requires a valid team number.");
                return;
            }

            int team2 = 0;
            if (!isSingle) {
                try {
                    team2 = Integer.parseInt(team2Field.getText().trim());
                    if (team2 <= 0) throw new NumberFormatException();
                    if (team1 == team2) {
                        showFieldError(team2Field, "Team 1 and Team 2 cannot be the same.");
                        return;
                    }
                } catch (NumberFormatException e) {
                    showFieldError(team2Field, "Team 2 requires a valid team number.");
                    return;
                }
            }

            int t1AutoScore;
            try {
                t1AutoScore = Integer.parseInt(t1AutoScoreField.getText().trim());
            } catch (NumberFormatException e) {
                showFieldError(t1AutoScoreField, "Team 1 Auto Score must be a number.");
                return;
            }

            int t2AutoScore = 0;
            if (!isSingle) {
                try {
                    t2AutoScore = Integer.parseInt(t2AutoScoreField.getText().trim());
                } catch (NumberFormatException e) {
                    showFieldError(t2AutoScoreField, "Team 2 Auto Score must be a number.");
                    return;
                }
            }

            int teleopArtifacts;
            try {
                teleopArtifacts = Integer.parseInt(teleopArtifactsField.getText().trim());
            } catch (NumberFormatException e) {
                showFieldError(teleopArtifactsField, "TeleOp Hits must be a valid number.");
                return;
            }

            String t1Proj = getSelectedToggleText(t1ProjGroup);
            String t2Proj = isSingle ? "NONE" : getSelectedToggleText(t2ProjGroup);
            String t1Row = getSelectedRowsString(t1Row1Btn, t1Row2Btn, t1Row3Btn);
            String t2Row = isSingle ? "NONE" : getSelectedRowsString(t2Row1Btn, t2Row2Btn, t2Row3Btn);

            ScoreEntry entry = new ScoreEntry(
                    editingScoreId == -1 ? 0 : editingScoreId,
                    isSingle ? ScoreEntry.Type.SINGLE : ScoreEntry.Type.ALLIANCE,
                    matchNum, alliance,
                    team1, team2,
                    t1AutoScore, t2AutoScore, t1Proj, t2Proj, t1Row, t2Row,
                    teleopArtifacts,
                    team1SequenceCheck.isSelected(), !isSingle && team2SequenceCheck.isSelected(),
                    team1L2ClimbCheck.isSelected(), !isSingle && team2L2ClimbCheck.isSelected(),
                    team1IgnoreCheck.isSelected(), !isSingle && team2IgnoreCheck.isSelected(),
                    team1BrokenCheck.isSelected(), !isSingle && team2BrokenCheck.isSelected(),
                    currentClickLocations, currentUsername,
                    editingScoreId == -1 ? null : editingOriginalTime,
                    editingScoreId == -1 ? ScoreEntry.SyncStatus.UNSYNCED : editingOriginalSyncStatus
            );

            if (isHost) {
                entry.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                matchDataService.submitScore(currentCompetition.getName(), entry);
                mainController.triggerDataRefreshAndBroadcast();
                statusLabel.setText("✔ Score saved and broadcasted!");
                statusLabel.setStyle("-fx-text-fill: #34D399;");
            } else {
                boolean sent = NetworkService.getInstance().sendScoreToServer(entry);
                if (sent) {
                    statusLabel.setText("✔ Score sent to host!");
                    statusLabel.setStyle("-fx-text-fill: #34D399;");
                } else {
                    entry.setSyncStatus(ScoreEntry.SyncStatus.UNSYNCED);
                    matchDataService.submitScore(currentCompetition.getName(), entry);
                    mainController.triggerDataRefreshAndBroadcast();
                    statusLabel.setText("⚠ Score saved locally (Offline mode).");
                    statusLabel.setStyle("-fx-text-fill: #FBBF24;");
                }
            }
            resetFormState();
        } catch (Exception e) {
            setErrorText("❌ Unexpected Error: " + e.getMessage());
        }
    }

    private void updateWeakCheckboxStatus(String teamNumberStr, CheckBox checkBox) {
        if (checkBox == null || teamNumberStr == null || teamNumberStr.trim().isEmpty() || currentCompetition == null) return;
        try {
            int teamNum = Integer.parseInt(teamNumberStr.trim());
            int matchCount = matchDataService.getTeamHistory(currentCompetition.getName(), teamNum).size();
            checkBox.setDisable(matchCount < 2 && editingScoreId == -1);
        } catch (Exception e) { checkBox.setDisable(true); }
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

    private void setRowsFromText(String text, ToggleButton r1, ToggleButton r2, ToggleButton r3) {
        r1.setSelected(text != null && text.contains("R1"));
        r2.setSelected(text != null && text.contains("R2"));
        r3.setSelected(text != null && text.contains("R3"));
    }

    public void loadScoreForEdit(ScoreEntry selected) {
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

        clearErrors();
    }
}