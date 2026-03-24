// File: src/main/java/com/bear27570/ftc/scouting/services/network/FtcScoutApiClient.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FtcScoutApiClient {

    public record FtcEventInfo(String name, boolean hasMatches) {}

    private final MatchDataService matchDataService;
    private final HttpClient client;
    private static final Logger log = LoggerFactory.getLogger(FtcEventInfo.class);
    public FtcScoutApiClient(MatchDataService matchDataService) {
        this.matchDataService = matchDataService;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public CompletableFuture<Integer> fetchAndSyncEventDataAsync(int season, String eventCode, String competitionName, Consumer<FtcEventInfo> onProgress) {
        String gqlUrl = "https://api.ftcscout.org/graphql";

        // 💡 架构师级做法：GraphQL 查询体依然拼接，但外层的 JSON Wrapper 让 Gson 处理转义
        String rawQuery = String.format("query { eventByCode(season: %d, code: \"%s\") { name hasMatches } }", season, eventCode);
        JsonObject infoPayload = new JsonObject();
        infoPayload.addProperty("query", rawQuery);

        HttpRequest infoReq = HttpRequest.newBuilder()
                .uri(URI.create(gqlUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(infoPayload.toString()))
                .build();

        return client.sendAsync(infoReq, HttpResponse.BodyHandlers.ofString())
                .thenCompose(infoRes -> {
                    if (infoRes.statusCode() != 200 || infoRes.body().contains("\"errors\"")) {
                        throw new RuntimeException("API Error. Check Season/Event Code.");
                    }

                    String eventName = "Unknown Event";
                    boolean hasMatches = false;

                    // 替换正则，使用 Gson 解析来获取名称和状态
                    try {
                        JsonObject root = JsonParser.parseString(infoRes.body()).getAsJsonObject();
                        JsonObject eventData = root.getAsJsonObject("data").getAsJsonObject("eventByCode");
                        if (eventData != null && !eventData.isJsonNull()) {
                            if (eventData.has("name") && !eventData.get("name").isJsonNull()) {
                                eventName = eventData.get("name").getAsString();
                            }
                            if (eventData.has("hasMatches") && !eventData.get("hasMatches").isJsonNull()) {
                                hasMatches = eventData.get("hasMatches").getAsBoolean();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse event metadata: " + e.getMessage());
                    }

                    if (onProgress != null) onProgress.accept(new FtcEventInfo(eventName, hasMatches));

                    if (!hasMatches) {
                        return CompletableFuture.completedFuture(0);
                    }

                    // 第二阶段：拉取具体比赛数据
                    String rawDataQuery = String.format("query { eventByCode(season: %d, code: \"%s\") { matches { matchNum scores { ... on MatchScores2025 { red { totalPointsNp majorsCommitted minorsCommitted } blue { totalPointsNp majorsCommitted minorsCommitted } } ... on MatchScores2024 { red { totalPointsNp majorsCommitted minorsCommitted } blue { totalPointsNp majorsCommitted minorsCommitted } } } } } }", season, eventCode);
                    JsonObject dataPayload = new JsonObject();
                    dataPayload.addProperty("query", rawDataQuery);

                    HttpRequest dataReq = HttpRequest.newBuilder()
                            .uri(URI.create(gqlUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(dataPayload.toString()))
                            .build();

                    return client.sendAsync(dataReq, HttpResponse.BodyHandlers.ofString())
                            .thenApply(dataRes -> {
                                if (dataRes.statusCode() == 200 && !dataRes.body().contains("\"errors\"")) {
                                    return parseAndSaveMatchData(dataRes.body(), competitionName);
                                }
                                throw new RuntimeException("Failed to fetch detailed match scores.");
                            });
                });
    }

    private int parseAndSaveMatchData(String json, String competitionName) {
        int count = 0;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || data.isJsonNull()) return 0;

            JsonObject eventByCode = data.getAsJsonObject("eventByCode");
            if (eventByCode == null || eventByCode.isJsonNull()) return 0;

            JsonArray matches = eventByCode.getAsJsonArray("matches");
            if (matches == null || matches.isJsonNull()) return 0;

            Set<String> processedMatches = new HashSet<>();
            for (JsonElement matchElem : matches) {
                JsonObject matchObj = matchElem.getAsJsonObject();
                if (!matchObj.has("matchNum") || matchObj.get("matchNum").isJsonNull()) continue;

                int matchNum = matchObj.get("matchNum").getAsInt();
                JsonObject scores = matchObj.getAsJsonObject("scores");
                if (scores == null || scores.isJsonNull()) continue;

                JsonObject red = scores.getAsJsonObject("red");
                JsonObject blue = scores.getAsJsonObject("blue");

                if (red != null && blue != null) {
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
            log.error("JSON parse error: " + e.getMessage(), e);
            throw new RuntimeException("Parse Error", e);
        }
        return count;
    }

    private int extractSafeInt(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : 0;
    }
}