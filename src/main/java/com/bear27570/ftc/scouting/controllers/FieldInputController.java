package com.bear27570.ftc.scouting.controllers;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class FieldInputController {

    @FXML private StackPane inputPane;
    @FXML private Canvas drawCanvas;
    @FXML private VBox teamSelectBox;
    @FXML private ToggleButton team1Btn, team2Btn;
    @FXML private ToggleButton addModeBtn, removeModeBtn;
    @FXML private Label countLabel, countT1Label, countT2Label;

    private Stage dialogStage;
    private final List<TeamPoint> points = new ArrayList<>();
    private boolean confirmed = false;
    private boolean isAllianceMode = true;
    private Label warningLabel;

    private static final double ZONE_DIVIDER_Y = 400.0;

    // Updated Data Class with Timestamp
    public static class TeamPoint {
        double x, y;
        int teamIndex;
        boolean isMiss;
        long timestamp; // New field

        public TeamPoint(double x, double y, int teamIndex, boolean isMiss, long timestamp) {
            this.x = x; this.y = y; this.teamIndex = teamIndex; this.isMiss = isMiss;
            this.timestamp = timestamp;
        }
    }

    @FXML
    public void initialize() {
        ToggleGroup teamGroup = new ToggleGroup();
        team1Btn.setToggleGroup(teamGroup);
        team2Btn.setToggleGroup(teamGroup);

        ToggleGroup modeGroup = new ToggleGroup();
        addModeBtn.setToggleGroup(modeGroup);
        removeModeBtn.setToggleGroup(modeGroup);

        modeGroup.selectedToggleProperty().addListener((o, old, newVal) -> updateCursorState());

        warningLabel = new Label("HOLD CTRL + CLICK!");
        warningLabel.setStyle("-fx-background-color: rgba(255, 0, 0, 0.85); -fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 15; -fx-background-radius: 8;");
        warningLabel.setVisible(false);
        warningLabel.setMouseTransparent(true);

        if (inputPane != null) {
            inputPane.getChildren().add(warningLabel);
        }

        redraw();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        dialogStage.getScene().setOnKeyPressed(this::handleKeyPressed);
        dialogStage.getScene().setOnKeyReleased(this::handleKeyReleased);
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.X) {
            if (addModeBtn.isSelected()) removeModeBtn.setSelected(true);
            else addModeBtn.setSelected(true);
            updateCursorState();
        } else if (event.getCode() == KeyCode.CONTROL) {
            updateCursorState();
        }
    }

    private void handleKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.CONTROL) updateCursorState();
    }

    private void updateCursorState() {
        if (inputPane == null) return;
        if (removeModeBtn.isSelected()) inputPane.setCursor(Cursor.CROSSHAIR);
        else inputPane.setCursor(Cursor.HAND);
    }

    @FXML
    private void handleCanvasClick(MouseEvent event) {
        if (!event.isControlDown()) {
            showWarning();
            return;
        }

        double x = event.getX();
        double y = event.getY();

        if (addModeBtn.isSelected()) {
            int currentTeam = team1Btn.isSelected() ? 1 : 2;
            boolean isMiss = (event.getButton() == MouseButton.SECONDARY);
            // Record current time for cycle calculation
            points.add(new TeamPoint(x, y, currentTeam, isMiss, System.currentTimeMillis()));
        } else {
            removeClosestPoint(x, y);
        }
        updateUI();
    }

    private void removeClosestPoint(double x, double y) {
        TeamPoint closest = null;
        double minDesc = Double.MAX_VALUE;
        double radius = 30.0;
        int currentTeam = team1Btn.isSelected() ? 1 : 2;

        for (TeamPoint p : points) {
            if (isAllianceMode && p.teamIndex != currentTeam) continue;
            double dist = Math.sqrt(Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2));
            if (dist < radius && dist < minDesc) {
                minDesc = dist;
                closest = p;
            }
        }
        if (closest != null) points.remove(closest);
    }

    private void showWarning() {
        if (warningLabel == null) return;
        warningLabel.setVisible(true);
        warningLabel.toFront();
        PauseTransition pt = new PauseTransition(Duration.seconds(0.6));
        pt.setOnFinished(e -> warningLabel.setVisible(false));
        pt.play();
    }

    private void updateUI() {
        long t1Count = points.stream().filter(p -> p.teamIndex == 1 && !p.isMiss).count();
        long t2Count = points.stream().filter(p -> p.teamIndex == 2 && !p.isMiss).count();
        countT1Label.setText(String.valueOf(t1Count));
        countT2Label.setText(String.valueOf(t2Count));
        countLabel.setText(String.valueOf(t1Count + t2Count));
        redraw();
    }

    private void redraw() {
        GraphicsContext gc = drawCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());

        gc.save();
        gc.setStroke(Color.web("#FFFFFF", 0.3));
        gc.setLineWidth(1);
        gc.setLineDashes(10);
        gc.strokeLine(0, ZONE_DIVIDER_Y, drawCanvas.getWidth(), ZONE_DIVIDER_Y);

        gc.setFill(Color.web("#FFFFFF", 0.5));
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.fillText("NEAR ZONE (Top)", 10, ZONE_DIVIDER_Y - 10);
        gc.fillText("FAR ZONE (Bottom)", 10, ZONE_DIVIDER_Y + 20);
        gc.restore();

        gc.setLineWidth(2);
        for (TeamPoint p : points) {
            Color baseColor = (p.teamIndex == 1) ? Color.web("#00BCD4") : Color.web("#E91E63");
            gc.setStroke(baseColor);
            gc.setFill(baseColor);

            if (p.isMiss) {
                double s = 6;
                gc.setLineWidth(3);
                gc.strokeLine(p.x - s, p.y - s, p.x + s, p.y + s);
                gc.strokeLine(p.x + s, p.y - s, p.x - s, p.y + s);
            } else {
                gc.fillOval(p.x - 6, p.y - 6, 12, 12);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1);
                gc.strokeOval(p.x - 6, p.y - 6, 12, 12);
            }
        }
    }

    public void loadExistingPoints(String locationStr) {
        points.clear();
        if (locationStr == null || locationStr.isEmpty()) return;

        String[] entries = locationStr.split(";");
        for (String entry : entries) {
            try {
                // Format: TeamIdx:x,y,state,timestamp
                String[] parts = entry.split(":");
                if (parts.length < 2) continue;

                int teamIdx = Integer.parseInt(parts[0]);
                String[] coords = parts[1].split(",");
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                int state = Integer.parseInt(coords[2]);

                // Backward compatibility: if timestamp missing, use 0
                long ts = (coords.length > 3) ? Long.parseLong(coords[3]) : 0;

                points.add(new TeamPoint(x, y, teamIdx, state == 1, ts));
            } catch (Exception e) { }
        }
        updateUI();
    }

    public String getLocationsString() {
        StringBuilder sb = new StringBuilder();
        for (TeamPoint p : points) {
            int missInt = p.isMiss ? 1 : 0;
            // Format: TeamIdx:x,y,state,timestamp;
            sb.append(p.teamIndex).append(":")
                    .append(String.format("%.1f,%.1f,%d,%d;", p.x, p.y, missInt, p.timestamp));
        }
        return sb.toString();
    }

    public void setAllianceMode(boolean isAllianceMode) {
        this.isAllianceMode = isAllianceMode;
        teamSelectBox.setVisible(isAllianceMode);
        teamSelectBox.setManaged(isAllianceMode);
        if (!isAllianceMode) { team1Btn.setSelected(true); }
    }

    @FXML private void handleClear() { points.clear(); updateUI(); }
    @FXML private void handleConfirm() { confirmed = true; dialogStage.close(); }
    public boolean isConfirmed() { return confirmed; }
    public int getTotalHitCount() { return (int) points.stream().filter(p -> !p.isMiss).count(); }
}