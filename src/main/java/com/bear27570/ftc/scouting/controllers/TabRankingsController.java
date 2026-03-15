// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabRankingsController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.MainApplication;
import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.TeamRanking;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.List;

public class TabRankingsController {

    @FXML private Label rankingLegendLabel;

    // ★ 从 MainController 转移过来的“修改公式”按钮
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
                 icon.setIconColor(javafx.scene.paint.Color.valueOf("#111111")); // 金底配极黑图标
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

        rankTrendCol.setCellValueFactory(new PropertyValueFactory<>("recentRatings"));
        rankTrendCol.setCellFactory(tc -> new TableCell<>() {
            private final javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(60, 20);
            @Override
            protected void updateItem(List<Double> ratings, boolean empty) {
                super.updateItem(ratings, empty);
                if (empty || ratings == null || ratings.size() < 2) {
                    setGraphic(null);
                } else {
                    javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
                    gc.clearRect(0, 0, 60, 20);

                    // 计算最大最小值以自适应缩放
                    double max = ratings.stream().max(Double::compareTo).orElse(1.0);
                    double min = ratings.stream().min(Double::compareTo).orElse(0.0);
                    double range = max - min == 0 ? 1 : max - min;

                    gc.setStroke(javafx.scene.paint.Color.web("#FDE047")); // 金色高亮线
                    gc.setLineWidth(1.5);
                    // 给线条加一点发光效果
                    gc.setEffect(new javafx.scene.effect.DropShadow(4, javafx.scene.paint.Color.web("#D4AF37")));

                    double xStep = 60.0 / (ratings.size() - 1);
                    gc.beginPath();
                    for (int i = 0; i < ratings.size(); i++) {
                        double x = i * xStep;
                        // Y轴翻转，数值越高Y越小 (顶部)
                        double y = 18 - ((ratings.get(i) - min) / range) * 16;
                        if (i == 0) gc.moveTo(x, y);
                        else gc.lineTo(x, y);
                    }
                    gc.stroke();

                    // 最后一个点画个发光小圆
                    double lastY = 18 - ((ratings.get(ratings.size()-1) - min) / range) * 16;
                    gc.setFill(javafx.scene.paint.Color.WHITE);
                    gc.fillOval(56, lastY - 2, 4, 4);

                    setGraphic(canvas);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
        rankingLegendLabel.setText("Penalty: Major=15, Minor=5. Auto Score now correctly skips matches with 0 points from average calculation.");
    }

    public void setDependencies(MainController mainController, MainApplication mainApp, Competition competition, boolean isHost) {
        this.mainController = mainController;
        this.mainApp = mainApp;
        this.currentCompetition = competition;
        this.isHost = isHost;

        // ★ 核心修复：根据当前用户是否为主机动态显示公式修改按钮
        if (editRatingButton != null) {
            editRatingButton.setVisible(isHost);
            editRatingButton.setManaged(isHost);
        }
    }

    // ★ 从 MainController 转移过来的按钮事件
    @FXML
    private void handleEditRating() {
        if (isHost) {
            try {
                mainApp.showFormulaEditView(currentCompetition);
                // 关闭编辑窗口后，通知 MainController 刷新整站数据（因为可能影响了排行榜计算）
                mainController.triggerDataRefreshAndBroadcast();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateData(List<TeamRanking> rankings, Competition comp) {
        this.currentCompetition = comp;
        rankingsTableView.setItems(FXCollections.observableArrayList(rankings));
        if (currentCompetition != null && isHost) {
            rankRatingCol.setText(currentCompetition.getRatingFormula().equals("total") ? "Rating" : "Rating *");
        }
    }

    public ObservableList<TeamRanking> getRankingsList() {
        return rankingsTableView.getItems();
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