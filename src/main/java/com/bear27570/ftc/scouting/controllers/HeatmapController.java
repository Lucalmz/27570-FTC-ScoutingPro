package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.ScoreEntry;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.*;
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
    @FXML private Label statusTextLabel;
    @FXML private Label brokenTextLabel;
    @FXML private Label cyclesLabel;

    @FXML private VBox routinesBox;

    @FXML private TableView<MatchDetailRow> matchTable;
    @FXML private TableColumn<MatchDetailRow, Integer> colMatchNum;
    @FXML private TableColumn<MatchDetailRow, String> colAlliance;
    @FXML private TableColumn<MatchDetailRow, Integer> colAuto;
    @FXML private TableColumn<MatchDetailRow, Integer> colTeleop;
    @FXML private TableColumn<MatchDetailRow, String> colPenalties;
    @FXML private TableColumn<MatchDetailRow, Integer> colScore;
    @FXML private TableColumn<MatchDetailRow, String> colStatus;
    private static final Logger log = LoggerFactory.getLogger(HeatmapController.class);
    private int currentTeamNumber;
    private static final double ZONE_DIVIDER_Y = 400.0;
    private static final int POINTS_PER_HIT = 3;
    private static final double SIGMA = 35.0;
    private static final int KERNEL_RADIUS = (int) (SIGMA * 3);

    public static class MatchDetailRow {
        private final ScoreEntry entry;
        private final String penaltiesInfo;
        private final String statusInfo;

        public MatchDetailRow(ScoreEntry entry, int currentTeam, Map<Integer, PenaltyRepository.FullPenaltyRow> penMap) {
            this.entry = entry;

            boolean isTeam1 = (entry.getTeam1() == currentTeam);
            boolean isBroken = isTeam1 ? entry.isTeam1Broken() : entry.isTeam2Broken();
            boolean isIgnored = isTeam1 ? entry.isTeam1Ignored() : entry.isTeam2Ignored();

            if (isBroken) this.statusInfo = "⚠ Broken";
            else if (isIgnored) this.statusInfo = "⚠ Weak/Ignored";
            else this.statusInfo = "✔ Active";

            PenaltyRepository.FullPenaltyRow pe = penMap != null ? penMap.get(entry.getMatchNumber()) : null;
            if (pe != null) {
                int redGaveAway = (pe.rMaj * 15) + (pe.rMin * 5);
                int blueGaveAway = (pe.bMaj * 15) + (pe.bMin * 5);
                boolean isRed = entry.getAlliance().equalsIgnoreCase("RED");
                int comm = isRed ? redGaveAway : blueGaveAway;
                int oppGained = isRed ? blueGaveAway : redGaveAway;

                if (entry.getScoreType() == ScoreEntry.Type.ALLIANCE) {
                    comm /= 2; oppGained /= 2;
                }
                this.penaltiesInfo = String.format("-%d / +%d", comm, oppGained);
            } else {
                this.penaltiesInfo = "N/A";
            }
        }

        public int getMatchNumber() { return entry.getMatchNumber(); }
        public String getAlliance() { return entry.getAlliance(); }
        public int getAuto() { return entry.getAutoArtifacts(); }
        public int getTeleop() { return entry.getTeleopArtifacts(); }
        public int getScore() { return entry.getTotalScore(); }
        public String getPenalties() { return penaltiesInfo; }
        public String getStatus() { return statusInfo; }
    }

    @FXML
    public void initialize() {
        colMatchNum.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getMatchNumber()).asObject());
        colAlliance.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAlliance()));
        colAuto.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getAuto()).asObject());
        colTeleop.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getTeleop()).asObject());
        colScore.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getScore()).asObject());
        colPenalties.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPenalties()));
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
    }

    public void setData(int teamNumber, List<ScoreEntry> matches, Map<Integer, PenaltyRepository.FullPenaltyRow> penaltyMap) {
        this.currentTeamNumber = teamNumber;
        teamTitleLabel.setText("Team " + teamNumber + " Probability Cloud");

        updateStatusLabels(teamNumber, matches);

        List<MatchDetailRow> tableData = matches.stream()
                .map(m -> new MatchDetailRow(m, teamNumber, penaltyMap))
                .collect(Collectors.toList());
        matchTable.setItems(FXCollections.observableArrayList(tableData));

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

    private static class RoutineStats {
        String combination;
        int count = 0;
        int totalScore = 0;
        RoutineStats(String combination) { this.combination = combination; }
    }

    private void calculateAndDraw(int teamNumber, List<ScoreEntry> matches) {
        List<Point> hitPoints = new ArrayList<>();
        int nearRoleHits = 0, nearRoleMatches = 0, farRoleHits = 0, farRoleMatches = 0;
        int globalFarShots = 0, globalFarHits = 0, globalNearShots = 0, globalNearHits = 0;
        int totalCycles = 0, matchesWithData = 0;

        Map<String, RoutineStats> routinesMap = new HashMap<>();
        int validAutoMatches = 0;

        for (ScoreEntry m : matches) {
            if ((m.getTeam1() == teamNumber && m.isTeam1Broken()) ||
                    (m.getTeam2() == teamNumber && m.isTeam2Broken())) {
                continue;
            }

            String rowData = (m.getTeam1() == teamNumber) ? m.getTeam1AutoRow() : m.getTeam2AutoRow();
            int autoScore = (m.getTeam1() == teamNumber) ? m.getTeam1AutoScore() : m.getTeam2AutoScore();

            if (rowData != null && !rowData.equals("NONE") && !rowData.trim().isEmpty()) {
                routinesMap.putIfAbsent(rowData, new RoutineStats(rowData));
                RoutineStats stat = routinesMap.get(rowData);
                stat.count++;
                stat.totalScore += autoScore;
                validAutoMatches++;
            }

            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            int mHits = 0, mNearShots = 0, mFarShots = 0;
            List<Long> matchTimestamps = new ArrayList<>();

            // 🌟 修复：严谨拦截热力图矩阵的崩溃源
            for (String p : locs.split(";")) {
                if (p == null || p.trim().isEmpty()) continue;

                try {
                    String[] parts = p.split(":");
                    if (parts.length < 2) {
                        log.error("⚠️ [Heatmap] Match " + m.getMatchNumber() + " 缺少队标映射，已跳过: " + p);
                        continue;
                    }

                    int pTeamIdx = Integer.parseInt(parts[0]);
                    int actualTeamNum = (pTeamIdx == 1) ? m.getTeam1() : m.getTeam2();

                    if (actualTeamNum == teamNumber) {
                        String[] coords = parts[1].split(",");
                        if (coords.length < 2) {
                            log.error("⚠️ [Heatmap] Match " + m.getMatchNumber() + " 缺少 X/Y 轴数据，已跳过: " + p);
                            continue;
                        }

                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);
                        int state = (coords.length >= 3) ? Integer.parseInt(coords[2]) : 0;
                        long ts = (coords.length > 3) ? Long.parseLong(coords[3]) : 0;
                        boolean isHit = (state == 0);

                        if (ts > 0) matchTimestamps.add(ts);

                        if (isHit) { hitPoints.add(new Point(x, y)); mHits++; }
                        if (y < ZONE_DIVIDER_Y) { mNearShots++; if (isHit) globalNearHits++; }
                        else { mFarShots++; if (isHit) globalFarHits++; }
                    }
                } catch (NumberFormatException e) {
                    log.error("❌ [Heatmap] 坐标数据非数字 (Match " + m.getMatchNumber() + "): '" + p + "' | 详情: " + e.getMessage());
                } catch (Exception e) {
                    log.error("❌ [Heatmap] 解析坐标流发生异常 (Match " + m.getMatchNumber() + "): '" + p + "' | 详情: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            if (!matchTimestamps.isEmpty()) {
                Collections.sort(matchTimestamps);
                int cycles = 1;
                for (int i = 1; i < matchTimestamps.size(); i++) {
                    if (matchTimestamps.get(i) - matchTimestamps.get(i - 1) > 4000) cycles++;
                }
                totalCycles += cycles;
                matchesWithData++;
            }

            globalNearShots += mNearShots; globalFarShots += mFarShots;
            int mTotalShots = mNearShots + mFarShots;
            if (mTotalShots > 0) {
                double ratio = (double) mFarShots / mTotalShots;
                if (ratio > 0.65) { farRoleHits += mHits; farRoleMatches++; }
                else if (ratio < 0.35) { nearRoleHits += mHits; nearRoleMatches++; }
            }
        }

        double finalNearAvg = nearRoleMatches == 0 ? 0 : (double) nearRoleHits / nearRoleMatches;
        double finalFarAvg = farRoleMatches == 0 ? 0 : (double) farRoleHits / farRoleMatches;
        double avgCycles = matchesWithData == 0 ? 0 : (double) totalCycles / matchesWithData;

        int fFarShots = globalFarShots, fFarHits = globalFarHits, fNearShots = globalNearShots, fNearHits = globalNearHits;

        List<RoutineStats> sortedRoutines = new ArrayList<>(routinesMap.values());
        sortedRoutines.sort((a, b) -> Integer.compare(b.count, a.count));
        final int finalValidAutoMatches = validAutoMatches;

        Platform.runLater(() -> {
            drawProbabilityCloud(hitPoints);
            updateRoutinesListUI(sortedRoutines, finalValidAutoMatches);
            updateAnalysis(fFarShots, fFarHits, fNearShots, fNearHits, finalNearAvg, finalFarAvg, avgCycles);
        });
    }

    private void updateAnalysis(int farShots, int farHits, int nearShots, int nearHits, double nearAvg, double farAvg, double avgCycles) {
        int totalShots = farShots + nearShots;
        int totalHits = farHits + nearHits;

        farShotsLabel.setText(String.format("%.1f Hits/M", farAvg));
        nearShotsLabel.setText(String.format("%.1f Hits/M", nearAvg));
        farAccLabel.setText(String.format("%.1f%%", (farShots > 0 ? (double)farHits/farShots*100 : 0)));
        nearAccLabel.setText(String.format("%.1f%%", (nearShots > 0 ? (double)nearHits/nearShots*100 : 0)));

        if (cyclesLabel != null) cyclesLabel.setText(String.format("%.1f", avgCycles));

        if (totalShots == 0) {
            playStyleLabel.setText("No Data"); efficiencyLabel.setText("0.0 PPS");
            return;
        }

        double farRatio = (double) farShots / totalShots;
        if (farRatio > 0.65) playStyleLabel.setText("Far Zone Specialist");
        else if (farRatio < 0.35) playStyleLabel.setText("Near Zone Rusher");
        else playStyleLabel.setText("Hybrid");

        double pps = (double) (totalHits * POINTS_PER_HIT) / totalShots;
        efficiencyLabel.setText(String.format("%.2f PPS", pps));
    }

    private void updateRoutinesListUI(List<RoutineStats> sortedRoutines, int totalMatches) {
        routinesBox.getChildren().clear();

        if (sortedRoutines.isEmpty() || totalMatches == 0) {
            Label noData = new Label("No routine data recorded.");
            noData.getStyleClass().add("text-muted");
            routinesBox.getChildren().add(noData);
            return;
        }

        String[] colors = { "#FDE047", "#00BCD4", "#E91E63", "#34D399" };

        for (int i = 0; i < Math.min(sortedRoutines.size(), 4); i++) {
            RoutineStats stat = sortedRoutines.get(i);
            double ratio = (double) stat.count / totalMatches;
            double avgScore = (double) stat.totalScore / stat.count;
            String colorHex = colors[i % colors.length];

            VBox rowBox = new VBox(6);

            HBox header = new HBox();
            header.setAlignment(Pos.BOTTOM_LEFT);

            Label nameLbl = new Label("[" + stat.combination.replace(" ", "+") + "]");
            nameLbl.setStyle("-fx-font-family: 'Teko'; -fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label statsLbl = new Label(String.format("%.0f%% | Avg: %.1f", ratio * 100, avgScore));
            statsLbl.setStyle("-fx-font-family: 'Teko'; -fx-font-size: 16px; -fx-text-fill: #E4E4E7;");

            header.getChildren().addAll(nameLbl, spacer, statsLbl);

            Rectangle bgBar = new Rectangle(0, 6, Color.web(colorHex, 0.2));
            bgBar.setWidth(260);
            bgBar.setArcWidth(6); bgBar.setArcHeight(6);

            Rectangle fillBar = new Rectangle(0, 6, Color.web(colorHex));
            fillBar.setWidth(Math.max(260 * ratio, 5));
            fillBar.setArcWidth(6); fillBar.setArcHeight(6);
            fillBar.setEffect(new DropShadow(5, Color.web(colorHex)));

            StackPane barPane = new StackPane(bgBar, fillBar);
            barPane.setAlignment(Pos.CENTER_LEFT);

            rowBox.getChildren().addAll(header, barPane);
            routinesBox.getChildren().add(rowBox);
        }
    }

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
        double hue = 240 - ratio * 240;
        return Color.hsb(hue, 1.0, 1.0, opacity);
    }

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }
}