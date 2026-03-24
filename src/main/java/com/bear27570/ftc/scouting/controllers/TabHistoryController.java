// File: src/main/java/com/bear27570/ftc/scouting/controllers/TabHistoryController.java
package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.ScoreEntry;
import com.bear27570.ftc.scouting.services.domain.MatchDataService;
import com.bear27570.ftc.scouting.viewmodels.SharedDataViewModel;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

public class TabHistoryController {

    @FXML private TextField searchField;
    @FXML private TableView<ScoreEntry> historyTableView;
    @FXML private TableColumn<ScoreEntry, ScoreEntry.SyncStatus> histSyncCol;
    @FXML private TableColumn<ScoreEntry, Integer> histMatchCol, histTotalCol;
    @FXML private TableColumn<ScoreEntry, String> histAllianceCol, histTeamsCol, histSubmitterCol, histTimeCol;
    @FXML private TableColumn<ScoreEntry, Void> histActionsCol;

    private MainController mainController;
    private MatchDataService matchDataService;
    private Competition currentCompetition;
    private String currentUsername;
    private boolean isHost;

    private SharedDataViewModel viewModel;

    @FXML
    public void initialize() {
        histMatchCol.setCellValueFactory(new PropertyValueFactory<>("matchNumber"));
        histAllianceCol.setCellValueFactory(new PropertyValueFactory<>("alliance"));
        histTeamsCol.setCellValueFactory(new PropertyValueFactory<>("teams"));
        histTotalCol.setCellValueFactory(new PropertyValueFactory<>("totalScore"));

        histSubmitterCol.setCellValueFactory(new PropertyValueFactory<>("submitter"));
        histSubmitterCol.setCellFactory(column -> new TableCell<ScoreEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null); setStyle("");
                } else {
                    double weight = viewModel != null ? viewModel.getReliabilities().getOrDefault(item, 1.0) : 1.0;
                    if (weight < 1.0) {
                        setText(item + " (Low Rel)");
                        setStyle("-fx-text-fill: #F87171 !important; -fx-font-weight: bold;");
                        FontIcon warningIcon = new FontIcon("fth-alert-triangle");
                        warningIcon.setIconSize(14);
                        warningIcon.setIconColor(Color.web("#F87171"));
                        setGraphic(warningIcon);
                    } else {
                        setText(item); setGraphic(null); setStyle("");
                    }
                }
            }
        });
        histTimeCol.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));

        histSyncCol.setCellValueFactory(new PropertyValueFactory<>("syncStatus"));
        histSyncCol.setCellFactory(param -> new TableCell<>() {
            private final Label badge = new Label();
            {
                setAlignment(javafx.geometry.Pos.CENTER);
                badge.setPrefWidth(70);
                badge.setAlignment(javafx.geometry.Pos.CENTER);
            }
            @Override protected void updateItem(ScoreEntry.SyncStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); } else {
                    badge.setText(status.name());
                    badge.getStyleClass().removeAll("cyber-badge-success", "cyber-badge-danger");
                    if (status == ScoreEntry.SyncStatus.SYNCED) {
                        badge.getStyleClass().add("cyber-badge-success");
                    } else {
                        badge.getStyleClass().add("cyber-badge-danger");
                    }
                    setGraphic(badge);
                }
            }
        });
        histActionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(8, editBtn, delBtn);
            {
                pane.setAlignment(javafx.geometry.Pos.CENTER);
                editBtn.getStyleClass().addAll("accent", "button-small");
                delBtn.getStyleClass().addAll("danger", "button-small");
                editBtn.setOnAction(e -> handleEditAction(getTableView().getItems().get(getIndex())));
                delBtn.setOnAction(e -> handleDeleteAction(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) { setGraphic(null); } else {
                    ScoreEntry entry = getTableView().getItems().get(getIndex());
                    boolean canEdit = isHost || entry.getSubmitter().equals(currentUsername);
                    boolean canDel = isHost;

                    editBtn.setVisible(canEdit); editBtn.setManaged(canEdit);
                    delBtn.setVisible(canDel); delBtn.setManaged(canDel);

                    if(!canEdit && !canDel) setGraphic(null);
                    else setGraphic(pane);
                }
            }
        });
    }

    public void setDependencies(MainController mainController, MatchDataService matchDataService,
                                Competition competition, String username, boolean isHost, SharedDataViewModel viewModel) {
        this.mainController = mainController;
        this.matchDataService = matchDataService;
        this.currentCompetition = competition;
        this.currentUsername = username;
        this.isHost = isHost;
        this.viewModel = viewModel;

        // 使用 ViewModel 的 ObservableList 创建过滤器，数据变动 UI 自动刷新
        FilteredList<ScoreEntry> filtered = new FilteredList<>(viewModel.getHistoryList(), p -> true);
        searchField.textProperty().addListener((o, old, newVal) -> filtered.setPredicate(s -> {
            if (newVal == null || newVal.isEmpty()) return true;
            String low = newVal.toLowerCase();
            return String.valueOf(s.getMatchNumber()).contains(low) || s.getTeams().contains(low);
        }));
        historyTableView.setItems(filtered);
    }

    public ObservableList<ScoreEntry> getScoreHistoryList() {
        return viewModel != null ? viewModel.getHistoryList() : javafx.collections.FXCollections.emptyObservableList();
    }

    private void handleEditAction(ScoreEntry selected) {
        mainController.requestEditScore(selected);
    }

    private void handleDeleteAction(ScoreEntry selected) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Match " + selected.getMatchNumber() + "?", ButtonType.YES, ButtonType.NO);

        DialogPane dialogPane = alert.getDialogPane();
        try {
            dialogPane.getStylesheets().add(getClass().getResource("/com/bear27570/ftc/scouting/styles/style.css").toExternalForm());
            dialogPane.getStyleClass().add("mac-card");
        } catch (Exception e) {}

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (isHost) {
                    matchDataService.deleteScore(selected.getId());
                    mainController.triggerDataRefreshAndBroadcast();
                }
            }
        });
    }
}