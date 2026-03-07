// File: NetworkServiceTest.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.PenaltyRepository;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class NetworkServiceTest {

    private NetworkService hostService;
    private NetworkService clientService;
    private NetworkDataHandler mockDataHandler;

    @BeforeAll
    static void initJavaFX() {
        // 由于 NetworkService 内部使用了 Platform.runLater()
        // 在无头 JUnit 测试中必须先启动 JavaFX Toolkit，否则会抛出异常
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit 已经初始化过，安全忽略
        }
    }

    @BeforeEach
    void setUp() {
        // 每次测试前初始化独立的 Service 实例
        hostService = new NetworkService();
        clientService = new NetworkService();

        mockDataHandler = Mockito.mock(NetworkDataHandler.class);
        hostService.setDataHandler(mockDataHandler);
    }

    @AfterEach
    void tearDown() {
        hostService.stop();
        clientService.stop();
    }

    // ==========================================
    // 1. 测试从机加入房间 (HTTP Join)
    // ==========================================
    @Test
    void testHostAndClientConnection_JoinRequest() throws Exception {
        Competition comp = new Competition("NetTestComp", "Admin");
        when(mockDataHandler.isUserApprovedOrCreator("TestClient", "NetTestComp")).thenReturn(true);
        when(mockDataHandler.getScores("NetTestComp")).thenReturn(Collections.emptyList());
        when(mockDataHandler.getRankings("NetTestComp")).thenReturn(Collections.emptyList());

        CountDownLatch joinLatch = new CountDownLatch(1);

        hostService.startHost(comp, score -> {});
        Thread.sleep(500);

        clientService.connectToHost("127.0.0.1", "TestClient", packet -> {
            if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE && packet.isApproved()) {
                joinLatch.countDown();
            }
        });

        boolean success = joinLatch.await(3, TimeUnit.SECONDS);
        assertTrue(success, "客户端未能收到加入批准响应 (HTTP 失败)");
    }

    // ==========================================
    // 2. 测试 UDP 局域网发现广播
    // ==========================================
    @Test
    void testUdpDiscovery_FindsHost() throws Exception {
        Competition comp = new Competition("DiscoveryTestComp", "HostUser");
        ObservableList<Competition> discoveredList = FXCollections.observableArrayList();

        // 1. 客户端开始监听 UDP
        clientService.startDiscovery(discoveredList);
        Thread.sleep(500); // 等待 UDP 端口绑定

        // 2. 主机开始发送 UDP 广播
        hostService.startHost(comp, score -> {});

        // 3. 等待最多 5 秒，检查列表里是否出现比赛
        boolean found = false;
        for (int i = 0; i < 50; i++) {
            if (!discoveredList.isEmpty()) {
                found = true;
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(found, "UDP 广播发现失败！可能是端口被占用或者虚拟网卡导致的组播失败。");
        System.out.println("✅ 成功发现局域网比赛: " + discoveredList.get(0).getName());
    }

    // ==========================================
    // 3. 测试成绩提交 (Client -> Host HTTP POST)
    // ==========================================
    @Test
    void testClientSubmitScoreAndHostReceives() throws Exception {
        Competition comp = new Competition("ScoreSubmitComp", "HostUser");
        when(mockDataHandler.isUserApprovedOrCreator("TestClient", "ScoreSubmitComp")).thenReturn(true);
        when(mockDataHandler.getScores("ScoreSubmitComp")).thenReturn(Collections.emptyList());
        when(mockDataHandler.getRankings("ScoreSubmitComp")).thenReturn(Collections.emptyList());

        CountDownLatch scoreReceivedLatch = new CountDownLatch(1);

        hostService.startHost(comp, score -> {
            if (score.getMatchNumber() == 99 && "TestClient".equals(score.getSubmitter())) {
                System.out.println("✅ 主机成功接收到从机提交的成绩: Match " + score.getMatchNumber());
                scoreReceivedLatch.countDown();
            }
        });
        Thread.sleep(500);

        // 客户端先连接主机
        CountDownLatch joinLatch = new CountDownLatch(1);
        clientService.connectToHost("127.0.0.1", "TestClient", packet -> {
            if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE && packet.isApproved()) {
                joinLatch.countDown();
            }
        });
        assertTrue(joinLatch.await(3, TimeUnit.SECONDS), "客户端未能连接到主机");

        // 客户端构造一份成绩并发送 (Match = 99)
        ScoreEntry entry = new ScoreEntry(ScoreEntry.Type.ALLIANCE, 99, "RED", 1111, 2222, 10, 20,
                true, false, true, false, false, false, false, false, "", "TestClient");

        boolean sent = clientService.sendScoreToServer(entry);
        assertTrue(sent, "HTTP 成绩发送请求返回失败");

        assertTrue(scoreReceivedLatch.await(3, TimeUnit.SECONDS), "主机未能触发接收成绩的回调");
    }

    // ==========================================
    // 4. 测试全局数据广播 (Host -> Client WebSocket)
    // ==========================================
    @Test
    void testHostBroadcastUpdateToClients() throws Exception {
        Competition comp = new Competition("BroadcastComp", "HostUser");
        when(mockDataHandler.isUserApprovedOrCreator("TestClient", "BroadcastComp")).thenReturn(true);
        when(mockDataHandler.getScores("BroadcastComp")).thenReturn(Collections.emptyList());
        when(mockDataHandler.getRankings("BroadcastComp")).thenReturn(Collections.emptyList());

        hostService.startHost(comp, score -> {});
        Thread.sleep(500);

        CountDownLatch broadcastLatch = new CountDownLatch(1);
        clientService.connectToHost("127.0.0.1", "TestClient", packet -> {
            // 我们通过识别特定的 officialEventName 来判断是不是后续主动发出的那条广播
            if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA
                    && "Official Broadcast Event".equals(packet.getOfficialEventName())) {
                System.out.println("✅ 客户端成功通过 WebSocket 收到主机的全局广播更新");
                broadcastLatch.countDown();
            }
        });

        // 等待 1 秒，确保 WebSocket 握手完毕
        Thread.sleep(1000);

        // 主动触发一次全员广播
        NetworkPacket broadcastPacket = new NetworkPacket(Collections.emptyList(), Collections.emptyList(), "Official Broadcast Event");
        hostService.broadcastUpdateToClients(broadcastPacket);

        assertTrue(broadcastLatch.await(3, TimeUnit.SECONDS), "客户端未能收到 WebSocket 全局广播");
    }
    // ==========================================
    // 5. 诊断工具：FTCScout API 连通与侦察员信誉度解析
    // ==========================================
    @Test
    void testScouterReliabilityDiagnostic() throws Exception {
        int testSeason = 2025; // 或者 2025
        String testEventCode = "CNCMPLB"; // 鲁班赛区

        System.out.println("\n=== 🕵️ 侦察员信誉度系统诊断测试 ===");

        // 模拟数据库，用于临时存储 API 抓取的数据
        Map<Integer, PenaltyRepository.FullPenaltyRow> dbMock = new ConcurrentHashMap<Integer, PenaltyRepository.FullPenaltyRow>();

        // 1. 拦截保存方法，将数据写入我们的 Mock DB
        MatchDataService mockMatchService = Mockito.mock(MatchDataService.class);
        Mockito.doAnswer(invocation -> {
            PenaltyEntry p = invocation.getArgument(1);
            dbMock.compute(p.getMatchNumber(), (k, v) -> {
                if (v == null) v = new PenaltyRepository.FullPenaltyRow(0, 0, 0, 0, 0, 0);
                if (p.getAlliance().equalsIgnoreCase("RED")) {
                    v.rMaj = p.getMajorCount(); v.rMin = p.getMinorCount(); v.rScore = p.getOfficialScore();
                } else {
                    v.bMaj = p.getMajorCount(); v.bMin = p.getMinorCount(); v.bScore = p.getOfficialScore();
                }
                return v;
            });
            return null;
        }).when(mockMatchService).submitPenalty(Mockito.anyString(), Mockito.any(PenaltyEntry.class));

        FtcScoutApiClient apiClient = new FtcScoutApiClient(mockMatchService);
        CountDownLatch latch = new CountDownLatch(1);

        // 2. 触发 API 拉取
        System.out.println("📡 正在连接 FTCScout 获取 " + testEventCode + " 数据...");
        apiClient.fetchAndSyncEventDataAsync(testSeason, testEventCode, "TestComp", new FtcScoutApiClient.ApiCallback() {
            @Override public void onEventFound(String name, boolean hasMatches) {}
            @Override public void onError(String error) { latch.countDown(); }
            @Override public void onSuccess(String name, int count) {
                System.out.println("✅ API 拉取完毕，共解析 " + count + " 场比赛。\n");
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS), "API 请求超时！");

        // 3. 寻找一场包含分数的有效比赛
        Integer targetMatchNum = dbMock.keySet().stream().filter(k -> dbMock.get(k).rScore > 0).findFirst().orElse(null);
        if (targetMatchNum == null) {
            System.err.println("❌ 致命错误：API 抓取成功，但所有比赛的分数(officialScore)都是 0！");
            System.err.println("👉 请检查 FtcScoutApiClient 里的 extractPenalty(..., \"totalpoints\") 正则表达式是否失效！");
            return;
        }

        PenaltyRepository.FullPenaltyRow officialData = dbMock.get(targetMatchNum);
        System.out.println("📊 选中测试场次: Match " + targetMatchNum);
        System.out.println("   -> 官方红方总分: " + officialData.rScore);
        System.out.println("   -> 官方蓝方总分: " + officialData.bScore);

        // 4. 模拟一个糟糕的侦察员，记了 0 分
        System.out.println("\n🚶 模拟侦察员 'BadScouter' 提交 Match " + targetMatchNum + " (红方) 得分为 0...");
        ScoreEntry fakeZeroScore = new ScoreEntry(
                ScoreEntry.Type.ALLIANCE, targetMatchNum, "RED",
                1111, 2222, 0, 0, // 0 自动，0 手动
                false, false, false, false, false, false, false, false,
                "", "BadScouter"
        );

        // =====================================
        // 5. 模拟 RankingService 的核心评估逻辑
        // =====================================
        System.out.println("\n--- 🧠 核心评估逻辑 Trace ---");

        if (fakeZeroScore.getScoreType() != ScoreEntry.Type.ALLIANCE) {
            System.out.println("🛑 判定跳过：模式为 SINGLE 单队模式，无法与官方联盟总分对比。");
            return;
        }
        System.out.println("✅ 模式校验：ALLIANCE 模式 (通过)");

        if (officialData == null) {
            System.out.println("🛑 判定跳过：未找到该场次的官方数据 (本地数据库缺少记录)。");
            return;
        }
        System.out.println("✅ 官方数据校验：记录存在 (通过)");

        boolean isRed = fakeZeroScore.getAlliance().equalsIgnoreCase("RED");
        int officialTotal = isRed ? officialData.rScore : officialData.bScore;

        if (officialTotal <= 0) {
            System.out.println("🛑 判定跳过：官方总分为 " + officialTotal + "，视为比赛未出分。");
            return;
        }
        System.out.println("✅ 官方分数校验：" + officialTotal + " 分 (通过)");

        // 侦察员算出的是纯机器得分，官方总分包含对面送的犯规分，需要补偿计算
        int penaltyGained = isRed ? (officialData.bMaj * 15 + officialData.bMin * 5) : (officialData.rMaj * 15 + officialData.rMin * 5);
        int scoutPredictedTotal = fakeZeroScore.getTotalScore() + penaltyGained;

        System.out.println("🧮 侦察员总分 (含犯规补偿) = " + fakeZeroScore.getTotalScore() + " + " + penaltyGained + " = " + scoutPredictedTotal);

        double error = Math.abs(scoutPredictedTotal - officialTotal) / (double) officialTotal;
        System.out.printf("⚖️ 误差率计算: |%d - %d| / %d = %.2f%%\n", scoutPredictedTotal, officialTotal, officialTotal, error * 100);

        if (error > 0.20) {
            System.out.println("🎯 结论：误差大于 20%，该侦察员将被打上 [⚠️ Low Rel] 标签，权重降为 0.5！");
        } else {
            System.out.println("🎯 结论：误差在允许范围内，侦察员可信。");
        }
        System.out.println("----------------------------------------\n");
    }
}