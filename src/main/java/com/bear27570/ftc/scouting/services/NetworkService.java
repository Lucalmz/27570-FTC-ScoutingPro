// File: NetworkService.java
package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.*;
import com.bear27570.ftc.scouting.services.network.NetworkDataHandler;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
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
    private DatagramSocket udpSocket;
    private ObjectOutputStream outToServer;

    private final CopyOnWriteArrayList<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private String hostingCompetitionName;
    private Runnable onMemberJoinCallback;

    private NetworkDataHandler dataHandler;
    private String officialEventName = null;

    public NetworkService() {}

    public void setDataHandler(NetworkDataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    public void setOfficialEventName(String name) {
        this.officialEventName = name;
    }

    public void setOnMemberJoinCallback(Runnable callback) {
        this.onMemberJoinCallback = callback;
    }

    // ==================== 主机逻辑 (Host) ====================

    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        this.hostingCompetitionName = competition.getName();

        if (running && serverSocket != null) {
            for (ClientHandler handler : connectedClients) {
                handler.setOnScoreReceived(onScoreReceived);
            }
            return;
        }

        stop();
        this.running = true;

        new Thread(() -> {
            try {
                ServerSocket currentServer = new ServerSocket(TCP_PORT);
                serverSocket = currentServer;
                System.out.println("[网络调试] 主机 TCP 服务已启动，端口: " + TCP_PORT);
                while (running && !currentServer.isClosed()) {
                    try {
                        Socket client = currentServer.accept();
                        System.out.println("[网络调试] 收到新连接: " + client.getInetAddress().getHostAddress());
                        ClientHandler handler = new ClientHandler(client, onScoreReceived);
                        connectedClients.add(handler);
                        handler.start();
                    } catch (IOException e) {
                        if (running && !currentServer.isClosed()) System.err.println("TCP Accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }, "TCP-Host-Thread").start();

        startUdpBeacon(competition);
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

    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private volatile Consumer<ScoreEntry> onScoreReceived;
        private String clientUsername = "未知用户";

        ClientHandler(Socket socket, Consumer<ScoreEntry> onScoreReceived) {
            this.socket = socket;
            this.onScoreReceived = onScoreReceived;
        }

        public void setOnScoreReceived(Consumer<ScoreEntry> callback) {
            this.onScoreReceived = callback;
        }

        public void sendPacket(NetworkPacket p) {
            if (out == null) return;
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
                out.flush();
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                while (running && !socket.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket packet) {
                        System.out.println("[网络调试] 主机收到数据包: " + packet.getType());

                        switch (packet.getType()) {

                            case JOIN_REQUEST:
                                this.clientUsername = packet.getUsername();
                                System.out.println("[网络调试] 正在处理 [" + clientUsername + "] 的加入请求...");

                                if (dataHandler != null) {
                                    try {
                                        dataHandler.ensureUserExists(clientUsername);

                                        // ★★★ 核心修复开始：判断用户是否以前已经获批过了 ★★★
                                        if (dataHandler.isUserApprovedOrCreator(clientUsername, hostingCompetitionName)) {
                                            System.out.println("[网络调试] 用户 [" + clientUsername + "] 已批准，直接恢复连接通行");
                                            sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                                            sendPacket(new NetworkPacket(
                                                    new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                                                    new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                                                    officialEventName));
                                        } else {
                                            // 是个真正的新人，老老实实进 Pending 列表等待批准
                                            dataHandler.addPendingMembership(clientUsername, hostingCompetitionName);
                                            System.out.println("[网络调试] 成功将[" + clientUsername + "] 写入数据库 Pending 列表");
                                            if (onMemberJoinCallback != null) {
                                                Platform.runLater(onMemberJoinCallback);
                                                System.out.println("[网络调试] 触发了主机 UI 的 MemberJoinCallback 刷新");
                                            } else {
                                                System.out.println("[网络调试] 提示：主机的 onMemberJoinCallback 为空，请手动刷新。");
                                            }
                                        }
                                    } catch (Exception dbEx) {
                                        System.err.println("[网络调试] 数据库操作失败: " + dbEx.getMessage());
                                        dbEx.printStackTrace();
                                    }
                                }else {
                                    System.err.println("[网络调试] 严重错误：主机的 dataHandler 为 NULL！请检查 MainApplication.init() 逻辑。");
                                }
                                break;

                            case SUBMIT_SCORE:
                                System.out.println("[网络调试] 收到 [" + clientUsername + "] 提交的成绩");
                                if (onScoreReceived != null) {
                                    Platform.runLater(() -> onScoreReceived.accept(packet.getScoreEntry()));
                                }
                                break;
                        }
                    }
                }
            } catch (EOFException e) {
                System.out.println("[网络调试] 从机 [" + clientUsername + "] 已断开连接。");
                stopClient();
            } catch (Exception e) {
                System.err.println("[网络调试] 主机处理 [" + clientUsername + "] 的网络流时发生严重崩溃:");
                e.printStackTrace();
                stopClient();
            }
        }
    }

    public void approveClient(String username) {
        for (ClientHandler handler : connectedClients) {
            if (username.equals(handler.clientUsername)) {
                handler.sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                if(dataHandler != null) {
                    handler.sendPacket(new NetworkPacket(
                            new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                            new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                            officialEventName));
                }
                System.out.println("[网络调试] 主机已批准并下发数据给: " + username);
                return;
            }
        }
    }

    // ==================== 从机逻辑 (Client) ====================

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        this.running = true;
        new Thread(() -> {
            try {
                if (udpSocket != null && !udpSocket.isClosed()) { udpSocket.close(); }
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
        clientSocket = new Socket();

        try {
            System.out.println("[网络调试] 从机尝试连接主机: " + hostAddress);
            clientSocket.connect(new InetSocketAddress(hostAddress, TCP_PORT), 5000);
            outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
            outToServer.flush();

            System.out.println("[网络调试] 连接成功！发送 JOIN_REQUEST...");
            outToServer.writeObject(new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername));
            outToServer.flush();
            outToServer.reset();

            Socket currentClientSocket = clientSocket;
            new Thread(() -> {
                try (ObjectInputStream in = new ObjectInputStream(currentClientSocket.getInputStream())) {
                    while (running && !currentClientSocket.isClosed()) {
                        Object obj = in.readObject();
                        if (obj instanceof NetworkPacket p) {
                            System.out.println("[网络调试] 从机收到响应: " + p.getType());
                            Platform.runLater(() -> onPacketReceived.accept(p));
                        }
                    }
                } catch (EOFException e) {
                    System.out.println("[网络调试] 与主机的连接已断开 (对方已关闭)");
                } catch (Exception e) {
                    if(running && !currentClientSocket.isClosed()) {
                        System.err.println("[网络调试] 从机读取主机数据报错:");
                        e.printStackTrace();
                    }
                }
            }, "Client-Listen-Thread").start();
        } catch (IOException e) {
            this.running = false;
            clientSocket = null;
            throw e;
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
                System.out.println("[网络调试] 从机已发送成绩数据");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public synchronized void stop() {
        this.running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        if (udpSocket != null) udpSocket.close();

        for (ClientHandler client : connectedClients) {
            try { client.socket.close(); } catch (Exception ignored) {}
        }
        connectedClients.clear();

        serverSocket = null;
        clientSocket = null;
        udpSocket = null;
        outToServer = null;
    }
}