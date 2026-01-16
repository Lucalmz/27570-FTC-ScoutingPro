package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class HeatmapController {
    @FXML private Canvas heatCanvas;
    @FXML private Label teamTitleLabel;

    // --- 概率云参数配置 ---
    // 为了让渐变更柔和，稍微增加一点扩散半径 (Sigma 30 -> 35)
    // 这样边缘的下降坡度会更缓，有利于渐变渲染
    private static final double SIGMA = 35.0;
    private static final int KERNEL_RADIUS = (int) (SIGMA * 3);

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    public void setData(int teamNumber, List<ScoreEntry> matches) {
        teamTitleLabel.setText("Probability Cloud - Team " + teamNumber);

        new Thread(() -> {
            List<Point> validPoints = extractPoints(teamNumber, matches);
            Platform.runLater(() -> {
                if (validPoints.isEmpty()) return;
                drawProbabilityCloud(validPoints);
            });
        }).start();
    }

    private List<Point> extractPoints(int targetTeam, List<ScoreEntry> matches) {
        List<Point> points = new ArrayList<>();
        double width = heatCanvas.getWidth();

        for (ScoreEntry match : matches) {
            String locs = match.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            boolean isRedAlliance = "RED".equalsIgnoreCase(match.getAlliance());
            int targetIndexInMatch = 0;
            if (match.getTeam1() == targetTeam) targetIndexInMatch = 1;
            else if (match.getTeam2() == targetTeam) targetIndexInMatch = 2;

            if (match.getScoreType() == ScoreEntry.Type.SINGLE) {
                targetIndexInMatch = 1;
            }

            if (targetIndexInMatch == 0) continue;

            String[] rawPoints = locs.split(";");
            for (String pStr : rawPoints) {
                if (pStr.isEmpty()) continue;
                try {
                    int teamIdx = 1;
                    String coordsStr = pStr;
                    if (pStr.contains(":")) {
                        String[] parts = pStr.split(":");
                        teamIdx = Integer.parseInt(parts[0]);
                        coordsStr = parts[1];
                    }

                    if (teamIdx == targetIndexInMatch) {
                        String[] coords = coordsStr.split(",");
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);

                        if (isRedAlliance) {
                            x = width - x;
                        }
                        points.add(new Point(x, y));
                    }
                } catch (Exception e) { /* ignore */ }
            }
        }
        return points;
    }

    private void drawProbabilityCloud(List<Point> points) {
        int w = (int) heatCanvas.getWidth();
        int h = (int) heatCanvas.getHeight();

        double[][] densityMap = new double[w][h];

        // 预计算高斯核
        int kernelSize = KERNEL_RADIUS * 2 + 1;
        double[][] gaussianKernel = new double[kernelSize][kernelSize];
        double sigmaSq2 = 2 * SIGMA * SIGMA;

        for (int Ky = 0; Ky < kernelSize; Ky++) {
            for (int Kx = 0; Kx < kernelSize; Kx++) {
                double dy = Ky - KERNEL_RADIUS;
                double dx = Kx - KERNEL_RADIUS;
                double distSq = dx*dx + dy*dy;
                gaussianKernel[Kx][Ky] = Math.exp(-distSq / sigmaSq2);
            }
        }

        // 累积密度
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
                        double val = gaussianKernel[kx][ky];
                        densityMap[x][y] += val;
                        if (densityMap[x][y] > maxDensity) maxDensity = densityMap[x][y];
                    }
                }
            }
        }

        PixelWriter pw = heatCanvas.getGraphicsContext2D().getPixelWriter();

        // 使用动态归一化因子
        // Math.max(maxDensity, 1.5) 确保即使只有一个点，也不会红得刺眼，而是保持柔和
        double normalizationFactor = Math.max(maxDensity, 1.5);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double density = densityMap[x][y];

                // 核心修改 1: 极低阈值 (0.0001)，几乎不做截断，保证边缘有数据
                if (density > 0.0001) {
                    double ratio = density / normalizationFactor;
                    if (ratio > 1.0) ratio = 1.0;

                    pw.setColor(x, y, getSmoothGradientColor(ratio));
                } else {
                    pw.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
    }

    /**
     * 丝滑渐变算法：
     * 透明 -> 紫色 -> 蓝色 -> 青色 -> 绿色 -> 黄色 -> 红色
     */
    private Color getSmoothGradientColor(double ratio) {
        // 核心修改 2: Opacity 不再有基数 (0.2)，而是从 0.0 开始
        // 使用 pow(ratio, 0.4) 是为了让低密度区域也能稍微显色（提亮暗部），否则边缘太淡看不清
        // 限制最大透明度为 0.9，保留一点背景纹理
        double opacity = Math.min(0.9, Math.pow(ratio, 0.45));

        double hue;
        double saturation = 1.0;
        // 亮度随密度稍微增加，边缘稍微暗一点，中心亮一点，增加体积感
        double brightness = 0.8 + (ratio * 0.2);

        // --- 调整后的色相映射 ---
        // 我们利用 0.0 - 0.5 的广阔空间来做 紫色->蓝色 的过渡

        if (ratio < 0.4) {
            // [0.0 - 0.4] 极低密度边缘 -> 冷色
            // Hue: 280 (Deep Purple) -> 200 (Light Blue)
            // 这里覆盖了大部分边缘区域，紫色会慢慢融化在背景里
            double localRatio = ratio / 0.4;
            hue = 280 - (localRatio * 80);

        } else if (ratio < 0.75) {
            // [0.4 - 0.75] 中等密度 -> 过渡色
            // Hue: 200 (Light Blue) -> 80 (Green/Lime)
            double localRatio = (ratio - 0.4) / 0.35;
            hue = 200 - (localRatio * 120);

        } else {
            // [0.75 - 1.0] 高密度核心 -> 暖色警告
            // Hue: 80 (Lime) -> 0 (Red)
            // 只有最后 25% 的高频区域会变暖
            double localRatio = (ratio - 0.75) / 0.25;
            hue = 80 - (localRatio * 80);
        }

        return Color.hsb(hue, saturation, brightness, opacity);
    }
}