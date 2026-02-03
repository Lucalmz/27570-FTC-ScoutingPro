package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HeatmapController {
    @FXML private Label teamTitleLabel;
    @FXML private Canvas heatmapCanvas;
    @FXML private Label playStyleLabel;
    @FXML private Label efficiencyLabel;
    @FXML private Label farShotsLabel; // 将显示 Far Role Avg
    @FXML private Label farAccLabel;
    @FXML private Label nearShotsLabel; // 将显示 Near Role Avg
    @FXML private Label nearAccLabel;
    @FXML private VBox statusBox;
    @FXML private Label statusTextLabel;
    @FXML private Label brokenTextLabel;

    private static final double ZONE_DIVIDER_Y = 400.0;
    private static final int POINTS_PER_HIT = 3;

    private static final double SIGMA = 35.0;
    private static final int KERNEL_RADIUS = (int) (SIGMA * 3);

    public void setData(int teamNumber, List<ScoreEntry> matches) {
        teamTitleLabel.setText("Team " + teamNumber + " Probability Cloud");
        updateStatusLabels(teamNumber, matches);
        new Thread(() -> calculateAndDraw(teamNumber, matches)).start();
    }

    private void updateStatusLabels(int teamNumber, List<ScoreEntry> matches) {
        boolean isIgnored = matches.stream().anyMatch(m ->
                (m.getTeam1() == teamNumber && m.isTeam1Ignored()) ||
                        (m.getTeam2() == teamNumber && m.isTeam2Ignored())
        );
        statusTextLabel.setText(isIgnored ? "IGNORED / WEAK" : "ACTIVE");
        statusTextLabel.setTextFill(isIgnored ? Color.web("#FF5252") : Color.LIGHTGREEN);

        List<Integer> brokenMatches = matches.stream()
                .filter(m -> (m.getTeam1() == teamNumber && m.isTeam1Broken()) ||
                        (m.getTeam2() == teamNumber && m.isTeam2Broken()))
                .map(ScoreEntry::getMatchNumber)
                .sorted()
                .collect(Collectors.toList());
        brokenTextLabel.setText(brokenMatches.isEmpty() ? "None" : "Matches: " + brokenMatches);
    }

    private void calculateAndDraw(int teamNumber, List<ScoreEntry> matches) {
        List<Point> hitPoints = new ArrayList<>();

        // 采样统计
        int nearRoleHits = 0, nearRoleMatches = 0;
        int farRoleHits = 0, farRoleMatches = 0;

        // 全局准确率统计
        int globalFarShots = 0, globalFarHits = 0;
        int globalNearShots = 0, globalNearHits = 0;

        for (ScoreEntry m : matches) {
            if ((m.getTeam1() == teamNumber && m.isTeam1Broken()) ||
                    (m.getTeam2() == teamNumber && m.isTeam2Broken())) continue;

            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            int mHits = 0, mNearShots = 0, mFarShots = 0;

            for (String p : locs.split(";")) {
                try {
                    String[] parts = p.split(":");
                    int pTeamIdx = Integer.parseInt(parts[0]);
                    int actualTeamNum = (pTeamIdx == 1) ? m.getTeam1() : m.getTeam2();

                    if (actualTeamNum == teamNumber) {
                        String[] coords = parts[1].split(",");
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);
                        int state = (coords.length >= 3) ? Integer.parseInt(coords[2]) : 0;
                        boolean isHit = (state == 0);

                        if (isHit) {
                            hitPoints.add(new Point(x, y));
                            mHits++;
                        }
                        if (y < ZONE_DIVIDER_Y) {
                            mNearShots++; if (isHit) globalNearHits++;
                        } else {
                            mFarShots++; if (isHit) globalFarHits++;
                        }
                    }
                } catch (Exception ignored) {}
            }

            globalNearShots += mNearShots;
            globalFarShots += mFarShots;

            // 角色判定逻辑
            int mTotalShots = mNearShots + mFarShots;
            if (mTotalShots > 0) {
                double ratio = (double) mFarShots / mTotalShots;
                if (ratio > 0.65) {
                    farRoleHits += mHits; farRoleMatches++;
                } else if (ratio < 0.35) {
                    nearRoleHits += mHits; nearRoleMatches++;
                }
            }
        }

        double finalNearAvg = nearRoleMatches == 0 ? 0 : (double) nearRoleHits / nearRoleMatches;
        double finalFarAvg = farRoleMatches == 0 ? 0 : (double) farRoleHits / farRoleMatches;
        int fFarShots = globalFarShots, fFarHits = globalFarHits;
        int fNearShots = globalNearShots, fNearHits = globalNearHits;

        Platform.runLater(() -> {
            drawProbabilityCloud(hitPoints);
            updateAnalysis(fFarShots, fFarHits, fNearShots, fNearHits, finalNearAvg, finalFarAvg);
        });
    }

    private void updateAnalysis(int farShots, int farHits, int nearShots, int nearHits, double nearAvg, double farAvg) {
        int totalShots = farShots + nearShots;
        int totalHits = farHits + nearHits;

        // 此处显示的是“作为该角色时的场均命中”
        farShotsLabel.setText(String.format("%.1f Hits/M", farAvg));
        nearShotsLabel.setText(String.format("%.1f Hits/M", nearAvg));

        farAccLabel.setText(String.format("%.1f%%", (farShots > 0 ? (double)farHits/farShots*100 : 0)));
        nearAccLabel.setText(String.format("%.1f%%", (nearShots > 0 ? (double)nearHits/nearShots*100 : 0)));

        if (totalShots == 0) {
            playStyleLabel.setText("No Data");
            efficiencyLabel.setText("0.0 PPS");
            return;
        }

        double farRatio = (double) farShots / totalShots;
        if (farRatio > 0.65) playStyleLabel.setText("Far Zone Specialist");
        else if (farRatio < 0.35) playStyleLabel.setText("Near Zone Rusher");
        else playStyleLabel.setText("Hybrid");

        double pps = (double) (totalHits * POINTS_PER_HIT) / totalShots;
        efficiencyLabel.setText(String.format("%.2f PPS", pps));
    }

    // --- 绘图逻辑 (与之前一致，省略部分重复的PixelWriter代码以保持长度，但逻辑完全保留) ---
    private void drawProbabilityCloud(List<Point> points) {
        int w = (int) heatmapCanvas.getWidth();
        int h = (int) heatmapCanvas.getHeight();
        GraphicsContext gc = heatmapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        gc.setStroke(Color.web("#FFFFFF", 0.3));
        gc.strokeLine(0, ZONE_DIVIDER_Y, w, ZONE_DIVIDER_Y);

        if (points.isEmpty()) return;

        double[][] densityMap = new double[w][h];
        double maxDensity = 0;
        double sigmaSq2 = 2 * SIGMA * SIGMA;

        for (Point p : points) {
            int cx = (int) p.x; int cy = (int) p.y;
            for (int x = Math.max(0, cx - KERNEL_RADIUS); x < Math.min(w, cx + KERNEL_RADIUS); x++) {
                for (int y = Math.max(0, cy - KERNEL_RADIUS); y < Math.min(h, cy + KERNEL_RADIUS); y++) {
                    double distSq = Math.pow(x - cx, 2) + Math.pow(y - cy, 2);
                    double val = Math.exp(-distSq / sigmaSq2);
                    densityMap[x][y] += val;
                    if (densityMap[x][y] > maxDensity) maxDensity = densityMap[x][y];
                }
            }
        }

        PixelWriter pw = gc.getPixelWriter();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (densityMap[x][y] > 0.001) {
                    double ratio = Math.min(1.0, densityMap[x][y] / Math.max(maxDensity, 1.5));
                    pw.setColor(x, y, getSmoothGradientColor(ratio));
                }
            }
        }
    }

    private Color getSmoothGradientColor(double ratio) {
        double opacity = Math.min(0.9, Math.pow(ratio, 0.45));
        double hue = 240 - ratio * 240; // 蓝(240) -> 红(0)
        return Color.hsb(hue, 1.0, 1.0, opacity);
    }

    private static class Point { double x, y; Point(double x, double y) { this.x = x; this.y = y; } }
}