package com.bear27570.ftc.scouting.models;

public class PenaltyEntry {
    private int matchNumber;
    private String alliance; // "RED" or "BLUE"
    private int majorCount;
    private int minorCount;

    public PenaltyEntry(int matchNumber, String alliance, int majorCount, int minorCount) {
        this.matchNumber = matchNumber;
        this.alliance = alliance;
        this.majorCount = majorCount;
        this.minorCount = minorCount;
    }

    public int getMatchNumber() { return matchNumber; }
    public String getAlliance() { return alliance; }
    public int getMajorCount() { return majorCount; }
    public int getMinorCount() { return minorCount; }
}