package com.bear27570.ftc.scouting.models;

public class PenaltyEntry {
    private int matchNumber;
    private int redMajor; // 红方犯规导致给蓝方加分的大罚次数 (30分)
    private int redMinor; // 红方犯规导致给蓝方加分的小罚次数 (10分)
    private int blueMajor;
    private int blueMinor;

    public PenaltyEntry(int matchNumber, int redMajor, int redMinor, int blueMajor, int blueMinor) {
        this.matchNumber = matchNumber;
        this.redMajor = redMajor;
        this.redMinor = redMinor;
        this.blueMajor = blueMajor;
        this.blueMinor = blueMinor;
    }

    public int getMatchNumber() { return matchNumber; }
    public int getRedMajor() { return redMajor; }
    public int getRedMinor() { return redMinor; }
    public int getBlueMajor() { return blueMajor; }
    public int getBlueMinor() { return blueMinor; }

    // 计算红方犯规送出的分数 (即蓝方获得的罚分)
    public int getRedPenaltyScore() { return (redMajor * 30) + (redMinor * 10); }
    // 计算蓝方犯规送出的分数 (即红方获得的罚分)
    public int getBluePenaltyScore() { return (blueMajor * 30) + (blueMinor * 10); }
}