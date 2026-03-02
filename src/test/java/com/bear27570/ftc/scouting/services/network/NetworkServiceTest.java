// File: NetworkServiceTest.java
package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterEach;
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

    @BeforeEach
    void setUp() {
        hostService = NetworkService.getInstance();
        hostService.stop();

        mockDataHandler = Mockito.mock(NetworkDataHandler.class);
        hostService.setDataHandler(mockDataHandler);

        clientService = new NetworkService();
    }

    @AfterEach
    void tearDown() {
        hostService.stop();
        clientService.stop();
    }

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

        boolean success = joinLatch.await(2, TimeUnit.SECONDS);
        assertTrue(success, "客户端未能收到加入批准响应 (TCP 失败)");
    }

    // ★ 新增的 UDP 发现测试
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

        assertTrue(found, "UDP 广播发现失败！可能是端口被占用或者网卡枚举问题。");
        System.out.println("成功发现局域网比赛: " + discoveredList.get(0).getName());
    }
}