// File: src/main/java/com/bear27570/ftc/scouting/AutoDataSimulatorApp.java
package com.bear27570.ftc.scouting;

import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.CompetitionRepository;
import com.bear27570.ftc.scouting.repository.DatabaseManager;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import com.bear27570.ftc.scouting.repository.ScoreRepository;
import com.bear27570.ftc.scouting.repository.UserRepository;
import com.bear27570.ftc.scouting.repository.impl.CompetitionRepositoryJdbiImpl;
import com.bear27570.ftc.scouting.repository.impl.PenaltyRepositoryJdbiImpl;
import com.bear27570.ftc.scouting.repository.impl.ScoreRepositoryJdbiImpl;
import com.bear27570.ftc.scouting.repository.impl.UserRepositoryJdbiImpl;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale; // 👈 新增引入
import java.util.Random;

public class AutoDataSimulatorApp {

    private static final String API_URL = "https://api.ftcscout.org/graphql";
    private static final String COMPETITION_NAME = "Simulated_Championship_2025";

    private static final String SUBMITTER_NAME = "Lucalmz";

    private static final double FIELD_WIDTH = 650.0;
    private static final double FIELD_HEIGHT = 650.0;
    private static final double DIVIDER_Y = FIELD_HEIGHT * (2.0 / 3.0);

    private static final Random random = new Random();

    // 💡 新增：构建一个随机池，模拟真实的赛场情况（有些队伍偏科，有些队伍全能）
    private static final String[] PRESET_ROUTINES = {
            "R1", "R1", "R2", "R2", "R3", "R3", "R3", // 单行比较常见
            "R1 R2", "R2 R3", "R1 R3",                // 双行混合
            "R1 R2 R3", "NONE", "NONE"                // 满分大佬或自动阶段翻车的
    };

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("🚀 启动 FTCScout 真实数据拉取与 AI 热力图模拟引擎");
        System.out.println("==================================================");

        String dbFolder = System.getProperty("user.home") + File.separator + ".ftcscoutingpro";
        File dbDir = new File(dbFolder);
        if (!dbDir.exists() && !dbDir.mkdirs()) {
            System.err.println("Warning: Could not create database directory at " + dbFolder);
        }
        String dbUrl = "jdbc:h2:" + dbFolder + File.separator + "ftc_scouting_master_db;AUTO_SERVER=TRUE";
        DatabaseManager.initialize(dbUrl);

        ScoreRepository scoreRepo = new ScoreRepositoryJdbiImpl();
        PenaltyRepository penaltyRepo = new PenaltyRepositoryJdbiImpl();
        UserRepository userRepo = new UserRepositoryJdbiImpl();
        CompetitionRepository compRepo = new CompetitionRepositoryJdbiImpl();

        userRepo.ensureUserExists(SUBMITTER_NAME);
        if (compRepo.findByName(COMPETITION_NAME) == null) {
            compRepo.create(COMPETITION_NAME, SUBMITTER_NAME, "total");
            compRepo.updateEventInfo(COMPETITION_NAME, 2025, "CNCMPLB", "FTC China Championship - LuBan");
            System.out.println("✅ 成功在数据库中初始化虚拟赛事: " + COMPETITION_NAME + " (Host: " + SUBMITTER_NAME + ")");
        } else {
            System.out.println("⚡ 虚拟赛事已存在，准备追加数据...");
        }

        int season = 2025;
        String eventCode = "CNCMPLB";

        String query = String.format("""
            {"query": "query Matches { eventByCode(season: %d, code: \\"%s\\") { matches { matchNum actualStartTime scores { ... on MatchScores2024 { red { totalPointsNp majorsCommitted minorsCommitted } blue { totalPointsNp majorsCommitted minorsCommitted } } ... on MatchScores2025 { red { totalPointsNp minorsCommitted majorsCommitted } blue { totalPointsNp minorsCommitted majorsCommitted } } } teams { alliance teamNumber } } } }" }
            """, season, eventCode).replace("\n", " ");

        try {
            System.out.println("📡 正在向 FTCScout 发送请求: " + eventCode);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                parseAndSimulateData(response.body(), scoreRepo, penaltyRepo);
            } else {
                System.err.println("❌ API 请求失败! HTTP 状态码: " + response.statusCode());
                System.err.println(response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseManager.close();
            System.out.println("✅ 数据库已安全关闭，模拟结束。");
        }
    }

    private static void parseAndSimulateData(String jsonBody, ScoreRepository scoreRepo, PenaltyRepository penaltyRepo) {
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
        JsonObject eventByCode = root.getAsJsonObject("data").getAsJsonObject("eventByCode");
        if (eventByCode == null || eventByCode.isJsonNull()) {
            System.err.println("❌ 未找到赛事数据，请检查 Season 或 Event Code！");
            return;
        }

        JsonArray matches = eventByCode.getAsJsonArray("matches");
        System.out.println("📥 成功拉取到 " + matches.size() + " 场比赛数据。开始生成模拟报告...");

        int count = 0;

        for (JsonElement matchElement : matches) {
            JsonObject matchObj = matchElement.getAsJsonObject();
            int matchNum = matchObj.get("matchNum").getAsInt();

            JsonObject scores = matchObj.getAsJsonObject("scores");
            if (scores == null || scores.isJsonNull()) continue;

            JsonArray teamsArray = matchObj.getAsJsonArray("teams");
            List<Integer> redTeams = new ArrayList<>();
            List<Integer> blueTeams = new ArrayList<>();

            for (JsonElement t : teamsArray) {
                JsonObject teamObj = t.getAsJsonObject();
                if (teamObj.get("alliance").getAsString().equalsIgnoreCase("Red")) {
                    redTeams.add(teamObj.get("teamNumber").getAsInt());
                } else {
                    blueTeams.add(teamObj.get("teamNumber").getAsInt());
                }
            }

            if (redTeams.size() < 2 || blueTeams.size() < 2) continue;

            simulateAlliance(matchNum, "RED", redTeams.get(0), redTeams.get(1), scores.getAsJsonObject("red"), scoreRepo, penaltyRepo);
            simulateAlliance(matchNum, "BLUE", blueTeams.get(0), blueTeams.get(1), scores.getAsJsonObject("blue"), scoreRepo, penaltyRepo);

            count++;
        }

        System.out.println("🎉 恭喜！成功生成并写入了 " + count + " 场比赛（共 " + (count * 2) + " 条联队记录）到数据库！");
    }

    private static void simulateAlliance(int matchNum, String allianceColor, int team1, int team2, JsonObject officialScoreObj, ScoreRepository scoreRepo, PenaltyRepository penaltyRepo) {
        int totalPointsNp = officialScoreObj.has("totalPointsNp") ? officialScoreObj.get("totalPointsNp").getAsInt() : 0;
        int minors = officialScoreObj.has("minorsCommitted") ? officialScoreObj.get("minorsCommitted").getAsInt() : 0;
        int majors = officialScoreObj.has("majorsCommitted") ? officialScoreObj.get("majorsCommitted").getAsInt() : 0;

        penaltyRepo.savePenaltyEntry(COMPETITION_NAME, new PenaltyEntry(matchNum, allianceColor, majors, minors, totalPointsNp));

        int estimatedAutoScore = (int) (totalPointsNp * 0.25);
        int t1Auto = estimatedAutoScore / 2;
        int t2Auto = estimatedAutoScore - t1Auto;

        boolean t1Seq = random.nextBoolean();
        boolean t2Seq = random.nextBoolean();
        boolean t1Climb = random.nextBoolean();
        boolean t2Climb = random.nextBoolean();

        int endgamePoints = (t1Seq ? 10 : 0) + (t2Seq ? 10 : 0) + (t1Climb ? 15 : 0) + (t2Climb ? 15 : 0);

        int estimatedTeleopPoints = totalPointsNp - estimatedAutoScore - endgamePoints;
        if (estimatedTeleopPoints < 0) estimatedTeleopPoints = 0;
        int totalTeleopHits = estimatedTeleopPoints / 3;

        int nearHits = (int) Math.round(totalTeleopHits * 0.60);
        int farHits = totalTeleopHits - nearHits;

        String clickLocations = generateHeatmapData(nearHits, farHits);

        // 💡 随机指派自动阶段战术
        String t1Proj = random.nextBoolean() ? "NEAR" : "FAR";
        String t2Proj = random.nextBoolean() ? "NEAR" : "FAR";
        String t1Row = PRESET_ROUTINES[random.nextInt(PRESET_ROUTINES.length)];
        String t2Row = PRESET_ROUTINES[random.nextInt(PRESET_ROUTINES.length)];

        ScoreEntry entry = new ScoreEntry(
                ScoreEntry.Type.ALLIANCE,
                matchNum,
                allianceColor,
                team1,
                team2,
                t1Auto, t2Auto,
                t1Proj, t2Proj,
                t1Row, t2Row, // 👈 使用随机生成的组合
                totalTeleopHits,
                t1Seq, t2Seq, t1Climb, t2Climb,
                false, false, false, false,
                clickLocations,
                SUBMITTER_NAME
        );
        entry.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);

        scoreRepo.save(COMPETITION_NAME, entry);
    }

    private static String generateHeatmapData(int nearHits, int farHits) {
        StringBuilder sb = new StringBuilder();
        long baseTimestamp = System.currentTimeMillis() - 120000;

        for (int i = 0; i < nearHits; i++) {
            double x = clamp(325 + random.nextGaussian() * 80, 50, FIELD_WIDTH - 50);
            double y = clamp(216 + random.nextGaussian() * 100, 50, DIVIDER_Y - 10);
            baseTimestamp += random.nextInt(4000) + 1000;
            // 💡 强制 Locale.US 防止双浮点数逗号污染
            sb.append(String.format(Locale.US, "1:%.1f,%.1f,0,%d;", x, y, baseTimestamp));
        }

        for (int i = 0; i < farHits; i++) {
            double x = clamp(325 + random.nextGaussian() * 60, 100, FIELD_WIDTH - 100);
            double y = clamp(540 + random.nextGaussian() * 50, DIVIDER_Y + 10, FIELD_HEIGHT - 30);
            baseTimestamp += random.nextInt(5000) + 1500;
            // 💡 强制 Locale.US
            sb.append(String.format(Locale.US, "2:%.1f,%.1f,0,%d;", x, y, baseTimestamp));
        }

        return sb.toString();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}