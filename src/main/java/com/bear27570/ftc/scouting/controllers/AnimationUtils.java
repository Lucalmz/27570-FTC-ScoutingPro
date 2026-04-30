// File: src/main/java/com/bear27570/ftc/scouting/controllers/AnimationUtils.java
package com.bear27570.ftc.scouting.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
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

    private static ButtonBase currentlyPressedButton = null;

    public static void enableGlobalButtonAnimations(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() instanceof Node) {
                ButtonBase btn = findButtonParent((Node) e.getTarget());
                if (btn != null) {
                    currentlyPressedButton = btn;
                    ScaleTransition st = new ScaleTransition(Duration.millis(50), btn);
                    st.setToX(0.96);
                    st.setToY(0.96);
                    st.play();
                }
            }
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (currentlyPressedButton != null) {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), currentlyPressedButton);
                st.setToX(1.0);
                st.setToY(1.0);
                st.setInterpolator(Interpolator.EASE_OUT);
                st.play();
                currentlyPressedButton = null;
            }
        });
    }

    private static ButtonBase findButtonParent(Node node) {
        while (node != null) {
            if (node instanceof ButtonBase) {
                return (ButtonBase) node;
            }
            node = node.getParent();
        }
        return null;
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
        tt.setFromX(0f);
        tt.setByX(6f);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setOnFinished(e -> node.setTranslateX(0));
        tt.playFromStart();
    }

    public static void playLoginSuccessTransition(StackPane root, Node cardNode, Stage stage, String username, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), root);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ScaleTransition st = new ScaleTransition(Duration.millis(300), cardNode);
        st.setToX(1.05);
        st.setToY(1.05);
        ParallelTransition pt = new ParallelTransition(ft, st);
        pt.setInterpolator(Interpolator.EASE_IN);
        pt.setOnFinished(e -> onFinished.run());
        pt.play();
    }

    public static void attachGlidingHighlight(TableView<?> table) {
        Platform.runLater(() -> {
            Pane parent = (Pane) table.getParent();
            if (parent == null) return;

            ScrollBar vBar = null;
            for (Node n : table.lookupAll(".scroll-bar:vertical")) {
                if (n instanceof ScrollBar) {
                    vBar = (ScrollBar) n;
                    break;
                }
            }
            final ScrollBar finalVBar = vBar;

            final double[] scrollTarget = {-1};
            final Timeline scrollTimeline = new Timeline();

            table.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
                if (e.getDeltaY() == 0 || e.isDirect() || finalVBar == null || !finalVBar.isVisible()) return;

                e.consume();

                if (scrollTarget[0] == -1 || scrollTimeline.getStatus() != javafx.animation.Animation.Status.RUNNING) {
                    scrollTarget[0] = finalVBar.getValue();
                }

                double step = finalVBar.getUnitIncrement() * (Math.abs(e.getDeltaY()) / 15.0) * 0.02;
                if (step <= 0) step = 1.0;

                scrollTarget[0] += -Math.signum(e.getDeltaY()) * step;
                scrollTarget[0] = Math.max(finalVBar.getMin(), Math.min(finalVBar.getMax(), scrollTarget[0]));

                scrollTimeline.stop();
                scrollTimeline.getKeyFrames().setAll(
                        new KeyFrame(Duration.millis(350),
                                new KeyValue(finalVBar.valueProperty(), scrollTarget[0], Interpolator.EASE_OUT)
                        )
                );
                scrollTimeline.play();
            });

            int index = parent.getChildren().indexOf(table);
            StackPane wrapper = new StackPane();

            if (parent instanceof VBox) VBox.setVgrow(wrapper, VBox.getVgrow(table));
            if (parent instanceof HBox) HBox.setHgrow(wrapper, HBox.getHgrow(table));

            wrapper.setPrefSize(table.getPrefWidth(), table.getPrefHeight());
            wrapper.setMaxSize(table.getMaxWidth(), table.getMaxHeight());

            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(wrapper.widthProperty());
            clip.heightProperty().bind(wrapper.heightProperty());
            wrapper.setClip(clip);

            Region highlight = new Region();
            highlight.getStyleClass().add("gliding-highlight");
            highlight.setMouseTransparent(true);
            highlight.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            highlight.setOpacity(0);
            highlight.setPrefHeight(40);

            parent.getChildren().set(index, wrapper);
            wrapper.getChildren().addAll(highlight, table);
            StackPane.setAlignment(highlight, Pos.TOP_LEFT);

            class HighlightController {
                TableRow<?> targetRow = null;
                int targetIndex = -1;
                double lastRowScreenY = -1;
                double lastSceneX = -1;
                double lastSceneY = -1;
                boolean isHovering = false;
                double targetY = 0;
                double targetH = 40;
                double targetW = 100;
                double targetOpacity = 0;

                final AnimationTimer timer = new AnimationTimer() {
                    @Override
                    public void handle(long now) {
                        if (!isHovering) {
                            targetOpacity = 0.0;
                            applyPhysics();
                            return;
                        }

                        if (targetRow != null && targetRow.isVisible() && targetRow.getIndex() == targetIndex && targetRow.getScene() != null && lastRowScreenY != -1) {
                            javafx.geometry.Bounds bScene = targetRow.localToScene(targetRow.getBoundsInLocal());
                            if (bScene != null) {
                                javafx.geometry.Bounds bWrapper = wrapper.sceneToLocal(bScene);
                                if (bWrapper != null) {
                                    double currentYOfOldRow = bWrapper.getMinY();
                                    double scrollDelta = currentYOfOldRow - lastRowScreenY;

                                    if (Math.abs(scrollDelta) > 0.1 && targetOpacity > 0.5) {
                                        highlight.setTranslateY(highlight.getTranslateY() + scrollDelta);
                                    }
                                }
                            }
                        }

                        boolean needsRescan = true;
                        if (targetRow != null && targetRow.isVisible() && targetRow.getIndex() == targetIndex && targetRow.getScene() != null) {
                            javafx.geometry.Point2D pt = targetRow.sceneToLocal(lastSceneX, lastSceneY);
                            if (pt != null && targetRow.contains(pt)) {
                                needsRescan = false;
                            }
                        }

                        if (needsRescan) {
                            updateMouse(lastSceneX, lastSceneY);
                        }

                        if (targetRow != null && targetRow.getIndex() == targetIndex && targetRow.isVisible() && targetRow.getScene() != null) {
                            javafx.geometry.Bounds bScene = targetRow.localToScene(targetRow.getBoundsInLocal());
                            if (bScene != null) {
                                javafx.geometry.Bounds bWrapper = wrapper.sceneToLocal(bScene);
                                if (bWrapper != null) {
                                    targetY = bWrapper.getMinY();
                                    lastRowScreenY = targetY;
                                    targetH = targetRow.getHeight();
                                    targetW = targetRow.getWidth();
                                    targetOpacity = 1.0;
                                } else {
                                    targetOpacity = 0.0;
                                    lastRowScreenY = -1;
                                }
                            } else {
                                targetOpacity = 0.0;
                                lastRowScreenY = -1;
                            }
                        } else {
                            targetOpacity = 0.0;
                            lastRowScreenY = -1;
                        }

                        applyPhysics();
                    }
                };

                private void applyPhysics() {
                    double curY = highlight.getTranslateY();
                    double curH = highlight.getPrefHeight();
                    double curW = highlight.getPrefWidth();
                    double curOp = highlight.getOpacity();

                    if (curOp < 0.05 && targetOpacity > 0.5) {
                        highlight.setTranslateY(targetY);
                        curY = targetY;
                    }

                    double moveFactor = 0.09;
                    double fadeFactor = 0.1;

                    highlight.setTranslateY(curY + (targetY - curY) * moveFactor);
                    highlight.setPrefHeight(curH + (targetH - curH) * moveFactor);
                    highlight.setPrefWidth(curW + (targetW - curW) * moveFactor);
                    highlight.setOpacity(curOp + (targetOpacity - curOp) * fadeFactor);
                }

                void updateMouse(double sceneX, double sceneY) {
                    boolean found = false;
                    for (Node node : table.lookupAll(".table-row-cell")) {
                        if (node instanceof TableRow && node.isVisible()) {
                            TableRow<?> row = (TableRow<?>) node;
                            if (!row.isEmpty()) {
                                javafx.geometry.Point2D pt = row.sceneToLocal(sceneX, sceneY);
                                if (pt != null && row.contains(pt)) {
                                    targetRow = row;
                                    targetIndex = row.getIndex();
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!found) {
                        targetRow = null;
                        targetIndex = -1;
                        lastRowScreenY = -1;
                    }
                }

                void clear() {
                    isHovering = false;
                    targetRow = null;
                    targetIndex = -1;
                    lastRowScreenY = -1;
                }
            }

            HighlightController ctrl = new HighlightController();

            table.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
                ctrl.isHovering = true;
                ctrl.lastSceneX = e.getSceneX();
                ctrl.lastSceneY = e.getSceneY();
            });

            table.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                ctrl.isHovering = true;
                ctrl.lastSceneX = e.getSceneX();
                ctrl.lastSceneY = e.getSceneY();
                ctrl.timer.start();
            });

            table.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                ctrl.clear();
            });
        });
    }

    public static void attachCyberpunkGlow(Region card, ObjectProperty<Color> themeColorProperty) {
        Platform.runLater(() -> {
            Pane parent = (Pane) card.getParent();
            if (parent == null) return;

            int index = parent.getChildren().indexOf(card);
            StackPane wrapper = new StackPane();
            wrapper.setPickOnBounds(false);

            if (parent instanceof VBox) VBox.setVgrow(wrapper, VBox.getVgrow(card));
            if (parent instanceof HBox) HBox.setHgrow(wrapper, HBox.getHgrow(card));

            // ============== 1. 底层光晕：使用静态高斯模糊 + 缓存 + 形变动画 ==============
            Node[] auras = new Node[4];
            for (int i = 0; i < 4; i++) {
                Rectangle aura = new Rectangle();
                aura.setMouseTransparent(true);
                aura.widthProperty().bind(card.widthProperty());
                aura.heightProperty().bind(card.heightProperty());
                aura.setArcWidth(16);
                aura.setArcHeight(16);
                aura.setFill(Color.TRANSPARENT);

                // 【修改】边框大幅度加粗 (从 6 提升到 16)，配合模糊，显得非常饱满
                aura.setStrokeWidth(16);
                aura.strokeProperty().bind(themeColorProperty);

                // 【修改】模糊半径稍微调大 (35 -> 45)，让粗线条柔和化
                GaussianBlur blur = new GaussianBlur(45);
                aura.setEffect(blur);

                aura.setCache(true);
                aura.setCacheHint(CacheHint.SPEED);
                auras[i] = aura;

                wrapper.getChildren().add(aura);
            }

            // ============== 2. 灯管底座 ==============
            Region neonTube = new Region();
            neonTube.setMouseTransparent(true);
            neonTube.prefWidthProperty().bind(card.widthProperty());
            neonTube.prefHeightProperty().bind(card.heightProperty());
            neonTube.maxWidthProperty().bind(card.widthProperty());
            neonTube.maxHeightProperty().bind(card.heightProperty());

            Runnable updateNeonStyle = () -> {
                Color c = themeColorProperty.get();
                if (c != null) {
                    String hex = String.format("#%02X%02X%02X",
                            (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
                    // 【修改】基础亮框的宽度从 4px 加强到 5px
                    neonTube.setStyle("-fx-border-color: " + hex + "; -fx-border-width: 5px; -fx-border-radius: 8px; -fx-background-color: transparent;");
                }
            };
            themeColorProperty.addListener((obs, oldC, newC) -> updateNeonStyle.run());
            updateNeonStyle.run();

            neonTube.setCache(true);
            neonTube.setCacheHint(CacheHint.SPEED);

            // ============== 3. 顶层：静息亮光带核心 ==============
            Rectangle brightCore = new Rectangle();
            brightCore.setMouseTransparent(true);
            brightCore.widthProperty().bind(card.widthProperty());
            brightCore.heightProperty().bind(card.heightProperty());
            brightCore.setArcWidth(16);
            brightCore.setArcHeight(16);
            brightCore.setFill(Color.TRANSPARENT);
            // 【修改】核心亮线从 1.5 强化到 2.0，更粗更锋利
            brightCore.setStrokeWidth(2.0);
            brightCore.setStrokeType(StrokeType.INSIDE);
            brightCore.strokeProperty().bind(themeColorProperty);
            brightCore.setEffect(new Glow(0.6));
            brightCore.setCache(true);
            brightCore.setCacheHint(CacheHint.SPEED);

            // ============== 4. 顶层：流动高光带 (绕着框跑动的流光) ==============
            Rectangle runningLight = new Rectangle();
            runningLight.setMouseTransparent(true);
            runningLight.widthProperty().bind(card.widthProperty());
            runningLight.heightProperty().bind(card.heightProperty());
            runningLight.setArcWidth(16);
            runningLight.setArcHeight(16);
            runningLight.setFill(Color.TRANSPARENT);
            // 【修改】流光线条从 3.0 加强到 4.5
            runningLight.setStrokeWidth(4.5);
            runningLight.setStrokeType(StrokeType.INSIDE);
            runningLight.strokeProperty().bind(themeColorProperty);
            runningLight.getStrokeDashArray().addAll(150.0, 3000.0);
            runningLight.setEffect(new Glow(1.0));

            parent.getChildren().set(index, wrapper);

            wrapper.getChildren().addAll(neonTube, card, brightCore, runningLight);

            playAsymmetricAuraAnimation(auras, neonTube, brightCore, runningLight);
        });
    }

    private static void playAsymmetricAuraAnimation(Node[] auras, Region neonTube, Rectangle brightCore, Rectangle runningLight) {
        Timeline tubeTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(neonTube.opacityProperty(), 0.5, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2000), new KeyValue(neonTube.opacityProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        tubeTimeline.setAutoReverse(true);
        tubeTimeline.setCycleCount(Timeline.INDEFINITE);
        tubeTimeline.play();

        Timeline coreTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(brightCore.opacityProperty(), 0.4, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1200), new KeyValue(brightCore.opacityProperty(), 0.9, Interpolator.EASE_BOTH))
        );
        coreTimeline.setAutoReverse(true);
        coreTimeline.setCycleCount(Timeline.INDEFINITE);
        coreTimeline.play();

        double totalLength = runningLight.getStrokeDashArray().get(0) + runningLight.getStrokeDashArray().get(1);
        Timeline runningTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(runningLight.strokeDashOffsetProperty(), 0)),
                new KeyFrame(Duration.millis(3500), new KeyValue(runningLight.strokeDashOffsetProperty(), -totalLength, Interpolator.LINEAR))
        );
        runningTimeline.setCycleCount(Timeline.INDEFINITE);
        runningTimeline.play();

        // 【修改】减小基准的 X/Y 偏移量基数，原来是 20，现在降到 10，让光晕不会跑出很远
        double[][] baseOffsets = {
                {-10, -10},
                {10, -10},
                {-10, 10},
                {10, 10}
        };

        for (int i = 0; i < auras.length; i++) {
            animateAuraRandomly(auras[i], baseOffsets[i][0], baseOffsets[i][1]);
        }
    }

    private static void animateAuraRandomly(Node aura, double baseX, double baseY) {
        double duration = 1000 + Math.random() * 2000;

        // 【修改】大幅收紧缩放极限，原来是 0.95~1.15，现在改为 0.98~1.06，防止放大导致向外飞走
        double targetScale = 0.98 + Math.random() * 0.08;

        // 【修改】削弱随机移动的乘数。原来是 (0.2 ~ 1.7)，现在降到 (0.1 ~ 0.7)，让光晕紧贴卡片中心
        double targetX = baseX * (0.1 + Math.random() * 0.6);
        double targetY = baseY * (0.1 + Math.random() * 0.6);

        // 【修改】因为光晕收紧了，稍微提高发光的下限透明度 (原来是 0.3~1.0，现在是 0.5~1.0)，防止看起来暗淡
        double targetOpacity = 0.5 + Math.random() * 0.5;

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(duration),
                        new KeyValue(aura.scaleXProperty(), targetScale, Interpolator.EASE_BOTH),
                        new KeyValue(aura.scaleYProperty(), targetScale, Interpolator.EASE_BOTH),
                        new KeyValue(aura.translateXProperty(), targetX, Interpolator.EASE_BOTH),
                        new KeyValue(aura.translateYProperty(), targetY, Interpolator.EASE_BOTH),
                        new KeyValue(aura.opacityProperty(), targetOpacity, Interpolator.EASE_BOTH)
                )
        );

        timeline.setOnFinished(e -> animateAuraRandomly(aura, baseX, baseY));
        timeline.play();
    }
}