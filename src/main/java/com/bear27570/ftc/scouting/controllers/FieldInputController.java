package com.bear27570.ftc.scouting.controllers;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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

    // 带队伍标记的点
    public static class TeamPoint {
        double x, y;
        int teamIndex; // 1 or 2
        public TeamPoint(double x, double y, int teamIndex) {
            this.x = x; this.y = y; this.teamIndex = teamIndex;
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

        redraw();
    }

    public void setDialogStage(Stage dialogStage) { this.dialogStage = dialogStage; }

    // 由 MainController 调用，设置是否显示双队切换
    public void setAllianceMode(boolean isAllianceMode) {
        this.isAllianceMode = isAllianceMode;
        teamSelectBox.setVisible(isAllianceMode);
        teamSelectBox.setManaged(isAllianceMode);
        if (!isAllianceMode) {
            team1Btn.setSelected(true); // 单队模式默认 Team 1
        }
    }

    @FXML
    private void handleCanvasClick(MouseEvent event) {
        if (addModeBtn.isSelected()) {
            int currentTeam = team1Btn.isSelected() ? 1 : 2;
            points.add(new TeamPoint(event.getX(), event.getY(), currentTeam));
        } else {
            removeClosestPoint(event.getX(), event.getY());
        }
        updateUI();
    }

    private void removeClosestPoint(double x, double y) {
        TeamPoint closest = null;
        double minDesc = Double.MAX_VALUE;
        double radius = 30.0;

        // 只能删除当前选中队伍的点，防止误删队友
        int currentTeam = team1Btn.isSelected() ? 1 : 2;

        for (TeamPoint p : points) {
            // 在联盟模式下，只删当前选中的队伍；单队模式下全删
            if (isAllianceMode && p.teamIndex != currentTeam) continue;

            double dist = Math.sqrt(Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2));
            if (dist < radius && dist < minDesc) {
                minDesc = dist;
                closest = p;
            }
        }
        if (closest != null) points.remove(closest);
    }

    private void updateUI() {
        long t1Count = points.stream().filter(p -> p.teamIndex == 1).count();
        long t2Count = points.stream().filter(p -> p.teamIndex == 2).count();

        countT1Label.setText(String.valueOf(t1Count));
        countT2Label.setText(String.valueOf(t2Count));
        countLabel.setText(String.valueOf(points.size()));

        redraw();
    }

    private void redraw() {
        GraphicsContext gc = drawCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());

        gc.setLineWidth(2);

        for (TeamPoint p : points) {
            if (p.teamIndex == 1) {
                gc.setFill(Color.web("#00BCD4")); // Cyan
                gc.setStroke(Color.WHITE);
            } else {
                gc.setFill(Color.web("#E91E63")); // Pink
                gc.setStroke(Color.WHITE);
            }
            gc.fillOval(p.x - 6, p.y - 6, 12, 12);
            gc.strokeOval(p.x - 6, p.y - 6, 12, 12);
        }
    }

    @FXML private void handleClear() {
        points.clear();
        updateUI();
    }

    @FXML private void handleConfirm() {
        confirmed = true;
        dialogStage.close();
    }

    public boolean isConfirmed() { return confirmed; }

    // 返回所有点的总数
    public int getTotalCount() { return points.size(); }

    // 字符串格式： "1:123.0,456.0;2:200.0,300.0;..."
    public String getLocationsString() {
        StringBuilder sb = new StringBuilder();
        for (TeamPoint p : points) {
            sb.append(p.teamIndex).append(":").append(String.format("%.1f,%.1f;", p.x, p.y));
        }
        return sb.toString();
    }
}