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

    public static final int TCP_PORT = 54321; // 定义一个固定的TCP端口
    public static final int UDP_PORT = 54322; // 定义一个固定的UDP端口
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private ObjectOutputStream out;
    private final List<ObjectOutputStream> clientOutputs = Collections.synchronizedList(new ArrayList<>());

    // --- 主机模式 ---
    public void startHostMode(Competition competition, Consumer<ScoreEntry> onScoreReceived) {
        // 1. 启动TCP服务器线程，等待客户端连接
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                System.out.println("Host started on port " + TCP_PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("Client connected: " + client.getInetAddress());
                    ObjectOutputStream clientOut = new ObjectOutputStream(client.getOutputStream());
                    clientOutputs.add(clientOut);
                    new ClientHandler(client, clientOut, onScoreReceived).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // 2. 启动UDP广播线程，宣告比赛的存在
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                String message = "FTC_SCOUTER_HOST;" + competition.getName() + ";" + competition.getCreatorUsername();
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                while (true) {
                    socket.send(packet);
                    Thread.sleep(2000); // 每2秒广播一次
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 主机用来处理单个客户端的线程
    private class ClientHandler extends Thread {
        private final Socket socket;
        private final Consumer<ScoreEntry> onScoreReceived;
        private ObjectInputStream in;

        public ClientHandler(Socket socket, ObjectOutputStream out, Consumer<ScoreEntry> onScoreReceived) {
            this.socket = socket;
            this.onScoreReceived = onScoreReceived;
            try {
                this.in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            try {
                while (true) {
                    NetworkPacket packet = (NetworkPacket) in.readObject();
                    if (packet.getType() == NetworkPacket.PacketType.SUBMIT_SCORE) {
                        // 使用 Platform.runLater 将任务交回给 JavaFX 主线程处理
                        Platform.runLater(() -> onScoreReceived.accept(packet.getScoreEntry()));
                    }
                }
            } catch (Exception e) {
                System.out.println("Client disconnected: " + socket.getInetAddress());
                clientOutputs.remove(out);
            }
        }
    }

    // 主机用来广播数据给所有客户端的方法
    public void broadcastUpdate(NetworkPacket updatePacket) {
        synchronized (clientOutputs) {
            for (ObjectOutputStream out : clientOutputs) {
                try {
                    out.writeObject(updatePacket);
                    out.reset(); // 清除缓存，确保发送的是最新对象
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // --- 客户端模式 ---
    public void findHosts(ObservableList<String> discoveredHosts) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    if (message.startsWith("FTC_SCOUTER_HOST;")) {
                        String hostAddress = packet.getAddress().getHostAddress();
                        // 确保在JavaFX主线程上更新UI
                        Platform.runLater(() -> {
                            if (!discoveredHosts.contains(hostAddress)) {
                                discoveredHosts.add(hostAddress);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void connectToHost(String hostIp, Consumer<NetworkPacket> onUpdateReceived) throws IOException {
        clientSocket = new Socket(hostIp, TCP_PORT);
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                while (true) {
                    NetworkPacket updatePacket = (NetworkPacket) in.readObject();
                    Platform.runLater(() -> onUpdateReceived.accept(updatePacket));
                }
            } catch (Exception e) {
                System.out.println("Disconnected from host.");
            }
        }).start();
    }

    public void sendScoreToServer(ScoreEntry scoreEntry) {
        if (out != null) {
            try {
                out.writeObject(new NetworkPacket(scoreEntry));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}