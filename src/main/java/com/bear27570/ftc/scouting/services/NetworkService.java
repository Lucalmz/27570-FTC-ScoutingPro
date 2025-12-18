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
import java.util.Iterator;
import java.util.function.Consumer;

public class NetworkService {

    private static NetworkService instance;

    public static synchronized NetworkService getInstance() {
        if (instance == null) {
            instance = new NetworkService();
        }
        return instance;
    }

    public static final int TCP_PORT = 54321;
    public static final int UDP_PORT = 54322;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DatagramSocket udpSocket;

    private ObjectOutputStream outToServer;
    // 使用同步列表
    private final List<ObjectOutputStream> clientOutputStreams = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean running = false;
    private Thread udpDiscoveryThread;
    private Thread tcpServerThread;
    private Thread hostBroadcastThread;

    private NetworkService() {}

    private enum State {IDLE, HOSTING, DISCOVERING, CLIENT}
    private State currentState = State.IDLE;

    public synchronized void startHost(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        if (currentState != State.IDLE) return;
        stop();
        running = true;
        currentState = State.HOSTING;

        tcpServerThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    clientOutputStreams.add(out);
                    new ClientHandler(client, out, onScoreReceived).start();
                }
            } catch (IOException e) {
                // Ignore close exception
            }
        });
        tcpServerThread.start();

        hostBroadcastThread = new Thread(() -> {
            try (DatagramSocket broadcastSocket = new DatagramSocket()) {
                broadcastSocket.setBroadcast(true);
                String message = String.format("FTC_SCOUTER;%s;%s", competition.getName(), competition.getCreatorUsername());
                byte[] buffer = message.getBytes();
                // 使用广播地址
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                while (running) {
                    broadcastSocket.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                // Ignore
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
                // Client disconnected
            } finally {
                clientOutputStreams.remove(out);
            }
        }
    }

    public void broadcastUpdateToClients(NetworkPacket updatePacket) {
        synchronized (clientOutputStreams) {
            Iterator<ObjectOutputStream> iterator = clientOutputStreams.iterator();
            while (iterator.hasNext()) {
                ObjectOutputStream out = iterator.next();
                try {
                    out.writeObject(updatePacket);
                    out.flush(); // 重要：刷新缓冲区
                    out.reset(); // 重要：防止对象缓存导致数据不更新
                } catch (IOException e) {
                    iterator.remove();
                }
            }
        }
    }

    public synchronized void startDiscovery(ObservableList<Competition> discoveredCompetitions) {
        if (currentState != State.IDLE) return;
        stop();
        running = true;
        currentState = State.DISCOVERING;

        udpDiscoveryThread = new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(UDP_PORT);
                udpSocket.setSoTimeout(1000);
                byte[] buffer = new byte[1024];

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());
                        if (message.startsWith("FTC_SCOUTER;")) {
                            String[] parts = message.split(";");
                            if (parts.length >= 3) {
                                Competition discovered = new Competition(parts[1], parts[2]);
                                discovered.setHostAddress(packet.getAddress().getHostAddress());
                                Platform.runLater(() -> {
                                    if (discoveredCompetitions.stream().noneMatch(c -> c.getName().equals(discovered.getName()))) {
                                        discoveredCompetitions.add(discovered);
                                    }
                                });
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        // Expected
                    }
                }
            } catch (IOException e) {
                // Ignore
            } finally {
                if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
            }
        });
        udpDiscoveryThread.start();
    }

    public synchronized void connectToHost(String hostAddress, Consumer<NetworkPacket> onUpdateReceived) throws IOException {
        stop();
        running = true;
        currentState = State.CLIENT;

        clientSocket = new Socket(hostAddress, TCP_PORT);
        outToServer = new ObjectOutputStream(clientSocket.getOutputStream());

        Thread clientListener = new Thread(() -> {
            try (ObjectInputStream inFromServer = new ObjectInputStream(clientSocket.getInputStream())) {
                while (running) {
                    NetworkPacket updatePacket = (NetworkPacket) inFromServer.readObject();
                    Platform.runLater(() -> onUpdateReceived.accept(updatePacket));
                }
            } catch (Exception e) {
                if (running) System.out.println("Disconnected from host.");
            }
        });
        clientListener.setDaemon(true);
        clientListener.start();
    }

    public void sendScoreToServer(ScoreEntry scoreEntry) {
        if (outToServer != null) {
            try {
                outToServer.writeObject(new NetworkPacket(scoreEntry));
                outToServer.flush();
                outToServer.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void stop() {
        if (currentState == State.IDLE) return;
        running = false;

        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException e) {}
        if (udpSocket != null) udpSocket.close();

        currentState = State.IDLE;
    }
}