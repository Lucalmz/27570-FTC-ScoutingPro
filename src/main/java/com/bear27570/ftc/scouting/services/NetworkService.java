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
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class NetworkService {

    private static NetworkService instance;

    public static synchronized NetworkService getInstance() {
        if (instance == null) instance = new NetworkService();
        return instance;
    }

    public static final int HTTP_PORT = 54321; // 复用原TCP端口用于HTTP/WS
    public static final int UDP_PORT = 54322;

    // === 主机端 (Host) 组件 ===
    private Javalin hostServer;
    // 使用泛型 WsContext 存储所有连接
    private final CopyOnWriteArrayList<WsContext> connectedWsClients = new CopyOnWriteArrayList<>();

    // === 从机端 (Client) 组件 ===
    private HttpClient httpClient;
    private WebSocket webSocketClient;
    private String currentHostIp; // 记录当前连接的主机IP，用于后续提交成绩

    // === 共享/网络发现组件 ===
    private DatagramSocket udpSocket;
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
            return; // 已经启动
        }

        stop();
        this.running = true;

        // 1. 启动 Javalin HTTP/WebSocket 服务器 (Javalin 7 写法)
        hostServer = Javalin.create(config -> {
            // [适配 Javalin 7] Banner 配置位置变更
            config.startup.showJavalinBanner = false;

            // [适配 Javalin 7] 修改 WebSocket 消息大小限制 (如果需要可以取消注释)
            // config.jetty.modifyWebSocketServletFactory(factory -> factory.setMaxTextMessageSize(5 * 1024 * 1024));

            //[适配 Javalin 7 核心变更] 路由必须在 create 阶段直接通过 config.routes 注册，不再使用 config.router.mount

            // 接口1：处理从机申请加入房间 (POST /api/join)
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

            // 接口2：接收从机提交的比赛成绩 (POST /api/score)
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

            // 接口3：WebSocket 全局数据广播通道 (WS /ws/updates)
            config.routes.ws("/ws/updates", ws -> {
                ws.onConnect(ctx -> {
                    connectedWsClients.add(ctx);
                    System.out.println("[网络调试] 从机 WebSocket 已建立连接: " + ctx.sessionId());

                    // 客户端连接成功后，主动推一次全量数据
                    if (dataHandler != null) {
                        NetworkPacket initialData = new NetworkPacket(
                                new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                                new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                                officialEventName);
                        ctx.send(gson.toJson(initialData));
                    }
                });

                ws.onClose(ctx -> {
                    // onClose 的 ctx 对象与 onConnect 的不同，必须用 sessionId 匹配移除
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

        // 2. 启动 UDP 广播
        startUdpBeacon(competition);
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

        // [修改建议] 避免调用 ctx.session().isOpen()，由于底层实现变化容易报错。
        // 最稳健的方式是直接 ctx.send() 并捕获异常，抛出异常说明连接已失效。
        connectedWsClients.removeIf(ctx -> {
            try {
                ctx.send(jsonPayload);
                return false; // 发送成功，保留连接
            } catch (Exception e) {
                return true;  // 发送异常（连接已断开），从此列表中移除
            }
        });
    }

    private void startUdpBeacon(Competition comp) {
        new Thread(() -> {
            try (DatagramSocket beacon = new DatagramSocket()) {
                beacon.setBroadcast(true);
                String msg = "FTC_SCOUTER;" + comp.getName() + ";" + comp.getCreatorUsername();
                byte[] buf = msg.getBytes();
                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(buf, buf.length, broadcastAddr, UDP_PORT);

                System.out.println("[网络调试] 主机 UDP 广播已启动...");
                while (running && !beacon.isClosed()) {
                    try {
                        beacon.send(packet);
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                System.err.println("UDP Beacon failed to start: " + e.getMessage());
            }
        }, "UDP-Beacon-Thread").start();
    }


    // =================================================================================
    //                               从机逻辑 (CLIENT)
    // =================================================================================

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        this.running = true;
        new Thread(() -> {
            try {
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.close();
                }
                DatagramSocket currentUdpSocket = new DatagramSocket(UDP_PORT);
                currentUdpSocket.setSoTimeout(2000);
                udpSocket = currentUdpSocket;
                byte[] buffer = new byte[1024];

                while (running && !currentUdpSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        currentUdpSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());

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
                System.err.println("UDP Discovery Port occupied.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "UDP-Discovery-Thread").start();
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

        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
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