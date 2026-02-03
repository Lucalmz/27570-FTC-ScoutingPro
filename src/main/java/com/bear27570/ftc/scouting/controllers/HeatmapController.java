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
    @FXML private Label farShotsLabel;
    @FXML private Label farAccLabel;
    @FXML private Label nearShotsLabel;
    @FXML private Label nearAccLabel;
    @FXML private VBox statusBox;
    @FXML private Label statusTextLabel;
    @FXML private Label brokenTextLabel;

    private static final double ZONE_DIVIDER_Y = 400.0;
    private static final int POINTS_PER_HIT = 3;

    // --- 高级概率云参数 ---
    private static final double SIGMA = 35.0; // 扩散标准差
    private static final int KERNEL_RADIUS = (int) (SIGMA * 3);

    public void setData(int teamNumber, List<ScoreEntry> matches) {
        teamTitleLabel.setText("Team " + teamNumber + " Probability Cloud");

        // 状态更新
        updateStatusLabels(teamNumber, matches);

        // 异步计算并绘图
        new Thread(() -> {
            calculateAndDraw(teamNumber, matches);
        }).start();
    }

    private void updateStatusLabels(int teamNumber, List<ScoreEntry> matches) {
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
        int farShots = 0, farHits = 0;
        int nearShots = 0, nearHits = 0;

        long validMatchCount = matches.stream().filter(m -> {
            boolean p1 = (m.getTeam1() == teamNumber && !m.isTeam1Broken());
            boolean p2 = (m.getTeam2() == teamNumber && !m.isTeam2Broken());
            return p1 || p2;
        }).count();
        if (validMatchCount == 0) validMatchCount = 1;

        for (ScoreEntry m : matches) {
            if ((m.getTeam1() == teamNumber && m.isTeam1Broken()) ||
                    (m.getTeam2() == teamNumber && m.isTeam2Broken())) continue;

            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            // 联盟颜色判断，用于镜像位置（如果需要）
            boolean isRedAlliance = "RED".equalsIgnoreCase(m.getAlliance());

            for (String p : locs.split(";")) {
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

                        // 如果是红色联盟，通常需要绕场中心镜像X轴（根据你的场地逻辑决定是否保留）
                        // if (isRedAlliance) x = heatmapCanvas.getWidth() - x;

                        if (y < ZONE_DIVIDER_Y) {
                            nearShots++; if (isHit) nearHits++;
                        } else {
                            farShots++; if (isHit) farHits++;
                        }
                        if (isHit) hitPoints.add(new Point(x, y));
                    }
                } catch (Exception ignored) {}
            }
        }

        // 最终分析结果
        int finalFarShots = farShots;
        int finalFarHits = farHits;
        int finalNearShots = nearShots;
        int finalNearHits = nearHits;
        long finalValidMatchCount = validMatchCount;

        // 回到 UI 线程渲染
        Platform.runLater(() -> {
            drawProbabilityCloud(hitPoints);
            updateAnalysis(finalFarShots, finalFarHits, finalNearShots, finalNearHits, (int) finalValidMatchCount);
        });
    }

    private void drawProbabilityCloud(List<Point> points) {
        int w = (int) heatmapCanvas.getWidth();
        int h = (int) heatmapCanvas.getHeight();
        GraphicsContext gc = heatmapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // 绘制辅助线（保持原样）
        gc.setStroke(Color.web("#FFFFFF", 0.3));
        gc.setLineWidth(1);
        gc.setLineDashes(10);
        gc.strokeLine(0, ZONE_DIVIDER_Y, w, ZONE_DIVIDER_Y);
        gc.setLineDashes(null);
        gc.setFill(Color.web("#FFFFFF", 0.5));
        gc.fillText("NEAR ZONE", 10, ZONE_DIVIDER_Y - 10);
        gc.fillText("FAR ZONE", 10, ZONE_DIVIDER_Y + 20);

        if (points.isEmpty()) return;

        // 1. 预计算高斯核
        int kernelSize = KERNEL_RADIUS * 2 + 1;
        double[][] gaussianKernel = new double[kernelSize][kernelSize];
        double sigmaSq2 = 2 * SIGMA * SIGMA;
        for (int Ky = 0; Ky < kernelSize; Ky++) {
            for (int Kx = 0; Kx < kernelSize; Kx++) {
                double dy = Ky - KERNEL_RADIUS;
                double dx = Kx - KERNEL_RADIUS;
                gaussianKernel[Kx][Ky] = Math.exp(-(dx * dx + dy * dy) / sigmaSq2);
            }
        }

        // 2. 累积密度图
        double[][] densityMap = new double[w][h];
        double maxDensity = 0;
        for (Point p : points) {
            int cx = (int) p.x;
            int cy = (int) p.y;
            int startX = Math.max(0, cx - KERNEL_RADIUS);
            int endX = Math.min(w, cx + KERNEL_RADIUS);
            int startY = Math.max(0, cy - KERNEL_RADIUS);
            int endY = Math.min(h, cy + KERNEL_RADIUS);

            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    int kx = x - cx + KERNEL_RADIUS;
                    int ky = y - cy + KERNEL_RADIUS;
                    if (kx >= 0 && kx < kernelSize && ky >= 0 && ky < kernelSize) {
                        densityMap[x][y] += gaussianKernel[kx][ky];
                        if (densityMap[x][y] > maxDensity) maxDensity = densityMap[x][y];
                    }
                }
            }
        }

        // 3. 渲染像素
        PixelWriter pw = gc.getPixelWriter();
        double normalizationFactor = Math.max(maxDensity, 1.5); // 保护：避免单点过红

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double density = densityMap[x][y];
                if (density > 0.0001) {
                    double ratio = Math.min(1.0, density / normalizationFactor);
                    pw.setColor(x, y, getSmoothGradientColor(ratio));
                }
            }
        }
    }

    /**
     * 经典热力图渐变：蓝色 -> 青色 -> 绿色 -> 黄色 -> 红色 (去掉了紫色)
     */
    private Color getSmoothGradientColor(double ratio) {
        // 依然使用指数函数处理透明度，确保边缘柔和
        double opacity = Math.min(0.9, Math.pow(ratio, 0.45));

        double hue;
        double saturation = 1.0;
        // 稍微提高整体亮度，使蓝色在深色背景上更清晰
        double brightness = 0.9 + (ratio * 0.1);

        if (ratio < 0.3) {
            // [0.0 - 0.3] 边缘低密度：深蓝色(240) -> 浅蓝色(200)
            double localRatio = ratio / 0.3;
            hue = 240 - (localRatio * 40);

        } else if (ratio < 0.6) {
            // [0.3 - 0.6] 低中密度：浅蓝色(200) -> 绿色(120)
            double localRatio = (ratio - 0.3) / 0.3;
            hue = 200 - (localRatio * 80);

        } else if (ratio < 0.85) {
            // [0.6 - 0.85] 中高密度：绿色(120) -> 黄色(60)
            double localRatio = (ratio - 0.6) / 0.25;
            hue = 120 - (localRatio * 60);

        } else {
            // [0.85 - 1.0] 核心高密度：黄色(60) -> 红色(0)
            double localRatio = (ratio - 0.85) / 0.15;
            hue = 60 - (localRatio * 60);
        }

        return Color.hsb(hue, saturation, brightness, opacity);
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

        double pps = (double) (totalHits * POINTS_PER_HIT) / totalShots;
        efficiencyLabel.setText(String.format("%.2f PPS", pps));
        if (pps >= 2.5) efficiencyLabel.setStyle("-fx-text-fill: #FF5252; -fx-font-size: 24px; -fx-font-weight: bold;");
        else if (pps >= 1.5) efficiencyLabel.setStyle("-fx-text-fill: #FFEB3B; -fx-font-size: 24px; -fx-font-weight: bold;");
        else efficiencyLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 24px; -fx-font-weight: bold;");
    }

    private static class Point { double x, y; Point(double x, double y) { this.x = x; this.y = y; } }
}