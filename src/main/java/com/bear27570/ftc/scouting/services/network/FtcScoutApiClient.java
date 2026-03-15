package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtcScoutApiClient {

    public interface ApiCallback {
        void onEventFound(String eventName, boolean hasMatches);
        void onSuccess(String eventName, int syncedMatchCount);
        void onError(String errorMessage);
    }

    private final MatchDataService matchDataService;

    public FtcScoutApiClient(MatchDataService matchDataService) {
        this.matchDataService = matchDataService;
    }

    public void fetchAndSyncEventDataAsync(int season, String eventCode, String competitionName, ApiCallback callback) {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String gqlUrl = "https://api.ftcscout.org/graphql";

                // 1. 查询赛事基本信息
                String infoQuery = String.format(
                        "{\"query\": \"query { eventByCode(season: %d, code: \\\"%s\\\") { name hasMatches } }\"}",
                        season, eventCode
                );

                HttpRequest infoReq = HttpRequest.newBuilder()
                        .uri(URI.create(gqlUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(infoQuery))
                        .build();
                HttpResponse<String> infoRes = client.send(infoReq, HttpResponse.BodyHandlers.ofString());

                if (infoRes.statusCode() != 200 || infoRes.body().contains("\"errors\"")) {
                    callback.onError("API Error. Check Season/Event Code.");
                    return;
                }

                String eventName = "Unknown Event";
                boolean hasMatches = false;

                Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(infoRes.body());
                if (nameMatcher.find()) eventName = nameMatcher.group(1);

                Matcher hasMatchesMatcher = Pattern.compile("\"hasMatches\"\\s*:\\s*(true|false)").matcher(infoRes.body());
                if (hasMatchesMatcher.find()) hasMatches = Boolean.parseBoolean(hasMatchesMatcher.group(1));

                callback.onEventFound(eventName, hasMatches);

                if (!hasMatches) {
                    callback.onSuccess(eventName, 0);
                    return;
                }

                // 2. 获取具体比赛数据 (★ 修改点：加入了 totalPoints 字段请求)
                int matchCount = 0;
                String[] queriesToTry = {
                        "{\"query\": \"query { eventByCode(season: %d, code: \\\"%s\\\") { matches { matchNum scores { ... on MatchScores2025 { red { totalPointsNp majorsCommitted minorsCommitted } blue { totalPointsNp majorsCommitted minorsCommitted } } } } } }\"}",
                        "{\"query\": \"query { eventByCode(season: %d, code: \\\"%s\\\") { matches { matchNum scores { ... on MatchScores2024 { red { totalPointsNp majorsCommitted minorsCommitted } blue { totalPointsNp majorsCommitted minorsCommitted } } } } } }\"}"
                };

                for (String qFormat : queriesToTry) {
                    String query = String.format(qFormat, season, eventCode);
                    HttpRequest dataReq = HttpRequest.newBuilder()
                            .uri(URI.create(gqlUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(query))
                            .build();

                    HttpResponse<String> dataRes = client.send(dataReq, HttpResponse.BodyHandlers.ofString());

                    if (dataRes.statusCode() == 200 && !dataRes.body().contains("\"errors\"")) {
                        matchCount = parseAndSaveMatchData(dataRes.body(), competitionName);
                        if (matchCount > 0) break;
                    }
                }

                callback.onSuccess(eventName, matchCount);

            } catch (Exception e) {
                callback.onError("Failed: " + e.getMessage());
            }
        }).start();
    }
    private int parseAndSaveMatchData(String json, String competitionName) {
        int count = 0;
        try {
            // 安全地将字符串解析为 JSON 树
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || data.isJsonNull()) return 0;

            JsonObject eventByCode = data.getAsJsonObject("eventByCode");
            if (eventByCode == null || eventByCode.isJsonNull()) return 0;

            JsonArray matches = eventByCode.getAsJsonArray("matches");
            if (matches == null || matches.isJsonNull()) return 0;

            Set<String> processedMatches = new HashSet<>();

            // 遍历每一场比赛
            for (JsonElement matchElem : matches) {
                JsonObject matchObj = matchElem.getAsJsonObject();
                int matchNum = matchObj.get("matchNum").getAsInt();

                JsonObject scores = matchObj.getAsJsonObject("scores");
                if (scores == null || scores.isJsonNull()) continue;

                JsonObject red = scores.getAsJsonObject("red");
                JsonObject blue = scores.getAsJsonObject("blue");

                if (red != null && blue != null) {
                    // 安全提取字段，如果官方以后加了别的字段，这里绝对不会越界崩溃
                    int rMin = extractSafeInt(red, "minorsCommitted");
                    int rMaj = extractSafeInt(red, "majorsCommitted");
                    int rScore = extractSafeInt(red, "totalPointsNp");

                    int bMin = extractSafeInt(blue, "minorsCommitted");
                    int bMaj = extractSafeInt(blue, "majorsCommitted");
                    int bScore = extractSafeInt(blue, "totalPointsNp");

                    boolean isNewMatch = false;

                    String redKey = matchNum + "_RED";
                    if (!processedMatches.contains(redKey)) {
                        processedMatches.add(redKey);
                        isNewMatch = true;
                        if (rMaj > 0 || rMin > 0 || rScore > 0) {
                            matchDataService.submitPenalty(competitionName, new PenaltyEntry(matchNum, "RED", rMaj, rMin, rScore));
                        }
                    }

                    String blueKey = matchNum + "_BLUE";
                    if (!processedMatches.contains(blueKey)) {
                        processedMatches.add(blueKey);
                        isNewMatch = true;
                        if (bMaj > 0 || bMin > 0 || bScore > 0) {
                            matchDataService.submitPenalty(competitionName, new PenaltyEntry(matchNum, "BLUE", bMaj, bMin, bScore));
                        }
                    }

                    if (isNewMatch) count++;
                }
            }
        } catch (Exception e) {
            System.err.println("JSON 解析异常: " + e.getMessage());
            e.printStackTrace();
        }
        return count;
    }

    // 替代原有的 Regex extractValue 方法
    private int extractSafeInt(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return 0;
    }
}