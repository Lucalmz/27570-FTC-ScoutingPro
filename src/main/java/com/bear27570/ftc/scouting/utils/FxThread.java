// File: src/main/java/com/bear27570/ftc/scouting/utils/FxThread.java
package com.bear27570.ftc.scouting.utils;

import javafx.application.Platform;

public class FxThread {
    /**
     * 优雅的防面条代码神器：自动判断当前线程，如果在后台线程则抛入队列，如果在 UI 线程则直接执行。
     */
    public static void run(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}