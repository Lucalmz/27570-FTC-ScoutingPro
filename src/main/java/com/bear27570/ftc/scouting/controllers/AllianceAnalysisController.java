// File: AllianceAnalysisController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;
import com.bear27570.ftc.scouting.services.domain.UserService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    @FXML private TableColumn<AnalysisResult, Double> combinedAccCol;
    @FXML private TableColumn<AnalysisResult, Double> stabilityCol;
    @FXML private TableColumn<AnalysisResult, String> styleCol;

    // AI 相关控件
    @FXML private PasswordField apiKeyField;
    @FXML private TextArea chatArea;
    @FXML private TextField chatInputField;

    private Competition competition;
    private Stage dialogStage;
    private RankingService rankingService;
    private MatchDataService matchDataService;
    private UserService userService;
    private String currentUsername;
    private static final double ZONE_DIVIDER_Y = 400.0;

    // --- 核心注入方法 ---
    public void setDependencies(Stage dialogStage, Competition competition, RankingService rankingService, MatchDataService matchDataService, UserService userService, String currentUsername) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        this.rankingService = rankingService;
        this.matchDataService = matchDataService;
        this.userService = userService;
        this.currentUsername = currentUsername;

        // 加载已绑定的 API Key
        String savedKey = userService.getApiKey(currentUsername);
        if (savedKey != null && !savedKey.isEmpty()) {
            apiKeyField.setText(savedKey);
            chatArea.appendText("System: Gemini API Key loaded from your profile.\n\n");
        } else {
            chatArea.appendText("System: Please link a Gemini API Key to use the AI Assistant.\n\n");
        }
    }

    // ========== API Chat 功能区 ==========

    @FXML
    private void handleSaveApiKey() {
        String key = apiKeyField.getText();
        if (key != null && !key.isEmpty()) {
            userService.updateApiKey(currentUsername, key);
            chatArea.appendText("System: API Key saved successfully to your account!\n\n");
        }
    }

    @FXML
    private void handleSendChat() {
        String prompt = chatInputField.getText();
        String apiKey = apiKeyField.getText();

        if (prompt == null || prompt.trim().isEmpty()) return;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            chatArea.appendText("System: Error - Please configure and save your Gemini API Key first.\n\n");
            return;
        }

        chatArea.appendText("You: " + prompt + "\n");
        chatInputField.clear();

        // 将左侧表格生成的数据构建成语境上下文传入 Prompt 中
        StringBuilder context = new StringBuilder("Data Context (Alliance Analysis for Main Team: " + mainTeamField.getText() + "):\n");
        if (analysisTable.getItems().isEmpty()) {
            context.append("No active analysis data currently. You can answer generic questions.\n");
        } else {
            for (AnalysisResult res : analysisTable.getItems()) {
                context.append(String.format("- Potential Partner %d: Projected Score %.1f, Combined Accuracy %.1f%%, Stability Deviation ±%.1f, Synergy Note: %s\n",
                        res.getPartnerTeam(), res.getTotalEfficiency(), res.getCombinedAccuracy(), res.getStability(), res.getStyleDesc()));
            }
        }

        chatArea.appendText("Gemini: Thinking...\n");

        new Thread(() -> {
            String response = callGeminiApi(apiKey, context.toString(), prompt);
            Platform.runLater(() -> {
                // 移除原有的 "Thinking..."
                String currentText = chatArea.getText();
                if (currentText.endsWith("Gemini: Thinking...\n")) {
                    chatArea.setText(currentText.substring(0, currentText.length() - "Gemini: Thinking...\n".length()));
                }
                chatArea.appendText("Gemini: " + response + "\n\n");
            });
        }).start();
    }

    // File: AllianceAnalysisController.java

    private String callGeminiApi(String apiKey, String context, String userPrompt) {
        try {
            // 1. 设置 HttpClient 的连接超时时间 (10秒)
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            // 2. 清除 API Key 前后可能带入的空格或换行符
            String cleanApiKey = apiKey.trim();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + cleanApiKey;

            String safeContext = escapeJson(context);
            String safePrompt = escapeJson(userPrompt);

            // 构造严格 JSON
            String payload = String.format("{\"contents\": [{\"parts\":[{\"text\": \"System: You are an FTC Robotics Scouting AI Assistant. Help the user analyze their team data. Base your insights strictly on the context below if applicable.\\n\\n%s\\n\\nUser: %s\"}]}]}", safeContext, safePrompt);

            // 3. 设置单次请求的响应超时时间 (30秒)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 4. 重点强化反馈：如果 API 拒绝请求，把谷歌原生的报错正文返回给 UI 界面！
            if (response.statusCode() != 200) {
                return "API Error (" + response.statusCode() + "). Details: \n" + response.body();
            }

            return parseGeminiResponseText(response.body());

        } catch (java.net.ConnectException e) {
            e.printStackTrace();
            return "Connection Failed: Unable to connect to Google API. Are you behind a strict firewall or do you need a proxy? \nError: " + e.toString();
        } catch (java.net.http.HttpTimeoutException e) {
            e.printStackTrace();
            return "Request Timed Out: The server took too long to respond. Please check your network.";
        } catch (Exception e) {
            e.printStackTrace(); // 将详细堆栈打印到开发环境的控制台
            // 5. 使用 e.toString() 代替 e.getMessage()，以防 NullPointerException 没有 Message
            return "Local Request failed: " + e.toString();
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String parseGeminiResponseText(String jsonBody) {
        int partsIdx = jsonBody.indexOf("\"parts\"");
        if (partsIdx != -1) {
            int textIdx = jsonBody.indexOf("\"text\"", partsIdx);
            if (textIdx != -1) {
                int startQuote = jsonBody.indexOf("\"", textIdx + 6);
                startQuote = jsonBody.indexOf("\"", startQuote + 1);
                if (startQuote != -1) {
                    StringBuilder sb = new StringBuilder();
                    boolean escaped = false;
                    for (int i = startQuote + 1; i < jsonBody.length(); i++) {
                        char c = jsonBody.charAt(i);
                        if (escaped) {
                            if (c == 'n') sb.append('\n');
                            else if (c == 'r') sb.append('\r');
                            else if (c == 't') sb.append('\t');
                            else sb.append(c);
                            escaped = false;
                        } else {
                            if (c == '\\') escaped = true;
                            else if (c == '"') break;
                            else sb.append(c);
                        }
                    }
                    return sb.toString();
                }
            }
        }
        return "Failed to parse API response structure.";
    }

    // ========== 原有分析逻辑保留 ==========

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
            List<TeamRanking> allRankings = rankingService.calculateRankings(competition.getName());

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
        List<ScoreEntry> matches = matchDataService.getTeamHistory(compName, teamNum);
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
        partnerCol.setCellValueFactory(new PropertyValueFactory<>("partnerTeam"));
        partnerCol.setStyle("-fx-alignment: CENTER;");
        totalEffCol.setCellValueFactory(new PropertyValueFactory<>("totalEfficiency"));
        totalEffCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.1f", item));
            }
        });
        totalEffCol.setStyle("-fx-alignment: CENTER;");
        combinedAccCol.setCellValueFactory(new PropertyValueFactory<>("combinedAccuracy"));
        combinedAccCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.1f%%", item));
            }
        });
        combinedAccCol.setStyle("-fx-alignment: CENTER;");
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

    public static class AnalysisResult {
        private final int partnerTeam;
        private final double totalEfficiency;
        private final double combinedAccuracy;
        private final double stability;
        private final String styleDesc;
        public AnalysisResult(int partnerTeam, double totalEfficiency, double combinedAccuracy, double stability, String styleDesc) {
            this.partnerTeam = partnerTeam; this.totalEfficiency = totalEfficiency; this.combinedAccuracy = combinedAccuracy;
            this.stability = stability; this.styleDesc = styleDesc;
        }
        public int getPartnerTeam() { return partnerTeam; }
        public double getTotalEfficiency() { return totalEfficiency; }
        public double getCombinedAccuracy() { return combinedAccuracy; }
        public double getStability() { return stability; }
        public String getStyleDesc() { return styleDesc; }
    }
}