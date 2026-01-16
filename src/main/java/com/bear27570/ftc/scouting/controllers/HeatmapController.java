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
    // 带宽 (Bandwidth/Sigma): 控制云的扩散程度。
    // 值越大越模糊（概括性强），值越小越精确（特异性强）。推荐 25-40。
    private static final double SIGMA = 30.0;

    // 核半径：通常取 3 * Sigma，覆盖 99% 的高斯分布区域
    private static final int KERNEL_RADIUS = (int) (SIGMA * 3);

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    public void setData(int teamNumber, List<ScoreEntry> matches) {
        teamTitleLabel.setText("Probability Cloud Analysis - Team " + teamNumber);

        // 异步计算以防止界面卡顿（虽然优化后很快，但在大量数据下保持UI响应是个好习惯）
        new Thread(() -> {
            // 1. 提取有效点
            List<Point> validPoints = extractPoints(teamNumber, matches);

            // 2. 渲染
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

            // 确定目标队伍在这场比赛中是 Team 1 还是 Team 2
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

                        // 统一坐标系：红方镜像翻转，使得所有投篮看起来都像是在同一侧进攻
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

    /**
     * 使用高斯核密度估计 (Gaussian KDE) 绘制概率云
     */
    private void drawProbabilityCloud(List<Point> points) {
        int w = (int) heatCanvas.getWidth();
        int h = (int) heatCanvas.getHeight();

        // 1. 初始化密度矩阵
        double[][] densityMap = new double[w][h];

        // 2. 预计算高斯核 (Optimization: Pre-computed Stencil)
        // 这避免了对每个像素重复计算 Math.exp，大幅提升性能
        int kernelSize = KERNEL_RADIUS * 2 + 1;
        double[][] gaussianKernel = new double[kernelSize][kernelSize];
        double sigmaSq2 = 2 * SIGMA * SIGMA;

        for (int Ky = 0; Ky < kernelSize; Ky++) {
            for (int Kx = 0; Kx < kernelSize; Kx++) {
                double dy = Ky - KERNEL_RADIUS;
                double dx = Kx - KERNEL_RADIUS;
                double distSq = dx*dx + dy*dy;
                // 高斯公式: e^(-d^2 / 2σ^2)
                gaussianKernel[Kx][Ky] = Math.exp(-distSq / sigmaSq2);
            }
        }

        // 3. 将核叠加到密度图上 (Accumulate Density)
        double maxDensity = 0;

        for (Point p : points) {
            int cx = (int) p.x;
            int cy = (int) p.y;

            // 仅遍历受影响的矩形区域
            int startX = Math.max(0, cx - KERNEL_RADIUS);
            int endX = Math.min(w, cx + KERNEL_RADIUS);
            int startY = Math.max(0, cy - KERNEL_RADIUS);
            int endY = Math.min(h, cy + KERNEL_RADIUS);

            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    // 从预计算核中获取值
                    int kx = x - cx + KERNEL_RADIUS;
                    int ky = y - cy + KERNEL_RADIUS;

                    // 边界检查（理论上循环边界已处理，但为了安全）
                    if (kx >= 0 && kx < kernelSize && ky >= 0 && ky < kernelSize) {
                        double val = gaussianKernel[kx][ky];
                        densityMap[x][y] += val;
                        if (densityMap[x][y] > maxDensity) {
                            maxDensity = densityMap[x][y];
                        }
                    }
                }
            }
        }

        // 4. 渲染 (Rendering)
        PixelWriter pw = heatCanvas.getGraphicsContext2D().getPixelWriter();

        // 阈值处理：如果 maxDensity 太小（数据点极少），强制提高基准，避免单一的点变成深红色
        // 这让单次投篮看起来像淡淡的云，而不是强烈的热点
        double normalizationFactor = Math.max(maxDensity, 1.5);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double density = densityMap[x][y];

                if (density > 0.01) { // 极小值忽略，优化透明度
                    // 归一化 (0.0 - 1.0)
                    double ratio = density / normalizationFactor;
                    if (ratio > 1.0) ratio = 1.0;

                    pw.setColor(x, y, getProbabilityColor(ratio));
                } else {
                    pw.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
    }

    /**
     * 专业的概率密度色阶映射
     * 0.0 - 0.2 : 透明 -> 蓝色 (Cold/Low Probability)
     * 0.2 - 0.5 : 蓝色 -> 绿色 -> 黄色
     * 0.5 - 1.0 : 黄色 -> 红色 (Hot/High Probability)
     */
    private Color getProbabilityColor(double ratio) {
        // 只有当概率密度超过一定阈值才开始显示颜色，制造"云"的边缘淡出效果
        double opacity = Math.min(0.85, ratio * 1.5); // 最高透明度 0.85，保留背景可见性

        // 使用 HSB 色彩空间进行平滑过渡
        // Hue: 240(Blue) -> 120(Green) -> 60(Yellow) -> 0(Red)
        // 我们希望主要分布在 240 -> 0 区间

        double hue;
        double saturation = 1.0;
        double brightness = 1.0;

        // 非线性映射，让红色区域（高密度）更集中，蓝色区域（低密度）范围更广
        // 这样视觉上更容易区分“偶尔出现”和“经常出现”
        if (ratio < 0.3) {
            // 0.0 - 0.3: Blue range (240 - 200)
            hue = 240 - (ratio / 0.3) * 40;
        } else if (ratio < 0.6) {
            // 0.3 - 0.6: Cyan to Green to Yellow (200 - 60)
            hue = 200 - ((ratio - 0.3) / 0.3) * 140;
        } else {
            // 0.6 - 1.0: Yellow to Red (60 - 0)
            hue = 60 - ((ratio - 0.6) / 0.4) * 60;
        }

        return Color.hsb(hue, saturation, brightness, opacity);
    }
}