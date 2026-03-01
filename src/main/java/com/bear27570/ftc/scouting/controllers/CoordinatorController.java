package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
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
    private MembershipRepository membershipRepository;

    // --- 核心注入方法 ---
    public void setDependencies(Stage dialogStage, Competition competition, MembershipRepository membershipRepository) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        this.membershipRepository = membershipRepository;

        refreshLists();
        startAutoRefresh();

        NetworkService.getInstance().setOnMemberJoinCallback(this::refreshLists);

        dialogStage.setOnHidden(e -> {
            stopAutoRefresh();
            NetworkService.getInstance().setOnMemberJoinCallback(null);
        });
    }

    private void startAutoRefresh() {
        if (autoRefreshTimeline != null) return;
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> refreshLists()));
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
        // --- 使用注入的 Repo ---
        pendingListView.setItems(FXCollections.observableArrayList(
                membershipRepository.getMembersByStatus(competition.getName(), Membership.Status.PENDING)));
        approvedListView.setItems(FXCollections.observableArrayList(
                membershipRepository.getMembersByStatus(competition.getName(), Membership.Status.APPROVED)));
    }

    @FXML
    private void handleApprove() {
        String selected = pendingListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            membershipRepository.updateMembershipStatus(selected, competition.getName(), Membership.Status.APPROVED);
            NetworkService.getInstance().approveClient(selected);
            refreshLists();
        }
    }

    @FXML
    private void handleDeny() {
        String selected = pendingListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            membershipRepository.removeMembership(selected, competition.getName());
            refreshLists();
        }
    }

    @FXML
    private void handleKick() {
        String selected = approvedListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            membershipRepository.removeMembership(selected, competition.getName());
            refreshLists();
        }
    }

    @FXML
    private void handleClose() {
        dialogStage.close();
    }
}