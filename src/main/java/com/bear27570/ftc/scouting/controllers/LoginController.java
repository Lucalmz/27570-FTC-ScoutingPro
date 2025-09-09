package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.services.DatabaseService;
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

    // 不再需要 dbService 实例
    public void setMainApp(MainApplication mainApp) {
        this.mainApp = mainApp;
    }

    @FXML
    private void handleLoginButton() throws IOException {
        String username = usernameField.getText();
        // 直接调用静态方法
        if (DatabaseService.authenticateUser(username, passwordField.getText())) {
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

        // 直接调用静态方法
        if (DatabaseService.createUser(username, password)) {
            messageLabel.setStyle("-fx-text-fill: #4CAF50;"); // Green color for success
            messageLabel.setText("User '" + username + "' created successfully! You can now log in.");
        } else {
            messageLabel.setStyle("-fx-text-fill: #FF5252;"); // Red for error
            messageLabel.setText("Username '" + username + "' already exists.");
        }
    }
}