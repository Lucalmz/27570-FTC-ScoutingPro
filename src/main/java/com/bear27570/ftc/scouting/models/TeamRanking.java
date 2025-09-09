package com.bear27570.ftc.scouting.models;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.Serializable;

public class TeamRanking implements Serializable {
    private final int teamNumber;
    private int matchesPlayed = 0;
    private double avgAutoArtifacts = 0;
    private double avgTeleopArtifacts = 0;
    private boolean canSequence = false;
    private boolean l2Capable = false;

    private static final long serialVersionUID = 4L;

    public TeamRanking(int teamNumber) {
        this.teamNumber = teamNumber;
    }

    public void addMatchResult(int auto, int teleop, boolean sequence, boolean climb) {
        double totalAuto = avgAutoArtifacts * matchesPlayed;
        double totalTeleop = avgTeleopArtifacts * matchesPlayed;
        matchesPlayed++;
        avgAutoArtifacts = (totalAuto + auto) / matchesPlayed;
        avgTeleopArtifacts = (totalTeleop + teleop) / matchesPlayed;
        if (sequence) this.canSequence = true;
        if (climb) this.l2Capable = true;
    }

    // Getters for TableView
    public int getTeamNumber() { return teamNumber; }
    public int getMatchesPlayed() { return matchesPlayed; }
    public double getAvgAutoArtifacts() { return avgAutoArtifacts; }
    public double getAvgTeleopArtifacts() { return avgTeleopArtifacts; }
    public String getCanSequence() { return canSequence ? "Yes" : "No"; }
    public String getL2Capable() { return l2Capable ? "Yes" : "No"; }
}