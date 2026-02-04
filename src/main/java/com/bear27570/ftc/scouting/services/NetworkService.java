package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class NetworkService {

    private static NetworkService instance;
    public static synchronized NetworkService getInstance() {
        if (instance == null) instance = new NetworkService();
        return instance;
    }

    public static final int TCP_PORT = 54321;
    public static final int UDP_PORT = 54322;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DatagramSocket udpSocket; // 客户端发现用的 Socket
    private ObjectOutputStream outToServer;

    private final CopyOnWriteArrayList<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private String hostingCompetitionName;
    private Runnable onMemberJoinCallback;

    private NetworkService() {}

    public void setOnMemberJoinCallback(Runnable callback) {
        this.onMemberJoinCallback = callback;
    }

    /**
     * 主机端：启动 TCP 服务器和 UDP 广播
     */
    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        // 如果已经在这个比赛的主持模式中，只更新回调
        if (running && competition.getName().equals(hostingCompetitionName) && serverSocket != null) {
            for (ClientHandler handler : connectedClients) {
                handler.setOnScoreReceived(onScoreReceived);
            }
            return;
        }

        // 如果是切换比赛，先停掉旧的
        stop();

        this.running = true; // 关键：标记运行中
        this.hostingCompetitionName = competition.getName();

        // 1. 启动 TCP 监听线程
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                System.out.println("TCP Server started on port " + TCP_PORT);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(client, onScoreReceived);
                        connectedClients.add(handler);
                        handler.start();
                    } catch (IOException e) {
                        if (running) System.err.println("TCP Accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }, "TCP-Server-Thread").start();

        // 2. 启动 UDP 广播（Beacon）
        startUdpBeacon(competition);
    }

    private void startUdpBeacon(Competition comp) {
        new Thread(() -> {
            try (DatagramSocket beacon = new DatagramSocket()) {
                beacon.setBroadcast(true);
                String msg = "FTC_SCOUTER;" + comp.getName() + ";" + comp.getCreatorUsername();
                byte[] buf = msg.getBytes();
                // 广播到全网段
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);

                System.out.println("UDP Beacon started for: " + comp.getName());
                while (running) {
                    beacon.send(packet);
                    Thread.sleep(2000); // 每2秒广播一次
                }
            } catch (Exception e) {
                System.err.println("UDP Beacon error: " + e.getMessage());
            }
        }, "UDP-Beacon-Thread").start();
    }

    /**
     * 客户端：启动 UDP 发现逻辑
     */
    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        // 如果正在搜索，不要重复启动
        if (running && udpSocket != null && !udpSocket.isClosed()) {
            return;
        }

        // 注意：搜索时不调用 stop()，否则会断开当前已经建立的连接
        this.running = true;

        new Thread(() -> {
            try {
                // 如果之前的发现 Socket 还没关，先关掉
                if (udpSocket != null) udpSocket.close();

                udpSocket = new DatagramSocket(UDP_PORT);
                udpSocket.setSoTimeout(2000);
                byte[] buffer = new byte[1024];

                System.out.println("UDP Discovery listening on port " + UDP_PORT);
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());

                        if (msg.startsWith("FTC_SCOUTER;")) {
                            String[] parts = msg.split(";");
                            String compName = parts[1];
                            String creator = parts[2];
                            String hostIp = packet.getAddress().getHostAddress();

                            Competition d = new Competition(compName, creator);
                            d.setHostAddress(hostIp);

                            Platform.runLater(() -> {
                                boolean exists = discoveredCompetitions.stream()
                                        .anyMatch(c -> c.getName().equals(d.getName()) && c.getHostAddress().equals(d.getHostAddress()));
                                if (!exists) {
                                    discoveredCompetitions.add(d);
                                    System.out.println("Found competition: " + compName + " at " + hostIp);
                                }
                            });
                        }
                    } catch (SocketTimeoutException ignored) {
                        // Timeout 是正常的，继续循环
                    } catch (IOException e) {
                        if (running) System.err.println("Discovery receive error: " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not start UDP Discovery: " + e.getMessage());
            } finally {
                if (udpSocket != null) udpSocket.close();
            }
        }, "UDP-Discovery-Thread").start();
    }

    /**
     * 客户端：连接到指定的 Host
     */
    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        // 连接前停止之前的“发现”或“旧连接”
        stop();

        this.running = true;
        clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(hostAddress, TCP_PORT), 5000);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());

        // 发送加入请求
        outToServer.writeObject(new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername));
        outToServer.flush();

        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                while (running) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket p) {
                        Platform.runLater(() -> onPacketReceived.accept(p));
                    }
                }
            } catch (Exception e) {
                System.err.println("Client Connection Lost: " + e.getMessage());
            }
        }, "Client-Listen-Thread").start();
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private Consumer<ScoreEntry> onScoreReceived;
        private String clientUsername;

        ClientHandler(Socket socket, Consumer<ScoreEntry> onScoreReceived) {
            this.socket = socket;
            this.onScoreReceived = onScoreReceived;
        }

        public void setOnScoreReceived(Consumer<ScoreEntry> onScoreReceived) { this.onScoreReceived = onScoreReceived; }

        public void sendPacket(NetworkPacket p) {
            try {
                out.writeObject(p);
                out.flush();
                out.reset();
            } catch (IOException e) { stopClient(); }
        }

        private void stopClient() {
            connectedClients.remove(this);
            try { socket.close(); } catch (IOException ignored) {}
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                while (running) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket packet) {
                        switch (packet.getType()) {

// 同时，在 ClientHandler 的 run 方法中，如果用户已经是 APPROVED，直接发数据
// 找到 ClientHandler 内部类的 run() 方法中的 JOIN_REQUEST 分支进行如下修改：
                            case JOIN_REQUEST:
                                this.clientUsername = packet.getUsername();
                                Membership.Status currentStatus = DatabaseService.getMembershipStatus(clientUsername, hostingCompetitionName);

                                if (currentStatus == null) {
                                    // 新申请
                                    DatabaseService.addMembership(clientUsername, hostingCompetitionName, Membership.Status.PENDING);
                                } else if (currentStatus == Membership.Status.APPROVED || currentStatus == Membership.Status.CREATOR) {
                                    // 如果已经是批准状态（比如断线重连），直接发通过包和数据
                                    sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                                    List<ScoreEntry> history = DatabaseService.getScoresForCompetition(hostingCompetitionName);
                                    List<TeamRanking> rankings = DatabaseService.calculateTeamRankings(hostingCompetitionName);
                                    sendPacket(new NetworkPacket(history, rankings));
                                }

                                if (onMemberJoinCallback != null) Platform.runLater(onMemberJoinCallback);
                                break;


                            case SUBMIT_SCORE:
                                if (onScoreReceived != null) Platform.runLater(() -> onScoreReceived.accept(packet.getScoreEntry()));
                                break;
                        }
                    }
                }
            } catch (Exception e) { stopClient(); }
        }
    }

    // 找到 NetworkService.java 中的 approveClient 方法并替换
    public void approveClient(String username) {
        for (ClientHandler handler : connectedClients) {
            if (username.equals(handler.clientUsername)) {
                // 1. 发送通过申请的响应
                handler.sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));

                // 2. 关键：立即给这个新批准的客户端同步一份当前数据，确保其 UI 解锁
                List<ScoreEntry> history = DatabaseService.getScoresForCompetition(hostingCompetitionName);
                List<TeamRanking> rankings = DatabaseService.calculateTeamRankings(hostingCompetitionName);
                handler.sendPacket(new NetworkPacket(history, rankings));

                System.out.println("Approved and synced data for user: " + username);
                return;
            }
        }
    }

    public void broadcastUpdateToClients(NetworkPacket updatePacket) {
        for (ClientHandler handler : connectedClients) handler.sendPacket(updatePacket);
    }

    public void sendScoreToServer(ScoreEntry scoreEntry) {
        if (outToServer != null) {
            try {
                outToServer.writeObject(new NetworkPacket(scoreEntry));
                outToServer.flush();
                outToServer.reset();
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public synchronized void stop() {
        this.running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        if (udpSocket != null) udpSocket.close();
        connectedClients.clear();
        serverSocket = null;
        clientSocket = null;
        udpSocket = null;
        outToServer = null;
    }
}
