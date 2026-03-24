package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.services.NetworkService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    // 用于存放历史记录的栈，实现撤销功能
    private final Stack<List<TeamPoint>> undoStack = new Stack<>();

    private boolean isAllianceMode = true;
    private Label warningLabel;
    private static final Logger log = LoggerFactory.getLogger(FieldInputController.class);
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

    private void saveState() {
        List<TeamPoint> snapshot = new ArrayList<>();
        for (TeamPoint p : points) {
            snapshot.add(p.copy());
        }
        undoStack.push(snapshot);
    }

    @FXML
    private void handleUndo() {
        if (!undoStack.isEmpty()) {
            points.clear();
            points.addAll(undoStack.pop());
            updateUI();
        }
    }

    private void handleKeyPressed(KeyEvent event) {
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

        saveState();

        double x = event.getX();
        double y = event.getY();

        if (addModeBtn.isSelected()) {
            int currentTeam = team1Btn.isSelected() ? 1 : 2;
            boolean isMiss = (event.getButton() == MouseButton.SECONDARY);
            points.add(new TeamPoint(x, y, currentTeam, isMiss, System.currentTimeMillis()));

            // ====== 声纳波纹动效 ======
            javafx.scene.shape.Circle ripple = new javafx.scene.shape.Circle(1);
            ripple.setStroke(currentTeam == 1 ? Color.web("#00BCD4") : Color.web("#E91E63"));
            ripple.setFill(Color.TRANSPARENT);
            ripple.setStrokeWidth(2);

            ripple.setManaged(false);
            ripple.setMouseTransparent(true);
            javafx.geometry.Point2D sceneCoords = drawCanvas.localToScene(x, y);
            javafx.geometry.Point2D paneCoords = inputPane.sceneToLocal(sceneCoords);

            ripple.setLayoutX(paneCoords.getX());
            ripple.setLayoutY(paneCoords.getY());

            inputPane.getChildren().add(ripple);

            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(Duration.millis(250), ripple);
            st.setToX(15);
            st.setToY(15);

            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(250), ripple);
            ft.setFromValue(1.0);
            ft.setToValue(0);

            ft.setOnFinished(e -> inputPane.getChildren().remove(ripple));

            st.play();
            ft.play();

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
        undoStack.clear();

        String[] entries = locationStr.split(";");
        for (String entry : entries) {
            // 🌟 修复：严谨拦截所有解析异常并记录脏数据上下文
            if (entry == null || entry.trim().isEmpty()) continue;
            try {
                String[] parts = entry.split(":");
                if (parts.length < 2) {
                    log.error("⚠️ [FieldInput] 坐标片段缺少冒号分隔符，已跳过: {}", entry);
                    continue;
                }

                int teamIdx = Integer.parseInt(parts[0]);
                String[] coords = parts[1].split(",");

                if (coords.length < 3) {
                    log.error("⚠️ [FieldInput] 坐标数据不完整(需 x,y,state)，已跳过:  {}", entry);
                    continue;
                }

                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                int state = Integer.parseInt(coords[2]);
                long ts = (coords.length > 3) ? Long.parseLong(coords[3]) : 0;

                points.add(new TeamPoint(x, y, teamIdx, state == 1, ts));

            } catch (NumberFormatException e) {
                log.error("❌ [FieldInput] 坐标数字解析失败，存在非法字符: '" + entry + "' | 详情: " + e.getMessage());
            } catch (Exception e) {
                log.error("❌ [FieldInput] 坐标解析发生未知异常: '" + entry + "' | 详情: " + e.getMessage(),e);
            }
        }
        updateUI();
    }

    private String getLocationsString() {
        StringBuilder sb = new StringBuilder();
        for (TeamPoint p : points) {
            int missInt = p.isMiss ? 1 : 0;
            sb.append(p.teamIndex).append(":")
                    .append(String.format(Locale.US,"%.1f,%.1f,%d,%d;", p.x, p.y, missInt, p.timestamp));
        }
        return sb.toString();
    }

    @FXML
    private void handleClear() {
        saveState();
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