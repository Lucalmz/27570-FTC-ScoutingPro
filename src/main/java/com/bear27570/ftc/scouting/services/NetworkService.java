package com.bear27570.ftc.scouting.services;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.NetworkPacket;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class NetworkService {

    // --- Singleton Pattern Implementation ---
    private static NetworkService instance;

    public static synchronized NetworkService getInstance() {
        if (instance == null) {
            instance = new NetworkService();
        }
        return instance;
    }
    // --- End Singleton Pattern ---

    public static final int TCP_PORT = 54321;
    public static final int UDP_PORT = 54322;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DatagramSocket udpSocket;

    private ObjectOutputStream outToServer;
    private final List<ObjectOutputStream> clientOutputStreams = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean running = false;
    private Thread udpDiscoveryThread;
    private Thread tcpServerThread;
    private Thread hostBroadcastThread;

    // ** 构造函数设为私有！**
    private NetworkService() {
    }

    // --- Service State ---
    private enum State {IDLE, HOSTING, DISCOVERING, CLIENT}

    private State currentState = State.IDLE;

    // --- HOST METHODS ---
    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        if (currentState != State.IDLE) {
            System.out.println("Cannot start host, service is busy in state: " + currentState);
            return;
        }
        stop();
        running = true;
        currentState = State.HOSTING;
        System.out.println("Transitioning to HOSTING state.");

        tcpServerThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                System.out.println("Host TCP Server started on port " + TCP_PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    System.out.println("Client connected: " + client.getInetAddress().getHostAddress());
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    clientOutputStreams.add(out);
                    new ClientHandler(client, out, onScoreReceived).start();
                }
            } catch (IOException e) {
                if (running) System.err.println("TCP Server Error: " + e.getMessage());
            } finally {
                System.out.println("TCP Server stopped.");
            }
        });
        tcpServerThread.start();

        hostBroadcastThread = new Thread(() -> {
            try (DatagramSocket broadcastSocket = new DatagramSocket()) {
                broadcastSocket.setBroadcast(true);
                String message = String.format("FTC_SCOUTER;%s;%s", competition.getName(), competition.getCreatorUsername());
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                while (running) {
                    broadcastSocket.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                if (running) System.err.println("UDP Broadcast Error: " + e.getMessage());
            } finally {
                System.out.println("UDP Broadcast stopped.");
            }
        });
        hostBroadcastThread.start();
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private final ObjectOutputStream out;
        private final Consumer<ScoreEntry> onScoreReceived;

        ClientHandler(Socket socket, ObjectOutputStream out, Consumer<ScoreEntry> onScoreReceived) {
            this.socket = socket;
            this.out = out;
            this.onScoreReceived = onScoreReceived;
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                while (running) {
                    NetworkPacket packet = (NetworkPacket) in.readObject();
                    if (packet.getType() == NetworkPacket.PacketType.SUBMIT_SCORE) {
                        Platform.runLater(() -> onScoreReceived.accept(packet.getScoreEntry()));
                    }
                }
            } catch (Exception e) {
                System.out.println("Client disconnected: " + socket.getInetAddress().getHostAddress());
            } finally {
                clientOutputStreams.remove(out);
            }
        }
    }

    public void broadcastUpdateToClients(NetworkPacket updatePacket) {
        synchronized (clientOutputStreams) {
            clientOutputStreams.removeIf(out -> {
                try {
                    out.writeObject(updatePacket);
                    out.reset();
                    return false;
                } catch (IOException e) {
                    System.err.println("Failed to broadcast to a client, removing it.");
                    return true;
                }
            });
        }
    }

    // --- CLIENT METHODS ---
    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        if (currentState != State.IDLE) return;
        stop(); // 先确保一切都已停止

        running = true;
        currentState = State.DISCOVERING;
        System.out.println("Transitioning to DISCOVERING state.");

        udpDiscoveryThread = new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(UDP_PORT);
                // --- 核心改动：设置1秒超时 ---
                udpSocket.setSoTimeout(1000);

                byte[] buffer = new byte[1024];
                System.out.println("UDP Discovery listener started on port " + UDP_PORT);

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet); // 最多阻塞1秒

                        String message = new String(packet.getData(), 0, packet.getLength());
                        if (message.startsWith("FTC_SCOUTER;")) {
                            String[] parts = message.split(";");
                            Competition discovered = new Competition(parts[1], parts[2]);
                            discovered.setHostAddress(packet.getAddress().getHostAddress());

                            Platform.runLater(() -> {
                                if (discoveredCompetitions.stream().noneMatch(c -> c.getName().equals(discovered.getName()))) {
                                    discoveredCompetitions.add(discovered);
                                }
                            });
                        }
                    } catch (SocketTimeoutException e) {
                        // 这是预期的超时，什么都不做，直接进入下一次循环检查 while(running)
                    }
                }
            } catch (IOException e) {
                if (running) System.err.println("UDP Discovery IO Error: " + e.getMessage());
            } finally {
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.close(); // 确保最终关闭
                }
                System.out.println("UDP Discovery listener thread finished.");
            }
        });
        udpDiscoveryThread.start();
    }
    public synchronized void connectToHost(String hostAddress, Consumer<NetworkPacket> onUpdateReceived) throws IOException {
        stop();

        running = true;
        currentState = State.CLIENT;
        System.out.println("Transitioning to CLIENT state.");

        clientSocket = new Socket(hostAddress, TCP_PORT);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
        new Thread(() -> {
            try (ObjectInputStream inFromServer = new ObjectInputStream(clientSocket.getInputStream())) {
                while (running) {
                    NetworkPacket updatePacket = (NetworkPacket) inFromServer.readObject();
                    Platform.runLater(() -> onUpdateReceived.accept(updatePacket));
                }
            } catch (Exception e) {
                if (running) System.out.println("Disconnected from host.");
            }
        }).start();
    }

    public void sendScoreToServer(ScoreEntry scoreEntry) {
        if (outToServer != null) {
            try {
                outToServer.writeObject(new NetworkPacket(scoreEntry));
                outToServer.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- GENERAL METHODS (最终强化版) ---
    public synchronized void stop() {
        if (currentState == State.IDLE) {
            return;
        }

        running = false; // **这是最重要的指令**

        // 关闭套接字以中断阻塞
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (IOException e) { /* ignore */ }
        try { if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close(); } catch (IOException e) { /* ignore */ }

        // 对于 UDP，close() 仍然是必须的，但线程现在会因为 running=false 而自行退出
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        // 中断线程作为最后的保险
        if (tcpServerThread != null) tcpServerThread.interrupt();
        if (hostBroadcastThread != null) hostBroadcastThread.interrupt();
        if (udpDiscoveryThread != null) udpDiscoveryThread.interrupt();

        currentState = State.IDLE;
        System.out.println("Stop command issued. State is now IDLE.");
    }
}