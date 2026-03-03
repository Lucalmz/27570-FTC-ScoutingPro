package com.bear27570.ftc.scouting.services.network;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class GeminiApiClient {

    public interface GeminiCallback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    // 辅助方法：构建具备代理设置的 HttpClient
    private HttpClient buildClient(boolean useProxy, String proxyHost, int proxyPort) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15));

        if (useProxy && proxyHost != null && !proxyHost.trim().isEmpty()) {
            // 手动指定代理
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
        } else {
            // 默认尝试读取系统代理
            builder.proxy(ProxySelector.getDefault());
        }
        return builder.build();
    }

    /**
     * 【新增功能】：像 IDEA 一样测试任意网址的网络连通性
     */
    public void testGenericNetworkAsync(String targetUrl, boolean useProxy, String proxyHost, int proxyPort, GeminiCallback callback) {
        new Thread(() -> {
            try {
                HttpClient client = buildClient(useProxy, proxyHost, proxyPort);

                // 自动补全 http/https
                String finalUrl = targetUrl.trim();
                if (!finalUrl.startsWith("http")) {
                    finalUrl = "https://" + finalUrl;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(finalUrl))
                        .timeout(Duration.ofSeconds(10))
                        .GET() // 使用 GET 请求测试连通性
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // HTTP 200~399 之间都算连通成功（包含重定向）
                if (response.statusCode() >= 200 && response.statusCode() < 400) {
                    callback.onSuccess("Connection Successful! HTTP " + response.statusCode());
                } else {
                    callback.onError("Connected, but returned HTTP " + response.statusCode());
                }
            } catch (java.net.ConnectException e) {
                callback.onError("Proxy/Network Error: Could not connect to the proxy or target server.\n" + e.getMessage());
            } catch (java.net.http.HttpTimeoutException e) {
                callback.onError("Timeout: The connection took too long. Proxy might be inactive.");
            } catch (Exception e) {
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 发送聊天请求（包含 FTC 提示词上下文）
     */
    public void sendChatRequestAsync(String apiKey, String model, String context, String userPrompt, boolean useProxy, String proxyHost, int proxyPort, GeminiCallback callback) {
        new Thread(() -> {
            try {
                HttpClient client = buildClient(useProxy, proxyHost, proxyPort);
                String cleanApiKey = apiKey.trim();
                String cleanModel = model.trim(); // 确保没有首尾空格导致 404

                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + cleanModel + ":generateContent?key=" + cleanApiKey;

                String safeContext = escapeJson(context);
                String safePrompt = escapeJson(userPrompt);

                // 🌟 【系统指令增强】：让 AI 知道自己的身份
                String systemPrompt = "System: You are an expert FIRST Tech Challenge (FTC) Robotics Scouting AI Assistant. " +
                        "Your goal is to help a team make strategic Alliance Selection decisions. " +
                        "Base your insights strictly on the data context provided below.\\n\\n";

                String payload = "{\"contents\": [{\"parts\":[{\"text\": \"" + systemPrompt + safeContext + "\\n\\nUser: " + safePrompt + "\"}]}]}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60)) // 留足 60 秒等待 AI 生成长文本
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    callback.onError("API Error (" + response.statusCode() + "). Details: \n" + response.body());
                    return;
                }

                String parsedResponse = parseGeminiResponseText(response.body());
                callback.onSuccess(parsedResponse);

            } catch (java.net.ConnectException e) {
                callback.onError("Connection Failed: Unable to connect to Google API. Check your proxy.\nError: " + e.toString());
            } catch (java.net.http.HttpTimeoutException e) {
                callback.onError("Request Timed Out: The server took too long to respond. Please check your network.");
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError("Local Request failed: " + e.toString());
            }
        }).start();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String parseGeminiResponseText(String jsonBody) {
        try {
            int textIdx = jsonBody.indexOf("\"text\"");
            if (textIdx == -1) return "No text found in response.";

            int colonIdx = jsonBody.indexOf(":", textIdx);
            int startQuote = jsonBody.indexOf("\"", colonIdx);

            if (startQuote != -1) {
                StringBuilder sb = new StringBuilder();
                boolean escaped = false;
                for (int i = startQuote + 1; i < jsonBody.length(); i++) {
                    char c = jsonBody.charAt(i);
                    if (escaped) {
                        if (c == 'n') sb.append('\n');
                        else if (c == 'r') sb.append('\r');
                        else if (c == 't') sb.append('\t');
                        else sb.append(c);
                        escaped = false;
                    } else {
                        if (c == '\\') escaped = true;
                        else if (c == '"') break;
                        else sb.append(c);
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return "Failed to parse API response. " + e.getMessage();
        }
        return "Failed to parse API response structure.";
    }
}