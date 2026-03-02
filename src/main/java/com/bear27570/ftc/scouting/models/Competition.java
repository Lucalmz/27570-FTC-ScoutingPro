package com.bear27570.ftc.scouting.models;

import java.io.Serializable;

public class Competition implements Serializable {
    private static final long serialVersionUID = 4L; // 更新版本号

    private final String name;
    private final String creatorUsername;
    private String ratingFormula;
    private String hostAddress;

    // 新增：FTCScout 绑定信息
    private int eventSeason;
    private String eventCode;
    private String officialEventName;

    public Competition(String name, String creatorUsername, String ratingFormula) {
        this.name = name;
        this.creatorUsername = creatorUsername;
        this.ratingFormula = (ratingFormula == null || ratingFormula.isEmpty()) ? "total" : ratingFormula;
    }

    public Competition(String name, String creatorUsername) {
        this(name, creatorUsername, "total");
    }

    public String getName() { return name; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getHostAddress() { return hostAddress; }
    public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }
    public String getRatingFormula() { return ratingFormula; }
    public void setRatingFormula(String ratingFormula) { this.ratingFormula = ratingFormula; }

    // --- 新增的 Getters & Setters ---
    public int getEventSeason() { return eventSeason; }
    public void setEventSeason(int eventSeason) { this.eventSeason = eventSeason; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public String getOfficialEventName() { return officialEventName; }
    public void setOfficialEventName(String officialEventName) { this.officialEventName = officialEventName; }

    @Override
    public String toString() {
        return String.format("'%s' (Hosted by: %s)", name, creatorUsername);
    }
}