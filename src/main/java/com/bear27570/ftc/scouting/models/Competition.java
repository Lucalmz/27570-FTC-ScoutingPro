package com.bear27570.ftc.scouting.models;

import java.io.Serializable; // Import this

public class Competition implements Serializable { // Add "implements Serializable"
    private static final long serialVersionUID = 2L; // Good practice for versioning

    private final String name;
    private final String creatorUsername;
    private String hostAddress; // Important for clients to know where to connect

    public Competition(String name, String creatorUsername) {
        this.name = name;
        this.creatorUsername = creatorUsername;
    }

    public String getName() { return name; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getHostAddress() { return hostAddress; }
    public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }

    @Override
    public String toString() {
        return String.format("'%s' (Hosted by: %s)", name, creatorUsername);
    }
}