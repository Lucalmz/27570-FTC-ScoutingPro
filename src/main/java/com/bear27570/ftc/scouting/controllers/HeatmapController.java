// File: HeatmapController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HeatmapController {

    // ==================== FXML 绑定的 UI 组件 ====================

    // 顶部标题
    @FXML private Label teamTitleLabel;

    // 热力图画布
    @FXML private Canvas heatmapCanvas;

    // 右侧分析面板标签
    @FXML private Label playStyleLabel;
    @FXML private Label efficiencyLabel;
    @FXML private Label farShotsLabel;
    @FXML private Label farAccLabel;
    @FXML private Label nearShotsLabel;
    @FXML private Label nearAccLabel;
    @FXML private Label statusTextLabel;
    @FXML private Label brokenTextLabel;
    @FXML private Label cyclesLabel;

    // 底部比赛详情表格 (含判罚数据)
    @FXML private TableView<MatchDetailRow> matchTable;
    @FXML private TableColumn<MatchDetailRow, Integer> colMatchNum;
    @FXML private TableColumn<MatchDetailRow, String> colAlliance;
    @FXML private TableColumn<MatchDetailRow, Integer> colAuto;
    @FXML private TableColumn<MatchDetailRow, Integer> colTeleop;
    @FXML private TableColumn<MatchDetailRow, String> colPenalties;
    @FXML private TableColumn<MatchDetailRow, Integer> colScore;
    @FXML private TableColumn<MatchDetailRow, String> colStatus;

    // ==================== 状态变量与常量 ====================

    private int currentTeamNumber;

    private static final double ZONE_DIVIDER_Y = 400.0;
    private static final int POINTS_PER_HIT = 3;

    // 热力图高斯模糊参数
    private static final double SIGMA = 35.0;
    private static final int KERNEL_RADIUS = (int) (SIGMA * 3);

    // ==================== 内部数据包装类 ====================

    /**
     * 用于在 TableView 中展示整合后数据的包装类 (包含得分、状态和罚分)
     */
    public static class MatchDetailRow {
        private final ScoreEntry entry;
        private final String penaltiesInfo;
        private final String statusInfo;

        public MatchDetailRow(ScoreEntry entry, int currentTeam, Map<Integer, PenaltyRepository.FullPenaltyRow> penMap) {
            this.entry = entry;

            // 1. 解析机器状态
            boolean isTeam1 = (entry.getTeam1() == currentTeam);
            boolean isBroken = isTeam1 ? entry.isTeam1Broken() : entry.isTeam2Broken();
            boolean isIgnored = isTeam1 ? entry.isTeam1Ignored() : entry.isTeam2Ignored();

            if (isBroken) {
                this.statusInfo = "⚠ Broken";
            } else if (isIgnored) {
                this.statusInfo = "⚠ Weak/Ignored";
            } else {
                this.statusInfo = "✔ Active";
            }

            // 2. 解析并计算判罚 (FTCScout 真实数据)
            PenaltyRepository.FullPenaltyRow pe = penMap != null ? penMap.get(entry.getMatchNumber()) : null;
            if (pe != null) {
                // 计算红蓝方各自犯规送出的分数 (Major=15, Minor=5)
                int redGaveAway = (pe.rMaj * 15) + (pe.rMin * 5);
                int blueGaveAway = (pe.bMaj * 15) + (pe.bMin * 5);

                // 判断当前队伍所在联盟
                boolean isRed = entry.getAlliance().equalsIgnoreCase("RED");
                int comm = isRed ? redGaveAway : blueGaveAway;         // 己方犯规送给对面的分
                int oppGained = isRed ? blueGaveAway : redGaveAway;    // 对手犯规白送给己方的分

                // 如果是 2v2 联盟模式，将判罚均摊给两支队伍 (平滑数据)
                if (entry.getScoreType() == ScoreEntry.Type.ALLIANCE) {
                    comm /= 2;
                    oppGained /= 2;
                }

                // 格式化展示: -犯规失分 / +对手白给分
                this.penaltiesInfo = String.format("-%d / +%d", comm, oppGained);
            } else {
                this.penaltiesInfo = "N/A"; // 没有绑定或没有抓取到该场判罚数据
            }
        }

        // JavaFX TableView 需要用到的 Getter
        public int getMatchNumber() { return entry.getMatchNumber(); }
        public String getAlliance() { return entry.getAlliance(); }
        public int getAuto() { return entry.getAutoArtifacts(); }
        public int getTeleop() { return entry.getTeleopArtifacts(); }
        public int getScore() { return entry.getTotalScore(); }
        public String getPenalties() { return penaltiesInfo; }
        public String getStatus() { return statusInfo; }
    }

    // ==================== 控制器初始化与数据入口 ====================

    @FXML
    public void initialize() {
        // 初始化表格的列数据绑定
        colMatchNum.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getMatchNumber()).asObject());
        colAlliance.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAlliance()));
        colAuto.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getAuto()).asObject());
        colTeleop.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getTeleop()).asObject());
        colScore.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getScore()).asObject());
        colPenalties.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPenalties()));
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
    }

    /**
     * 外部调用接口：注入队伍数据并开始分析渲染
     * @param teamNumber 队伍编号
     * @param matches 该队伍的所有历史比赛记录
     * @param penaltyMap 从数据库获取的该赛事的判罚哈希表
     */
    public void setData(int teamNumber, List<ScoreEntry> matches, Map<Integer, PenaltyRepository.FullPenaltyRow> penaltyMap) {
        this.currentTeamNumber = teamNumber;
        teamTitleLabel.setText("Team " + teamNumber + " Probability Cloud");

        // 1. 更新右上角的活跃状态/损坏场次提示
        updateStatusLabels(teamNumber, matches);

        // 2. 将数据封装为 MatchDetailRow 并填充到底部表格
        List<MatchDetailRow> tableData = matches.stream()
                .map(m -> new MatchDetailRow(m, teamNumber, penaltyMap))
                .collect(Collectors.toList());
        matchTable.setItems(FXCollections.observableArrayList(tableData));

        // 3. 开启后台线程，执行密集型计算（坐标解析、循环周期、高斯模糊矩阵计算）
        new Thread(() -> calculateAndDraw(teamNumber, matches)).start();
    }

    // ==================== 核心逻辑处理区 ====================

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

        int nearRoleHits = 0, nearRoleMatches = 0;
        int farRoleHits = 0, farRoleMatches = 0;

        int globalFarShots = 0, globalFarHits = 0;
        int globalNearShots = 0, globalNearHits = 0;

        // 进货周期 (Cycle) 计算变量
        int totalCycles = 0;
        int matchesWithData = 0;

        for (ScoreEntry m : matches) {
            // 如果在这场比赛中机器坏了，就不把这场的数据计入它的热力图和平均值中，以免拉低它的真实水平
            if ((m.getTeam1() == teamNumber && m.isTeam1Broken()) ||
                    (m.getTeam2() == teamNumber && m.isTeam2Broken())) {
                continue;
            }

            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            int mHits = 0, mNearShots = 0, mFarShots = 0;
            List<Long> matchTimestamps = new ArrayList<>();

            // 解析点击坐标数据格式 "teamIdx:x,y,state,timestamp;"
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
                        long ts = (coords.length > 3) ? Long.parseLong(coords[3]) : 0;
                        boolean isHit = (state == 0); // state == 0 代表命中 (没有打叉)

                        if (ts > 0) matchTimestamps.add(ts);

                        if (isHit) {
                            hitPoints.add(new Point(x, y));
                            mHits++;
                        }

                        // Y 轴分割判断场地远近区域 (Zone)
                        if (y < ZONE_DIVIDER_Y) {
                            mNearShots++;
                            if (isHit) globalNearHits++;
                        } else {
                            mFarShots++;
                            if (isHit) globalFarHits++;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // --- 进货周期 (Cycle Calculation) 算法 ---
            // 如果两次投篮/放置间隔超过 4000 毫秒(4秒)，我们就认为它回去重新进货了，算作一个新的 Cycle
            if (!matchTimestamps.isEmpty()) {
                Collections.sort(matchTimestamps);
                int cycles = 1; // 至少算作 1 个 Cycle (预装载或第一次捡球)
                for (int i = 1; i < matchTimestamps.size(); i++) {
                    long diff = matchTimestamps.get(i) - matchTimestamps.get(i - 1);
                    if (diff > 4000) {
                        cycles++;
                    }
                }
                totalCycles += cycles;
                matchesWithData++;
            }

            globalNearShots += mNearShots;
            globalFarShots += mFarShots;

            // 分析这局它的战术定位
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

        // 计算最终聚合数据
        double finalNearAvg = nearRoleMatches == 0 ? 0 : (double) nearRoleHits / nearRoleMatches;
        double finalFarAvg = farRoleMatches == 0 ? 0 : (double) farRoleHits / farRoleMatches;
        double avgCycles = matchesWithData == 0 ? 0 : (double) totalCycles / matchesWithData;

        int fFarShots = globalFarShots, fFarHits = globalFarHits;
        int fNearShots = globalNearShots, fNearHits = globalNearHits;

        // 计算完毕，切回 JavaFX 主线程更新 UI
        Platform.runLater(() -> {
            drawProbabilityCloud(hitPoints);
            updateAnalysis(fFarShots, fFarHits, fNearShots, fNearHits, finalNearAvg, finalFarAvg, avgCycles);
        });
    }

    // ==================== UI 渲染区 ====================

    private void updateAnalysis(int farShots, int farHits, int nearShots, int nearHits,
                                double nearAvg, double farAvg, double avgCycles) {
        int totalShots = farShots + nearShots;
        int totalHits = farHits + nearHits;

        // 更新侧边栏数据
        farShotsLabel.setText(String.format("%.1f Hits/M", farAvg));
        nearShotsLabel.setText(String.format("%.1f Hits/M", nearAvg));
        farAccLabel.setText(String.format("%.1f%%", (farShots > 0 ? (double)farHits/farShots*100 : 0)));
        nearAccLabel.setText(String.format("%.1f%%", (nearShots > 0 ? (double)nearHits/nearShots*100 : 0)));

        if (cyclesLabel != null) {
            cyclesLabel.setText(String.format("%.1f", avgCycles));
        }

        if (totalShots == 0) {
            playStyleLabel.setText("No Data");
            efficiencyLabel.setText("0.0 PPS");
            return;
        }

        // 分析打法风格
        double farRatio = (double) farShots / totalShots;
        if (farRatio > 0.65) playStyleLabel.setText("Far Zone Specialist");
        else if (farRatio < 0.35) playStyleLabel.setText("Near Zone Rusher");
        else playStyleLabel.setText("Hybrid");

        // 计分效率计算 (Points Per Shot)
        double pps = (double) (totalHits * POINTS_PER_HIT) / totalShots;
        efficiencyLabel.setText(String.format("%.2f PPS", pps));
    }

    private void drawProbabilityCloud(List<Point> points) {
        int w = (int) heatmapCanvas.getWidth();
        int h = (int) heatmapCanvas.getHeight();
        GraphicsContext gc = heatmapCanvas.getGraphicsContext2D();

        // 清空画布并画出分割线
        gc.clearRect(0, 0, w, h);
        gc.setStroke(Color.web("#FFFFFF", 0.3));
        gc.strokeLine(0, ZONE_DIVIDER_Y, w, ZONE_DIVIDER_Y);

        if (points.isEmpty()) return;

        // 1. 初始化密度矩阵
        double[][] densityMap = new double[w][h];
        double maxDensity = 0;
        double sigmaSq2 = 2 * SIGMA * SIGMA;

        // 2. 利用高斯核累加概率密度
        for (Point p : points) {
            int cx = (int) p.x;
            int cy = (int) p.y;
            for (int x = Math.max(0, cx - KERNEL_RADIUS); x < Math.min(w, cx + KERNEL_RADIUS); x++) {
                for (int y = Math.max(0, cy - KERNEL_RADIUS); y < Math.min(h, cy + KERNEL_RADIUS); y++) {
                    double distSq = Math.pow(x - cx, 2) + Math.pow(y - cy, 2);
                    densityMap[x][y] += Math.exp(-distSq / sigmaSq2);
                    if (densityMap[x][y] > maxDensity) maxDensity = densityMap[x][y];
                }
            }
        }

        // 3. 根据密度矩阵逐像素映射颜色
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

    /**
     * 将密度比例 (0.0 ~ 1.0) 映射为赛博朋克风格的光效颜色
     */
    private Color getSmoothGradientColor(double ratio) {
        // 映射规则：低密度偏蓝/暗，高密度偏红/高亮
        double opacity = Math.min(0.9, Math.pow(ratio, 0.45));
        double hue = 240 - ratio * 240; // 色相从蓝色(240)过渡到红色(0)
        return Color.hsb(hue, 1.0, 1.0, opacity);
    }

    // 内部结构体用于暂存坐标
    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }
}