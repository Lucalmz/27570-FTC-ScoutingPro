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

    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        // 健壮性改进：如果已经在这个比赛的主持模式中，只更新回调，不重启服务器
        if (running && competition.getName().equals(hostingCompetitionName) && serverSocket != null) {
            for (ClientHandler handler : connectedClients) {
                handler.setOnScoreReceived(onScoreReceived);
            }
            return;
        }

        stop(); // 只有当切换比赛或初次启动时才停止旧连接
        running = true;
        this.hostingCompetitionName = competition.getName();

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
                        if (running) System.err.println("Accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }).start();

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
        private Consumer<ScoreEntry> onScoreReceived;
        private String clientUsername;

        ClientHandler(Socket socket, Consumer<ScoreEntry> onScoreReceived) {
            this.socket = socket;
            this.onScoreReceived = onScoreReceived;
        }

        public void setOnScoreReceived(Consumer<ScoreEntry> onScoreReceived) {
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
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket packet) {
                        switch (packet.getType()) {
                            case JOIN_REQUEST:
                                this.clientUsername = packet.getUsername();
                                // 自动存入数据库为 PENDING
                                DatabaseService.addMembership(clientUsername, hostingCompetitionName, Membership.Status.PENDING);
                                // 触发 UI 刷新（如果窗口开着）
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
        // Discovery 模式不强制停止正在主持的服务器，仅重置 UDP 接收器
        if (udpSocket != null) udpSocket.close();

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
                    catch (IOException e) { break; }
                }
            } catch (IOException ignored) {}
        }).start();
    }

    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        stop(); // Client 模式连接新主机前必须清理旧连接
        running = true;
        clientSocket = new Socket(hostAddress, TCP_PORT);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());

        outToServer.writeObject(new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername));
        outToServer.flush();

        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                while (running) {
                    NetworkPacket p = (NetworkPacket) in.readObject();
                    Platform.runLater(() -> onPacketReceived.accept(p));
                }
            } catch (Exception e) {
                Platform.runLater(() -> System.err.println("Disconnected from Host."));
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
        serverSocket = null;
        clientSocket = null;
        udpSocket = null;
    }
}
