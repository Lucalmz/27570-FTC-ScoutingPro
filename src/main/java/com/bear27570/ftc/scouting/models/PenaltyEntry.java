// File: PenaltyEntry.java
package com.bear27570.ftc.scouting.models;

public class PenaltyEntry {
    private int matchNumber;
    private String alliance; // "RED" or "BLUE"
    private int majorCount;
    private int minorCount;
    // ★ 新增：官方最终总分
    private int officialScore;

    public PenaltyEntry(int matchNumber, String alliance, int majorCount, int minorCount, int officialScore) {
        this.matchNumber = matchNumber;
        this.alliance = alliance;
        this.majorCount = majorCount;
        this.minorCount = minorCount;
        this.officialScore = officialScore;
    }

    public int getMatchNumber() { return matchNumber; }
    public String getAlliance() { return alliance; }
    public int getMajorCount() { return majorCount; }
    public int getMinorCount() { return minorCount; }
    public int getOfficialScore() { return officialScore; } // Getter
}