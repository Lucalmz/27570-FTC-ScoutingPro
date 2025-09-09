package com.bear27570.ftc.scouting.models;

public class Membership {
    public enum Status { PENDING, APPROVED, CREATOR }

    private final String username;
    private final String competitionName;
    private final Status status;

    public Membership(String username, String competitionName, Status status) {
        this.username = username;
        this.competitionName = competitionName;
        this.status = status;
    }

    public String getUsername() { return username; }
    public String getCompetitionName() { return competitionName; }
    public Status getStatus() { return status; }
}