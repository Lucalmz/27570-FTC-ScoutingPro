package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtcScoutApiClient {

    // 定义回调接口，用于向 Controller 报告进度和结果
    public interface ApiCallback {
        void onEventFound(String eventName, boolean hasMatches);
        void onSuccess(String eventName, int syncedMatchCount);
        void onError(String errorMessage);
    }

    private final MatchDataService matchDataService;

    public FtcScoutApiClient(MatchDataService matchDataService) {
        this.matchDataService = matchDataService;
    }

    /**
     * 异步获取赛事信息并同步罚分数据
     */
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

                // 通知 Controller 已经找到了赛事
                callback.onEventFound(eventName, hasMatches);

                if (!hasMatches) {
                    callback.onSuccess(eventName, 0);
                    return;
                }

                // 2. 获取具体比赛罚分数据 (尝试 2025 和 2024 的 Schema)
                int matchCount = 0;
                String[] queriesToTry = {
                        "{\"query\": \"query { eventByCode(season: %d, code: \\\"%s\\\") { matches { matchNum scores { ... on MatchScores2025 { red { majorsCommitted minorsCommitted } blue { majorsCommitted minorsCommitted } } } } } }\"}",
                        "{\"query\": \"query { eventByCode(season: %d, code: \\\"%s\\\") { matches { matchNum scores { ... on MatchScores2024 { red { majorsCommitted minorsCommitted } blue { majorsCommitted minorsCommitted } } } } } }\"}"
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
        int startIdx = json.indexOf("\"matches\"");
        if (startIdx == -1) return 0;
        String content = json.substring(startIdx);

        String[] matchBlocks = content.split("\"matchNum\"\\s*:");

        for (int i = 1; i < matchBlocks.length; i++) {
            String block = matchBlocks[i];

            Matcher mNum = Pattern.compile("^\\s*(\\d+)").matcher(block);
            if (!mNum.find()) continue;
            int matchNum = Integer.parseInt(mNum.group(1));

            String lowerBlock = block.toLowerCase();
            int redIdx = lowerBlock.indexOf("\"red\"");
            int blueIdx = lowerBlock.indexOf("\"blue\"");

            if (redIdx != -1 && blueIdx != -1) {
                String redPart, bluePart;
                if (redIdx < blueIdx) {
                    redPart = lowerBlock.substring(redIdx, blueIdx);
                    bluePart = lowerBlock.substring(blueIdx, Math.min(lowerBlock.length(), blueIdx + 300));
                } else {
                    bluePart = lowerBlock.substring(blueIdx, redIdx);
                    redPart = lowerBlock.substring(redIdx, Math.min(lowerBlock.length(), redIdx + 300));
                }

                int rMin = extractPenalty(redPart, "minorscommitted");
                int rMaj = extractPenalty(redPart, "majorscommitted");
                int bMin = extractPenalty(bluePart, "minorscommitted");
                int bMaj = extractPenalty(bluePart, "majorscommitted");

                if (rMaj > 0 || rMin > 0) {
                    matchDataService.submitPenalty(competitionName, new PenaltyEntry(matchNum, "RED", rMaj, rMin));
                }
                if (bMaj > 0 || bMin > 0) {
                    matchDataService.submitPenalty(competitionName, new PenaltyEntry(matchNum, "BLUE", bMaj, bMin));
                }
                count++;
            }
        }
        return count;
    }

    private int extractPenalty(String jsonPart, String keyNameLowerCase) {
        Pattern p = Pattern.compile("\"" + keyNameLowerCase + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(jsonPart);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }
}