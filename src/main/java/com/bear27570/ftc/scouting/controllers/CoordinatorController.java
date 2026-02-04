package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.services.DatabaseService;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CoordinatorController {
    @FXML private ListView<String> pendingListView;
    @FXML private ListView<String> approvedListView;
    private Timeline autoRefreshTimeline;
    private Stage dialogStage;
    private Competition competition;

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        startAutoRefresh();
        NetworkService.getInstance().setOnMemberJoinCallback(this::refreshLists);

        // 窗口关闭时移除回调，防止内存泄漏
        dialogStage.setOnHidden(e -> NetworkService.getInstance().setOnMemberJoinCallback(null));
        // 当窗口关闭时停止刷新，防止资源浪费
        dialogStage.setOnCloseRequest(e -> stopAutoRefresh());
    }
    private void startAutoRefresh() {
        if (autoRefreshTimeline != null) return;
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
            refreshLists();
        }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
        }
    }
    private void refreshLists() {
        pendingListView.setItems(FXCollections.observableArrayList(
                DatabaseService.getMembersByStatus(competition.getName(), Membership.Status.PENDING)));
        approvedListView.setItems(FXCollections.observableArrayList(
                DatabaseService.getMembersByStatus(competition.getName(), Membership.Status.APPROVED)));
    }


    @FXML
    private void handleDeny() {
        String selected = pendingListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            DatabaseService.removeMembership(selected, competition.getName());
            refreshLists();
        }
    }
    @FXML
    private void handleApprove() {
        String selected = pendingListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // 1. 更新数据库
            DatabaseService.updateMembershipStatus(selected, competition.getName(), Membership.Status.APPROVED);
            // 2. 通过网络通知 Client
            NetworkService.getInstance().approveClient(selected);
            refreshLists();
        }
    }
    @FXML
    private void handleKick() {
        String selected = approvedListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            DatabaseService.removeMembership(selected, competition.getName());
            refreshLists();
        }
    }

    @FXML
    private void handleClose() {
        stopAutoRefresh();dialogStage.close();
    }
}