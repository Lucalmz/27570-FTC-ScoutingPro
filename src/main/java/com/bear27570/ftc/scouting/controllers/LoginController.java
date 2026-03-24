// File: src/main/java/com/bear27570/ftc/scouting/controllers/LoginController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.services.domain.UserService;
import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    private MainApplication mainApp;
    private UserService userService;
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    public void setDependencies(MainApplication mainApp, UserService userService) {
        this.mainApp = mainApp;
        this.userService = userService;

        // 绑定回车键：在密码框按回车 -> 登录
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) handleLoginWrapper();
        });

        // 绑定回车键：在用户名框按回车 -> 跳转到密码框
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });
    }

    private void handleLoginWrapper() {
        try {
            handleLoginButton();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLoginButton() throws IOException {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            setMessage("Please enter both username and password.", true);
            return;
        }

        if (userService.login(username, password)) {
            // 1. 获取基础容器和窗口
            StackPane root = (StackPane) usernameField.getScene().getRoot();
            VBox loginCard = (VBox) root.getChildren().get(0);
            Stage stage = (Stage) root.getScene().getWindow();

            // 2. 将原有的登录卡片淡出隐藏
            FadeTransition fadeOutCard = new FadeTransition(Duration.millis(300), loginCard);
            fadeOutCard.setToValue(0);
            fadeOutCard.setOnFinished(e -> {
                loginCard.setVisible(false);

                // 3. 【核心修复】创建可写的代理 Property 来间接修改 Stage 宽高
                DoubleProperty proxyWidth = new SimpleDoubleProperty(stage.getWidth());
                proxyWidth.addListener((obs, oldVal, newVal) -> stage.setWidth(newVal.doubleValue()));

                DoubleProperty proxyHeight = new SimpleDoubleProperty(stage.getHeight());
                proxyHeight.addListener((obs, oldVal, newVal) -> stage.setHeight(newVal.doubleValue()));

                // 动态拓展窗口大小至 HubView 尺寸 (1000 x 700)
                Timeline expandStage = new Timeline(
                        new KeyFrame(Duration.millis(400),
                                new KeyValue(proxyWidth, 1000.0, Interpolator.EASE_BOTH),
                                new KeyValue(proxyHeight, 700.0, Interpolator.EASE_BOTH)
                        )
                );

                expandStage.setOnFinished(e2 -> {
                    // 确保放大后窗口依然在屏幕正中央
                    stage.centerOnScreen();

                    // 4. 创建金色发光手写体 Label
                    Label welcomeLabel = new Label("Welcome back, " + username);
                    String fontPath = "/com/bear27570/ftc/scouting/fonts/Pacifico-Regular.ttf";

                    java.net.URL fontUrl = getClass().getResource(fontPath);

                    if (fontUrl != null) {
                        String fontUrlString = fontUrl.toExternalForm();
                        // 2. 使用 URL 加载字体
                        Font customFont = Font.loadFont(fontUrlString, 68);

                        if (customFont != null) {
                            // 3. 兜底：确保 JVM 在内存层面应用了字体
                            welcomeLabel.setFont(customFont);

                            // 4. 强压：利用内联 CSS 最高优先级的特性，强制锁死字体名和大小
                            // 特别注意：获取 family name 并包裹引号
                            welcomeLabel.setStyle(
                                    "-fx-font-family: '" + customFont.getFamily() + "'; " +
                                            "-fx-font-size: 68px; " +
                                            "-fx-text-fill: #FDE047;"
                            );
                        } else {
                            // 极端情况：文件存在但无法作为字体加载
                            log.error("❌ 字体文件格式错误或已损坏 (Font format invalid): " + fontUrlString);
                            welcomeLabel.setStyle("-fx-font-size: 68px; -fx-font-style: italic; -fx-text-fill: #FDE047;");
                        }
                    } else {
                        // 根本情况：文件根本不存在
                        log.error("⚠️ 资源未找到 (Resource not found)! 请再次确认 'src/main/resources' 结构: " + fontPath);
                        welcomeLabel.setStyle("-fx-font-size: 68px; -fx-font-style: italic; -fx-text-fill: #FDE047;");
                    }
                    // 赛博朋克风金色弥散发光特效
                    welcomeLabel.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.web("#D4AF37"), 25, 0.4, 0, 0));

                    // 5. 核心原理：创建一个矩形作为遮罩 (Clip)
                    // 给一个足够大的固定高度，避免因字体渲染差异被裁切
                    Rectangle clipRect = new Rectangle(0, 150);
                    clipRect.setWidth(0); // 初始宽度为0，完全遮挡文字

                    welcomeLabel.setClip(clipRect);
                    root.getChildren().add(welcomeLabel);

                    // 6. 书写动画 (遮罩向右展开，平滑擦除文字)
                    // 直接将宽度展开到 1000 (窗口最大宽度)，确保任何长度的名字都能完整显示
                    Timeline wipeIn = new Timeline(
                            new KeyFrame(Duration.ZERO, new KeyValue(clipRect.widthProperty(), 0)),
                            new KeyFrame(Duration.millis(1500), new KeyValue(clipRect.widthProperty(), 1000.0, Interpolator.EASE_OUT))
                    );

                    wipeIn.setOnFinished(e3 -> {
                        PauseTransition hold = new PauseTransition(Duration.seconds(0.5));
                        hold.setOnFinished(e4 -> {
                            try {
                                mainApp.showHubView(username);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        });
                        hold.play();
                    });

                    // 稍微延迟 200ms 再开始写，呼吸感更好
                    PauseTransition preDelay = new PauseTransition(Duration.millis(200));
                    preDelay.setOnFinished(ex -> wipeIn.play());
                    preDelay.play();
                });

                expandStage.play();
            });

            fadeOutCard.play();

        } else {
            setMessage("Login Failed: Incorrect username or password.", true);
        }
    }

    @FXML
    private void handleCreateUserButton() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // 1. 基础非空校验
        if (username == null || username.trim().isEmpty()) {
            setMessage("Username cannot be empty.", true);
            return;
        }
        if (password == null || password.isEmpty()) {
            setMessage("Password cannot be empty.", true);
            return;
        }

        // 2. 调用业务层注册
        boolean success = userService.register(username, password);

        // 3. 根据真实结果反馈
        if (success) {
            setMessage("User '" + username + "' created! Please Log In.", false);
            // 注册成功后，自动清空密码框以便用户重新输入确认
            passwordField.clear();
        } else {
            // 如果返回 false，通常是因为主键(username)冲突
            setMessage("Registration Failed: Username '" + username + "' already exists.", true);
        }
    }

    /**
     * 统一的消息显示方法
     * @param text 显示的文本
     * @param isError true=红色错误警告, false=绿色成功提示
     */
    private void setMessage(String text, boolean isError) {
        messageLabel.setText(text);
        // 清除旧样式
        messageLabel.getStyleClass().removeAll("error-label", "success-label");

        // 添加新样式 (对应 MainApplication 中的 CSS)
        if (isError) {
            messageLabel.getStyleClass().add("error-label");
            // 错误时抖动输入框增强反馈 (可选)
            usernameField.setStyle("-fx-border-color: #f87171;");
            passwordField.setStyle("-fx-border-color: #f87171;");
        } else {
            messageLabel.getStyleClass().add("success-label");
            // 成功时恢复边框颜色
            usernameField.setStyle("");
            passwordField.setStyle("");
        }
    }
}