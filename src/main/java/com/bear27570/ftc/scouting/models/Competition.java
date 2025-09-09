package com.bear27570.ftc.scouting.models;

public class Competition {
    private final String name;
    private final String creatorUsername;

    public Competition(String name, String creatorUsername) {
        this.name = name;
        this.creatorUsername = creatorUsername;
    }

    public String getName() { return name; }
    public String getCreatorUsername() { return creatorUsername; }

    @Override
    public String toString() {
        return String.format("'%s' (Created by: %s)", name, creatorUsername);
    }
}