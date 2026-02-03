package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.services.DatabaseService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AllianceAnalysisController {

    @FXML private TextField mainTeamField;
    @FXML private Label mainTeamStatsLabel;
    @FXML private TableView<AnalysisResult> analysisTable;
    @FXML private TableColumn<AnalysisResult, Integer> partnerCol;
    @FXML private TableColumn<AnalysisResult, Double> totalEffCol;
    @FXML private TableColumn<AnalysisResult, String> combinedAccCol;
    @FXML private TableColumn<AnalysisResult, String> stabilityCol;
    @FXML private TableColumn<AnalysisResult, String> styleCol;

    private Competition competition;
    private Stage dialogStage;

    // 参考 HeatmapController 的常量
    private static final double ZONE_DIVIDER_Y = 400.0;

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
    }

    /**
     * 内部画像类，完全同步 HeatmapController 的统计口径
     */
    private static class TeamHeatmapProfile {
        int teamNum;
        double avgNearHits; // 近点场均命中
        double avgFarHits;  // 远点场均命中
        String style;       // 风格标签
        double accuracy;    // 整体准确率
        double stability;   // 标准差
    }

    @FXML
    private void handleAnalyze() {
        String input = mainTeamField.getText();
        if (input.isEmpty()) return;

        try {
            int mainTeamNum = Integer.parseInt(input);
            List<TeamRanking> allRankings = DatabaseService.calculateTeamRankings(competition.getName());

            // 1. 计算主队画像
            TeamHeatmapProfile mainProfile = getTeamHeatmapProfile(competition.getName(), mainTeamNum, allRankings);
            if (mainProfile == null) {
                mainTeamStatsLabel.setText("Team " + mainTeamNum + " has no match data.");
                analysisTable.getItems().clear();
                return;
            }

            mainTeamStatsLabel.setText(String.format("Main: %d | Near Avg: %.1f | Far Avg: %.1f | Style: %s",
                    mainTeamNum, mainProfile.avgNearHits, mainProfile.avgFarHits, mainProfile.style));

            List<AnalysisResult> results = new ArrayList<>();

            // 2. 遍历其他队伍进行最优搭配计算
            for (TeamRanking tr : allRankings) {
                if (tr.getTeamNumber() == mainTeamNum) continue;

                TeamHeatmapProfile partnerProfile = getTeamHeatmapProfile(competition.getName(), tr.getTeamNumber(), allRankings);
                if (partnerProfile == null) continue;

                // --- 核心逻辑：强制取异位置效率最优解 ---
                // 方案 1: 主队打远点 + 队友打近点
                double scorePlanA = mainProfile.avgFarHits + partnerProfile.avgNearHits;
                // 方案 2: 主队打近点 + 队友打远点
                double scorePlanB = mainProfile.avgNearHits + partnerProfile.avgFarHits;

                double bestCombinedHits;
                String synergyNote;

                if (scorePlanA >= scorePlanB) {
                    bestCombinedHits = scorePlanA;
                    synergyNote = "Main-Far / Partner-Near";
                } else {
                    bestCombinedHits = scorePlanB;
                    synergyNote = "Main-Near / Partner-Far";
                }

                // 识别潜在冲突（如果两队在某个位置都是0或极低）
                if (mainProfile.style.equals(partnerProfile.style) && !mainProfile.style.equals("Hybrid")) {
                    synergyNote += " (Style Conflict!)";
                }

                double combinedAcc = (mainProfile.accuracy + partnerProfile.accuracy) / 2.0;
                double combinedStability = Math.sqrt(Math.pow(mainProfile.stability, 2) + Math.pow(partnerProfile.stability, 2));

                results.add(new AnalysisResult(partnerProfile.teamNum, bestCombinedHits, combinedAcc, combinedStability, synergyNote));
            }

            // 按综合场均命中数排序
            results.sort(Comparator.comparingDouble(AnalysisResult::getTotalEfficiency).reversed());
            analysisTable.setItems(FXCollections.observableArrayList(results));

        } catch (NumberFormatException e) {
            mainTeamStatsLabel.setText("Invalid Team Number");
        }
    }

    /**
     * 实现与 HeatmapController 相同的统计逻辑
     */
    private TeamHeatmapProfile getTeamHeatmapProfile(String compName, int teamNum, List<TeamRanking> rankings) {
        List<ScoreEntry> matches = DatabaseService.getScoresForTeam(compName, teamNum);

        // 过滤掉该队在该场次 Broken 的数据
        List<ScoreEntry> validMatches = matches.stream().filter(m -> {
            boolean isT1 = (m.getTeam1() == teamNum && !m.isTeam1Broken());
            boolean isT2 = (m.getTeam2() == teamNum && !m.isTeam2Broken());
            return isT1 || isT2;
        }).toList();

        if (validMatches.isEmpty()) return null;

        int nearHits = 0, farHits = 0;
        int nearShots = 0, farShots = 0;

        for (ScoreEntry m : validMatches) {
            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            for (String p : locs.split(";")) {
                try {
                    String[] parts = p.split(":");
                    int pTeamIdx = Integer.parseInt(parts[0]);
                    int actualTeamNum = (pTeamIdx == 1) ? m.getTeam1() : m.getTeam2();

                    if (actualTeamNum == teamNum) {
                        String[] coords = parts[1].split(",");
                        double y = Double.parseDouble(coords[1]);
                        int state = (coords.length >= 3) ? Integer.parseInt(coords[2]) : 0;
                        boolean isHit = (state == 0); // 0 为 Hit

                        if (y < ZONE_DIVIDER_Y) {
                            nearShots++; if (isHit) nearHits++;
                        } else {
                            farShots++; if (isHit) farHits++;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        TeamHeatmapProfile profile = new TeamHeatmapProfile();
        profile.teamNum = teamNum;
        int mCount = validMatches.size();
        profile.avgNearHits = (double) nearHits / mCount;
        profile.avgFarHits = (double) farHits / mCount;

        // 风格判断逻辑 (同步 HeatmapController)
        int totalShots = nearShots + farShots;
        if (totalShots == 0) profile.style = "Unknown";
        else {
            double farRatio = (double) farShots / totalShots;
            if (farRatio > 0.65) profile.style = "Far";
            else if (farRatio < 0.35) profile.style = "Near";
            else profile.style = "Hybrid";
        }

        // 获取基础排名中的准确率和稳定性
        TeamRanking tr = rankings.stream().filter(r -> r.getTeamNumber() == teamNum).findFirst().orElse(null);
        profile.accuracy = (tr != null) ? parseAcc(tr.getAccuracyFormatted()) : 0;
        List<Double> scores = DatabaseService.getValidMatchScores(compName, teamNum);
        profile.stability = DatabaseService.calculateStdDev(scores);

        return profile;
    }

    private double parseAcc(String accStr) {
        if (accStr == null || accStr.equals("N/A")) return 0.0;
        return Double.parseDouble(accStr.replace("%", ""));
    }

    @FXML public void initialize() {
        partnerCol.setCellValueFactory(new PropertyValueFactory<>("partnerTeam"));
        totalEffCol.setCellValueFactory(new PropertyValueFactory<>("totalEfficiency"));
        combinedAccCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.1f%%", cellData.getValue().getCombinedAccuracy())));
        stabilityCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("±%.1f", cellData.getValue().getStability())));
        styleCol.setCellValueFactory(new PropertyValueFactory<>("styleDesc"));
    }

    public static class AnalysisResult {
        private final int partnerTeam;
        private final double totalEfficiency;
        private final double combinedAccuracy;
        private final double stability;
        private final String styleDesc;

        public AnalysisResult(int partnerTeam, double totalEfficiency, double combinedAccuracy, double stability, String styleDesc) {
            this.partnerTeam = partnerTeam;
            this.totalEfficiency = totalEfficiency;
            this.combinedAccuracy = combinedAccuracy;
            this.stability = stability;
            this.styleDesc = styleDesc;
        }

        public int getPartnerTeam() { return partnerTeam; }
        public double getTotalEfficiency() { return totalEfficiency; } // 这里显示的是场均总命中
        public double getCombinedAccuracy() { return combinedAccuracy; }
        public double getStability() { return stability; }
        public String getStyleDesc() { return styleDesc; }
    }
}