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
                stopClient();
            }
        }
    }

    // 当 Host 在界面点击批准时调用此方法通知 Client
    public void approveClient(String username) {
        for (ClientHandler handler : connectedClients) {
            if (username.equals(handler.clientUsername)) {
                handler.sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                return;
            }
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
                    Platform.runLater(() -> onPacketReceived.accept(p));
                }
            } catch (Exception ignored) {}
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