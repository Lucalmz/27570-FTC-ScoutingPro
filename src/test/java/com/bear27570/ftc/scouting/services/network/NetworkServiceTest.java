// File: NetworkServiceTest.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.PenaltyEntry;
import com.bear27570.ftc.scouting.models.ScoreEntry;
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
    // 5. 测试 FTCScout API 连通与判罚解析
    // ==========================================
    @Test
    void testFtcScoutCommunication() throws Exception {
        // ========================================================
        // 💡 提示：你可以在这里修改赛季和赛事编号来测试任何其他比赛
        int testSeason = 2025;
        String testEventCode = "CNCMPLB"; // 鲁班赛区 (China Championship Luban)
        // ========================================================

        System.out.println("\n--- 开始测试 FTCScout 连通性: " + testSeason + " " + testEventCode + " ---");

        // Mock 数据库服务，拦截“保存判罚”动作，我们不存数据库，直接打印到控制台
        MatchDataService mockMatchService = Mockito.mock(MatchDataService.class);
        Mockito.doAnswer(invocation -> {
            PenaltyEntry penalty = invocation.getArgument(1);

            // 按大判 15，小判 5 的 Into The Deep / Centerstage 规则计算“犯规送给对面的分”
            int commPts = (penalty.getMajorCount() * 15) + (penalty.getMinorCount() * 5);

            // 输出格式美化
            System.out.printf("🔹 比赛场次: Match %-3d | 犯规联盟: %-4s | 犯规送出分数: -%-3d (大判: %d, 小判: %d)\n",
                    penalty.getMatchNumber(), penalty.getAlliance(), commPts, penalty.getMajorCount(), penalty.getMinorCount());

            return null;
        }).when(mockMatchService).submitPenalty(Mockito.anyString(), Mockito.any(PenaltyEntry.class));

        FtcScoutApiClient apiClient = new FtcScoutApiClient(mockMatchService);
        CountDownLatch latch = new CountDownLatch(1);

        apiClient.fetchAndSyncEventDataAsync(testSeason, testEventCode, "TestComp", new FtcScoutApiClient.ApiCallback() {
            @Override
            public void onEventFound(String eventName, boolean hasMatches) {
                System.out.println("✅ 成功绑定赛事: " + eventName + " | 是否已开始比赛: " + hasMatches);
            }

            @Override
            public void onSuccess(String eventName, int syncedMatchCount) {
                System.out.println("✅ 拉取完毕! 本次共抓取了 " + syncedMatchCount + " 场带有犯规数据的比赛。");
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                System.err.println("❌ API 抓取失败: " + errorMessage);
                latch.countDown(); // 出错也释放锁，防止测试卡死
            }
        });

        // 给予网络请求最多 15 秒的时间
        boolean finished = latch.await(15, TimeUnit.SECONDS);
        System.out.println("----------------------------------------------------------\n");
        assertTrue(finished, "FTCScout API 请求超时，请检查网络或代理设置！");
    }
}