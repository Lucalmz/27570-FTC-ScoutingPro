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
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
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
        node.setTranslateY(20);
        node.setScaleX(0.98);
        node.setScaleY(0.98);
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);

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

            Rectangle lightBar = new Rectangle(0, 3, Color.web("#FDE047"));
            lightBar.setEffect(new DropShadow(8, Color.web("#FDE047")));
            lightBar.setArcWidth(3);
            lightBar.setArcHeight(3);

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
                        javafx.geometry.Bounds targetBoundsInHeader = headerArea.sceneToLocal(tabNode.localToScene(tabNode.getBoundsInLocal()));
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

    /**
     * 顶级赛博悬浮胶囊通知入场与退场动画
     */
    public static void playWelcomeToast(Node toastNode) {
        toastNode.setTranslateY(-60);
        toastNode.setOpacity(0);
        toastNode.setVisible(true);

        ParallelTransition entry = new ParallelTransition();
        TranslateTransition ttIn = new TranslateTransition(Duration.millis(650), toastNode);
        ttIn.setToY(30);
        ttIn.setInterpolator(Interpolator.SPLINE(0.17, 0.89, 0.32, 1.0));
        FadeTransition ftIn = new FadeTransition(Duration.millis(400), toastNode);
        ftIn.setToValue(1.0);
        entry.getChildren().addAll(ttIn, ftIn);

        ParallelTransition exit = new ParallelTransition();
        TranslateTransition ttOut = new TranslateTransition(Duration.millis(500), toastNode);
        ttOut.setToY(-60);
        ttOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition ftOut = new FadeTransition(Duration.millis(400), toastNode);
        ftOut.setToValue(0.0);
        exit.getChildren().addAll(ttOut, ftOut);

        SequentialTransition sequence = new SequentialTransition(entry, new PauseTransition(Duration.seconds(2.5)), exit);
        sequence.setOnFinished(e -> toastNode.setVisible(false));
        sequence.play();
    }

    // =========================================================================
    // 动态流光边框 + 香槟金玻璃底
    // =========================================================================

    public static StackPane wrapWithGlowingBorder(Region content, Color glowColor, double cornerRadius, double borderWidth) {
        StackPane wrapper = new StackPane();
        wrapper.setPickOnBounds(false);

        // 🚀 核心修复 1：彻底拔掉原有的深色 CSS 伪装，换上纯正的香槟金类！
        content.getStyleClass().removeAll("mac-card", "card", "cyber-glass-card", "panel-inner");
        content.getStyleClass().add("champagne-glass-card");

        // 2. 创建超大旋转光斑光源
        Rectangle rotatingLight = new Rectangle();
        rotatingLight.widthProperty().bind(content.widthProperty().multiply(2.2));
        rotatingLight.heightProperty().bind(content.heightProperty().multiply(2.2));

        LinearGradient lightGradient = new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.48, Color.TRANSPARENT),
                new Stop(0.5, glowColor),
                new Stop(0.52, Color.TRANSPARENT),
                new Stop(1, Color.TRANSPARENT)
        );
        rotatingLight.setFill(lightGradient);

        RotateTransition rt = new RotateTransition(Duration.seconds(4.0), rotatingLight);
        rt.setByAngle(360);
        rt.setInterpolator(Interpolator.LINEAR);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.play();

        // 3. 镂空剪裁遮罩 (让光源只在边框处露出来)
        Rectangle outerRect = new Rectangle();
        outerRect.widthProperty().bind(content.widthProperty());
        outerRect.heightProperty().bind(content.heightProperty());
        outerRect.setArcWidth(cornerRadius * 2);
        outerRect.setArcHeight(cornerRadius * 2);

        Rectangle innerRect = new Rectangle();
        innerRect.widthProperty().bind(content.widthProperty().subtract(borderWidth * 2));
        innerRect.heightProperty().bind(content.heightProperty().subtract(borderWidth * 2));
        innerRect.setArcWidth((cornerRadius - borderWidth) * 2);
        innerRect.setArcHeight((cornerRadius - borderWidth) * 2);

        innerRect.xProperty().bind(outerRect.xProperty().add(borderWidth));
        innerRect.yProperty().bind(outerRect.yProperty().add(borderWidth));

        Shape borderMask = Shape.subtract(outerRect, innerRect);
        rotatingLight.setClip(borderMask);

        DropShadow glowEffect = new DropShadow(BlurType.GAUSSIAN, glowColor, 15, 0.45, 0, 0);
        rotatingLight.setEffect(glowEffect);

        // 🚀 核心修复 2：因为 content 现在拥有 .champagne-glass-card，其自身的高不透明度
        // 已经完美充当了 Diffuser 挡板，我们不再需要插入多余的 Region 了。
        wrapper.getChildren().addAll(rotatingLight, content);
        return wrapper;
    }

    public static void playShakeAnimation(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0f); tt.setByX(8f); tt.setCycleCount(6); tt.setAutoReverse(true);
        tt.setOnFinished(e -> node.setTranslateX(0));
        tt.playFromStart();
    }

    public static void attachSpatialButtonAnimation(Node button, Color glowColor) {
        DropShadow glow = new DropShadow(15, glowColor);
        glow.setInput(new DropShadow(5, Color.BLACK));
        button.setOnMouseEntered(e -> {
            button.setEffect(glow);
            ScaleTransition st = new ScaleTransition(Duration.millis(150), button);
            st.setToX(1.03); st.setToY(1.03); st.play();
        });
        button.setOnMouseExited(e -> {
            button.setEffect(null);
            ScaleTransition st = new ScaleTransition(Duration.millis(150), button);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
    }

    public static void playLoginSuccessTransition(StackPane root, Node cardNode, Stage stage, String username, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(350), root);
        ft.setFromValue(1.0); ft.setToValue(0.0);
        ScaleTransition st = new ScaleTransition(Duration.millis(350), cardNode);
        st.setToX(1.1); st.setToY(1.1);
        ParallelTransition pt = new ParallelTransition(ft, st);
        pt.setInterpolator(Interpolator.EASE_IN);
        pt.setOnFinished(e -> onFinished.run());
        pt.play();
    }
}