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

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
    }

    @FXML
    private void handleAnalyze() {
        String input = mainTeamField.getText();
        if (input.isEmpty()) return;

        try {
            int mainTeamNum = Integer.parseInt(input);
            List<TeamRanking> allRankings = DatabaseService.calculateTeamRankings(competition.getName());

            TeamRanking mainTeam = allRankings.stream()
                    .filter(t -> t.getTeamNumber() == mainTeamNum)
                    .findFirst().orElse(null);

            if (mainTeam == null) {
                mainTeamStatsLabel.setText("Team " + mainTeamNum + " has no data.");
                analysisTable.getItems().clear();
                return;
            }

            List<Double> mainScores = DatabaseService.getValidMatchScores(competition.getName(), mainTeamNum);
            double mainStability = DatabaseService.calculateStdDev(mainScores);
            String mainStyle = getZoneStyle(competition.getName(), mainTeamNum);

            mainTeamStatsLabel.setText(String.format("Main Team: %d | Rtg: %.1f | Acc: %s | StdDev: %.2f | Style: %s",
                    mainTeamNum, mainTeam.getRating(), mainTeam.getAccuracyFormatted(), mainStability, mainStyle));

            List<AnalysisResult> results = new ArrayList<>();

            for (TeamRanking partner : allRankings) {
                if (partner.getTeamNumber() == mainTeamNum) continue;

                double combinedScore = mainTeam.getRating() + partner.getRating();

                double acc1 = parseAcc(mainTeam.getAccuracyFormatted());
                double acc2 = parseAcc(partner.getAccuracyFormatted());
                double combinedAcc = (acc1 + acc2) / 2.0;

                List<Double> partnerScores = DatabaseService.getValidMatchScores(competition.getName(), partner.getTeamNumber());
                double partnerStability = DatabaseService.calculateStdDev(partnerScores);
                double combinedStability = Math.sqrt(Math.pow(mainStability, 2) + Math.pow(partnerStability, 2));

                String partnerStyle = getZoneStyle(competition.getName(), partner.getTeamNumber());
                String synergyNote = analyzeSynergy(mainStyle, partnerStyle);

                results.add(new AnalysisResult(partner.getTeamNumber(), combinedScore, combinedAcc, combinedStability, synergyNote));
            }

            results.sort(Comparator.comparingDouble(AnalysisResult::getTotalEfficiency).reversed());

            analysisTable.setItems(FXCollections.observableArrayList(results));

        } catch (NumberFormatException e) {
            mainTeamStatsLabel.setText("Invalid Team Number");
        }
    }

    private String getZoneStyle(String compName, int teamNum) {
        List<ScoreEntry> matches = DatabaseService.getScoresForTeam(compName, teamNum);
        int far = 0, near = 0;
        for (ScoreEntry m : matches) {
            String locs = m.getClickLocations();
            if (locs == null) continue;
            for (String p : locs.split(";")) {
                try {
                    String[] parts = p.split(":");
                    if (parts.length < 2) continue;
                    int teamIdx = Integer.parseInt(parts[0]);
                    int actualTeam = (teamIdx == 1) ? m.getTeam1() : m.getTeam2();
                    if (actualTeam == teamNum) {
                        String[] coords = parts[1].split(",");
                        if (Double.parseDouble(coords[1]) < 400) near++; else far++;
                    }
                } catch (Exception ignored) {}
            }
        }
        int total = far + near;
        if (total == 0) return "Unknown";
        double ratio = (double) far / total;
        if (ratio > 0.65) return "Far";
        if (ratio < 0.35) return "Near";
        return "Hybrid";
    }

    private String analyzeSynergy(String s1, String s2) {
        if (s1.equals("Far") && s2.equals("Near")) return "Excellent (Zone Coverage)";
        if (s1.equals("Near") && s2.equals("Far")) return "Excellent (Zone Coverage)";
        if (s1.equals("Hybrid") || s2.equals("Hybrid")) return "Good (Flexible)";
        if (s1.equals(s2) && !s1.equals("Unknown")) return "Conflict Risk (Same Zone)";
        return "Standard";
    }

    private double parseAcc(String accStr) {
        if (accStr.equals("N/A")) return 0.0;
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
        public double getTotalEfficiency() { return totalEfficiency; }
        public double getCombinedAccuracy() { return combinedAccuracy; }
        public double getStability() { return stability; }
        public String getStyleDesc() { return styleDesc; }
    }
}