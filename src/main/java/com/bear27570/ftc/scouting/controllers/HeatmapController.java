package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

public class HeatmapController {
    @FXML private Label teamTitleLabel;
    @FXML private Canvas heatmapCanvas;

    // 右侧边栏控件
    @FXML private Label playStyleLabel;
    @FXML private Label efficiencyLabel;

    // 效率拆分
    @FXML private Label farShotsLabel; // 显示远点场均进球
    @FXML private Label farAccLabel;
    @FXML private Label nearShotsLabel; // 显示近点场均进球
    @FXML private Label nearAccLabel;

    // 新增：右侧状态显示
    @FXML private VBox statusBox;
    @FXML private Label statusTextLabel;
    @FXML private Label brokenTextLabel;

    private static final double ZONE_DIVIDER_Y = 400.0;

    // 修改：所有球统一为 3 分
    private static final int POINTS_PER_HIT = 3;

    public void setData(int teamNumber, List<ScoreEntry> matches) {
        teamTitleLabel.setText("Team " + teamNumber + " Heatmap");

        // --- 1. 状态检查 (Weak/Ignored) ---
        boolean isIgnored = matches.stream().anyMatch(m ->
                (m.getTeam1() == teamNumber && m.isTeam1Ignored()) ||
                        (m.getTeam2() == teamNumber && m.isTeam2Ignored())
        );
        if (isIgnored) {
            statusTextLabel.setText("IGNORED / WEAK");
            statusTextLabel.setTextFill(Color.web("#FF5252"));
        } else {
            statusTextLabel.setText("ACTIVE");
            statusTextLabel.setTextFill(Color.LIGHTGREEN);
        }

        // --- 2. 车坏了场次统计 ---
        List<Integer> brokenMatches = matches.stream()
                .filter(m -> (m.getTeam1() == teamNumber && m.isTeam1Broken()) ||
                        (m.getTeam2() == teamNumber && m.isTeam2Broken()))
                .map(ScoreEntry::getMatchNumber)
                .sorted()
                .collect(Collectors.toList());

        if (brokenMatches.isEmpty()) {
            brokenTextLabel.setText("None");
        } else {
            brokenTextLabel.setText("Matches: " + brokenMatches.toString());
        }

        calculateAndDraw(teamNumber, matches);
    }

    private void calculateAndDraw(int teamNumber, List<ScoreEntry> matches) {
        GraphicsContext gc = heatmapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, heatmapCanvas.getWidth(), heatmapCanvas.getHeight());

        gc.setStroke(Color.web("#FFFFFF", 0.3));
        gc.setLineWidth(1);
        gc.setLineDashes(10);
        gc.strokeLine(0, ZONE_DIVIDER_Y, heatmapCanvas.getWidth(), ZONE_DIVIDER_Y);
        gc.setLineDashes(null);

        gc.setFill(Color.web("#FFFFFF", 0.5));
        // 修改：Top = Near, Bottom = Far
        gc.fillText("NEAR ZONE (Top)", 10, ZONE_DIVIDER_Y - 10);
        gc.fillText("FAR ZONE (Bottom)", 10, ZONE_DIVIDER_Y + 20);

        int farShots = 0, farHits = 0;
        int nearShots = 0, nearHits = 0;

        // 计算有效场次 (剔除Broken的场次可能更准，但此处按“出场数”算)
        long validMatchCount = matches.stream().filter(m -> {
            boolean p1 = (m.getTeam1() == teamNumber && !m.isTeam1Broken());
            boolean p2 = (m.getTeam2() == teamNumber && !m.isTeam2Broken());
            return p1 || p2;
        }).count();
        if (validMatchCount == 0) validMatchCount = 1; // 避免除0

        for (ScoreEntry m : matches) {
            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;
            String[] points = locs.split(";");
            for (String p : points) {
                try {
                    String[] parts = p.split(":");
                    if (parts.length < 2) continue;
                    int pTeamIdx = Integer.parseInt(parts[0]);
                    int actualTeamNum = (pTeamIdx == 1) ? m.getTeam1() : m.getTeam2();

                    if (actualTeamNum == teamNumber) {
                        String[] coords = parts[1].split(",");
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);
                        int state = (coords.length >= 3) ? Integer.parseInt(coords[2]) : 0;
                        boolean isHit = (state == 0);

                        // 修改判定：y < 400 是上方(Near)，y > 400 是下方(Far)
                        if (y < ZONE_DIVIDER_Y) {
                            nearShots++;
                            if (isHit) nearHits++;
                        } else {
                            farShots++;
                            if (isHit) farHits++;
                        }

                        if (isHit) {
                            gc.setFill(Color.rgb(0, 255, 0, 0.6));
                            gc.fillOval(x - 5, y - 5, 10, 10);
                        } else {
                            gc.setStroke(Color.rgb(255, 0, 0, 0.6));
                            gc.setLineWidth(2);
                            gc.strokeLine(x - 5, y - 5, x + 5, y + 5);
                            gc.strokeLine(x + 5, y - 5, x - 5, y + 5);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        updateAnalysis(farShots, farHits, nearShots, nearHits, (int)validMatchCount);
    }

    private void updateAnalysis(int farShots, int farHits, int nearShots, int nearHits, int matchCount) {
        int totalShots = farShots + nearShots;
        int totalHits = farHits + nearHits;

        double avgFarHits = (double) farHits / matchCount;
        double avgNearHits = (double) nearHits / matchCount;

        double farAcc = (farShots > 0) ? (double) farHits / farShots * 100.0 : 0;
        double nearAcc = (nearShots > 0) ? (double) nearHits / nearShots * 100.0 : 0;

        farShotsLabel.setText(String.format("%.1f Hits/M", avgFarHits));
        nearShotsLabel.setText(String.format("%.1f Hits/M", avgNearHits));

        farAccLabel.setText(String.format("%.1f%%", farAcc));
        nearAccLabel.setText(String.format("%.1f%%", nearAcc));

        if (totalShots == 0) {
            playStyleLabel.setText("No Data");
            efficiencyLabel.setText("0.0 PPS");
            return;
        }

        // 修改风格判定：远点占比高 (Far > Near)
        double farRatio = (double) farShots / totalShots;
        if (farRatio > 0.65) {
            playStyleLabel.setText("Far Zone Specialist");
            playStyleLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 16px; -fx-font-weight: bold;");
        } else if (farRatio < 0.35) {
            playStyleLabel.setText("Near Zone Rusher");
            playStyleLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 16px; -fx-font-weight: bold;");
        } else {
            playStyleLabel.setText("Hybrid");
            playStyleLabel.setStyle("-fx-text-fill: #00BCD4; -fx-font-size: 16px; -fx-font-weight: bold;");
        }

        // 修改：效率计算 PPS = (TotalHits * 3) / TotalShots. 结果应 <= 3.0
        double totalScore = totalHits * POINTS_PER_HIT;
        double pps = totalScore / totalShots;

        efficiencyLabel.setText(String.format("%.2f PPS", pps));

        // 阈值调整：最高3.0
        if (pps >= 2.5) efficiencyLabel.setStyle("-fx-text-fill: #FF5252; -fx-font-size: 24px; -fx-font-weight: bold;");
        else if (pps >= 1.5) efficiencyLabel.setStyle("-fx-text-fill: #FFEB3B; -fx-font-size: 24px; -fx-font-weight: bold;");
        else efficiencyLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 24px; -fx-font-weight: bold;");
    }
}