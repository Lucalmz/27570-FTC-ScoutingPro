package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.services.DatabaseService;
import javafx.beans.property.SimpleObjectProperty;
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

    // 修改：将列类型明确为数值类型，确保 TableView 自动执行数值排序
    @FXML private TableColumn<AnalysisResult, Integer> partnerCol;
    @FXML private TableColumn<AnalysisResult, Double> totalEffCol;
    @FXML private TableColumn<AnalysisResult, Double> combinedAccCol; // 改为 Double
    @FXML private TableColumn<AnalysisResult, Double> stabilityCol;   // 改为 Double
    @FXML private TableColumn<AnalysisResult, String> styleCol;

    private Competition competition;
    private Stage dialogStage;
    private static final double ZONE_DIVIDER_Y = 400.0;

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
    }

    private static class TeamHeatmapProfile {
        int teamNum;
        double nearRoleAvgHits;
        double farRoleAvgHits;
        String style;
        double accuracy;
        double stability;
    }

    @FXML
    private void handleAnalyze() {
        String input = mainTeamField.getText();
        if (input.isEmpty()) return;

        try {
            int mainTeamNum = Integer.parseInt(input);
            List<TeamRanking> allRankings = DatabaseService.calculateTeamRankings(competition.getName());

            TeamHeatmapProfile mainProfile = getTeamHeatmapProfile(competition.getName(), mainTeamNum, allRankings);
            if (mainProfile == null) {
                mainTeamStatsLabel.setText("Team " + mainTeamNum + " has no match data.");
                analysisTable.getItems().clear();
                return;
            }

            mainTeamStatsLabel.setText(String.format("Main: %d | Near: %.1f Hits/M | Far: %.1f Hits/M | Style: %s",
                    mainTeamNum, mainProfile.nearRoleAvgHits, mainProfile.farRoleAvgHits, mainProfile.style));

            List<AnalysisResult> results = new ArrayList<>();
            for (TeamRanking tr : allRankings) {
                if (tr.getTeamNumber() == mainTeamNum) continue;

                TeamHeatmapProfile partnerProfile = getTeamHeatmapProfile(competition.getName(), tr.getTeamNumber(), allRankings);
                if (partnerProfile == null) continue;

                double scorePlanA = mainProfile.farRoleAvgHits + partnerProfile.nearRoleAvgHits;
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

                if (mainProfile.style.equals(partnerProfile.style) && !mainProfile.style.equals("Hybrid")) {
                    synergyNote += " (Style Conflict!)";
                }
                if (bestCombinedHits == 0) synergyNote = "Insufficient Data";

                double combinedAcc = (mainProfile.accuracy + partnerProfile.accuracy) / 2.0;
                double combinedStability = Math.sqrt(Math.pow(mainProfile.stability, 2) + Math.pow(partnerProfile.stability, 2));

                results.add(new AnalysisResult(partnerProfile.teamNum, bestCombinedHits, combinedAcc, combinedStability, synergyNote));
            }

            results.sort(Comparator.comparingDouble(AnalysisResult::getTotalEfficiency).reversed());
            analysisTable.setItems(FXCollections.observableArrayList(results));

        } catch (NumberFormatException e) {
            mainTeamStatsLabel.setText("Invalid Team Number");
        }
    }

    private TeamHeatmapProfile getTeamHeatmapProfile(String compName, int teamNum, List<TeamRanking> rankings) {
        List<ScoreEntry> matches = DatabaseService.getScoresForTeam(compName, teamNum);
        List<ScoreEntry> validMatches = matches.stream().filter(m -> {
            boolean isT1 = (m.getTeam1() == teamNum && !m.isTeam1Broken());
            boolean isT2 = (m.getTeam2() == teamNum && !m.isTeam2Broken());
            return isT1 || isT2;
        }).collect(Collectors.toList());

        if (validMatches.isEmpty()) return null;

        double sumHitsWhenNear = 0, sumHitsWhenFar = 0;
        int countNearGames = 0, countFarGames = 0, globalNearShots = 0, globalFarShots = 0;

        for (ScoreEntry m : validMatches) {
            String locs = m.getClickLocations();
            if (locs == null || locs.isEmpty()) continue;
            int mNearHits = 0, mFarHits = 0, mNearShots = 0, mFarShots = 0;
            for (String p : locs.split(";")) {
                try {
                    String[] parts = p.split(":");
                    int pTeamIdx = Integer.parseInt(parts[0]);
                    int actualTeamNum = (pTeamIdx == 1) ? m.getTeam1() : m.getTeam2();
                    if (actualTeamNum == teamNum) {
                        String[] coords = parts[1].split(",");
                        double y = Double.parseDouble(coords[1]);
                        boolean isHit = (Integer.parseInt(coords[2]) == 0);
                        if (y < ZONE_DIVIDER_Y) { mNearShots++; if (isHit) mNearHits++; }
                        else { mFarShots++; if (isHit) mFarHits++; }
                    }
                } catch (Exception ignored) {}
            }
            globalNearShots += mNearShots; globalFarShots += mFarShots;
            int totalShots = mNearShots + mFarShots;
            if (totalShots > 0) {
                double farRatio = (double) mFarShots / totalShots;
                if (farRatio > 0.65) { sumHitsWhenFar += (mNearHits + mFarHits); countFarGames++; }
                else if (farRatio < 0.35) { sumHitsWhenNear += (mNearHits + mFarHits); countNearGames++; }
            }
        }

        TeamHeatmapProfile profile = new TeamHeatmapProfile();
        profile.teamNum = teamNum;
        profile.nearRoleAvgHits = (countNearGames > 0) ? (sumHitsWhenNear / countNearGames) : 0.0;
        profile.farRoleAvgHits = (countFarGames > 0) ? (sumHitsWhenFar / countFarGames) : 0.0;

        int totalGlobalShots = globalNearShots + globalFarShots;
        if (totalGlobalShots == 0) profile.style = "Unknown";
        else {
            double farRatio = (double) globalFarShots / totalGlobalShots;
            if (farRatio > 0.65) profile.style = "Far";
            else if (farRatio < 0.35) profile.style = "Near";
            else profile.style = "Hybrid";
        }

        TeamRanking tr = rankings.stream().filter(r -> r.getTeamNumber() == teamNum).findFirst().orElse(null);
        profile.accuracy = (tr != null) ? parseAcc(tr.getAccuracyFormatted()) : 0;
        List<Double> scores = validMatches.stream().map(m -> (double)m.getTotalScore() / (m.getScoreType() == ScoreEntry.Type.ALLIANCE ? 2.0 : 1.0)).collect(Collectors.toList());
        profile.stability = calculateStdDev(scores);
        return profile;
    }

    private double parseAcc(String accStr) {
        if (accStr == null || accStr.equals("N/A")) return 0.0;
        try { return Double.parseDouble(accStr.replace("%", "")); } catch (Exception e) { return 0.0; }
    }

    private double calculateStdDev(List<Double> data) {
        if (data == null || data.size() < 2) return 0.0;
        double mean = data.stream().mapToDouble(d -> d).average().orElse(0.0);
        return Math.sqrt(data.stream().mapToDouble(d -> Math.pow(d - mean, 2)).sum() / (data.size() - 1));
    }

    @FXML public void initialize() {
        // 1. Partner Team (Integer 排序正常)
        partnerCol.setCellValueFactory(new PropertyValueFactory<>("partnerTeam"));
        partnerCol.setStyle("-fx-alignment: CENTER;");

        // 2. Efficiency (Double 排序正常)
        totalEffCol.setCellValueFactory(new PropertyValueFactory<>("totalEfficiency"));
        totalEffCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.1f", item));
            }
        });
        totalEffCol.setStyle("-fx-alignment: CENTER;");

        // 3. Accuracy (改为使用数值进行排序，仅在显示时加 %)
        combinedAccCol.setCellValueFactory(new PropertyValueFactory<>("combinedAccuracy"));
        combinedAccCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.1f%%", item));
            }
        });
        combinedAccCol.setStyle("-fx-alignment: CENTER;");

        // 4. Stability (改为使用数值进行排序，仅在显示时加 ±)
        stabilityCol.setCellValueFactory(new PropertyValueFactory<>("stability"));
        stabilityCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("±%.1f", item));
            }
        });
        stabilityCol.setStyle("-fx-alignment: CENTER;");

        styleCol.setCellValueFactory(new PropertyValueFactory<>("styleDesc"));
        styleCol.setStyle("-fx-alignment: CENTER_LEFT;");
    }

    // 结果类中的字段现在保持原始数值类型
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
