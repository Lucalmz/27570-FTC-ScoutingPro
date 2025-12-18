package com.bear27570.ftc.scouting.models;

import java.io.Serializable;

public class Competition implements Serializable {
    private static final long serialVersionUID = 3L; // Version updated

    private final String name;
    private final String creatorUsername;
    private String ratingFormula; // 新增：评分公式
    private String hostAddress;

    public Competition(String name, String creatorUsername, String ratingFormula) {
        this.name = name;
        this.creatorUsername = creatorUsername;
        // 默认为 total (总分)
        this.ratingFormula = (ratingFormula == null || ratingFormula.isEmpty()) ? "total" : ratingFormula;
    }

    // 兼容旧构造逻辑
    public Competition(String name, String creatorUsername) {
        this(name, creatorUsername, "total");
    }

    public String getName() { return name; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getHostAddress() { return hostAddress; }
    public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }

    public String getRatingFormula() { return ratingFormula; }
    public void setRatingFormula(String ratingFormula) { this.ratingFormula = ratingFormula; }

    @Override
    public String toString() {
        return String.format("'%s' (Hosted by: %s)", name, creatorUsername);
    }
}