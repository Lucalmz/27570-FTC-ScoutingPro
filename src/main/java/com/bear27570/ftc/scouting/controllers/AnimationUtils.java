// File: src/main/java/com/bear27570/ftc/scouting/controllers/AnimationUtils.java
package com.bear27570.ftc.scouting.controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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
        tt.setFromX(0f);
        tt.setByX(6f);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
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

            // =========================================================================
            // 1. 获取垂直滚动条 (需在最前获取，方便后续绑定)
            // =========================================================================
            ScrollBar vBar = null;
            for (Node n : table.lookupAll(".scroll-bar:vertical")) {
                if (n instanceof ScrollBar) {
                    vBar = (ScrollBar) n;
                    break;
                }
            }
            final ScrollBar finalVBar = vBar;

            // =========================================================================
            // 2. 平滑滚动注入 (✅ 修复：采用更舒适的步长，告别“一滑到底”)
            // =========================================================================
            final double[] scrollTarget = {-1};
            final Timeline scrollTimeline = new Timeline();

            table.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
                if (e.getDeltaY() == 0 || e.isDirect() || finalVBar == null || !finalVBar.isVisible()) return;

                e.consume();

                if (scrollTarget[0] == -1 || scrollTimeline.getStatus() != javafx.animation.Animation.Status.RUNNING) {
                    scrollTarget[0] = finalVBar.getValue();
                }

                double step = finalVBar.getUnitIncrement() * (Math.abs(e.getDeltaY()) / 15.0)* 0.1;
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

            // =========================================================================
            // 3. 布局包裹与高亮块初始化
            // =========================================================================
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
// =========================================================================
            // 3. 核心物理引擎 (✅ 终极版：以列表为参考系，带物理弹簧追踪感)
            // =========================================================================
            class HighlightController {
                TableRow<?> targetRow = null;
                int targetIndex = -1;

                // 记录上一帧目标行的物理Y轴位置，用于计算列表的滚动偏移量
                double lastRowScreenY = -1;

                // 全局缓存鼠标的真实屏幕坐标
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

                        // 【魔法步骤 1】：计算列表这一帧是否发生了滚动？
                        if (targetRow != null && targetRow.isVisible() && targetRow.getIndex() == targetIndex && targetRow.getScene() != null && lastRowScreenY != -1) {
                            javafx.geometry.Bounds bScene = targetRow.localToScene(targetRow.getBoundsInLocal());
                            if (bScene != null) {
                                javafx.geometry.Bounds bWrapper = wrapper.sceneToLocal(bScene);
                                if (bWrapper != null) {
                                    double currentYOfOldRow = bWrapper.getMinY();
                                    double scrollDelta = currentYOfOldRow - lastRowScreenY;

                                    // 如果列表滚了，瞬间将高亮块按同样幅度位移，制造“钉在列表里”的错觉！
                                    if (Math.abs(scrollDelta) > 0.1 && targetOpacity > 0.5) {
                                        highlight.setTranslateY(highlight.getTranslateY() + scrollDelta);
                                    }
                                }
                            }
                        }

                        // 【魔法步骤 2】：判断鼠标是否脱离了当前绑定的行？
                        boolean needsRescan = true;
                        if (targetRow != null && targetRow.isVisible() && targetRow.getIndex() == targetIndex && targetRow.getScene() != null) {
                            // 把全局鼠标坐标映射到当前行的内部，看看还在不在里面
                            javafx.geometry.Point2D pt = targetRow.sceneToLocal(lastSceneX, lastSceneY);
                            if (pt != null && targetRow.contains(pt)) {
                                needsRescan = false; // 还在老地方，不用重新扫描
                            }
                        }

                        // 【魔法步骤 3】：如果列表滚走导致鼠标指到新的一行了，立即重新抓取！
                        if (needsRescan) {
                            updateMouse(lastSceneX, lastSceneY);
                        }

                        // 【步骤 4】：更新目标行的标准物理参数
                        if (targetRow != null && targetRow.getIndex() == targetIndex && targetRow.isVisible() && targetRow.getScene() != null) {
                            javafx.geometry.Bounds bScene = targetRow.localToScene(targetRow.getBoundsInLocal());
                            if (bScene != null) {
                                javafx.geometry.Bounds bWrapper = wrapper.sceneToLocal(bScene);
                                if (bWrapper != null) {
                                    targetY = bWrapper.getMinY();
                                    lastRowScreenY = targetY; // 保存当前行的物理Y，留给下一帧算偏移用！
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

                        // 应用物理缓动
                        applyPhysics();
                    }
                };

                private void applyPhysics() {
                    double curY = highlight.getTranslateY();
                    double curH = highlight.getPrefHeight();
                    double curW = highlight.getPrefWidth();
                    double curOp = highlight.getOpacity();

                    // 如果是刚出现，直接闪现到目标位置，防止从天而降
                    if (curOp < 0.05 && targetOpacity > 0.5) {
                        highlight.setTranslateY(targetY);
                        curY = targetY;
                    }

                    double moveFactor = 0.1;
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

            // =========================================================================
            // 4. 事件绑定：现在只管投喂鼠标坐标，剩下的全交给 AnimationTimer 引擎！
            // =========================================================================
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
}