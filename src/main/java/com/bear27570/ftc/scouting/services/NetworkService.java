package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.*;
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

    private NetworkService() {}

    public void setOnMemberJoinCallback(Runnable callback) {
        this.onMemberJoinCallback = callback;
    }

    /**
     * 主机：启动或更新 Server 状态
     */
    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        this.hostingCompetitionName = competition.getName();

        // 健壮性：如果已经在运行，不重启 ServerSocket，但更新回调
        if (running && serverSocket != null) {
            for (ClientHandler handler : connectedClients) {
                handler.setOnScoreReceived(onScoreReceived);
            }
            return;
        }

        stop(); // 只有彻底没运行才重头启动
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
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                while (running) {
                    beacon.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {}
        }).start();
    }

    /**
     * 内部类：处理每个从机连接
     */
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
                out.reset(); // 重要：防止对象缓存
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
                            case JOIN_REQUEST:
                                this.clientUsername = packet.getUsername();
                                DatabaseService.addMembership(clientUsername, hostingCompetitionName, Membership.Status.PENDING);

                                // 如果该用户已经是批准状态，直接发通过响应
                                Membership.Status stat = DatabaseService.getMembershipStatus(clientUsername, hostingCompetitionName);
                                if (stat == Membership.Status.APPROVED || stat == Membership.Status.CREATOR) {
                                    sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
                                    // 顺便补发一次全量数据
                                    sendPacket(new NetworkPacket(
                                            DatabaseService.getScoresForCompetition(hostingCompetitionName),
                                            DatabaseService.calculateTeamRankings(hostingCompetitionName)));
                                }

                                if (onMemberJoinCallback != null) Platform.runLater(onMemberJoinCallback);
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
                // 批准后立即同步当前数据
                handler.sendPacket(new NetworkPacket(
                        DatabaseService.getScoresForCompetition(hostingCompetitionName),
                        DatabaseService.calculateTeamRankings(hostingCompetitionName)));
                return;
            }
        }
    }

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        this.running = true;
        new Thread(() -> {
            try {
                if (udpSocket != null) udpSocket.close();
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
                            Competition d = new Competition(parts[1], parts[2]);
                            d.setHostAddress(packet.getAddress().getHostAddress());
                            Platform.runLater(() -> {
                                if (discoveredCompetitions.stream().noneMatch(c -> c.getName().equals(d.getName()) && c.getHostAddress().equals(d.getHostAddress())))
                                    discoveredCompetitions.add(d);
                            });
                        }
                    } catch (SocketTimeoutException ignored) {}
                    catch (IOException e) { break; }
                }
            } catch (IOException ignored) {}
        }).start();
    }

    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        stop();
        this.running = true;
        clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(hostAddress, TCP_PORT), 5000);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());

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
                System.err.println("Connection to Host lost.");
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
                outToServer.reset(); // 重要
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