// File: src/main/java/com/bear27570/ftc/scouting/services/NetworkService.java
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

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class NetworkService {
    public static class UdpBeaconData {
        public String magic = "FTC_SCOUTER";
        public String compName;
        public String creator;
        public UdpBeaconData(String compName, String creator) {
            this.compName = compName;
            this.creator = creator;
        }
    }

    public static final int HTTP_PORT = 54321;
    public static final int UDP_PORT = 54322;
    public static final String MULTICAST_IP = "239.255.27.57";
    final Gson gson = new Gson();

    private static NetworkService instance;
    public static synchronized NetworkService getInstance() {
        if (instance == null) instance = new NetworkService();
        return instance;
    }

    volatile boolean running = false;
    NetworkDataHandler dataHandler;
    String hostingCompetitionName;
    Runnable onMemberJoinCallback;
    String officialEventName = null;

    private final HostManager hostManager = new HostManager(this);
    private final ClientManager clientManager = new ClientManager(this);
    private final UdpManager udpManager = new UdpManager(this);

    public void setDataHandler(NetworkDataHandler dataHandler) { this.dataHandler = dataHandler; }
    public void setOfficialEventName(String name) { this.officialEventName = name; }
    public void setOnMemberJoinCallback(Runnable callback) { this.onMemberJoinCallback = callback; }

    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        this.hostingCompetitionName = competition.getName();
        if (running) return;
        stop();
        this.running = true;
        hostServer = hostManager.start(onScoreReceived);
        udpManager.startBeacon(competition);
    }

    public void approveClient(String username) {
        if (dataHandler == null) return;
        // 核心逻辑：只要主机点击了批准，立刻向所有连在 WebSocket 上的从机（包括正在等待室里的）广播最新数据
        broadcastUpdateToClients(new NetworkPacket(
                new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                officialEventName));
    }

    public void broadcastUpdateToClients(NetworkPacket updatePacket) {
        hostManager.broadcast(updatePacket);
    }

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        this.running = true;
        udpManager.startDiscovery(discoveredCompetitions);
    }

    public synchronized CompletableFuture<Boolean> connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) {
        stop();
        this.running = true;
        return clientManager.connect(hostAddress, myUsername, onPacketReceived);
    }

    public CompletableFuture<Boolean> sendScoreToServer(ScoreEntry scoreEntry) {
        return clientManager.sendScore(scoreEntry);
    }

    Javalin hostServer;
    MulticastSocket multicastSocket;
    WebSocket webSocketClient;
    Thread beaconThread;

    public synchronized void stop() {
        this.running = false;
        if (hostServer != null) { hostServer.stop(); hostServer = null; }
        if (multicastSocket != null) { multicastSocket.close(); multicastSocket = null; }
        if(beaconThread != null){
            beaconThread.interrupt();
            beaconThread = null;
        }
        if (webSocketClient != null) {
            try {
                webSocketClient.sendClose(WebSocket.NORMAL_CLOSURE, "User left waiting room or logged out");
            } catch (Exception ignored) {}
            webSocketClient = null;
        }
        clientManager.httpClient = null;
        hostManager.connectedWsClients.clear();
    }
}

// ----------------------- Host Manager -----------------------

class HostManager {
    private final NetworkService core;
    final CopyOnWriteArrayList<WsContext> connectedWsClients = new CopyOnWriteArrayList<>();

    HostManager(NetworkService core) { this.core = core; }

    Javalin start(Consumer<ScoreEntry> onScoreReceived) {
        return Javalin.create(config -> {
            config.startup.showJavalinBanner = false;

            // 1. HTTP 握手接口
            config.routes.post("/api/join", ctx -> {
                try {
                    NetworkPacket req = core.gson.fromJson(ctx.body(), NetworkPacket.class);
                    if (core.dataHandler != null) {
                        core.dataHandler.ensureUserExists(req.getUsername());
                        if (core.dataHandler.isUserApprovedOrCreator(req.getUsername(), core.hostingCompetitionName)) {
                            ctx.result(core.gson.toJson(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true)));
                        } else {
                            // 记录待审批，触发 UI 刷新
                            core.dataHandler.addPendingMembership(req.getUsername(), core.hostingCompetitionName);
                            if (core.onMemberJoinCallback != null) Platform.runLater(core.onMemberJoinCallback);
                            ctx.result(core.gson.toJson(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, false)));
                        }
                    }
                } catch (Exception e) { ctx.status(400).result("Bad Request"); }
            });

            // 2. HTTP 成绩提交接口
            config.routes.post("/api/score", ctx -> {
                try {
                    NetworkPacket req = core.gson.fromJson(ctx.body(), NetworkPacket.class);
                    if (onScoreReceived != null) Platform.runLater(() -> onScoreReceived.accept(req.getScoreEntry()));
                    ctx.status(200).result("Score Received");
                } catch (Exception e) { ctx.status(400).result("Invalid Score"); }
            });

            // 3. WebSocket 数据推送与等待室管道
            config.routes.ws("/ws/updates", ws -> {
                ws.onConnect(ctx -> {
                    // 修复 1：Javalin Java 版中 session 是公共字段，且超时接收毫秒(long)
                    ctx.session.setIdleTimeout(Duration.ofMinutes(60));

                    // 修复 2：去除 core. 前缀，直接使用本类的 connectedWsClients
                    connectedWsClients.add(ctx);
                    String user = ctx.queryParam("user");

                    if (core.dataHandler != null && user != null) {
                        if (core.dataHandler.isUserApprovedOrCreator(user, core.hostingCompetitionName)) {
                            ctx.send(core.gson.toJson(new NetworkPacket(
                                    new java.util.ArrayList<>(core.dataHandler.getScores(core.hostingCompetitionName)),
                                    new java.util.ArrayList<>(core.dataHandler.getRankings(core.hostingCompetitionName)),
                                    core.officialEventName)));
                        }
                    }
                });

                // 修复 3：去除 core. 前缀
                ws.onClose(ctx -> connectedWsClients.removeIf(c -> c.sessionId().equals(ctx.sessionId())));
                ws.onError(ctx -> connectedWsClients.removeIf(c -> c.sessionId().equals(ctx.sessionId())));
            });
        }).start(NetworkService.HTTP_PORT);
    }

    void broadcast(NetworkPacket updatePacket) {
        String jsonPayload = core.gson.toJson(updatePacket);
        connectedWsClients.removeIf(ctx -> {
            try {
                ctx.send(jsonPayload);
                return false; // 发送成功，保留在列表中
            } catch (Exception e) {
                return true;  // 发送失败（断线了），从列表中移除
            }
        });
    }
}

// ----------------------- Client Manager -----------------------

class ClientManager {
    private final NetworkService core;
    HttpClient httpClient;
    private String currentHostIp;

    ClientManager(NetworkService core) { this.core = core; }

    CompletableFuture<Boolean> connect(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) {
        this.currentHostIp = hostAddress;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        String httpUrl = "http://" + hostAddress + ":" + NetworkService.HTTP_PORT + "/api/join";
        NetworkPacket joinReq = new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(httpUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(core.gson.toJson(joinReq)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) throw new RuntimeException("Host HTTP " + response.statusCode());

                    // 解析主机的 HTTP 响应（告知当前是已批准还是待审批）
                    NetworkPacket respPacket = core.gson.fromJson(response.body(), NetworkPacket.class);
                    Platform.runLater(() -> onPacketReceived.accept(respPacket));

                    // 无论主机是否同意，只要 HTTP 连通了，立马建立 WebSocket 进入等待室蹲守
                    String wsUrl = "ws://" + hostAddress + ":" + NetworkService.HTTP_PORT + "/ws/updates?user=" + myUsername;
                    return httpClient.newWebSocketBuilder()
                            .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                                StringBuilder partialMsg = new StringBuilder();
                                @Override
                                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                                    partialMsg.append(data);
                                    if (last) {
                                        try {
                                            NetworkPacket p = core.gson.fromJson(partialMsg.toString(), NetworkPacket.class);
                                            Platform.runLater(() -> onPacketReceived.accept(p));
                                        } catch (Exception e) { e.printStackTrace(); }
                                        partialMsg.setLength(0);
                                    }
                                    return WebSocket.Listener.super.onText(ws, data, last);
                                }
                            }).thenApply(ws -> {
                                core.webSocketClient = ws;
                                return respPacket.isApproved();
                            });
                });
    }

    CompletableFuture<Boolean> sendScore(ScoreEntry scoreEntry) {
        if (httpClient == null || currentHostIp == null) return CompletableFuture.completedFuture(false);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://" + currentHostIp + ":" + NetworkService.HTTP_PORT + "/api/score"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(core.gson.toJson(new NetworkPacket(scoreEntry))))
                .build();

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> res.statusCode() == 200)
                .exceptionally(ex -> false);
    }
}

// ----------------------- UDP Manager -----------------------

class UdpManager {
    private final NetworkService core;
    UdpManager(NetworkService core) { this.core = core; }

    void startBeacon(Competition comp) {
        core.beaconThread = new Thread(() -> {
            try (MulticastSocket beacon = new MulticastSocket()) {
                beacon.setTimeToLive(1);
                InetAddress group = InetAddress.getByName(NetworkService.MULTICAST_IP);
                byte[] buf = core.gson.toJson(new NetworkService.UdpBeaconData(comp.getName(), comp.getCreatorUsername())).getBytes(StandardCharsets.UTF_8);

                while (core.running && !beacon.isClosed()) {
                    try {
                        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface ni = interfaces.nextElement();
                            if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) continue;
                            try {
                                beacon.setNetworkInterface(ni);
                                beacon.send(new DatagramPacket(buf, buf.length, group, NetworkService.UDP_PORT));
                            } catch (Exception ignored) {}
                        }
                        Thread.sleep(2000); // 每 2 秒广播一次自己
                    } catch (Exception e) { if (core.running) e.printStackTrace(); }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }, "Multicast-Beacon-Thread");
        core.beaconThread.start();
    }

    void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        new Thread(() -> {
            try {
                if (core.multicastSocket != null && !core.multicastSocket.isClosed()) core.multicastSocket.close();
                core.multicastSocket = new MulticastSocket(NetworkService.UDP_PORT);
                core.multicastSocket.setSoTimeout(2000);
                InetAddress group = InetAddress.getByName(NetworkService.MULTICAST_IP);
                SocketAddress groupAddress = new InetSocketAddress(group, NetworkService.UDP_PORT);

                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.isLoopback() && ni.isUp() && ni.supportsMulticast()) {
                        try { core.multicastSocket.joinGroup(groupAddress, ni); } catch (Exception ignored) {}
                    }
                }
                byte[] buffer = new byte[1024];

                while (core.running && !core.multicastSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        core.multicastSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                        if (msg.contains("FTC_SCOUTER")) {
                            try {
                                NetworkService.UdpBeaconData beacon = core.gson.fromJson(msg, NetworkService.UdpBeaconData.class);
                                if ("FTC_SCOUTER".equals(beacon.magic)) {
                                    Competition d = new Competition(beacon.compName, beacon.creator);
                                    d.setHostAddress(packet.getAddress().getHostAddress());

                                    Platform.runLater(() -> {
                                        // 避免重复添加同一个主机
                                        if (discoveredCompetitions.stream().noneMatch(c ->
                                                c.getName().equals(d.getName()) && c.getHostAddress().equals(d.getHostAddress()))) {
                                            discoveredCompetitions.add(d);
                                        }
                                    });
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (SocketTimeoutException ignored) {}
                    catch (Exception e) { if (core.running) break; }
                }
            } catch (Exception e) { e.printStackTrace(); }
            finally {
                if (core.multicastSocket != null && !core.multicastSocket.isClosed()) {
                    core.multicastSocket.close();
                }
            }
        }, "Multicast-Discovery-Thread").start();
    }
}