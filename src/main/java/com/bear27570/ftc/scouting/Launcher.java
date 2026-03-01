// File: Launcher.java
package com.bear27570.ftc.scouting;

import javafx.application.Application;

/**
 * 独立的启动类，用于绕过 Java 11+ 的模块化检查。
 * 解决 "JavaFX runtime components are missing" 以及打包后双击无法运行的问题。
 */
public class Launcher {

    public static void main(String[] args) {
        // 显式调用 JavaFX 的启动方法，加载我们的主程序应用类
        Application.launch(MainApplication.class, args);
    }

}