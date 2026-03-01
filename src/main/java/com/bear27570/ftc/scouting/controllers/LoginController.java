package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.services.domain.UserService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    private MainApplication mainApp;
    private UserService userService;

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

    // 包装一下异常处理，方便 Lambda 调用
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
            setMessage("Login Successful! Entering Hub...", false);
            // 延迟 0.5 秒跳转，提升用户体验
            PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
            pause.setOnFinished(e -> {
                try {
                    mainApp.showHubView(username);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            pause.play();
        } else {
            // 这里对应 authenticateUser 返回 false
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
        // 注意：这里没有密码长度检查了，完全依赖数据库写入结果
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