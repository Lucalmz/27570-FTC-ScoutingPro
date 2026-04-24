// File: src/main/java/com/bear27570/ftc/scouting/models/Competition.java
package com.bear27570.ftc.scouting.models;

import java.io.Serializable;

public class Competition implements Serializable {
    private static final long serialVersionUID = 5L;

    // 1. 去掉 final 修饰符，以便 JDBI 反射写入
    private String name;
    private String creatorUsername;
    private String ratingFormula;
    private String hostAddress;
    private String bannedTeams = "";

    private int eventSeason;
    private String eventCode;
    private String officialEventName;

    // 2. 必须添加的无参构造函数 (供 JDBI / JSON 序列化框架使用)
    public Competition() {
    }

    public Competition(String name, String creatorUsername, String ratingFormula) {
        this.name = name;
        this.creatorUsername = creatorUsername;
        this.ratingFormula = (ratingFormula == null || ratingFormula.isEmpty()) ? "total" : ratingFormula;
    }

    public Competition(String name, String creatorUsername) {
        this(name, creatorUsername, "total");
    }

    // --- Getters ---
    public String getName() { return name; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getHostAddress() { return hostAddress; }
    public String getRatingFormula() { return ratingFormula; }
    public int getEventSeason() { return eventSeason; }
    public String getEventCode() { return eventCode; }
    public String getOfficialEventName() { return officialEventName; }
    public String getBannedTeams() { return bannedTeams; }

    // --- Setters (3. 补全之前因为 final 缺失的 Setters) ---
    public void setName(String name) { this.name = name; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }
    public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }
    public void setRatingFormula(String ratingFormula) { this.ratingFormula = ratingFormula; }
    public void setEventSeason(int eventSeason) { this.eventSeason = eventSeason; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public void setOfficialEventName(String officialEventName) { this.officialEventName = officialEventName; }
    public void setBannedTeams(String bannedTeams) { this.bannedTeams = bannedTeams; }

    public boolean isTeamBanned(int teamNumber) {
        if (bannedTeams == null || bannedTeams.isEmpty()) return false;
        String[] teams = bannedTeams.split(",");
        for (String t : teams) {
            if (t.trim().equals(String.valueOf(teamNumber))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("'%s' (Hosted by: %s)", name, creatorUsername);
    }
}