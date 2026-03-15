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
import java.util.Stack;

public class FieldInputController {

    @FXML private StackPane inputPane;
    @FXML private Canvas drawCanvas;
    @FXML private VBox teamSelectBox;
    @FXML private ToggleButton team1Btn, team2Btn;
    @FXML private ToggleButton addModeBtn, removeModeBtn;
    @FXML private Label countLabel, countT1Label, countT2Label;

    private Stage dialogStage;
    private MainController parentController;

    private final List<TeamPoint> points = new ArrayList<>();
    // 新增：用于存放历史记录的栈，实现撤销功能
    private final Stack<List<TeamPoint>> undoStack = new Stack<>();

    private boolean isAllianceMode = true;
    private Label warningLabel;

    private static final double ZONE_DIVIDER_Y = 400.0;

    public static class TeamPoint {
        double x, y;
        int teamIndex;
        boolean isMiss;
        long timestamp;

        public TeamPoint(double x, double y, int teamIndex, boolean isMiss, long timestamp) {
            this.x = x; this.y = y; this.teamIndex = teamIndex; this.isMiss = isMiss;
            this.timestamp = timestamp;
        }

        // 深拷贝方法
        public TeamPoint copy() {
            return new TeamPoint(x, y, teamIndex, isMiss, timestamp);
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

    public void setDependencies(Stage dialogStage, MainController parentController, boolean isAllianceMode, String existingLocations) {
        this.dialogStage = dialogStage;
        this.parentController = parentController;
        this.isAllianceMode = isAllianceMode;

        teamSelectBox.setVisible(isAllianceMode);
        teamSelectBox.setManaged(isAllianceMode);
        if (!isAllianceMode) { team1Btn.setSelected(true); }

        if (existingLocations != null && !existingLocations.isEmpty()) {
            loadExistingPoints(existingLocations);
        }

        dialogStage.getScene().setOnKeyPressed(this::handleKeyPressed);
        dialogStage.getScene().setOnKeyReleased(this::handleKeyReleased);
    }

    // --- 新增：保存当前状态到撤销栈 ---
    private void saveState() {
        List<TeamPoint> snapshot = new ArrayList<>();
        for (TeamPoint p : points) {
            snapshot.add(p.copy());
        }
        undoStack.push(snapshot);
    }

    // --- 新增：处理撤销逻辑 ---
    @FXML
    private void handleUndo() {
        if (!undoStack.isEmpty()) {
            points.clear();
            points.addAll(undoStack.pop());
            updateUI();
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        // 新增：拦截 Ctrl + Z
        if (event.isControlDown() && event.getCode() == KeyCode.Z) {
            handleUndo();
            return;
        }

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

        // 修改：在修改数组之前，保存快照到撤销栈
        saveState();

        double x = event.getX();
        double y = event.getY();

        if (addModeBtn.isSelected()) {
            int currentTeam = team1Btn.isSelected() ? 1 : 2;
            boolean isMiss = (event.getButton() == MouseButton.SECONDARY);
            points.add(new TeamPoint(x, y, currentTeam, isMiss, System.currentTimeMillis()));

            // ====== 修复后的声纳波纹动效 ======
            // 1. 创建圆（初始半径为2，不设置初始中心系，默认为0,0）
            javafx.scene.shape.Circle ripple = new javafx.scene.shape.Circle(2);
            ripple.setStroke(currentTeam == 1 ? Color.web("#00BCD4") : Color.web("#E91E63"));
            ripple.setFill(Color.TRANSPARENT);
            ripple.setStrokeWidth(2);

            // 2. ★ 核心修复：解除 StackPane 的自动居中强制束缚
            ripple.setManaged(false);

            // 3. ★ 核心修复：坐标系跃迁转换
            // 将 Canvas 的内部坐标系(x,y) -> 转换到整个窗口场景的绝对坐标 -> 再转换到外层 inputPane 的局部坐标
            javafx.geometry.Point2D sceneCoords = drawCanvas.localToScene(x, y);
            javafx.geometry.Point2D paneCoords = inputPane.sceneToLocal(sceneCoords);

            // 精确设置波纹的绝对原点
            ripple.setLayoutX(paneCoords.getX());
            ripple.setLayoutY(paneCoords.getY());

            inputPane.getChildren().add(ripple);

            // 4. 动效播放
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(Duration.millis(400), ripple);
            st.setToX(15);
            st.setToY(15);

            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(400), ripple);
            ft.setFromValue(1.0);
            ft.setToValue(0);

            // 动画结束后，自动把波纹对象从内存/UI树中安全移除，防止内存泄漏
            ft.setOnFinished(e -> inputPane.getChildren().remove(ripple));

            st.play();
            ft.play();
            // ============================

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

    private void loadExistingPoints(String locationStr) {
        points.clear();
        undoStack.clear(); // 加载已有数据时清空撤销栈，防止撤销到空白

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
                long ts = (coords.length > 3) ? Long.parseLong(coords[3]) : 0;
                points.add(new TeamPoint(x, y, teamIdx, state == 1, ts));
            } catch (Exception e) { }
        }
        updateUI();
    }

    private String getLocationsString() {
        StringBuilder sb = new StringBuilder();
        for (TeamPoint p : points) {
            int missInt = p.isMiss ? 1 : 0;
            sb.append(p.teamIndex).append(":")
                    .append(String.format("%.1f,%.1f,%d,%d;", p.x, p.y, missInt, p.timestamp));
        }
        return sb.toString();
    }

    @FXML
    private void handleClear() {
        saveState(); // 清空前保存快照，允许撤销清空操作
        points.clear();
        updateUI();
    }

    @FXML
    private void handleConfirm() {
        int totalHits = (int) points.stream().filter(p -> !p.isMiss).count();
        parentController.onFieldInputConfirmed(totalHits, getLocationsString());
        dialogStage.close();
    }
}