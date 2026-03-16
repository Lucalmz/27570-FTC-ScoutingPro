// File: src/main/java/com/bear27570/ftc/scouting/controllers/AllianceAnalysisController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;
import com.bear27570.ftc.scouting.services.domain.UserService;
import com.bear27570.ftc.scouting.services.network.GeminiApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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

    @FXML private PasswordField apiKeyField;
    @FXML private WebView chatWebView;
    @FXML private TextArea chatInputField;

    @FXML private ComboBox<String> modelComboBox;
    @FXML private CheckBox useProxyCheck;
    @FXML private TextField proxyHostField;
    @FXML private TextField proxyPortField;

    private WebEngine webEngine;
    private final Queue<String> jsQueue = new LinkedList<>();

    private Competition competition;
    private Stage dialogStage;
    private RankingService rankingService;
    private MatchDataService matchDataService;
    private UserService userService;
    private String currentUsername;
    private static final double ZONE_DIVIDER_Y = 400.0;

    private GeminiApiClient geminiApiClient;

    // ★ 聊天记录管理
    private static class ChatMessage {
        String role; // "SYS", "USER", "AI"
        String text;
        boolean isError;
        ChatMessage(String role, String text, boolean isError) {
            this.role = role; this.text = text; this.isError = isError;
        }
    }
    private List<ChatMessage> chatHistory = new ArrayList<>();

    public void setDependencies(Stage dialogStage, Competition competition, RankingService rankingService, MatchDataService matchDataService, UserService userService, String currentUsername) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        this.rankingService = rankingService;
        this.matchDataService = matchDataService;
        this.userService = userService;
        this.currentUsername = currentUsername;

        this.geminiApiClient = new GeminiApiClient();

        String savedKey = userService.getApiKey(currentUsername);
        if (savedKey != null && !savedKey.isEmpty()) {
            apiKeyField.setText(savedKey);
            addMessageToHistory("SYS", "✅ Gemini API Key loaded from your profile.", false);
        } else {
            addMessageToHistory("SYS", "⚠️ Please link a Gemini API Key to use the AI Assistant.", false);
        }
    }

    @FXML public void initialize() {
        webEngine = chatWebView.getEngine();

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                while (!jsQueue.isEmpty()) {
                    webEngine.executeScript(jsQueue.poll());
                }
            }
        });

        initializeChatHtml();

        chatInputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    // new line
                } else {
                    event.consume();
                    handleSendChat();
                }
            }
        });

        setupTable();

        modelComboBox.setItems(FXCollections.observableArrayList(
                "gemini-3-flash-preview",
                "gemini-3-pro-preview",
                "gemini-3.1-flash-lite-preview",
                "gemini-flash-lite-latest",
                "gemini-2.5-flash",
                "gemini-2.5-pro"
        ));
        modelComboBox.getSelectionModel().select("gemini-3.1-flash-lite-preview");

        proxyHostField.setText("127.0.0.1");
        proxyPortField.setText("7890");
        proxyHostField.disableProperty().bind(useProxyCheck.selectedProperty().not());
        proxyPortField.disableProperty().bind(useProxyCheck.selectedProperty().not());
    }

    private void safeExecuteScript(String script) {
        Platform.runLater(() -> {
            if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                webEngine.executeScript(script);
            } else {
                jsQueue.add(script);
            }
        });
    }

    private void setupTable() {
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

    private void initializeChatHtml() {
        String htmlTemplate = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<script src='https://cdn.jsdelivr.net/npm/marked/marked.min.js'></script>"
                + "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css'>"
                + "<script src='https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js'></script>"
                + "<style>"
                + "body { font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #131314; color: #e3e3e3; margin: 0; padding: 15px; font-size: 14px; line-height: 1.6; }"
                + ".msg-container { margin-bottom: 24px; display: flex; flex-direction: column; }"
                + ".user-msg { align-items: flex-end; }"
                + ".user-bubble { background-color: #3b3b3b; color: white; padding: 12px 18px; border-radius: 18px 18px 4px 18px; max-width: 80%; word-wrap: break-word; white-space: pre-wrap; font-size: 15px; }"
                + ".ai-msg { align-items: flex-start; max-width: 95%; }"
                + ".ai-header { font-weight: 600; color: #a8c7fa; margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }"
                + ".sys-msg { text-align: center; color: #8ab4f8; font-size: 13px; margin: 10px 0; background: #1e1e1e; padding: 8px; border-radius: 8px; }"
                + ".thinking { color: #9aa0a6; font-style: italic; animation: pulse 1.5s infinite; }"
                + "pre { background-color: #1e1e1e; padding: 12px; border-radius: 8px; overflow-x: auto; border: 1px solid #333; }"
                + "code { font-family: 'Consolas', monospace; font-size: 13px; }"
                + "table { border-collapse: collapse; width: 100%; margin: 10px 0; }"
                + "th, td { border: 1px solid #444; padding: 8px; text-align: left; }"
                + "th { background-color: #2b2b2b; }"
                + "a { color: #8ab4f8; text-decoration: none; }"
                + "@keyframes pulse { 0% { opacity: 0.6; } 50% { opacity: 1; } 100% { opacity: 0.6; } }"
                + "</style></head><body>"
                + "<div id='chat-box'></div>"
                + "<script>"
                + "function addSysMsg(text) { "
                + "  const div = document.createElement('div'); div.className='sys-msg'; div.innerText=text; document.getElementById('chat-box').appendChild(div); window.scrollTo(0, document.body.scrollHeight); "
                + "}"
                + "function addUserMsg(text) { "
                + "  const div = document.createElement('div'); div.className='msg-container user-msg'; "
                + "  const bubble = document.createElement('div'); bubble.className='user-bubble'; bubble.innerText=text; "
                + "  div.appendChild(bubble); document.getElementById('chat-box').appendChild(div); window.scrollTo(0, document.body.scrollHeight); "
                + "}"
                + "function addAiMsg(mdText, isError) { "
                + "  const div = document.createElement('div'); div.className='msg-container ai-msg'; "
                + "  const header = document.createElement('div'); header.className='ai-header'; header.innerHTML='✨ Gemini'; "
                + "  const content = document.createElement('div'); "
                + "  if(isError) { content.style.color = '#ff6b6b'; content.innerText = mdText; } "
                + "  else { "
                + "      if(typeof marked !== 'undefined') { content.innerHTML = marked.parse(mdText); content.querySelectorAll('pre code').forEach((el) => hljs.highlightElement(el)); } "
                + "      else { content.innerText = mdText; } "
                + "  } "
                + "  div.appendChild(header); div.appendChild(content); document.getElementById('chat-box').appendChild(div); window.scrollTo(0, document.body.scrollHeight); "
                + "}"
                + "function showThinking() { "
                + "  const div = document.createElement('div'); div.id='thinking-flag'; div.className='msg-container ai-msg thinking'; "
                + "  div.innerHTML='<div class=\"ai-header\">✨ Gemini</div><div>Thinking & analyzing data...</div>'; "
                + "  document.getElementById('chat-box').appendChild(div); window.scrollTo(0, document.body.scrollHeight); "
                + "}"
                + "function removeThinking() { const el = document.getElementById('thinking-flag'); if(el) el.remove(); }"
                + "</script></body></html>";
        webEngine.loadContent(htmlTemplate);
    }

    private String escapeJsString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // --- 新增：Java端统一消息记录和全量刷新 ---
    private void addMessageToHistory(String role, String text, boolean isError) {
        chatHistory.add(new ChatMessage(role, text, isError));
        if (role.equals("SYS")) appendSystemMessageJS(text);
        else if (role.equals("USER")) appendUserMessageJS(text);
        else appendAiMessageJS(text, isError);
    }

    private void redrawChat() {
        safeExecuteScript("document.getElementById('chat-box').innerHTML = '';");
        for (ChatMessage msg : chatHistory) {
            if (msg.role.equals("SYS")) appendSystemMessageJS(msg.text);
            else if (msg.role.equals("USER")) appendUserMessageJS(msg.text);
            else appendAiMessageJS(msg.text, msg.isError);
        }
    }

    private void appendSystemMessageJS(String text) {
        safeExecuteScript("addSysMsg('" + escapeJsString(text) + "');");
    }

    private void appendUserMessageJS(String text) {
        safeExecuteScript("addUserMsg('" + escapeJsString(text) + "');");
    }

    private void appendAiMessageJS(String text, boolean isError) {
        Platform.runLater(() -> {
            try {
                String safeEncodedText = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
                safeExecuteScript("removeThinking();");
                safeExecuteScript("addAiMsg(decodeURIComponent('" + safeEncodedText + "'), " + isError + ");");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showThinkingIndicator() {
        safeExecuteScript("showThinking();");
    }

    // ========== API Chat 工具栏功能区 ==========

    @FXML
    private void handleEditLastPrompt() {
        int lastUserIdx = -1;
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            if (chatHistory.get(i).role.equals("USER")) { lastUserIdx = i; break; }
        }
        if (lastUserIdx != -1) {
            String lastPrompt = chatHistory.get(lastUserIdx).text;
            chatInputField.setText(lastPrompt);
            chatHistory.subList(lastUserIdx, chatHistory.size()).clear(); // 移除它和之后的AI回答
            redrawChat();
        }
    }

    @FXML
    private void handleRegenerate() {
        int lastUserIdx = -1;
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            if (chatHistory.get(i).role.equals("USER")) { lastUserIdx = i; break; }
        }
        if (lastUserIdx != -1) {
            String lastPrompt = chatHistory.get(lastUserIdx).text;
            chatHistory.subList(lastUserIdx + 1, chatHistory.size()).clear(); // 只移除AI回答
            redrawChat();
            executeGeminiCall(lastPrompt);
        }
    }

    @FXML
    private void handleClearChat() {
        chatHistory.clear();
        redrawChat();
        addMessageToHistory("SYS", "Chat history cleared.", false);
    }

    @FXML
    private void handleSaveApiKey() {
        String key = apiKeyField.getText();
        if (key != null && !key.isEmpty()) {
            userService.updateApiKey(currentUsername, key);
            addMessageToHistory("SYS", "✅ API Key saved successfully to your account!", false);
        }
    }

    @FXML
    private void handleCheckConnection() {
        Stage testDialog = new Stage();
        testDialog.initModality(Modality.APPLICATION_MODAL);
        testDialog.setTitle("Test Proxy Connection");

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");

        Label label = new Label("Enter URL to test proxy connection:");
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        HBox inputRow = new HBox(10);
        TextField urlField = new TextField("https://www.youtube.com");
        urlField.setPrefWidth(250);
        Button testBtn = new Button("Test");
        inputRow.getChildren().addAll(urlField, testBtn);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(120);
        resultArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #a9b7c6;");

        testBtn.setOnAction(e -> {
            String targetUrl = urlField.getText();
            boolean useProxy = useProxyCheck.isSelected();
            String host = proxyHostField.getText();
            int port = parseProxyPort(proxyPortField.getText());

            resultArea.setText("Testing connection to " + targetUrl + "...\n");

            geminiApiClient.testGenericNetworkAsync(targetUrl, useProxy, host, port, new GeminiApiClient.GeminiCallback() {
                @Override public void onSuccess(String response) { Platform.runLater(() -> resultArea.appendText("✅ " + response + "\n")); }
                @Override public void onError(String errorMessage) { Platform.runLater(() -> resultArea.appendText("❌ Failed:\n" + errorMessage + "\n")); }
            });
        });

        vbox.getChildren().addAll(label, inputRow, resultArea);
        testDialog.setScene(new Scene(vbox, 400, 250));
        testDialog.show();
    }

    @FXML
    private void handleSendChat() {
        String prompt = chatInputField.getText();
        if (prompt == null || prompt.trim().isEmpty()) return;

        chatInputField.clear();
        addMessageToHistory("USER", prompt, false);
        executeGeminiCall(prompt);
    }

    private void executeGeminiCall(String prompt) {
        String apiKey = apiKeyField.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            addMessageToHistory("SYS", "⚠️ Error: Please configure and save your Gemini API Key first.", true);
            return;
        }

        StringBuilder context = new StringBuilder("FTC Alliance Analysis for Main Team: " + mainTeamField.getText() + ":\n");
        if (analysisTable.getItems().isEmpty()) {
            context.append("No active analysis data currently. Answer general FTC questions.\n");
        } else {
            for (AnalysisResult res : analysisTable.getItems()) {
                context.append(String.format("- Partner Team %d: Proj. Total Score %.1f, Comb. Accuracy %.1f%%, Stability ±%.1f, Note: %s\n",
                        res.getPartnerTeam(), res.getTotalEfficiency(), res.getCombinedAccuracy(), res.getStability(), res.getStyleDesc()));
            }
        }

        showThinkingIndicator();

        String model = modelComboBox.getValue();
        boolean useProxy = useProxyCheck.isSelected();
        String host = proxyHostField.getText();
        int port = parseProxyPort(proxyPortField.getText());

        geminiApiClient.sendChatRequestAsync(apiKey, model, context.toString(), prompt, useProxy, host, port, new GeminiApiClient.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                Platform.runLater(() -> addMessageToHistory("AI", response, false));
            }
            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> addMessageToHistory("AI", errorMessage, true));
            }
        });
    }

    private int parseProxyPort(String portStr) {
        try { return Integer.parseInt(portStr.trim()); } catch (NumberFormatException e) { return 7890; }
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