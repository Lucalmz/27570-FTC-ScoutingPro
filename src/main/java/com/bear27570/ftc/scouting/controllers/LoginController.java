// File: src/main/java/com/bear27570/ftc/scouting/controllers/LoginController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.services.domain.UserService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML private Button loginButton;
    @FXML private Button createUserButton;

    // 确保你的 FXML 中该 VBox 拥有 fx:id="loginCard"
    @FXML private VBox loginCard;

    private MainApplication mainApp;
    private UserService userService;

    public void setDependencies(MainApplication mainApp, UserService userService) {
        this.mainApp = mainApp;
        this.userService = userService;

        // 绑定回车快捷键
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) handleLoginWrapper();
        });
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });

        // 挂载高级空间计算按钮光效
        if (loginButton != null) {
            AnimationUtils.attachSolidPressAnimation(loginButton);
        }
        if (createUserButton != null) {
            AnimationUtils.attachSolidPressAnimation(createUserButton);
        }
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
            // 登录成功：清空提示，准备退场动画
            setMessage("", false);

            StackPane root = (StackPane) usernameField.getScene().getRoot();
            Node cardNode = root.getChildren().get(0); // 此时取出的是包裹了流光边框的整个组件
            Stage stage = (Stage) root.getScene().getWindow();

            // 调用统一动画工具类处理退场并进入下一个界面
            AnimationUtils.playLoginSuccessTransition(root, cardNode, stage, username, () -> {
                try {
                    mainApp.showHubView(username,true);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

        } else {
            // 登录失败：显示红色警告并触发物理反馈动画
            setMessage("Login Failed: Incorrect username or password.", true);
        }
    }

    @FXML
    private void handleCreateUserButton() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty()) {
            setMessage("Username cannot be empty.", true);
            return;
        }
        if (password == null || password.isEmpty()) {
            setMessage("Password cannot be empty.", true);
            return;
        }

        if (userService.register(username, password)) {
            setMessage("User '" + username + "' created! Please Log In.", false);
            passwordField.clear();
        } else {
            setMessage("Registration Failed: Username '" + username + "' already exists.", true);
        }
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("error-label", "success-label");

        if (isError) {
            messageLabel.getStyleClass().add("error-label");
            usernameField.setStyle("-fx-border-color: #f87171;");
            passwordField.setStyle("-fx-border-color: #f87171;");

            // 触发物理错误抖动
            AnimationUtils.playShakeAnimation(usernameField);
            AnimationUtils.playShakeAnimation(passwordField);
        } else {
            messageLabel.getStyleClass().add("success-label");
            usernameField.setStyle("");
            passwordField.setStyle("");
        }
    }
}