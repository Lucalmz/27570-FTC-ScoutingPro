package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
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
    private String hostingCompetitionName; // 记录当前主持的比赛名

    private NetworkService() {}
    // 在 NetworkService 类中添加
    private Runnable onMemberJoinCallback;

    public void setOnMemberJoinCallback(Runnable callback) {
        this.onMemberJoinCallback = callback;
    }

    // 在 ClientHandler 内部类中添加辅助方法
    private void notifyMembershipUpdate() {
        if (NetworkService.getInstance().onMemberJoinCallback != null) {
            // 必须在 JavaFX 线程执行 UI 更新
            Platform.runLater(NetworkService.getInstance().onMemberJoinCallback);
        }
    }
    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        stop();
        running = true;
        this.hostingCompetitionName = competition.getName();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(client, onScoreReceived);
                    connectedClients.add(handler);
                    handler.start();
                }
            } catch (IOException ignored) {}
        }).start();

        // UDP 广播保持不变...
        startUdpBeacon(competition);
    }

    private void startUdpBeacon(Competition comp) {
        new Thread(() -> {
            try (DatagramSocket beacon = new DatagramSocket()) {
                beacon.setBroadcast(true);
                String msg = "FTC_SCOUTER;" + comp.getName() + ";" + comp.getCreatorUsername();
                byte[] buf = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                while (running) {
                    beacon.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private final Consumer<ScoreEntry> onScoreReceived;
        private String clientUsername;

        ClientHandler(Socket socket, Consumer<ScoreEntry> onScoreReceived) {
            this.socket = socket;
            this.onScoreReceived = onScoreReceived;
        }

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
                    NetworkPacket packet = (NetworkPacket) in.readObject();
                    switch (packet.getType()) {
                        case JOIN_REQUEST:
                            System.out.println("收到加入请求: " + packet.getUsername()); // <--- 添加日志
                            this.clientUsername = packet.getUsername();
                            // 关键点：收到请求立即写入 Host 的数据库为 PENDING
                            DatabaseService.addMembership(clientUsername, hostingCompetitionName, Membership.Status.PENDING);
                            // 这里可以加一个通知给 Host UI 的机制，但目前 refreshLists 就能看到
                            break;
                        case SUBMIT_SCORE:
                            Platform.runLater(() -> onScoreReceived.accept(packet.getScoreEntry()));
                            break;
                    }
                }
            } catch (Exception e) {
                System.err.println("ClientHandler 异常: " + e.getMessage()); // <--- 关键：打印错误信息
                e.printStackTrace();
                stopClient();
            }
        }
    }

    // 当 Host 在界面点击批准时调用此方法通知 Client
    public void approveClient(String username) {
        boolean found = false;
        System.out.println("Host尝试批准用户: [" + username + "]"); // Debug日志

        for (ClientHandler handler : connectedClients) {
            // 打印当前连接的客户端，检查是否匹配
            System.out.println(" - 检查连接: [" + handler.clientUsername + "]");

            if (username.equals(handler.clientUsername)) {
                handler.sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                System.out.println("Host已发送批准指令给: " + username); // Debug日志
                found = true;
                return;
            }
        }

        if (!found) {
            System.err.println("错误: 找不到用户 [" + username + "] 的在线连接。");
            System.err.println("当前在线列表: " + connectedClients.size() + " 人");
        }
    }
    public void broadcastUpdateToClients(NetworkPacket updatePacket) {
        for (ClientHandler handler : connectedClients) {
            handler.sendPacket(updatePacket);
        }
    }

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        stop();
        running = true;
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(UDP_PORT);
                udpSocket.setSoTimeout(1000);
                byte[] buffer = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (msg.startsWith("FTC_SCOUTER;")) {
                            String[] parts = msg.split(";");
                            Competition d = new Competition(parts[1], parts[2]);
                            d.setHostAddress(packet.getAddress().getHostAddress());
                            Platform.runLater(() -> {
                                if (discoveredCompetitions.stream().noneMatch(c -> c.getName().equals(d.getName())))
                                    discoveredCompetitions.add(d);
                            });
                        }
                    } catch (SocketTimeoutException ignored) {}
                }
            } catch (IOException ignored) {} finally { if (udpSocket != null) udpSocket.close(); }
        }).start();
    }

    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        stop();
        running = true;
        clientSocket = new Socket(hostAddress, TCP_PORT);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());

        // 1. 发送加入请求
        outToServer.writeObject(new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername));
        outToServer.flush();

        // 2. 开启监听
        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                while (running) {
                    NetworkPacket p = (NetworkPacket) in.readObject();
                    System.out.println("Client收到包类型: " + p.getType()); // <--- Debug日志 1

                    // 必须用 Platform.runLater 包裹，否则不能操作界面
                    Platform.runLater(() -> {
                        System.out.println("正在UI线程处理包: " + p.getType()); // <--- Debug日志 2
                        try {
                            onPacketReceived.accept(p);
                        } catch (Exception e) {
                            System.err.println("Callback处理逻辑出错:"); // <--- 捕获 Callback 内部的逻辑错误
                            e.printStackTrace();
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("Client监听线程崩溃:"); // <--- 关键：不再忽略异常
                e.printStackTrace();
            }
        }).start();
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
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        if (udpSocket != null) udpSocket.close();
        connectedClients.clear();
    }
}