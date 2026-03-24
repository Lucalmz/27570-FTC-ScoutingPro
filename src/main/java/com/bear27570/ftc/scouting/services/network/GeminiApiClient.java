// File: src/main/java/com/bear27570/ftc/scouting/services/network/GeminiApiClient.java
package com.bear27570.ftc.scouting.services.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GeminiApiClient {

    private HttpClient buildClient(boolean useProxy, String proxyHost, int proxyPort) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15));

        if (useProxy && proxyHost != null && !proxyHost.trim().isEmpty()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
        } else {
            builder.proxy(ProxySelector.getDefault());
        }
        return builder.build();
    }

    public CompletableFuture<String> testGenericNetworkAsync(String targetUrl, boolean useProxy, String proxyHost, int proxyPort) {
        HttpClient client = buildClient(useProxy, proxyHost, proxyPort);
        String finalUrl = targetUrl.trim().startsWith("http") ? targetUrl.trim() : "https://" + targetUrl.trim();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 400) {
                        return "Connection Successful! HTTP " + response.statusCode();
                    } else {
                        throw new RuntimeException("Connected, but returned HTTP " + response.statusCode());
                    }
                });
    }

    public CompletableFuture<String> sendChatRequestAsync(String apiKey, String model, String context, String userPrompt, boolean useProxy, String proxyHost, int proxyPort) {
        HttpClient client = buildClient(useProxy, proxyHost, proxyPort);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model.trim() + ":generateContent?key=" + apiKey.trim();

        String systemPrompt = "System: You are an expert FIRST Tech Challenge (FTC) Robotics Scouting AI Assistant. " +
                "Your goal is to help a team make strategic Alliance Selection decisions. " +
                "Base your insights strictly on the data context provided below.\n\n";

        // 💡 架构师级做法：使用 Gson 对象树安全构造 JSON
        JsonObject partObj = new JsonObject();
        partObj.addProperty("text", systemPrompt + context + "\n\nUser: " + userPrompt);

        JsonArray partsArray = new JsonArray();
        partsArray.add(partObj);

        JsonObject contentObj = new JsonObject();
        contentObj.add("parts", partsArray);

        JsonArray contentsArray = new JsonArray();
        contentsArray.add(contentObj);

        JsonObject payloadObj = new JsonObject();
        payloadObj.add("contents", contentsArray);

        String payload = payloadObj.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("API Error (" + response.statusCode() + "). Details: \n" + response.body());
                    }
                    return parseGeminiResponseText(response.body());
                });
    }

    // 💡 彻底抛弃硬编码截取，使用标准的 JSON 解析树提取数据
    private String parseGeminiResponseText(String jsonBody) {
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();

            if (root.has("candidates") && root.get("candidates").isJsonArray()) {
                JsonArray candidates = root.getAsJsonArray("candidates");
                if (!candidates.isEmpty()) {
                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                    if (firstCandidate.has("content")) {
                        JsonObject content = firstCandidate.getAsJsonObject("content");
                        if (content.has("parts") && content.get("parts").isJsonArray()) {
                            JsonArray parts = content.getAsJsonArray("parts");
                            if (!parts.isEmpty()) {
                                JsonObject firstPart = parts.get(0).getAsJsonObject();
                                if (firstPart.has("text")) {
                                    return firstPart.get("text").getAsString();
                                }
                            }
                        }
                    }
                }
            }
            return "No text found in response or unexpected API structure.";
        } catch (Exception e) {
            return "Failed to parse API response using Gson. Details: " + e.getMessage();
        }
    }
}