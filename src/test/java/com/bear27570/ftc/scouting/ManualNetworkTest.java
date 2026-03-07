package com.bear27570.ftc.scouting;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.network.NetworkDataHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Scanner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 手动网络连接测试工具 (Java 21 兼容版)
 *
 * 使用说明：
 * 1. 确保两台电脑在同一局域网。
 * 2. 电脑 A 运行此程序 -> 输入 'host'。
 * 3. 电脑 B 运行此程序 -> 输入 'client'。
 * 4. 如果防火墙未拦截，Client 应能自动发现 Host，或手动输入 Host IP。
 */
public class ManualNetworkTest extends Application {

    private NetworkService networkService;
    private final Scanner scanner = new Scanner(System.in);

    // =================================================================
    //  【关键】Java 21 兼容启动器
    //  请运行这个 Launcher.main()，而不是直接运行 ManualNetworkTest
    // =================================================================
    public static class Launcher {
        public static void main(String[] args) {
            Application.launch(ManualNetworkTest.class, args);
        }
    }

    /**
     * 兼容旧版 IDE 的入口，建议优先使用上面的 Launcher.main
     */
    public static void main(String[] args) {
        Launcher.main(args);
    }

    @Override
    public void start(Stage primaryStage) {
        System.out.println("=========================================");
        System.out.println("   FTC Scouting Network Test Tool");
        System.out.println("   (v2.1.5 Data Model Adapted)");
        System.out.println("=========================================");

        networkService = NetworkService.getInstance();

        // 保持 JavaFX 线程存活，即使没有打开任何窗口
        Platform.setImplicitExit(false);

        // 在新线程运行控制台交互，避免阻塞 JavaFX UI 线程
        new Thread(this::consoleLoop).start();
    }

    private void consoleLoop() {
        try {
            System.out.print("请选择模式 (输入 host 或 client): ");
            // 简单的输入循环
            while (scanner.hasNextLine()) {
                String mode = scanner.nextLine().trim().toLowerCase();
                if ("host".equals(mode)) {
                    runAsHost();
                    break;
                } else if ("client".equals(mode)) {
                    runAsClient();
                    break;
                } else {
                    if (!mode.isEmpty()) {
                        System.out.print("输入无效，请输入 'host' 或 'client': ");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    //                 主机逻辑
    // ==========================================
    private void runAsHost() {
        try {
            System.out.println("\n[主机模式] 初始化中...");

            // 1. 模拟数据处理器 (Mock)
            // 如果报错 ClassNotFoundException: org.mockito...
            // 请确保 pom.xml 中 mockito 依赖没有 <scope>test</scope> 限制，或者将此文件移入 src/test/java
            NetworkDataHandler mockHandler = mock(NetworkDataHandler.class);

            // 模拟行为：允许任何人加入，返回空数据
            when(mockHandler.isUserApprovedOrCreator(anyString(), anyString())).thenReturn(true);
            when(mockHandler.getScores(anyString())).thenReturn(Collections.emptyList());
            when(mockHandler.getRankings(anyString())).thenReturn(Collections.emptyList());

            networkService.setDataHandler(mockHandler);
            networkService.setOfficialEventName("FTC Test Event 2026");

            // 2. 启动主机
            Competition comp = new Competition("TestComp", "HostUser");
            networkService.startHost(comp, (score) -> {
                System.out.println(">>> [主机] 收到成绩提交! ");
                System.out.println("    Match: " + score.getMatchNumber());
                System.out.println("    Alliance: " + score.getAlliance());
                System.out.println("    Auto Score: " + score.getAutoArtifacts());
                System.out.println("    TeleOp Score: " + score.getTeleopArtifacts());
                System.out.println("    Total Score: " + score.getTotalScore());
            });

            String localIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println("-----------------------------------------");
            System.out.println("[成功] 主机已启动！");
            System.out.println("本机 IP 地址: " + localIp);
            System.out.println("HTTP 端口: " + NetworkService.HTTP_PORT);
            System.out.println("UDP  端口: " + NetworkService.UDP_PORT);
            System.out.println("正在等待从机连接...");
            System.out.println("-----------------------------------------");

        } catch (Exception e) {
            System.err.println("[错误] 主机启动失败: ");
            e.printStackTrace();
            Platform.exit();
        }
    }

    // ==========================================
    //                 从机逻辑
    // ==========================================
    private void runAsClient() {
        try {
            System.out.println("\n[从机模式] 准备连接...");

            // 1. 启动 UDP 自动发现
            System.out.println("正在监听 UDP 广播 (等待 3 秒)...");
            ObservableList<Competition> discovered = FXCollections.observableArrayList();

            // 当发现新主机时打印出来
            discovered.addListener((ListChangeListener<Competition>) c -> {
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (Competition comp : c.getAddedSubList()) {
                            System.out.println(">>> [UDP 发现] 主机: " + comp.getHostAddress() + " | 比赛: " + comp.getName());
                        }
                    }
                }
            });

            networkService.startDiscovery(discovered);

            // 简单的倒计时，让用户有机会看到 UDP 结果
            Thread.sleep(3000);

            // 2. 获取 IP 输入
            System.out.print("\n请输入主机 IP 地址 (如果上方已发现，请直接输入 IP): ");
            String hostIp = scanner.nextLine().trim();

            if (hostIp.isEmpty()) {
                System.out.println("IP 不能为空！程序退出。");
                Platform.exit();
                return;
            }

            System.out.println("正在连接到 " + hostIp + " ...");

            // 3. 连接主机
            String myUsername = "ClientUser_01";
            networkService.connectToHost(hostIp, myUsername, (packet) -> {
                // 处理收到的包
                if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE) {
                    if (packet.isApproved()) {
                        System.out.println(">>> [连接成功] 已加入主机房间！");
                        // 连接成功 1 秒后，自动发送一条测试成绩
                        testSendScore(myUsername);
                    } else {
                        System.out.println(">>> [连接失败] 主机拒绝了加入请求 (或待审批)。");
                    }
                } else if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
                    System.out.println(">>> [数据同步] 收到全量数据更新。");
                    if (packet.getOfficialEventName() != null) {
                        System.out.println("    当前赛事: " + packet.getOfficialEventName());
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("[错误] 连接失败: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    // 构造并发送测试成绩
    private void testSendScore(String username) {
        new Thread(() -> {
            try {
                System.out.println("\n[测试] 准备发送模拟成绩...");
                Thread.sleep(1500); // 稍等一下再发

                // 使用适应您 ScoreEntry 定义的构造函数
                ScoreEntry dummyScore = new ScoreEntry(
                        ScoreEntry.Type.ALLIANCE, // Type
                        1,                        // Match Number
                        "Red",                    // Alliance
                        12345,                    // Team 1
                        67890,                    // Team 2
                        5,                        // Auto Artifacts
                        10,                       // TeleOp Artifacts
                        true,                     // Team 1 Sequence
                        false,                    // Team 2 Sequence
                        true,                     // Team 1 L2 Climb
                        false,                    // Team 2 L2 Climb
                        false,                    // T1 Ignored
                        false,                    // T2 Ignored
                        false,                    // T1 Broken
                        false,                    // T2 Broken
                        "[]",                     // Click Locations (JSON)
                        username                  // Submitter
                );

                // 显式设置为未同步 (虽然构造函数已默认设置)
                dummyScore.setSyncStatus(ScoreEntry.SyncStatus.UNSYNCED);

                boolean success = networkService.sendScoreToServer(dummyScore);

                if (success) {
                    System.out.println("[测试] 成绩已通过 HTTP 发送成功！");
                } else {
                    System.err.println("[测试] 成绩发送失败！");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (networkService != null) {
            networkService.stop();
        }
        System.out.println("NetworkService 已停止，程序退出。");
        System.exit(0);
    }
}