// File: src/main/java/com/bear27570/ftc/scouting/controllers/AnimationUtils.java
package com.bear27570.ftc.scouting.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
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
        node.setTranslateY(15);
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);

        FadeTransition ft = new FadeTransition(Duration.millis(250), node);
        ft.setToValue(1.0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(250), node);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setOnFinished(e -> {
            node.setCache(false);
            node.setCacheHint(CacheHint.DEFAULT);
        });
        Platform.runLater(pt::play);
    }

    public static void attachLightBarAnimation(TabPane tabPane) {
        Platform.runLater(() -> {
            StackPane headerArea = (StackPane) tabPane.lookup(".tab-header-area");
            if (headerArea == null) return;

            Rectangle lightBar = new Rectangle(0, 3, Color.web("#FAFAFA"));
            lightBar.setArcWidth(3);
            lightBar.setArcHeight(3);
            lightBar.setTranslateY(2);

            StackPane.setAlignment(lightBar, Pos.BOTTOM_LEFT);
            headerArea.getChildren().add(lightBar);

            Runnable updateBar = () -> {
                Pane headersRegion = (Pane) tabPane.lookup(".headers-region");
                if (headersRegion != null) {
                    int index = tabPane.getSelectionModel().getSelectedIndex();
                    if (index >= 0 && index < headersRegion.getChildren().size()) {
                        Node tabNode = headersRegion.getChildren().get(index);
                        javafx.geometry.Bounds targetBounds = headerArea.sceneToLocal(tabNode.localToScene(tabNode.getBoundsInLocal()));
                        if (targetBounds != null) {
                            Timeline timeline = new Timeline(
                                    new KeyFrame(Duration.millis(200),
                                            new KeyValue(lightBar.translateXProperty(), targetBounds.getMinX(), Interpolator.EASE_BOTH),
                                            new KeyValue(lightBar.widthProperty(), targetBounds.getWidth(), Interpolator.EASE_BOTH)
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

    public static void playWelcomeToast(Node toastNode) {
        toastNode.setTranslateY(-60);
        toastNode.setOpacity(0);
        toastNode.setVisible(true);

        ParallelTransition entry = new ParallelTransition();
        TranslateTransition ttIn = new TranslateTransition(Duration.millis(500), toastNode);
        ttIn.setToY(30);
        // ✅ 这里修复了 IllegalArgumentException: Control point coordinates must all be in range [0,1]
        ttIn.setInterpolator(Interpolator.SPLINE(0.17, 0.89, 0.32, 1.0));
        FadeTransition ftIn = new FadeTransition(Duration.millis(300), toastNode);
        ftIn.setToValue(1.0);
        entry.getChildren().addAll(ttIn, ftIn);

        ParallelTransition exit = new ParallelTransition();
        TranslateTransition ttOut = new TranslateTransition(Duration.millis(400), toastNode);
        ttOut.setToY(-60);
        ttOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition ftOut = new FadeTransition(Duration.millis(300), toastNode);
        ftOut.setToValue(0.0);
        exit.getChildren().addAll(ttOut, ftOut);

        SequentialTransition sequence = new SequentialTransition(entry, new PauseTransition(Duration.seconds(2.5)), exit);
        sequence.setOnFinished(e -> toastNode.setVisible(false));
        sequence.play();
    }

    public static void playShakeAnimation(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0f); tt.setByX(6f); tt.setCycleCount(6); tt.setAutoReverse(true);
        tt.setOnFinished(e -> node.setTranslateX(0));
        tt.playFromStart();
    }

    /**
     * 极简工业风：扎实的物理按压反馈
     */
    public static void attachSolidPressAnimation(Node button) {
        button.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(50), button);
            st.setToX(0.96);
            st.setToY(0.96);
            st.play();
        });
        button.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
    }

    public static void playLoginSuccessTransition(StackPane root, Node cardNode, Stage stage, String username, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), root);
        ft.setFromValue(1.0); ft.setToValue(0.0);
        ScaleTransition st = new ScaleTransition(Duration.millis(300), cardNode);
        st.setToX(1.05); st.setToY(1.05);
        ParallelTransition pt = new ParallelTransition(ft, st);
        pt.setInterpolator(Interpolator.EASE_IN);
        pt.setOnFinished(e -> onFinished.run());
        pt.play();
    }
}