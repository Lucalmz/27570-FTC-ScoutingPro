// File: src/main/java/com/bear27570/ftc/scouting/controllers/AnimationUtils.java
package com.bear27570.ftc.scouting.controllers;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class AnimationUtils {

    // 1. 数字滚轮动效 (Odometer)
    public static void animateNumber(Label label, double start, double end, String format, int durationMs) {
        DoubleProperty value = new SimpleDoubleProperty(start);
        value.addListener((obs, oldVal, newVal) -> {
            label.setText(String.format(format, newVal.doubleValue()));
        });
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(value, start)),
                new KeyFrame(Duration.millis(durationMs), new KeyValue(value, end, Interpolator.EASE_OUT))
        );
        timeline.play();
    }

    // 2. 丝滑浮现动效 (Fade & Translate)
    public static void playSmoothEntrance(Node node) {
        node.setOpacity(0);
        node.setTranslateY(20); // 初始下移 20px

        FadeTransition ft = new FadeTransition(Duration.millis(400), node);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(400), node);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.play();
    }
}