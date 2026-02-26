package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.services.domain.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    private MainApplication mainApp;

    // 核心改造：控制器不再依赖具体的 DatabaseService，而是依赖抽象的 UserService 接口
    private UserService userService;

    /**
     * 依赖注入点：由调用方 (MainApplication) 在加载 FXML 后调用此方法传入具体的实现。
     */
    public void setDependencies(MainApplication mainApp, UserService userService) {
        this.mainApp = mainApp;
        this.userService = userService;
    }

    @FXML
    private void handleLoginButton() throws IOException {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // 改造后：调用注入的实例方法，不再使用 DatabaseService.authenticateUser(...)
        if (userService.login(username, password)) {
            mainApp.showHubView(username);
        } else {
            messageLabel.setText("Invalid username or password.");
        }
    }

    @FXML
    private void handleCreateUserButton() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Username and password are required to create a user.");
            return;
        }

        // 改造后：调用实例方法
        if (userService.register(username, password)) {
            messageLabel.setStyle("-fx-text-fill: #4CAF50;"); // 成功为绿色
            messageLabel.setText("User '" + username + "' created successfully! You can now log in.");
        } else {
            messageLabel.setStyle("-fx-text-fill: #FF5252;"); // 失败为红色
            messageLabel.setText("Username '" + username + "' already exists or password too short (min 6 chars).");
        }
    }
}