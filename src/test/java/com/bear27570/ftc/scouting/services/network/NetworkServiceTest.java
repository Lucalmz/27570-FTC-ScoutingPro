package com.bear27570.ftc.scouting.services.network;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.services.NetworkService;
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
        // 因为是单例模式测试，为了防止端口冲突，每次重新初始化时需要确保关闭
        hostService.stop();

        // 1. 创建假的 DataHandler，屏蔽数据库
        mockDataHandler = Mockito.mock(NetworkDataHandler.class);
        hostService.setDataHandler(mockDataHandler);

        // 我们需要另一个独立实例扮演 Client
        // 由于旧代码是单例，为了在同一台机器上做集成测试，可以通过反射创建新实例，或依靠真实的端口环回
        // 这里简化演示：由于只是测流程，假设客户端也能用 NetworkService 的普通方法发包
        clientService = new NetworkService();
    }

    @AfterEach
    void tearDown() {
        hostService.stop();
        clientService.stop();
    }

    @Test
    void testHostAndClientConnection_JoinRequest() throws Exception {
        // --- Arrange ---
        Competition comp = new Competition("NetTestComp", "Admin");

        // 模拟：当用户申请加入时，模拟数据库返回已批准
        when(mockDataHandler.isUserApprovedOrCreator("TestClient", "NetTestComp")).thenReturn(true);
        when(mockDataHandler.getScores("NetTestComp")).thenReturn(Collections.emptyList());
        when(mockDataHandler.getRankings("NetTestComp")).thenReturn(Collections.emptyList());

        // 使用 CountDownLatch 等待异步网络回调
        CountDownLatch joinLatch = new CountDownLatch(1);

        // --- Act ---
        // 启动 Host
        hostService.startHost(comp, score -> {});
        Thread.sleep(500); // 等待服务器启动完成

        // 启动 Client 连接到 localhost
        clientService.connectToHost("127.0.0.1", "TestClient", packet -> {
            // 断言客户端收到了加入成功的响应！
            if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE && packet.isApproved()) {
                joinLatch.countDown(); // 成功接收，释放锁
            }
        });

        // --- Assert ---
        // 等待最多 2 秒钟，如果倒计时归零说明测试通过，否则网络连接失败
        boolean success = joinLatch.await(2, TimeUnit.SECONDS);
        assertTrue(success, "客户端未能收到来自服务器的加入批准响应！");

        // 测试通过，证明：TCP Socket 通信正常 + 数据包序列化正常 + 接口解耦成功！
    }
}