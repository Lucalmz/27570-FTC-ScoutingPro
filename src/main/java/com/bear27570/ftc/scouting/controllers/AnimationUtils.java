// File: src/main/java/com/bear27570/ftc/scouting/controllers/AnimationUtils.java
package com.bear27570.ftc.scouting.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class AnimationUtils {

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

    public static void playSmoothEntrance(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateY(20);
        node.setScaleX(0.98);
        node.setScaleY(0.98);

        FadeTransition ft = new FadeTransition(Duration.millis(350), node);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(350), node);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition st = new ScaleTransition(Duration.millis(350), node);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt, st);
        pt.play();
    }

    public static void attachLightBarAnimation(TabPane tabPane) {
        Platform.runLater(() -> {
            StackPane headerArea = (StackPane) tabPane.lookup(".tab-header-area");
            if (headerArea == null) return;

            // --- 节点 A：核心高亮光条 ---
            Rectangle lightBar = new Rectangle(0, 3, Color.web("#FDE047"));
            lightBar.setEffect(new DropShadow(8, Color.web("#FDE047")));
            lightBar.setArcWidth(3);
            lightBar.setArcHeight(3);

            // --- 节点 B：下落式弥散光影 (Glow Box) ---
            LinearGradient glowGradient = new LinearGradient(
                    0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FDE047", 0.35)),
                    new Stop(1, Color.web("#FDE047", 0.0))
            );
            Rectangle glowBox = new Rectangle(0, 35);
            glowBox.setFill(glowGradient);
            glowBox.setMouseTransparent(true);
            glowBox.setTranslateY(3);

            StackPane.setAlignment(glowBox, Pos.TOP_LEFT);
            StackPane.setAlignment(lightBar, Pos.TOP_LEFT);
            headerArea.getChildren().addAll(glowBox, lightBar);

            Runnable updateBar = () -> {
                Pane headersRegion = (Pane) tabPane.lookup(".headers-region");
                if (headersRegion != null) {
                    int index = tabPane.getSelectionModel().getSelectedIndex();
                    if (index >= 0 && index < headersRegion.getChildren().size()) {
                        Node tabNode = headersRegion.getChildren().get(index);

                        // ★ 绝对坐标系转换，解决各种 Padding 导致的错位问题
                        javafx.geometry.Bounds localBounds = tabNode.getBoundsInLocal();
                        javafx.geometry.Bounds sceneBounds = tabNode.localToScene(localBounds);
                        javafx.geometry.Bounds targetBoundsInHeader = headerArea.sceneToLocal(sceneBounds);

                        if (targetBoundsInHeader != null) {
                            double targetX = targetBoundsInHeader.getMinX();
                            double targetWidth = targetBoundsInHeader.getWidth();

                            Timeline timeline = new Timeline(
                                    new KeyFrame(Duration.millis(300),
                                            new KeyValue(lightBar.translateXProperty(), targetX, Interpolator.EASE_BOTH),
                                            new KeyValue(lightBar.widthProperty(), targetWidth, Interpolator.EASE_BOTH),
                                            new KeyValue(glowBox.translateXProperty(), targetX, Interpolator.EASE_BOTH),
                                            new KeyValue(glowBox.widthProperty(), targetWidth, Interpolator.EASE_BOTH)
                                    )
                            );
                            timeline.play();
                        }
                    }
                }
            };

            tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> updateBar.run());
            tabPane.widthProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateBar));
            Platform.runLater(updateBar);
        });
    }
}