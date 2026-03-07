// File: NetworkService.java
package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.services.network.NetworkDataHandler;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class NetworkService {

    private static NetworkService instance;

    public static synchronized NetworkService getInstance() {
        if (instance == null) instance = new NetworkService();
        return instance;
    }

    public static final int HTTP_PORT = 54321;
    public static final int UDP_PORT = 54322;
    // 使用 239.0.0.0/8 本地管理范围内的多播地址，27570 是你们的车号彩蛋
    public static final String MULTICAST_IP = "239.255.27.57";

    // === 主机端 (Host) 组件 ===
    private Javalin hostServer;
    private final CopyOnWriteArrayList<WsContext> connectedWsClients = new CopyOnWriteArrayList<>();

    // === 从机端 (Client) 组件 ===
    private HttpClient httpClient;
    private WebSocket webSocketClient;
    private String currentHostIp;

    // === 共享/网络发现组件 ===
    private MulticastSocket multicastSocket; // 改用 MulticastSocket
    private volatile boolean running = false;
    private String hostingCompetitionName;
    private Runnable onMemberJoinCallback;

    private NetworkDataHandler dataHandler;
    private String officialEventName = null;
    private final Gson gson = new Gson();

    public NetworkService() {
    }

    public void setDataHandler(NetworkDataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    public void setOfficialEventName(String name) {
        this.officialEventName = name;
    }

    public void setOnMemberJoinCallback(Runnable callback) {
        this.onMemberJoinCallback = callback;
    }

    // =================================================================================
    //                               主机逻辑 (HOST)
    // =================================================================================

    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        this.hostingCompetitionName = competition.getName();

        if (running && hostServer != null) {
            return;
        }

        stop();
        this.running = true;

        hostServer = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;

            config.routes.post("/api/join", ctx -> {
                try {
                    NetworkPacket req = gson.fromJson(ctx.body(), NetworkPacket.class);
                    String clientUsername = req.getUsername();
                    System.out.println("[网络调试] 收到 [" + clientUsername + "] 的 HTTP 加入请求");

                    if (dataHandler != null) {
                        dataHandler.ensureUserExists(clientUsername);

                        if (dataHandler.isUserApprovedOrCreator(clientUsername, hostingCompetitionName)) {
                            NetworkPacket resp = new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true);
                            ctx.result(gson.toJson(resp));
                            System.out.println("[网络调试] 用户 [" + clientUsername + "] 验证通过");
                        } else {
                            dataHandler.addPendingMembership(clientUsername, hostingCompetitionName);
                            if (onMemberJoinCallback != null) {
                                Platform.runLater(onMemberJoinCallback);
                            }
                            NetworkPacket resp = new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, false);
                            ctx.result(gson.toJson(resp));
                            System.out.println("[网络调试] 用户[" + clientUsername + "] 已加入待审批列表");
                        }
                    } else {
                        ctx.status(500).result("DataHandler is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    ctx.status(400).result("Bad Request");
                }
            });

            config.routes.post("/api/score", ctx -> {
                try {
                    NetworkPacket req = gson.fromJson(ctx.body(), NetworkPacket.class);
                    ScoreEntry score = req.getScoreEntry();
                    System.out.println("[网络调试] 收到来自[" + score.getSubmitter() + "] 提交的 Match " + score.getMatchNumber() + " 成绩");

                    if (onScoreReceived != null) {
                        Platform.runLater(() -> onScoreReceived.accept(score));
                    }
                    ctx.status(200).result("Score Received");
                } catch (Exception e) {
                    e.printStackTrace();
                    ctx.status(400).result("Invalid Score Data");
                }
            });

            config.routes.ws("/ws/updates", ws -> {
                ws.onConnect(ctx -> {
                    connectedWsClients.add(ctx);
                    System.out.println("[网络调试] 从机 WebSocket 已建立连接: " + ctx.sessionId());

                    if (dataHandler != null) {
                        NetworkPacket initialData = new NetworkPacket(
                                new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                                new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                                officialEventName);
                        ctx.send(gson.toJson(initialData));
                    }
                });

                ws.onClose(ctx -> {
                    connectedWsClients.removeIf(c -> c.sessionId().equals(ctx.sessionId()));
                    System.out.println("[网络调试] 从机 WebSocket 已断开连接: " + ctx.sessionId());
                });

                ws.onError(ctx -> {
                    connectedWsClients.removeIf(c -> c.sessionId().equals(ctx.sessionId()));
                    System.err.println("[网络调试] 从机 WebSocket 发生异常");
                });
            });

        }).start(HTTP_PORT);

        System.out.println("[网络调试] Javalin HTTP/WS 主机已启动，端口: " + HTTP_PORT);

        // 启动优雅的多播组发现
        startMulticastBeacon(competition);
    }

    public void approveClient(String username) {
        if (dataHandler == null) return;
        NetworkPacket fullData = new NetworkPacket(
                new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                officialEventName);
        broadcastUpdateToClients(fullData);
        System.out.println("[网络调试] 主机已批准 [" + username + "]，并向全网广播了最新数据包");
    }

    public void broadcastUpdateToClients(NetworkPacket updatePacket) {
        String jsonPayload = gson.toJson(updatePacket);
        connectedWsClients.removeIf(ctx -> {
            try {
                ctx.send(jsonPayload);
                return false;
            } catch (Exception e) {
                return true;
            }
        });
    }

    /**
     * 【核心改动：发送端多播穿透】
     * 遍历所有物理网卡，挨个向多播组发包，无视 Windows 默认路由表的限制
     */
    private void startMulticastBeacon(Competition comp) {
        new Thread(() -> {
            try (MulticastSocket beacon = new MulticastSocket()) {
                // TTL=1 意味着包绝对不会越过路由器，仅限本地物理线缆和当前交换机 (极其克制文明)
                beacon.setTimeToLive(1);
                InetAddress group = InetAddress.getByName(MULTICAST_IP);
                String msg = "FTC_SCOUTER;" + comp.getName() + ";" + comp.getCreatorUsername();
                byte[] buf = msg.getBytes(StandardCharsets.UTF_8);

                System.out.println("[网络调试] 主机 Multicast 多播引擎已启动 (智能多网卡穿透模式)...");

                while (running && !beacon.isClosed()) {
                    try {
                        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface ni = interfaces.nextElement();
                            // 过滤条件：排除回环地址(127.0.0.1)、未启用的网卡、不支持多播的网卡
                            if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) continue;

                            try {
                                // 强行指定此包从特定的网卡（如对拷线生成的虚拟以太网卡）发出去
                                beacon.setNetworkInterface(ni);
                                DatagramPacket packet = new DatagramPacket(buf, buf.length, group, UDP_PORT);
                                beacon.send(packet);
                            } catch (Exception ignored) {
                                // 有些虚拟网卡可能没有 IPv4 地址，忽略报错继续下一个
                            }
                        }
                        Thread.sleep(2000); // 依然保持 2 秒的心跳
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                System.err.println("Multicast Beacon failed to start: " + e.getMessage());
            }
        }, "Multicast-Beacon-Thread").start();
    }


    // =================================================================================
    //                               从机逻辑 (CLIENT)
    // =================================================================================

    /**
     * 【核心改动：接收端多网卡订阅】
     * 让电脑上所有的网卡都加入多播组，确保不管网线插在哪，都能收到包
     */
    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        this.running = true;
        new Thread(() -> {
            MulticastSocket socket = null;
            try {
                if (multicastSocket != null && !multicastSocket.isClosed()) {
                    multicastSocket.close();
                }

                socket = new MulticastSocket(UDP_PORT);
                socket.setSoTimeout(2000);

                InetAddress group = InetAddress.getByName(MULTICAST_IP);
                SocketAddress groupAddress = new InetSocketAddress(group, UDP_PORT);

                System.out.println("[网络调试] 从机 Multicast 侦听器正在绑定网卡...");

                // 遍历所有网卡，让每一个物理网卡都向交换机声明“我要加入 239.255.27.57 组”
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) continue;
                    try {
                        // 使用 Java 14+ 推荐的现代 API 加入多播组
                        socket.joinGroup(groupAddress, ni);
                        System.out.println(" - 成功绑定监听网卡: " + ni.getDisplayName());
                    } catch (Exception e) {
                        // 忽略不能绑定多播的特定网卡（如某些 VPN 虚拟网卡）
                    }
                }

                this.multicastSocket = socket;
                byte[] buffer = new byte[1024];

                while (running && !socket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                        if (msg.startsWith("FTC_SCOUTER;")) {
                            String[] parts = msg.split(";");
                            if (parts.length >= 3) {
                                Competition d = new Competition(parts[1], parts[2]);
                                d.setHostAddress(packet.getAddress().getHostAddress());

                                Platform.runLater(() -> {
                                    boolean exists = discoveredCompetitions.stream()
                                            .anyMatch(c -> c.getName().equals(d.getName()) && c.getHostAddress().equals(d.getHostAddress()));
                                    if (!exists) {
                                        discoveredCompetitions.add(d);
                                    }
                                });
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (IOException e) {
                        if (running) break;
                    }
                }
            } catch (BindException e) {
                System.err.println("Multicast Discovery Port occupied.");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close(); // 关闭 socket 会自动让底层网卡退出多播组，保持整洁
                }
            }
        }, "Multicast-Discovery-Thread").start();
    }

    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        stop();
        this.running = true;
        this.currentHostIp = hostAddress;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String httpUrl = "http://" + hostAddress + ":" + HTTP_PORT + "/api/join";
        String wsUrl = "ws://" + hostAddress + ":" + HTTP_PORT + "/ws/updates";

        System.out.println("[网络调试] 从机尝试 HTTP 连接主机: " + httpUrl);

        try {
            NetworkPacket joinReq = new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(httpUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(joinReq)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Host returned error HTTP code: " + response.statusCode());
            }

            NetworkPacket respPacket = gson.fromJson(response.body(), NetworkPacket.class);
            System.out.println("[网络调试] 从机收到 HTTP 响应，批准状态: " + respPacket.isApproved());

            if (respPacket.getType() == NetworkPacket.PacketType.JOIN_RESPONSE && respPacket.isApproved()) {
                Platform.runLater(() -> onPacketReceived.accept(respPacket));

                httpClient.newWebSocketBuilder()
                        .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                            StringBuilder partialMsg = new StringBuilder();

                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                partialMsg.append(data);
                                if (last) {
                                    try {
                                        NetworkPacket updatePacket = gson.fromJson(partialMsg.toString(), NetworkPacket.class);
                                        System.out.println("[网络调试] 从机通过 WebSocket 收到了主机的广播更新");
                                        Platform.runLater(() -> onPacketReceived.accept(updatePacket));
                                    } catch (Exception e) {
                                        System.err.println("[网络调试] 解析 WebSocket 广播 JSON 失败");
                                        e.printStackTrace();
                                    }
                                    partialMsg.setLength(0);
                                }
                                return WebSocket.Listener.super.onText(webSocket, data, last);
                            }

                            @Override
                            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                                System.out.println("[网络调试] 已经与主机的 WebSocket 断开连接");
                                return null;
                            }

                            @Override
                            public void onError(WebSocket webSocket, Throwable error) {
                                System.err.println("[网络调试] WebSocket 发生异常: " + error.getMessage());
                            }
                        }).thenAccept(ws -> {
                            NetworkService.this.webSocketClient = ws;
                            System.out.println("[网络调试] WebSocket 数据监听通道已建立！");
                        });

            } else {
                Platform.runLater(() -> onPacketReceived.accept(respPacket));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to connect to host via HTTP", e);
        }
    }

    public boolean sendScoreToServer(ScoreEntry scoreEntry) {
        if (httpClient == null || currentHostIp == null) {
            System.err.println("[网络调试] HttpClient 未初始化或不知晓主机IP，进入离线保存模式");
            return false;
        }

        try {
            String targetUrl = "http://" + currentHostIp + ":" + HTTP_PORT + "/api/score";
            NetworkPacket packet = new NetworkPacket(scoreEntry);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(packet)))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                System.out.println("[网络调试] 从机成绩 HTTP 提交成功！");
                return true;
            } else {
                System.err.println("[网络调试] 从机成绩 HTTP 提交失败，状态码: " + res.statusCode());
                return false;
            }
        } catch (Exception e) {
            System.err.println("[网络调试] 从机发送成绩网络异常: " + e.getMessage());
            return false;
        }
    }

    // =================================================================================
    //                               生命周期管理
    // =================================================================================

    public synchronized void stop() {
        this.running = false;

        if (hostServer != null) {
            hostServer.stop();
            hostServer = null;
        }

        if (multicastSocket != null) {
            multicastSocket.close();
            multicastSocket = null;
        }

        if (webSocketClient != null) {
            webSocketClient.sendClose(WebSocket.NORMAL_CLOSURE, "Application closing");
            webSocketClient = null;
        }
        httpClient = null;
        connectedWsClients.clear();

        System.out.println("[网络调试] NetworkService 资源已完全清理");
    }
}