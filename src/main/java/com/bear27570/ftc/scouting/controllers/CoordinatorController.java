package com.bear27570.ftc.scouting.controllers;

import com.bear27570.ftc.scouting.models.Competition;
import com.bear27570.ftc.scouting.models.Membership;
import com.bear27570.ftc.scouting.services.DatabaseService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public class CoordinatorController {
    @FXML private ListView<String> pendingListView;
    @FXML private ListView<String> approvedListView;

    private Stage dialogStage;
    private Competition competition;

    public void setDialogStage(Stage dialogStage, Competition competition) {
        this.dialogStage = dialogStage;
        this.competition = competition;
        refreshLists();
    }

    private void refreshLists() {
        pendingListView.setItems(FXCollections.observableArrayList(
                DatabaseService.getMembersByStatus(competition.getName(), Membership.Status.PENDING)));
        approvedListView.setItems(FXCollections.observableArrayList(
                DatabaseService.getMembersByStatus(competition.getName(), Membership.Status.APPROVED)));
    }

    @FXML
    private void handleApprove() {
        String selected = pendingListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            DatabaseService.updateMembershipStatus(selected, competition.getName(), Membership.Status.APPROVED);
            refreshLists();
        }
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
    private void handleKick() {
        String selected = approvedListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            DatabaseService.removeMembership(selected, competition.getName());
            refreshLists();
        }
    }

    @FXML
    private void handleClose() {
        dialogStage.close();
    }
}