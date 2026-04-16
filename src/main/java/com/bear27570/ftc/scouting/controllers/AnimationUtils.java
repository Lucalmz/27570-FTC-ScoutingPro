// File: src/main/java/com/bear27570/ftc/scouting/controllers/AnimationUtils.java
package com.bear27570.ftc.scouting.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.stream.Stream;

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

    /**
     * 为整个 Scene 启用全局按钮缩放动效
     */
    public static void enableGlobalButtonAnimations(Scene scene) {
        // 拦截鼠标按下事件
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

        // 拦截鼠标松开事件
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (currentlyPressedButton != null) {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), currentlyPressedButton);
                st.setToX(1.0);
                st.setToY(1.0);
                st.setInterpolator(Interpolator.EASE_OUT);
                st.play();
                currentlyPressedButton = null; // 重置
            }
        });
    }

    /**
     * 向上遍历节点树，寻找触发事件的真实按钮
     * （因为用户可能点到了按钮里面的图标或文字，而不是按钮本身）
     */
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

                double step = finalVBar.getUnitIncrement() * (Math.abs(e.getDeltaY()) / 15.0)* 0.02;
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
            wrapper.setPickOnBounds(false); // 确保包装器不吞噬鼠标事件

            // 保持卡片原有的自适应生长属性
            if (parent instanceof VBox) VBox.setVgrow(wrapper, VBox.getVgrow(card));
            if (parent instanceof HBox) HBox.setHgrow(wrapper, HBox.getHgrow(card));

            // ============== 1. 底层：霓虹灯管发射器 (承载多段雾化光晕) ==============
            Region neonTube = new Region();
            neonTube.setMouseTransparent(true);
            neonTube.prefWidthProperty().bind(card.widthProperty());
            neonTube.prefHeightProperty().bind(card.heightProperty());
            neonTube.maxWidthProperty().bind(card.widthProperty());
            neonTube.maxHeightProperty().bind(card.heightProperty());

            themeColorProperty.addListener((obs, oldC, newC) -> {
                String hex = String.format("#%02X%02X%02X",
                        (int)(newC.getRed() * 255), (int)(newC.getGreen() * 255), (int)(newC.getBlue() * 255));
                neonTube.setStyle("-fx-border-color: " + hex + "; -fx-border-width: 4px; -fx-border-radius: 8px; -fx-background-color: transparent;");
            });
            String initHex = String.format("#%02X%02X%02X",
                    (int)(themeColorProperty.get().getRed() * 255), (int)(themeColorProperty.get().getGreen() * 255), (int)(themeColorProperty.get().getBlue() * 255));
            neonTube.setStyle("-fx-border-color: " + initHex + "; -fx-border-width: 4px; -fx-border-radius: 8px; -fx-background-color: transparent;");

            // 多段不对称 DropShadow 链
            DropShadow ds1 = new DropShadow(); ds1.setBlurType(BlurType.GAUSSIAN);
            DropShadow ds2 = new DropShadow(); ds2.setBlurType(BlurType.GAUSSIAN); ds2.setInput(ds1);
            DropShadow ds3 = new DropShadow(); ds3.setBlurType(BlurType.GAUSSIAN); ds3.setInput(ds2);
            DropShadow ds4 = new DropShadow(); ds4.setBlurType(BlurType.GAUSSIAN); ds4.setInput(ds3);
            neonTube.setEffect(ds4);

            // ============== 2. 顶层：静息亮光带核心 (锋利的边缘静态亮线) ==============
            Rectangle brightCore = new Rectangle();
            brightCore.setMouseTransparent(true);
            brightCore.widthProperty().bind(card.widthProperty());
            brightCore.heightProperty().bind(card.heightProperty());
            brightCore.setArcWidth(16); // 对应 8px 的 border-radius (直径16)
            brightCore.setArcHeight(16);
            brightCore.setFill(Color.TRANSPARENT);
            brightCore.setStrokeWidth(1.5);
            brightCore.setStrokeType(StrokeType.INSIDE); // 画在内侧避免模糊
            brightCore.strokeProperty().bind(themeColorProperty);
            brightCore.setEffect(new Glow(0.6)); // 基础发光

            // ============== 3. 顶层：流动高光带 (绕着框跑动的流光) ==============
            Rectangle runningLight = new Rectangle();
            runningLight.setMouseTransparent(true);
            runningLight.widthProperty().bind(card.widthProperty());
            runningLight.heightProperty().bind(card.heightProperty());
            runningLight.setArcWidth(16);
            runningLight.setArcHeight(16);
            runningLight.setFill(Color.TRANSPARENT);
            runningLight.setStrokeWidth(3.0); // 比核心稍宽，显得更有能量
            runningLight.setStrokeType(StrokeType.INSIDE);
            runningLight.strokeProperty().bind(themeColorProperty);
            // 虚线设置：一段长为150的光带，配上一段长为3000的空白(确保一次只跑一小段)
            runningLight.getStrokeDashArray().addAll(150.0, 3000.0);
            runningLight.setEffect(new Glow(1.0)); // 最强泛光

            // 替换节点
            parent.getChildren().set(index, wrapper);

            // 【关键层级】：光晕底座最下 -> 实际卡片在中间 -> 静态亮框在顶层 -> 流动流光在最上层
            wrapper.getChildren().addAll(neonTube, card, brightCore, runningLight);

            // 启动异步不对称光晕系统 + 亮光带动画
            playAsymmetricAuraAnimation(new DropShadow[]{ds1, ds2, ds3, ds4}, neonTube, brightCore, runningLight, themeColorProperty);
        });
    }

    // ==========================================
    // 异步多段不对称光晕系统 (随机扩散) + 亮带流光控制
    // ==========================================
    private static void playAsymmetricAuraAnimation(DropShadow[] shadows, Region neonTube, Rectangle brightCore, Rectangle runningLight, ObjectProperty<Color> themeColor) {
        // 灯管实体本身的基础呼吸
        Timeline tubeTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(neonTube.opacityProperty(), 0.5, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2000), new KeyValue(neonTube.opacityProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        tubeTimeline.setAutoReverse(true);
        tubeTimeline.setCycleCount(Timeline.INDEFINITE);
        tubeTimeline.play();

        // 【新增】静态亮光框的高频呼吸 (电涌感)
        Timeline coreTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(brightCore.opacityProperty(), 0.4, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1200), new KeyValue(brightCore.opacityProperty(), 0.9, Interpolator.EASE_BOTH))
        );
        coreTimeline.setAutoReverse(true);
        coreTimeline.setCycleCount(Timeline.INDEFINITE);
        coreTimeline.play();

        // 【新增】跑马灯流光的无限循环跑动
        double totalLength = runningLight.getStrokeDashArray().get(0) + runningLight.getStrokeDashArray().get(1); // 150 + 3000 = 3150
        Timeline runningTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(runningLight.strokeDashOffsetProperty(), 0)),
                new KeyFrame(Duration.millis(3500), new KeyValue(runningLight.strokeDashOffsetProperty(), -totalLength, Interpolator.LINEAR))
        );
        runningTimeline.setCycleCount(Timeline.INDEFINITE);
        runningTimeline.play();

        double[][] baseOffsets = {
                {-20, -20},
                { 20, -20},
                {-20,  20},
                { 20,  20}
        };

        for (int i = 0; i < shadows.length; i++) {
            startIndependentShadowAnimation(shadows[i], themeColor, baseOffsets[i][0], baseOffsets[i][1]);
        }
    }

    private static void startIndependentShadowAnimation(DropShadow ds, ObjectProperty<Color> themeColor, double baseX, double baseY) {
        DoubleProperty opacityProp = new SimpleDoubleProperty(0.5);

        ds.colorProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(() -> {
            Color c = themeColor.get();
            if (c == null) return Color.TRANSPARENT;
            double op = Math.max(0, Math.min(1.0, opacityProp.get()));
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), op);
        }, themeColor, opacityProp));

        animateShadowRandomly(ds, opacityProp, baseX, baseY);
    }

    private static void animateShadowRandomly(DropShadow ds, DoubleProperty opacityProp, double baseX, double baseY) {
        double duration = 1000 + Math.random() * 2000;
        double targetRadius = 25 + Math.random() * 60;
        double targetSpread = 0.05 + Math.random() * 0.35;
        double targetX = baseX * (0.2 + Math.random() * 1.5);
        double targetY = baseY * (0.2 + Math.random() * 1.5);
        double targetOpacity = 0.3 + Math.random() * 0.7;

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(duration),
                        new KeyValue(ds.radiusProperty(), targetRadius, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), targetSpread, Interpolator.EASE_BOTH),
                        new KeyValue(ds.offsetXProperty(), targetX, Interpolator.EASE_BOTH),
                        new KeyValue(ds.offsetYProperty(), targetY, Interpolator.EASE_BOTH),
                        new KeyValue(opacityProp, targetOpacity, Interpolator.EASE_BOTH)
                )
        );

        timeline.setOnFinished(e -> animateShadowRandomly(ds, opacityProp, baseX, baseY));
        timeline.play();
    }
}