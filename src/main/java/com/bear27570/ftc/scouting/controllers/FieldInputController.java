package com.bear27570.ftc.scouting.controllers;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class FieldInputController {
    @FXML private Canvas drawCanvas;
    @FXML private VBox teamSelectBox;
    @FXML private ToggleButton team1Btn, team2Btn;
    @FXML private ToggleButton addModeBtn, removeModeBtn;
    @FXML private Label countLabel, countT1Label, countT2Label;

    private Stage dialogStage;
    private final List<TeamPoint> points = new ArrayList<>();
    private boolean confirmed = false;
    private boolean isAllianceMode = true;

    private static final double ZONE_DIVIDER_Y = 400.0;

    public static class TeamPoint {
        double x, y;
        int teamIndex;
        boolean isMiss;
        public TeamPoint(double x, double y, int teamIndex, boolean isMiss) {
            this.x = x; this.y = y; this.teamIndex = teamIndex; this.isMiss = isMiss;
        }
    }

    @FXML public void initialize() {
        ToggleGroup teamGroup = new ToggleGroup();
        team1Btn.setToggleGroup(teamGroup); team2Btn.setToggleGroup(teamGroup);
        ToggleGroup modeGroup = new ToggleGroup();
        addModeBtn.setToggleGroup(modeGroup); removeModeBtn.setToggleGroup(modeGroup);
        redraw();
    }

    // 新增：加载现有数据
    public void loadExistingPoints(String locationStr) {
        points.clear();
        if (locationStr == null || locationStr.isEmpty()) return;

        String[] entries = locationStr.split(";");
        for (String entry : entries) {
            try {
                String[] parts = entry.split(":");
                if (parts.length < 2) continue;

                int teamIdx = Integer.parseInt(parts[0]);
                String[] coords = parts[1].split(",");
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                int state = Integer.parseInt(coords[2]);

                points.add(new TeamPoint(x, y, teamIdx, state == 1));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        updateUI();
    }

    public void setDialogStage(Stage dialogStage) { this.dialogStage = dialogStage; }
    public void setAllianceMode(boolean isAllianceMode) {
        this.isAllianceMode = isAllianceMode;
        teamSelectBox.setVisible(isAllianceMode);
        teamSelectBox.setManaged(isAllianceMode);
        if (!isAllianceMode) { team1Btn.setSelected(true); }
    }

    @FXML private void handleCanvasClick(MouseEvent event) {
        if (addModeBtn.isSelected()) {
            int currentTeam = team1Btn.isSelected() ? 1 : 2;
            boolean isMiss = (event.getButton() == MouseButton.SECONDARY);
            points.add(new TeamPoint(event.getX(), event.getY(), currentTeam, isMiss));
        } else {
            removeClosestPoint(event.getX(), event.getY());
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
            if (dist < radius && dist < minDesc) { minDesc = dist; closest = p; }
        }
        if (closest != null) points.remove(closest);
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
                double s = 6; gc.setLineWidth(3);
                gc.strokeLine(p.x - s, p.y - s, p.x + s, p.y + s);
                gc.strokeLine(p.x + s, p.y - s, p.x - s, p.y + s);
            } else {
                gc.fillOval(p.x - 6, p.y - 6, 12, 12);
                gc.setStroke(Color.WHITE); gc.setLineWidth(1);
                gc.strokeOval(p.x - 6, p.y - 6, 12, 12);
            }
        }
    }

    @FXML private void handleClear() { points.clear(); updateUI(); }
    @FXML private void handleConfirm() { confirmed = true; dialogStage.close(); }
    public boolean isConfirmed() { return confirmed; }
    public int getTotalHitCount() { return (int) points.stream().filter(p -> !p.isMiss).count(); }
    public String getLocationsString() {
        StringBuilder sb = new StringBuilder();
        for (TeamPoint p : points) {
            int missInt = p.isMiss ? 1 : 0;
            sb.append(p.teamIndex).append(":").append(String.format("%.1f,%.1f,%d;", p.x, p.y, missInt));
        }
        return sb.toString();
    }
}