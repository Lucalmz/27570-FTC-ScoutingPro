// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabRankingsController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.TeamRanking;
import com.bear27570.ftc.scouting.utils.FxThread;
import com.bear27570.ftc.scouting.viewmodels.SharedDataViewModel;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class TabRankingsController {

    @FXML private Label rankingLegendLabel;
    @FXML private Button editRatingButton;

    @FXML private TableView<TeamRanking> rankingsTableView;
    @FXML private TableColumn<TeamRanking, Integer> rankTeamCol, rankMatchesCol;
    @FXML private TableColumn<TeamRanking, Double> rankAutoCol, rankTeleopCol;
    @FXML private TableColumn<TeamRanking, Double> rankRatingCol;
    @FXML private TableColumn<TeamRanking, Double> rankAccuracyCol;
    @FXML private TableColumn<TeamRanking, Double> rankPenCommCol, rankPenOppCol;
    @FXML private TableColumn<TeamRanking, String> rankSequenceCol, rankL2Col;
    @FXML private TableColumn<TeamRanking, Void> rankHeatmapCol;
    @FXML private TableColumn<TeamRanking, List<Double>> rankTrendCol;

    private MainController mainController;
    private MainApplication mainApp;
    private Competition currentCompetition;
    private boolean isHost;
    private static final Logger log = LoggerFactory.getLogger(TabRankingsController.class);
    private SharedDataViewModel viewModel;

    @FXML
    public void initialize() {
        rankTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        rankTeamCol.setStyle("-fx-alignment: CENTER;");

        rankMatchesCol.setCellValueFactory(new PropertyValueFactory<>("matchesPlayed"));
        rankMatchesCol.setStyle("-fx-alignment: CENTER;");

        rankRatingCol.setCellValueFactory(new PropertyValueFactory<>("rating"));
        rankRatingCol.setCellFactory(createNumberCellFactory("%.2f"));
        rankRatingCol.setStyle("-fx-alignment: CENTER;");

        rankAutoCol.setCellValueFactory(new PropertyValueFactory<>("avgAutoArtifacts"));
        rankAutoCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankAutoCol.setStyle("-fx-alignment: CENTER;");

        rankTeleopCol.setCellValueFactory(new PropertyValueFactory<>("avgTeleopArtifacts"));
        rankTeleopCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankTeleopCol.setStyle("-fx-alignment: CENTER;");

        rankAccuracyCol.setCellValueFactory(cellData -> {
            String accStr = cellData.getValue().getAccuracyFormatted().replace("%", "");
            double val = accStr.equals("N/A") ? -1.0 : Double.parseDouble(accStr);
            return new SimpleObjectProperty<>(val);
        });
        rankAccuracyCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item < 0) setText(empty ? null : "N/A");
                else setText(String.format("%.1f%%", item));
            }
        });
        rankAccuracyCol.setStyle("-fx-alignment: CENTER;");

        rankPenCommCol.setCellValueFactory(new PropertyValueFactory<>("avgPenaltyCommitted"));
        rankPenCommCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankPenCommCol.setStyle("-fx-alignment: CENTER;");

        rankPenOppCol.setCellValueFactory(new PropertyValueFactory<>("avgOpponentPenalty"));
        rankPenOppCol.setCellFactory(createNumberCellFactory("%.1f"));
        rankPenOppCol.setStyle("-fx-alignment: CENTER;");

        rankSequenceCol.setCellValueFactory(new PropertyValueFactory<>("canSequence"));
        rankSequenceCol.setStyle("-fx-alignment: CENTER;");
        rankL2Col.setCellValueFactory(new PropertyValueFactory<>("l2Capable"));
        rankL2Col.setStyle("-fx-alignment: CENTER;");

        rankHeatmapCol.setCellFactory(p -> new TableCell<>() {
            private final Button btn = new Button("");

            {
                btn.getStyleClass().addAll("button", "accent");

                FontIcon icon = new FontIcon("fth-map");
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.valueOf("#111111"));
                btn.setGraphic(icon);
                btn.setText("");

                btn.setOnAction(e -> {
                    try {
                        var rowData = getTableView().getItems().get(getIndex());
                        if (rowData != null) {
                            mainApp.showHeatmapView(currentCompetition, rowData.getTeamNumber());
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
// 重新定义 Trend 列：动能徽章 (Momentum Badge)
        rankTrendCol.setCellValueFactory(new PropertyValueFactory<>("recentRatings"));
        rankTrendCol.setCellFactory(tc -> new TableCell<>() {

            // 声明 UI 组件
            private final Label textLabel = new Label();
            private final FontIcon icon = new FontIcon();
            private final HBox badge = new HBox(6, icon, textLabel);

            {
                badge.setAlignment(javafx.geometry.Pos.CENTER);
                textLabel.setStyle("-fx-font-family: 'Oxanium'; -fx-font-weight: 800;");
            }

            @Override
            protected void updateItem(List<Double> ratings, boolean empty) {
                super.updateItem(ratings, empty);

                // 如果没有数据或比赛场次不足2场，不显示趋势
                if (empty || ratings == null || ratings.size() < 2) {
                    setGraphic(null);
                    return;
                }

                // 🧠 核心逻辑：动量计算 (Momentum Calculation)
                // 总体平均分
                double overallAvg = ratings.stream().mapToDouble(d -> d).average().orElse(0.0);

                // 近期平均分（取最近2场的平均值，消除单场意外断线的干扰）
                double recentAvg;
                if (ratings.size() >= 3) {
                    recentAvg = (ratings.get(ratings.size() - 1) + ratings.get(ratings.size() - 2)) / 2.0;
                } else {
                    recentAvg = ratings.get(ratings.size() - 1);
                }

                // 计算差值
                double delta = recentAvg - overallAvg;

                // 🧹 清理之前的样式残留
                badge.getStyleClass().removeAll("cyber-badge-success", "cyber-badge-danger", "cyber-badge-neutral");

                // 🎨 判定并渲染 UI
                if (delta > 10) {
                    // 状态火热 (Improving)
                    icon.setIconLiteral("fth-trending-up");
                    icon.setIconColor(javafx.scene.paint.Color.web("#34D399"));
                    textLabel.setText(String.format("+%.1f", delta));
                    textLabel.setStyle("-fx-text-fill: #34D399;");
                    badge.getStyleClass().add("cyber-badge-success");

                } else if (delta < -10) {
                    // 状态下滑 (Declining)
                    icon.setIconLiteral("fth-trending-down");
                    icon.setIconColor(javafx.scene.paint.Color.web("#EF4444"));
                    textLabel.setText(String.format("%.1f", delta)); // 负数自带负号
                    textLabel.setStyle("-fx-text-fill: #EF4444;");
                    badge.getStyleClass().add("cyber-badge-danger");

                } else {
                    // 状态稳定 (Stable)
                    icon.setIconLiteral("fth-minus");
                    icon.setIconColor(javafx.scene.paint.Color.web("#A1A1AA"));
                    textLabel.setText("STABLE");
                    textLabel.setStyle("-fx-text-fill: #A1A1AA; -fx-font-size: 10px;"); // Stable字多，稍微缩细一点
                    badge.getStyleClass().add("cyber-badge-neutral");
                }

                setGraphic(badge);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        rankingLegendLabel.setText("Penalty: Major=15, Minor=5. Auto Score now correctly skips matches with 0 points from average calculation.");
        AnimationUtils.attachGlidingHighlight(rankingsTableView);
    }

    public void setDependencies(MainController mainController, MainApplication mainApp, Competition competition, boolean isHost, SharedDataViewModel viewModel) {
        this.mainController = mainController;
        this.mainApp = mainApp;
        this.currentCompetition = competition;
        this.isHost = isHost;
        this.viewModel = viewModel;

        if (editRatingButton != null) {
            editRatingButton.setVisible(isHost);
            editRatingButton.setManaged(isHost);
        }

        rankingsTableView.setItems(viewModel.getRankingsList());
    }

    @FXML
    private void handleEditRating() {
        if (isHost) {
            try {
                mainApp.showFormulaEditView(currentCompetition);
                mainController.triggerDataRefreshAndBroadcast();
            } catch (IOException e) {
                log.error("Failed to open formula edit view", e);
            }
        }
    }

    public void updateCompetition(Competition comp) {
        this.currentCompetition = comp;
        FxThread.run(() -> {
            if (currentCompetition != null && isHost) {
                rankRatingCol.setText(currentCompetition.getRatingFormula().equals("total") ? "Rating" : "Rating *");
            }
        });
    }

    public ObservableList<TeamRanking> getRankingsList() {
        return viewModel != null ? viewModel.getRankingsList() : javafx.collections.FXCollections.emptyObservableList();
    }

    private <T> Callback<TableColumn<T, Double>, TableCell<T, Double>> createNumberCellFactory(String format) {
        return tc -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(format, item));
            }
        };
    }
}