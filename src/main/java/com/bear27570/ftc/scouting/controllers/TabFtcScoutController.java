// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabFtcScoutController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.services.network.FtcScoutApiClient;
import com.bear27570.ftc.scouting.utils.FxThread;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox; // 假设你的根节点是 VBox，如果是别的请自行更改
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class TabFtcScoutController {

    @FXML private VBox ftcScoutContainer;
    @FXML private TextField ftcScoutSeasonField;
    @FXML private TextField ftcScoutEventField;
    @FXML private Label boundEventNameLabel;
    @FXML private Label autoFetchStatusLabel;

    private MainController mainController;
    private FtcScoutApiClient ftcScoutApiClient;
    private Competition currentCompetition;
    private boolean isHost;

    // 新增：核心主题色绑定属性（默认科技蓝）
    private final ObjectProperty<Color> themeColor = new SimpleObjectProperty<>(Color.web("#0A84FF"));

    @FXML
    public void initialize() {
        // 绑定赛博朋克流光和雾化光晕效果
        if (ftcScoutContainer != null) {
            AnimationUtils.attachCyberpunkGlow(ftcScoutContainer, themeColor);
        }
    }

    public void setDependencies(MainController mainController, FtcScoutApiClient apiClient, Competition competition, boolean isHost) {
        this.mainController = mainController;
        this.ftcScoutApiClient = apiClient;
        this.currentCompetition = competition;
        this.isHost = isHost;

        if (currentCompetition.getOfficialEventName() != null && !currentCompetition.getOfficialEventName().isEmpty()) {
            ftcScoutSeasonField.setText(String.valueOf(currentCompetition.getEventSeason()));
            ftcScoutEventField.setText(currentCompetition.getEventCode());
            boundEventNameLabel.setText("Bound Event: " + currentCompetition.getOfficialEventName());
        }
    }

    // 颜色渐变动画辅助方法
    private void animateThemeColor(Color targetColor) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(350),
                        new KeyValue(themeColor, targetColor, Interpolator.EASE_OUT)
                )
        );
        timeline.play();
    }

    @FXML
    private void handleBindAndFetch() {
        if (!isHost) {
            autoFetchStatusLabel.setText("Error: Only Host can sync with FTCScout.");
            autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
            animateThemeColor(Color.web("#FF453A")); // 权限错误：红光
            return;
        }

        String seasonStr = ftcScoutSeasonField.getText().trim();
        String eventCode = ftcScoutEventField.getText().trim();
        if (seasonStr.isEmpty() || eventCode.isEmpty()) {
            autoFetchStatusLabel.setText("Please enter Season (Int) and Event Code.");
            animateThemeColor(Color.web("#FF9F0A")); // 输入不全：黄光
            return;
        }

        int season;
        try {
            season = Integer.parseInt(seasonStr);
        } catch (NumberFormatException e) {
            autoFetchStatusLabel.setText("Error: Season must be a valid year.");
            animateThemeColor(Color.web("#FF453A")); // 格式错误：红光
            return;
        }

        autoFetchStatusLabel.setText("Connecting to FTCScout...");
        autoFetchStatusLabel.setStyle("-fx-text-fill: #0A84FF;");
        boundEventNameLabel.setText("Event: Fetching...");
        animateThemeColor(Color.web("#0A84FF")); // 正在获取：蓝光

        // 💥 链式调用
        ftcScoutApiClient.fetchAndSyncEventDataAsync(season, eventCode, currentCompetition.getName(),
                        progress -> FxThread.run(() -> boundEventNameLabel.setText("Bound Event: " + progress.name())))
                .thenAccept(syncedCount -> FxThread.run(() -> {
                    // UI 更新
                    if (syncedCount > 0) {
                        autoFetchStatusLabel.setText("Success! Synced penalties for " + syncedCount + " matches.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #32D74B;");
                        animateThemeColor(Color.web("#32D74B")); // 成功：绿光
                    } else {
                        autoFetchStatusLabel.setText("Event Linked. No matches found yet.");
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #FF9F0A;");
                        animateThemeColor(Color.web("#FF9F0A")); // 没数据：黄光
                    }
                    // 获取到名字后全局更新
                    String officialName = boundEventNameLabel.getText().replace("Bound Event: ", "");
                    mainController.updateOfficialEventName(officialName);
                }))
                .exceptionally(ex -> {
                    FxThread.run(() -> {
                        boundEventNameLabel.setText("Event: Error/Not Found");
                        autoFetchStatusLabel.setText(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                        autoFetchStatusLabel.setStyle("-fx-text-fill: #FF453A;");
                        animateThemeColor(Color.web("#FF453A")); // 获取失败：红光
                    });
                    return null;
                });
    }
}