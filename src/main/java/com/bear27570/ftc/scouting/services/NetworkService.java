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
                // ★ 修复点：使用局部变量，防止旧线程误用已被置空的全局变量
                ServerSocket currentServerSocket = new ServerSocket(TCP_PORT);
                serverSocket = currentServerSocket;

                // ★ 修复点：除了 running，还要判断当前的 Socket 是否已被关闭
                while (running && !currentServerSocket.isClosed()) {
                    try {
                        Socket client = currentServerSocket.accept();
                        ClientHandler handler = new ClientHandler(client, onScoreReceived);
                        connectedClients.add(handler);
                        handler.start();
                    } catch (IOException e) {
                        if (running && !currentServerSocket.isClosed()) {
                            System.err.println("TCP Accept error: " + e.getMessage());
                        } else {
                            break; // 正常退出旧线程
                        }
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

                while (running && !beacon.isClosed()) {
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
                                } catch (Exception ignored) {}
                            }
                        }
                    }

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
                out.flush();
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                // ★ 修复点：加入 !socket.isClosed() 判断
                while (running && !socket.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkPacket packet) {
                        switch (packet.getType()) {
                            case JOIN_REQUEST:
                                this.clientUsername = packet.getUsername();
                                if(dataHandler != null) {
                                    dataHandler.addPendingMembership(clientUsername, hostingCompetitionName);
                                    if (dataHandler.isUserApprovedOrCreator(clientUsername, hostingCompetitionName)) {
                                        sendPacket(new NetworkPacket(NetworkPacket.PacketType.JOIN_RESPONSE, true));
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
                    handler.sendPacket(new NetworkPacket(
                            new java.util.ArrayList<>(dataHandler.getScores(hostingCompetitionName)),
                            new java.util.ArrayList<>(dataHandler.getRankings(hostingCompetitionName)),officialEventName));
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

                // ★ 修复点：同上，使用局部变量防止串线
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
                                    if (discoveredCompetitions.stream().noneMatch(c -> c.getName().equals(d.getName()) && c.getHostAddress().equals(d.getHostAddress()))) {
                                        discoveredCompetitions.add(d);
                                    }
                                });
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (IOException e) {
                        if (running && !currentUdpSocket.isClosed()) {
                            System.err.println("UDP Receive Error: " + e.getMessage());
                        }
                        break;
                    }
                }
            } catch (java.net.BindException e) {
                System.err.println("CRITICAL ERROR: UDP Port " + UDP_PORT + " is already in use!");
            } catch (IOException e) {
                System.err.println("Failed to start UDP Discovery: " + e.getMessage());
            }
        }, "UDP-Discovery-Thread").start();
    }

    public synchronized void connectToHost(String hostAddress, String myUsername, Consumer<NetworkPacket> onPacketReceived) throws IOException {
        stop();
        this.running = true;
        clientSocket = new Socket();

        try {
            clientSocket.connect(new InetSocketAddress(hostAddress, TCP_PORT), 5000);
            outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
            outToServer.flush();

            outToServer.writeObject(new NetworkPacket(NetworkPacket.PacketType.JOIN_REQUEST, myUsername));
            outToServer.flush();
            outToServer.reset();

            Socket currentClientSocket = clientSocket;

            new Thread(() -> {
                try (ObjectInputStream in = new ObjectInputStream(currentClientSocket.getInputStream())) {
                    while (running && !currentClientSocket.isClosed()) {
                        Object obj = in.readObject();
                        if (obj instanceof NetworkPacket p) {
                            Platform.runLater(() -> onPacketReceived.accept(p));
                        }
                    }
                } catch (Exception e) {
                    if(running && !currentClientSocket.isClosed()) System.err.println("Connection to Host lost.");
                }
            }, "Client-Listen-Thread").start();
        } catch (IOException e) {
            // ★ 修复点：一旦连接异常，立刻把状态设为 false，以免产生垃圾线程
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