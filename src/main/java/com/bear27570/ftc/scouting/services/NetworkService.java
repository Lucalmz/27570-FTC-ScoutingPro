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

    public void setOfficialEventName(String name) {
        this.officialEventName = name;
    }

    // 修改为 public 以便单元测试可以独立实例化模拟 Client
    public NetworkService() {}

    public void setDataHandler(NetworkDataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    public void setOnMemberJoinCallback(Runnable callback) {
        this.onMemberJoinCallback = callback;
    }

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
                serverSocket = new ServerSocket(TCP_PORT);
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
        }, "TCP-Host-Thread").start();

        startUdpBeacon(competition);
    }

    private void startUdpBeacon(Competition comp) {
        new Thread(() -> {
            try (DatagramSocket beacon = new DatagramSocket()) {
                beacon.setBroadcast(true);
                String msg = "FTC_SCOUTER;" + comp.getName() + ";" + comp.getCreatorUsername();
                byte[] buf = msg.getBytes();

                while (running) {
                    // 1. 遍历所有真实网卡，对每个网卡专属的广播地址发送 (修复多网卡/虚拟机网卡丢包问题)
                    java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface networkInterface = interfaces.nextElement();
                        if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;

                        for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                            InetAddress broadcast = interfaceAddress.getBroadcast();
                            if (broadcast != null) {
                                try {
                                    DatagramPacket packet = new DatagramPacket(buf, buf.length, broadcast, UDP_PORT);
                                    beacon.send(packet);
                                } catch (Exception ignored) {} // 忽略不可达网卡
                            }
                        }
                    }

                    // 2. 依然发一份全局广播 255.255.255.255 作为保底
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                        beacon.send(packet);
                    } catch (Exception ignored) {}

                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                System.err.println("CRITICAL: UDP Beacon Error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "UDP-Beacon-Thread").start();
    }


    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private volatile Consumer<ScoreEntry> onScoreReceived;
        private String clientUsername;

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
                out.flush(); // ★ 修复点1：强制推送流协议头，防止两端互相死锁等待
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                while (running) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket packet) {
                        switch (packet.getType()) {
                            case JOIN_REQUEST:
                                this.clientUsername = packet.getUsername();
                                if(dataHandler != null) {
                                    dataHandler.addPendingMembership(clientUsername, hostingCompetitionName);
                                    if (dataHandler.isUserApprovedOrCreator(clientUsername, hostingCompetitionName)) {
                                        sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));

                                        // ★ 修复点2：使用 new java.util.ArrayList<>() 包装，防止 ObservableList 导致序列化失败静默断开
                                        sendPacket(new NetworkPacket(
                                                new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                                                new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                                                officialEventName));
                                    }
                                }
                                if (onMemberJoinCallback != null) {
                                    Platform.runLater(onMemberJoinCallback);
                                }
                                break;
                            case SUBMIT_SCORE:
                                if (onScoreReceived != null) {
                                    Platform.runLater(() -> onScoreReceived.accept(packet.getScoreEntry()));
                                }
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                stopClient();
            }
        }
    }

    public void approveClient(String username) {
        for (ClientHandler handler : connectedClients) {
            if (username.equals(handler.clientUsername)) {
                handler.sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                if(dataHandler != null) {
                    // ★ 修复点3：同上，防止主机在点击“批准”时向从机发送不支持序列化的 ObservableList 导致断开连接
                    handler.sendPacket(new NetworkPacket(
                            new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                            new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),
                            officialEventName));
                }
                return;
            }
        }
    }

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        this.running = true;
        new Thread(() -> {
            try {
                if (udpSocket != null && !udpSocket.isClosed()) { udpSocket.close(); }
                // 如果端口被占用，这里会抛出 BindException，必须打印出来
                udpSocket = new DatagramSocket(UDP_PORT);
                udpSocket.setSoTimeout(2000);
                byte[] buffer = new byte[1024];

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());

                        if (msg.startsWith("FTC_SCOUTER;")) {
                            String[] parts = msg.split(";");
                            if (parts.length >= 3) {
                                Competition d = new Competition(parts[1], parts[2]);
                                d.setHostAddress(packet.getAddress().getHostAddress());

                                Platform.runLater(() -> {
                                    if (discoveredCompetitions.stream().noneMatch(c -> c.getName().equals(d.getName()) && c.getHostAddress().equals(d.getHostAddress()))) {
                                        discoveredCompetitions.add(d);
                                        System.out.println("Discovered Match: " + d.getName() + " at " + d.getHostAddress());
                                    }
                                });
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                        // 正常的两秒超时，继续循环监听
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("UDP Receive Error: " + e.getMessage());
                        }
                        break;
                    }
                }
            } catch (java.net.BindException e) {
                System.err.println("CRITICAL ERROR: UDP Port " + UDP_PORT + " is already in use!");
                System.err.println("Another instance of this app might be running in the background. Please close it in Task Manager.");
            } catch (IOException e) {
                System.err.println("Failed to start UDP Discovery: " + e.getMessage());
            }
        }, "UDP-Discovery-Thread").start();
    }

    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        stop();
        this.running = true;
        clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(hostAddress, TCP_PORT), 5000);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());

        // ★ 优化点：建立连接时也加上 outToServer.flush(); 防御性编程确保安全
        outToServer.flush();

        outToServer.writeObject(new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername));
        outToServer.flush();
        outToServer.reset();

        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                while (running) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket p) {
                        Platform.runLater(() -> onPacketReceived.accept(p));
                    }
                }
            } catch (Exception e) {
                if(running) System.err.println("Connection to Host lost.");
            }
        }, "Client-Listen-Thread").start();
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