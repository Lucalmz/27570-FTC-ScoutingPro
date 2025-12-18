package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.ScoreEntry;
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

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    public void setData(int teamNumber, List<ScoreEntry> matches) {
        teamTitleLabel.setText("Heatmap Analysis for Team " + teamNumber);

        // 1. 提取所有有效坐标点
        List<Point> validPoints = extractPoints(teamNumber, matches);

        // 2. 如果没有数据，直接返回
        if (validPoints.isEmpty()) return;

        // 3. 绘制高级热力图
        drawAdvancedHeatmap(validPoints);
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

            // 如果是 SINGLE 模式，且队伍匹配，那么所有点都属于该队 (Team 1)
            if (match.getScoreType() == ScoreEntry.Type.SINGLE) {
                targetIndexInMatch = 1;
            }

            if (targetIndexInMatch == 0) continue; // 没找到该队，跳过

            String[] rawPoints = locs.split(";");
            for (String pStr : rawPoints) {
                if (pStr.isEmpty()) continue;
                try {
                    // 解析格式: "Index:x,y" (新格式) 或 "x,y" (旧格式兼容)
                    int teamIdx = 1; // 默认归属 Team 1 (兼容旧数据)
                    String coordsStr = pStr;

                    if (pStr.contains(":")) {
                        String[] parts = pStr.split(":");
                        teamIdx = Integer.parseInt(parts[0]);
                        coordsStr = parts[1];
                    }

                    // 只有当点的归属索引 == 队伍在这场比赛的位置索引时，才纳入计算
                    if (teamIdx == targetIndexInMatch) {
                        String[] coords = coordsStr.split(",");
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);

                        // 红方数据镜像翻转
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

    private void drawAdvancedHeatmap(List<Point> points) {
        int w = (int) heatCanvas.getWidth();
        int h = (int) heatCanvas.getHeight();

        // 密度矩阵
        double[][] density = new double[w][h];
        double maxDensity = 0;

        // 扩散半径 (影响热点的大小)
        int radius = 40;
        // 扩散强度衰减 (高斯分布简化版)

        for (Point p : points) {
            int cx = (int) p.x;
            int cy = (int) p.y;

            // 仅遍历点周围的矩形区域，加速计算
            int startX = Math.max(0, cx - radius);
            int endX = Math.min(w, cx + radius);
            int startY = Math.max(0, cy - radius);
            int endY = Math.min(h, cy + radius);

            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    double dist = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2));
                    if (dist < radius) {
                        // 线性衰减：中心加 1.0，边缘加 0.0
                        double val = 1.0 - (dist / radius);
                        density[x][y] += val;
                        if (density[x][y] > maxDensity) maxDensity = density[x][y];
                    }
                }
            }
        }

        // 渲染到 Canvas
        PixelWriter pw = heatCanvas.getGraphicsContext2D().getPixelWriter();

        // 为了视觉效果，如果 maxDensity 太小，强制设一个最小值，避免只投了一个球就变成大红色
        if (maxDensity < 3.0) maxDensity = 3.0;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double val = density[x][y];
                if (val > 0) {
                    double ratio = val / maxDensity;
                    // 限制最大为 1.0
                    if (ratio > 1.0) ratio = 1.0;

                    // 获取颜色
                    Color color = getHeatMapColor(ratio);
                    pw.setColor(x, y, color);
                } else {
                    // 透明
                    pw.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
    }

    /**
     * 将 0.0 - 1.0 的强度映射为 蓝 -> 绿 -> 黄 -> 红
     * 使用 HSB 色彩空间：
     * Hue: 240 (Blue) -> 0 (Red)
     */
    private Color getHeatMapColor(double value) {
        // 范围映射：value (0->1)  => Hue (240 -> 0)
        // 稍微截断一下，从 0.1 开始显示颜色，否则太淡
        double hue = 240.0 * (1.0 - value);

        // 透明度：强度越高越不透明，最低 0.3，最高 0.8 (保留一点背景可见性)
        double opacity = 0.3 + (0.5 * value);

        return Color.hsb(hue, 1.0, 1.0, opacity);
    }
}