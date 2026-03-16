// File: MainApplication.java
package com.bear27570.ftc.scouting;

import atlantafx.base.theme.CupertinoDark;
import com.bear27570.ftc.scouting.controllers.*;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.repository.*;
import com.bear27570.ftc.scouting.repository.impl.*;
import com.bear27570.ftc.scouting.services.NetworkService;
import com.bear27570.ftc.scouting.services.domain.*;
import com.bear27570.ftc.scouting.services.domain.impl.*;
import com.bear27570.ftc.scouting.services.network.DefaultNetworkDataHandler;
import com.bear27570.ftc.scouting.services.network.NetworkDataHandler;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class MainApplication extends Application {

    private Stage primaryStage;

    private UserRepository userRepository;
    private MembershipRepository membershipRepository;
    private CompetitionRepository competitionRepository;
    private ScoreRepository scoreRepository;
    private PenaltyRepository penaltyRepository;

    private UserService userService;
    private CompetitionService competitionService;
    private MatchDataService matchDataService;
    private RankingService rankingService;

    private void applyTheme(Scene scene) {
        URL cssUrl = getClass().getResource("styles/style.css");
        if (cssUrl == null) {
            cssUrl = getClass().getResource("/styles/style.css");
        }
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("WARNING: Could not find 'styles/style.css'. Check your resource folder structure.");
        }
    }

    private void setStageIcon(Stage stage) {
        try {
            String iconPath = "images/logo.png";
            InputStream iconStream = getClass().getResourceAsStream(iconPath);
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/images/logo.png");
            }
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                System.err.println("WARNING: logo.png not found in 'images/' folder.");
            }
        } catch (Exception e) {
            System.err.println("WARNING: Failed to load application icon: " + e.getMessage());
        }
    }

    @Override
    public void init() throws Exception {
        Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
        String dbFolder = System.getProperty("user.home") + File.separator + ".ftcscoutingpro";
        new File(dbFolder).mkdirs();
        String dbUrl = "jdbc:h2:" + dbFolder + File.separator + "ftc_scouting_master_db";

        DatabaseManager.initialize(dbUrl);

        userRepository = new UserRepositoryJdbcImpl(dbUrl);
        membershipRepository = new MembershipRepositoryJdbcImpl(dbUrl);
        competitionRepository = new CompetitionRepositoryJdbcImpl(dbUrl);
        scoreRepository = new ScoreRepositoryJdbcImpl(dbUrl);
        penaltyRepository = new PenaltyRepositoryJdbcImpl(dbUrl);

        userService = new UserServiceImpl(userRepository);
        competitionService = new CompetitionServiceImpl(competitionRepository, membershipRepository, userRepository);
        matchDataService = new MatchDataServiceImpl(scoreRepository, penaltyRepository);
        rankingService = new RankingService(scoreRepository, penaltyRepository, competitionRepository);

        NetworkDataHandler networkDataHandler = new DefaultNetworkDataHandler(membershipRepository, userRepository, matchDataService, rankingService);
        NetworkService.getInstance().setDataHandler(networkDataHandler);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("FTC Scouting Pro");
        setStageIcon(primaryStage);
        showLoginView();
    }

    public void showLoginView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/LoginView.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        applyTheme(scene);
        primaryStage.setScene(scene);
        setStageIcon(primaryStage);
        LoginController controller = loader.getController();
        controller.setDependencies(this, userService);
        primaryStage.show();
    }

    public void showHubView(String username) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/HubView.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        applyTheme(scene);
        primaryStage.setScene(scene);
        HubController controller = loader.getController();
        controller.setDependencies(this, username, competitionService);
    }

    public void showScoringView(Competition competition, String username, boolean isHost) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/MainView.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        applyTheme(scene);
        primaryStage.setScene(scene);
        MainController controller = loader.getController();
        // ★ 修复点：将 userService 一并注入进去
        controller.setDependencies(this, competition, username, isHost, matchDataService, rankingService, competitionRepository, userService);
    }

    public void showCoordinatorView(Competition competition) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/CoordinatorView.fxml"));
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Manage Members");
        setStageIcon(stage);
        Scene scene = new Scene(loader.load());
        applyTheme(scene);
        stage.setScene(scene);
        CoordinatorController controller = loader.getController();
        controller.setDependencies(stage, competition, membershipRepository);
        stage.show();
    }

    public void showFormulaEditView(Competition competition) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/FormulaEditView.fxml"));
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Edit Formula");
        setStageIcon(stage);
        Scene scene = new Scene(loader.load());
        applyTheme(scene);
        stage.setScene(scene);
        FormulaEditController controller = loader.getController();
        controller.setDependencies(stage, competition, competitionRepository);
        stage.show();
    }

    // File: src/main/java/com/bear27570/ftc/scouting/MainApplication.java

    public void showHeatmapView(Competition competition, int teamNum) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/HeatmapView.fxml"));
        Stage stage = new Stage();
        Map<Integer, PenaltyRepository.FullPenaltyRow> penaltyMap = penaltyRepository.getFullPenalties(competition.getName());
        List<ScoreEntry> matches = matchDataService.getTeamHistory(competition.getName(), teamNum);
        stage.setTitle("Heatmap - Team " + teamNum);
        setStageIcon(stage);

        // ★ 修复 1：给定一个初始的合理大小，而不是任由内容无限撑开
        Scene scene = new Scene(loader.load(), 1200, 800);

        // ★ 修复 2：动态获取当前屏幕的真实可用边界
        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();

        // ★ 修复 3：强行限制最大高度和宽度（留出 40px 的安全边距）
        stage.setMaxHeight(screenBounds.getHeight() - 40);
        stage.setMaxWidth(screenBounds.getWidth());

        applyTheme(scene);
        stage.setScene(scene);
        HeatmapController controller = loader.getController();
        controller.setData(teamNum, matches, penaltyMap);
        stage.show();
    }

    public void showAllianceAnalysisView(Competition competition, String username) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/AllianceAnalysisView.fxml"));
        Stage stage = new Stage();
        stage.setTitle("Alliance Analysis & AI Assistant");
        setStageIcon(stage);
        Scene scene = new Scene(loader.load());
        applyTheme(scene);
        stage.setScene(scene);
        AllianceAnalysisController controller = loader.getController();
        controller.setDependencies(stage, competition, rankingService, matchDataService, userService, username);
        stage.show();
    }

    public void showFieldInputView(MainController parentController, boolean isAllianceMode, String existingLocations) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/FieldInputView.fxml"));
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Field Input");
        setStageIcon(stage);
        Scene scene = new Scene(loader.load());
        applyTheme(scene);
        stage.setScene(scene);
        FieldInputController controller = loader.getController();
        controller.setDependencies(stage, parentController, isAllianceMode, existingLocations);
        stage.showAndWait();
    }
}