// File: src/test/java/com/bear27570/ftc/scouting/ManualNetworkTest.java
package com.bear27570.ftc.scouting;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.*;
import com.bear27570.ftc.scouting.repository.impl.*;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.services.domain.RankingService;
import com.bear27570.ftc.scouting.services.domain.impl.MatchDataServiceImpl;
import com.bear27570.ftc.scouting.services.network.DefaultNetworkDataHandler;
import com.bear27570.ftc.scouting.services.network.NetworkDataHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * FTC Scouting Pro - 全真局域网集群测试沙盒
 *
 * 使用方法 (在 IDEA 中允许运行多个实例)：
 * 1. 运行实例 A -> 输入 'host' -> 记录显示的 IP。
 * 2. 运行实例 B -> 输入 'client' -> 输入不同的用户名 -> 测试发送数据。
 * 3. 运行实例 C -> 输入 'client' -> 模拟多并发。
 */
public class ManualNetworkTest extends Application {

    private NetworkService networkService;
    private final Scanner scanner = new Scanner(System.in);

    // 真实的底层服务引用，用于测试查库
    private MatchDataService matchDataService;
    private MembershipRepository membershipRepository;
    private String competitionName = "Simulated_Championship_2026";
    private String currentUsername;

    public static class Launcher {
        public static void main(String[] args) {
            Application.launch(ManualNetworkTest.class, args);
        }
    }

    public static void main(String[] args) {
        Launcher.main(args);
    }

    @Override
    public void start(Stage primaryStage) {
        System.out.println("==================================================");
        System.out.println(" 🚀 FTC Scouting Pro - True Isolated Cluster Test");
        System.out.println("==================================================");

        networkService = NetworkService.getInstance();
        Platform.setImplicitExit(false);

        new Thread(this::consoleLoop).start();
    }

    private void consoleLoop() {
        try {
            System.out.print("👉 请选择启动模式 (host / client): ");
            String mode = scanner.nextLine().trim().toLowerCase();

            System.out.print("👉 请输入当前测试节点的用户名 (如 LeadScout, Scouter_A): ");
            currentUsername = scanner.nextLine().trim();

            // ★ 核心隔离：为每个进程分配完全独立的内存数据库
            String dbUrl = "jdbc:h2:mem:test_db_" + UUID.randomUUID().toString().substring(0,8) + ";DB_CLOSE_DELAY=-1";
            System.out.println("💽 初始化独立内存数据库: " + dbUrl);
            DatabaseManager.initialize(dbUrl);

            // 初始化真实的 Repository 和 Service
            UserRepository userRepo = new UserRepositoryJdbiImpl();
            membershipRepository = new MembershipRepositoryJdbiImpl();
            CompetitionRepository compRepo = new CompetitionRepositoryJdbiImpl();
            ScoreRepository scoreRepo = new ScoreRepositoryJdbiImpl();
            PenaltyRepository penaltyRepo = new PenaltyRepositoryJdbiImpl();

            matchDataService = new MatchDataServiceImpl(scoreRepo, penaltyRepo);
            RankingService rankingService = new RankingService(scoreRepo, penaltyRepo, compRepo);

            // 组装真实的 NetworkDataHandler
            NetworkDataHandler realHandler = new DefaultNetworkDataHandler(membershipRepository, userRepo, matchDataService, rankingService);
            networkService.setDataHandler(realHandler);
            networkService.setOfficialEventName("FTC Asia Pacific 2026");

            if ("host".equals(mode)) {
                // 主机需要先在本地数据库创建比赛
                compRepo.create(competitionName, currentUsername, "total");
                membershipRepository.addMembership(currentUsername, competitionName, com.bear27570.ftc.scouting.models.Membership.Status.CREATOR);
                runAsHost();
            } else {
                runAsClient();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    //                  主机逻辑
    // ==========================================
    private void runAsHost() {
        try {
            System.out.println("\n👑 [主机模式] 初始化中...");

            Competition comp = new Competition(competitionName, currentUsername);
            networkService.startHost(comp, (score) -> {
                System.out.println("\n📥 [主机] 收到来自 " + score.getSubmitter() + " 的成绩提交 (Match " + score.getMatchNumber() + ")!");
                // 模拟业务层的落库操作
                score.setSyncStatus(ScoreEntry.SyncStatus.SYNCED);
                matchDataService.submitScore(competitionName, score);
                System.out.println("   ✅ 数据已落入主机本地内存数据库。");

                // 触发全局广播
                networkService.broadcastUpdateToClients(new NetworkPacket(
                        matchDataService.getHistory(competitionName),
                        null, "FTC Asia Pacific 2026"
                ));
            });

            // 监听加入请求
            networkService.setOnMemberJoinCallback(() -> {
                System.out.println("\n🔔 [主机通知] 有新的侦查员申请加入！请使用 'approve <用户名>' 命令批准。");
            });

            String localIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println("-----------------------------------------");
            System.out.println("✅ 主机已启动！(IP: " + localIp + ")");
            System.out.println("可用命令: 'status' (查看库中数据), 'approve <user>' (批准加入), 'exit'");
            System.out.println("-----------------------------------------");

            while (scanner.hasNextLine()) {
                String cmd = scanner.nextLine().trim();
                if (cmd.equals("exit")) break;
                if (cmd.equals("status")) {
                    List<ScoreEntry> scores = matchDataService.getHistory(competitionName);
                    System.out.println("📊 主机当前数据库记录数: " + scores.size());
                } else if (cmd.startsWith("approve ")) {
                    String targetUser = cmd.substring(8).trim();
                    membershipRepository.updateMembershipStatus(targetUser, competitionName, com.bear27570.ftc.scouting.models.Membership.Status.APPROVED);
                    networkService.approveClient(targetUser);
                    System.out.println("✅ 已批准 " + targetUser + " 加入赛事！并已广播全量数据。");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Platform.exit();
        }
    }

    // ==========================================
    //                  从机逻辑
    // ==========================================
    private void runAsClient() {
        try {
            System.out.println("\n💻 [从机模式] 准备连接...");
            System.out.println("📡 正在监听 UDP 广播自动发现主机 (等待 3 秒)...");

            ObservableList<Competition> discovered = FXCollections.observableArrayList();
            discovered.addListener((ListChangeListener<Competition>) c -> {
                while (c.next()) {
                    if (c.wasAdded()) {
                        for (Competition comp : c.getAddedSubList()) {
                            System.out.println(">>> 🎯 [UDP 发现] 找到主机: " + comp.getHostAddress() + " | 比赛: " + comp.getName());
                        }
                    }
                }
            });

            networkService.startDiscovery(discovered);
            Thread.sleep(3000);

            System.out.print("\n👉 请输入要连接的主机 IP 地址: ");
            String hostIp = scanner.nextLine().trim();
            if (hostIp.isEmpty()) return;

            System.out.println("🔗 正在连接到 " + hostIp + " ...");

            networkService.connectToHost(hostIp, currentUsername, (packet) -> {
                if (packet.getType() == NetworkPacket.PacketType.JOIN_RESPONSE) {
                    if (packet.isApproved()) {
                        System.out.println("✅ [连接成功] 已加入主机房间！");
                    } else {
                        System.out.println("⏳ [待审批] 主机已收到请求，请等待 Host 审批...");
                    }
                } else if (packet.getType() == NetworkPacket.PacketType.UPDATE_DATA) {
                    System.out.println("\n🔄 [数据同步] 收到主机广播的全量数据更新！(包含 " + packet.getScoreHistory().size() + " 条记录)");
                    // 模拟从机同步本地库
                    matchDataService.syncWithHostData(competitionName, packet.getScoreHistory());
                }
            });

            System.out.println("可用命令: 'send' (发送一条测试数据), 'status' (查看本地同步的数据), 'exit'");

            int matchCounter = 1;
            while (scanner.hasNextLine()) {
                String cmd = scanner.nextLine().trim();
                if (cmd.equals("exit")) break;
                if (cmd.equals("status")) {
                    List<ScoreEntry> scores = matchDataService.getHistory(competitionName);
                    System.out.println("📊 从机本地数据库记录数: " + scores.size());
                } else if (cmd.equals("send")) {
                    ScoreEntry dummyScore = new ScoreEntry(
                            ScoreEntry.Type.ALLIANCE, matchCounter++, "Red", 1234, 5678,
                            15, 10, "NEAR", "FAR", "R1 R2", "NONE",
                            25, true, false, true, false, false, false, false, false,
                            "1:300.0,250.0,0,0;", currentUsername
                    );

                    networkService.sendScoreToServer(dummyScore).thenAccept(success -> {
                        if (success) {
                            System.out.println("🚀 成绩已成功推送到主机！");
                        } else {
                            System.out.println("❌ 成绩推送失败，主机可能掉线。");
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Platform.exit();
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (networkService != null) networkService.stop();
        DatabaseManager.close(); // 清理连接池
        System.out.println("🛑 测试结束，资源已安全释放。");
        System.exit(0);
    }
}