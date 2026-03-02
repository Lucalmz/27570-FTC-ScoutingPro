package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.repository.MembershipRepository;
import com.bear27570.ftc.scouting.services.NetworkService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

public class CoordinatorController {

    @FXML private ListView<String> pendingListView;
    @FXML private ListView<String> approvedListView;

    private Timeline autoRefreshTimeline;
    private Stage dialogStage;
    private Competition competition;
    private MembershipRepository membershipRepository;

    public void setDependencies(Stage dialogStage, Competition competition, MembershipRepository membershipRepository) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        this.membershipRepository = membershipRepository;

        System.out.println("【UI调试】管理窗口初始化: " + competition.getName());

        refreshLists();
        startAutoRefresh();

        // 绑定网络回调
        NetworkService.getInstance().setOnMemberJoinCallback(() -> {
            System.out.println("【UI调试】收到新成员加入通知，正在刷新UI...");
            Platform.runLater(this::refreshLists);
        });

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
        if (membershipRepository == null || competition == null) return;

        try {
            // ★ 修复点：数据库直接返回的就是 List<String> (用户名)，不需要再转换了
            // 之前的错误是因为强行把 List<String> 赋值给了 List<Membership>
            List<String> pendingNames = membershipRepository.getMembersByStatus(competition.getName(), Membership.Status.PENDING);
            List<String> approvedNames = membershipRepository.getMembersByStatus(competition.getName(), Membership.Status.APPROVED);

            // 直接设置到 ListView
            pendingListView.setItems(FXCollections.observableArrayList(pendingNames));
            approvedListView.setItems(FXCollections.observableArrayList(approvedNames));

            // 简单调试日志
            if (pendingNames != null && !pendingNames.isEmpty()) {
                System.out.println("【UI调试】刷新列表，待批准: " + pendingNames);
            }

        } catch (Exception e) {
            System.err.println("【UI调试】列表刷新失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleApprove() {
        String username = pendingListView.getSelectionModel().getSelectedItem();
        if (username != null) {
            System.out.println("【UI调试】批准: " + username);
            membershipRepository.updateMembershipStatus(username, competition.getName(), Membership.Status.APPROVED);
            NetworkService.getInstance().approveClient(username);
            refreshLists();
        }
    }

    @FXML
    private void handleDeny() {
        String username = pendingListView.getSelectionModel().getSelectedItem();
        if (username != null) {
            System.out.println("【UI调试】拒绝: " + username);
            membershipRepository.removeMembership(username, competition.getName());
            refreshLists();
        }
    }

    @FXML
    private void handleKick() {
        String username = approvedListView.getSelectionModel().getSelectedItem();
        if (username != null) {
            System.out.println("【UI调试】踢出: " + username);
            membershipRepository.removeMembership(username, competition.getName());
            refreshLists();
        }
    }

    @FXML
    private void handleClose() {
        dialogStage.close();
    }
}