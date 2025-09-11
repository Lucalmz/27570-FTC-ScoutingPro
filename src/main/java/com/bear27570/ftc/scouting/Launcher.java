package com.bear27570.ftc.scouting;

// 这个类是解决 maven-shade-plugin 与 JavaFX 冲突的标准解决方案
public class Launcher {
    public static void main(String[] args) {
        MainApplication.main(args);
    }
}