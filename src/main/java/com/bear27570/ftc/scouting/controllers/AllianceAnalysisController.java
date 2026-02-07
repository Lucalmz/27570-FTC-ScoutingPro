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
import java.util.stream.Collectors;

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

    // 区域划分线 Y坐标
    private static final double ZONE_DIVIDER_Y = 400.0;

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
    }

    /**
     * 内部画像类：记录在特定位置主打时的场均命中数 (Hits/Match)
     */
    private static class TeamHeatmapProfile {
        int teamNum;
        double nearRoleAvgHits; // 当作为近端主打时的场均总命中 (Hits/Match)
        double farRoleAvgHits;  // 当作为远端主打时的场均总命中 (Hits/Match)
        String style;           // 整体风格标签
        double accuracy;        // 整体准确率
        double stability;       // 标准差
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

            mainTeamStatsLabel.setText(String.format("Main: %d | Near: %.1f Hits/M | Far: %.1f Hits/M | Style: %s",
                    mainTeamNum, mainProfile.nearRoleAvgHits, mainProfile.farRoleAvgHits, mainProfile.style));

            List<AnalysisResult> results = new ArrayList<>();

            // 2. 遍历其他队伍进行最优搭配计算
            for (TeamRanking tr : allRankings) {
                if (tr.getTeamNumber() == mainTeamNum) continue;

                TeamHeatmapProfile partnerProfile = getTeamHeatmapProfile(competition.getName(), tr.getTeamNumber(), allRankings);
                if (partnerProfile == null) continue;

                // --- 核心逻辑：使用"在该位置时的场均命中"进行搭配 ---

                // 方案 1: 主队负责远端 + 队友负责近端
                double scorePlanA = mainProfile.farRoleAvgHits + partnerProfile.nearRoleAvgHits;

                // 方案 2: 主队负责近端 + 队友负责远端
                double scorePlanB = mainProfile.nearRoleAvgHits + partnerProfile.farRoleAvgHits;

                double bestCombinedHits;
                String synergyNote;

                if (scorePlanA >= scorePlanB) {
                    bestCombinedHits = scorePlanA;
                    synergyNote = "Main-Far / Partner-Near";
                } else {
                    bestCombinedHits = scorePlanB;
                    synergyNote = "Main-Near / Partner-Far";
                }

                // 风格冲突提示：如果两队整体风格完全一致且不是混合型
                if (mainProfile.style.equals(partnerProfile.style) && !mainProfile.style.equals("Hybrid")) {
                    synergyNote += " (Style Conflict!)";
                }

                // 数据不足提示 (例如某队从未打过远端)
                if (bestCombinedHits == 0) {
                    synergyNote = "Insufficient Data";
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
     * 计算逻辑：
     * 1. 遍历每一场比赛。
     * 2. 计算单场远射比例。
     * 3. 根据比例判断该场角色 (Near/Far)。
     * 4. 将该场比赛的 **总命中数** 计入对应角色的总和。
     * 5. 最终除以该角色出现的场次，得出该位置的 Hits/Match。
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

        // 统计变量
        double sumHitsWhenNear = 0;
        int countNearGames = 0;

        double sumHitsWhenFar = 0;
        int countFarGames = 0;

        // 用于计算整体风格的全局累计
        int globalNearShots = 0;
        int globalFarShots = 0;

        for (ScoreEntry m : validMatches) {
            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;

            // 单场统计
            int matchNearHits = 0, matchFarHits = 0;
            int matchNearShots = 0, matchFarShots = 0;

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
                            matchNearShots++;
                            if (isHit) matchNearHits++;
                        } else {
                            matchFarShots++;
                            if (isHit) matchFarHits++;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 更新全局统计
            globalNearShots += matchNearShots;
            globalFarShots += matchFarShots;

            // --- 判断本场角色并记录总命中 ---
            int matchTotalShots = matchNearShots + matchFarShots;
            int matchTotalHits = matchNearHits + matchFarHits;

            if (matchTotalShots > 0) {
                double farRatio = (double) matchFarShots / matchTotalShots;

                if (farRatio > 0.65) {
                    // 本场判定为：主远 (Far Role)
                    sumHitsWhenFar += matchTotalHits; // 记录当场总命中
                    countFarGames++;
                } else if (farRatio < 0.35) {
                    // 本场判定为：主近 (Near Role)
                    sumHitsWhenNear += matchTotalHits; // 记录当场总命中
                    countNearGames++;
                }
                // Hybrid 场次不计入极限强度计算
            }
        }

        TeamHeatmapProfile profile = new TeamHeatmapProfile();
        profile.teamNum = teamNum;

        // 计算 Hits/Match (根据角色采样)
        profile.nearRoleAvgHits = (countNearGames > 0) ? (sumHitsWhenNear / countNearGames) : 0.0;
        profile.farRoleAvgHits = (countFarGames > 0) ? (sumHitsWhenFar / countFarGames) : 0.0;

        // 整体风格标签
        int totalGlobalShots = globalNearShots + globalFarShots;
        if (totalGlobalShots == 0) profile.style = "Unknown";
        else {
            double globalFarRatio = (double) globalFarShots / totalGlobalShots;
            if (globalFarRatio > 0.65) profile.style = "Far";
            else if (globalFarRatio < 0.35) profile.style = "Near";
            else profile.style = "Hybrid";
        }

        // 获取准确率和稳定性
        TeamRanking tr = rankings.stream().filter(r -> r.getTeamNumber() == teamNum).findFirst().orElse(null);
        profile.accuracy = (tr != null) ? parseAcc(tr.getAccuracyFormatted()) : 0;

        // --- 修复冲突：直接在本地计算分数标准差，不调用不存在的 DatabaseService 方法 ---
        List<Double> scores = validMatches.stream()
                .map(m -> (m.getScoreType() == ScoreEntry.Type.ALLIANCE) ? m.getTotalScore() / 2.0 : (double) m.getTotalScore())
                .collect(Collectors.toList());
        profile.stability = calculateStdDev(scores);

        return profile;
    }

    private double parseAcc(String accStr) {
        if (accStr == null || accStr.equals("N/A")) return 0.0;
        try {
            return Double.parseDouble(accStr.replace("%", ""));
        } catch (Exception e) { return 0.0; }
    }

    // --- 新增：本地标准差计算方法 ---
    private double calculateStdDev(List<Double> data) {
        if (data == null || data.size() < 2) return 0.0;

        double sum = 0.0;
        for (double d : data) sum += d;
        double mean = sum / data.size();

        double temp = 0;
        for (double d : data) {
            temp += Math.pow(mean - d, 2);
        }
        return Math.sqrt(temp / (data.size() - 1));
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
        public double getTotalEfficiency() { return totalEfficiency; }
        public double getCombinedAccuracy() { return combinedAccuracy; }
        public double getStability() { return stability; }
        public String getStyleDesc() { return styleDesc; }
    }
}
