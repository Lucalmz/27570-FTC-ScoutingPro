package com.bear27570.ftc.scouting;

import com.bear27570.ftc.scouting.controllers.CoordinatorController;
import com.bear27570.ftc.scouting.controllers.FormulaEditController;
import com.bear27570.ftc.scouting.controllers.HubController;
import com.bear27570.ftc.scouting.controllers.LoginController;
import com.bear27570.ftc.scouting.controllers.MainController;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.services.DatabaseService;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class MainApplication extends Application {
    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        try {
            Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/com/bear27570/ftc/scouting/images/logo.png")));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.out.println("Logo not found, skipping.");
        }

        DatabaseService.initializeMasterDatabase();
        primaryStage.setOnCloseRequest(event -> shutdown());

        showLoginView();
    }

    public void showLoginView() throws IOException {
        NetworkService.getInstance().stop();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/LoginView.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        LoginController controller = loader.getController();
        controller.setMainApp(this);
        primaryStage.setTitle("FTC Scouting - Login");
        primaryStage.show();
    }

    public void showHubView(String username) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/HubView.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        HubController controller = loader.getController();
        controller.setMainApp(this, username);
        primaryStage.setTitle("FTC Scouting - Competition Hub");
    }

    public void showScoringView(Competition competition, String username, boolean isHost) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/MainView.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        MainController controller = loader.getController();
        controller.setMainApp(this, competition, username, isHost);
        primaryStage.setTitle("FTC Scouting - " + competition.getName());
    }

    public void showCoordinatorView(Competition competition) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/CoordinatorView.fxml"));
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Manage Coordinators for " + competition.getName());
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(primaryStage);
        dialogStage.setScene(new Scene(loader.load()));

        CoordinatorController controller = loader.getController();
        controller.setDialogStage(dialogStage,competition);

        dialogStage.showAndWait();
    }

    // 新增：打开公式编辑窗口
    public void showFormulaEditView(Competition competition) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/FormulaEditView.fxml"));
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Edit Rating Formula - " + competition.getName());
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(primaryStage);
        dialogStage.setScene(new Scene(loader.load()));

        FormulaEditController controller = loader.getController();
        controller.setDialogStage(dialogStage, competition);
        dialogStage.showAndWait();
    }

    @Override
    public void stop() {
        shutdown();
    }
    private void shutdown() {
        NetworkService.getInstance().stop();
        Platform.exit();
        System.exit(0);
    }
    public static void main(String[] args) {
        launch(args);
    }
}