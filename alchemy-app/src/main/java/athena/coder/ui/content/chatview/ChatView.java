package athena.coder.ui.content.chatview;

import athena.coder.app.ChatManager;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.entity.chat.ChatDetail;
import athena.coder.entity.chat.ChatEnum;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static athena.coder.app.AppState.chatList;
import static athena.coder.entity.BasicConstants.InPutUi.TEXTAREA_HEIGHT;
import static athena.coder.entity.BasicConstants.InPutUi.TEXTAREA_WIDTH;
import static athena.coder.ui.content.basicInputview.InputView.newInputViewWithBorder;

/**
 * 聊天视图 —— 消息渲染（marked.js + highlight.js）、自动滚动、WebView 虚拟化。
 */
public class ChatView {

    private static final Logger LOG = Logger.getLogger(ChatView.class.getName());

    private static final String MARKED_JS = loadResource("web/marked.min.js");
    private static final String HIGHLIGHT_JS = loadResource("web/highlight.min.js");
    private static final String BASE_CSS = loadResource("web/base.css");
    private static final String HLJS_THEME_CSS = loadResource("web/hljs-theme.css");
    private static final String APP_JS = loadResource("web/app.js");
    private static final String TWEMOJI_CDN = "https://cdn.jsdelivr.net/gh/jdecked/twemoji@17.0.3/assets/svg/";
    private static final String HTML_TEMPLATE_PREFIX = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
            + BASE_CSS + HLJS_THEME_CSS
            + "</style></head><body><div id=\"card\" class=\"";
    private static final String HTML_TEMPLATE_SUFFIX = "\"><div id=\"content\"></div></div><script>"
            + MARKED_JS + "</script><script>" + HIGHLIGHT_JS
            + "</script><script>" + APP_JS + "</script></body></html>";
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private static final double CHAT_VIEW_WIDTH = TEXTAREA_WIDTH;
    private static final int MAX_ACTIVE_WEBVIEWS = 20;
    private static final double VIRTUALIZE_BUFFER_PX = 1500;
    private static final double DEFAULT_PLACEHOLDER_HEIGHT = 100;

    private static volatile Ctx ctx;

    /** 卡片 CSS 类名：msg-card 为底板 + 类型修饰符控制顶部色带颜色。 */
    private static String cssClassForType(ChatEnum type) {
        return switch (type) {
            case ROBOT_RESULT -> "msg-card card-result";
            case ROBOT_REPORT -> "msg-card card-report";
            case ROBOT_ERROR  -> "msg-card card-error";
            case ROBOT_CONFIRM -> "msg-card card-confirm";
            default           -> "msg-card";
        };
    }

    /**
     * 创建完整的聊天视图容器（消息列表 + 底部输入框）。
     */
    public static StackPane newChatViewWrapper() {
        StackPane root = new StackPane();
        root.getChildren().add(newChatView());

        StackPane inputPane = new StackPane();
        inputPane.setAlignment(Pos.BOTTOM_CENTER);
        inputPane.setPickOnBounds(false);
        StackPane input = newInputViewWithBorder();
        input.setMaxWidth(TEXTAREA_WIDTH);
        input.setMaxHeight(TEXTAREA_HEIGHT);
        inputPane.getChildren().add(input);
        root.getChildren().add(inputPane);

        return root;
    }

    // ==================== 公开 API ====================

    /** 流式更新机器人消息内容。 */
    public static void updateStreamingContent(ChatDetail detail) {
        Ctx c = ctx;
        if (c == null) return;

        WebView wv = c.webViewMap.get(detail.getUuid());
        if (wv == null) {
            wv = materializeLatestMessage(c);
            if (wv == null) return;
            if (wv.getEngine().getLoadWorker().getState() != Worker.State.SUCCEEDED) return;
        }
        final WebView target = wv;
        Platform.runLater(() -> renderMarkdown(target, detail.getContent()));
    }

    /** 在 VBox 底部添加 loading 指示器。 */
    public static void addLoadingIndicator() {
        Ctx c = ctx;
        if (c == null || c.loadingHBox != null) return;

        // ── 三个脉冲圆点 ──
        Circle dot1 = new Circle(3.5, Color.web("#3b82f6"));
        Circle dot2 = new Circle(3.5, Color.web("#3b82f6"));
        Circle dot3 = new Circle(3.5, Color.web("#3b82f6"));
        dot1.setOpacity(0.3);
        dot2.setOpacity(0.3);
        dot3.setOpacity(0.3);
        HBox dotsBox = new HBox(5, dot1, dot2, dot3);
        dotsBox.setAlignment(Pos.CENTER_LEFT);

        // 脉冲动画：0→1→2→1→0 依次点亮
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        e -> {
                            dot1.setOpacity(1.0);
                            dot2.setOpacity(0.3);
                            dot3.setOpacity(0.3);
                        }),
                new KeyFrame(Duration.millis(250),
                        e -> {
                            dot1.setOpacity(0.3);
                            dot2.setOpacity(1.0);
                            dot3.setOpacity(0.3);
                        }),
                new KeyFrame(Duration.millis(500),
                        e -> {
                            dot1.setOpacity(0.3);
                            dot2.setOpacity(0.3);
                            dot3.setOpacity(1.0);
                        }),
                new KeyFrame(Duration.millis(750),
                        e -> {
                            dot1.setOpacity(0.3);
                            dot2.setOpacity(0.3);
                            dot3.setOpacity(0.3);
                        })
        );
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();

        // ── 文字 ──
        Label text = new Label("准备中...");
        text.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        c.loadingLabel = text;

        // ── 单行卡片：圆点 + 文字 ──
        HBox row = new HBox(10, dotsBox, text);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 0, 14, 0));
        card.setStyle("-fx-background-color: transparent;");
        card.setMaxWidth(CHAT_VIEW_WIDTH);

        // 保存动画引用以便移除时停止
        card.setUserData(pulse);
        c.loadingHBox = card;
        c.vBox.getChildren().add(card);
    }

    /** 移除 loading 指示器以及所有进度小卡片。 */
    public static void removeLoadingIndicator() {
        Ctx c = ctx;
        if (c == null || c.loadingHBox == null) return;
        // 停止脉冲动画
        Object data = c.loadingHBox.getUserData();
        if (data instanceof Timeline t) {
            t.stop();
        }
        // 清空进度小卡片
        for (Label card : c.progressCards) {
            c.vBox.getChildren().remove(card);
        }
        c.progressCards.clear();
        c.vBox.getChildren().remove(c.loadingHBox);
        c.loadingHBox = null;
        c.loadingLabel = null;
    }

    /** 更新 loading 文字并追加进度小卡片；内容由 ai 层以「【专家名】 描述」形式直出，此处纯展示。 */
    public static void updateLoadingStep(String content) {
        Ctx c = ctx;
        if (c == null || c.loadingLabel == null || content == null || content.isBlank()) return;

        String display = content.trim();
        Platform.runLater(() -> {
            c.loadingLabel.setText(display);
            appendProgressCard(c, display);
        });
    }

    /** 如果是工具调用（非"调用大模型"），在 loading 上方插入进度小卡片，超过5条时丢弃最早。 */
    private static void appendProgressCard(Ctx c, String display) {
        if (display.contains("调用大模型")) return;
        Label card = new Label(display);
        card.setMaxWidth(CHAT_VIEW_WIDTH);
        card.setPadding(new Insets(2, 0, 2, 0));
        card.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-background-color: transparent;");
        c.progressCards.add(card);
        // 超过 5 条，丢弃最早
        if (c.progressCards.size() > 5) {
            Label oldest = c.progressCards.removeFirst();
            c.vBox.getChildren().remove(oldest);
        }
        // 插入到 loading 指示灯上方
        if (c.loadingHBox != null && c.vBox.getChildren().contains(c.loadingHBox)) {
            int idx = c.vBox.getChildren().indexOf(c.loadingHBox);
            c.vBox.getChildren().add(idx, card);
        } else {
            c.vBox.getChildren().add(card);
        }
    }

    private static void addNodeBeforeLoading(Ctx c, javafx.scene.Node node) {
        if (c.loadingHBox != null && c.vBox.getChildren().contains(c.loadingHBox)) {
            int idx = c.vBox.getChildren().indexOf(c.loadingHBox);
            c.vBox.getChildren().add(idx, node);
        } else {
            c.vBox.getChildren().add(node);
        }
    }

    /** 创建 ScrollPane + VBox + 注册监听器。 */
    private static ScrollPane newChatView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setPadding(new Insets(0, 0, TEXTAREA_HEIGHT, 0));
        vBox.setMaxWidth(CHAT_VIEW_WIDTH);
        vBox.setBackground(Background.fill(Color.TRANSPARENT));

        Ctx c = new Ctx(scrollPane, vBox);
        ctx = c;

        // ── VBox 高度变化 → 自动滚到底部（布局完成后触发，无时序问题）──
        vBox.heightProperty().addListener((obs, old, h) -> {
            if (h.doubleValue() > old.doubleValue()) {
                Platform.runLater(() -> scrollPane.setVvalue(1.0));
            }
        });

        // ── 消息列表监听 ──
        ListChangeListener<ChatDetail> chatListener = change -> {
            while (change.next()) {
                if (change.wasRemoved() && chatList.isEmpty()) {
                    vBox.getChildren().clear();
                    c.webViewMap.clear();
                    c.details.clear();
                    c.confirmBar = null;
                    c.lastResultIndex = -1;
                }
                if (change.wasAdded()) {
                    List<? extends ChatDetail> added = change.getAddedSubList();
                    if (added.isEmpty()) continue;
                    for (ChatDetail detail : added) {
                        renderMessage(c, detail);
                    }
                }
            }
        };
        c.chatListListener = chatListener;
        chatList.addListener(chatListener);

        // ── 滚动结束 → 虚拟化检查 ──
        scrollPane.setOnScrollFinished(e -> Platform.runLater(() -> virtualize(c)));

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(vBox);
        stackPane.setAlignment(Pos.TOP_CENTER);
        scrollPane.setContent(stackPane);

        // ── 初始加载兜底 ──
        if (!chatList.isEmpty()) {
            for (ChatDetail detail : chatList) {
                renderMessage(c, detail);
            }
        }

        return scrollPane;
    }

    /** 统一消息渲染入口。 */
    private static void renderMessage(Ctx c, ChatDetail detail) {
        String typeStr = detail.getType();
        if (ChatEnum.USER.name().equals(typeStr)) {
            c.details.add(detail);
            c.vBox.getChildren().add(createUserBubble(detail));
            return;
        }
        ChatEnum type = resolveType(detail);
        if (type == ChatEnum.ROBOT_PROGRESS) return;
        c.details.add(detail);
        // 记录最新结果卡索引（确认等待阶段即计划卡），供「查看计划」钩子跳转
        if (type == ChatEnum.ROBOT_RESULT) {
            c.lastResultIndex = c.details.size() - 1;
        }
        WebView wv = createRobotWebView(detail, 60, cssClassForType(type));
        c.webViewMap.put(detail.getUuid(), wv);
        addNodeBeforeLoading(c, wv);
    }

    /** 机器人 WebView 工厂：加载 HTML 模板，加载完成后渲染内容。 */
    private static WebView createRobotWebView(ChatDetail detail, double initHeight, String cssClass) {
        WebView wv = new WebView();
        wv.setPrefWidth(CHAT_VIEW_WIDTH);
        wv.setMaxWidth(CHAT_VIEW_WIDTH);
        wv.setMinWidth(CHAT_VIEW_WIDTH);
        wv.setPrefHeight(initHeight);
        wv.setMinHeight(initHeight);
        wv.setMaxHeight(Double.MAX_VALUE);
        wv.getEngine().setJavaScriptEnabled(true);

        // WebView 滚轮事件转发到外层 ScrollPane，避免滚动失效
        if (ctx != null && ctx.scrollPane != null) {
            wv.addEventFilter(ScrollEvent.SCROLL, e -> {
                double dy = e.getDeltaY();
                double newVal = ctx.scrollPane.getVvalue() - dy / 200.0;
                ctx.scrollPane.setVvalue(Math.max(0, Math.min(1, newVal)));
                e.consume();
            });
        }

        loadHtmlToWebView(wv, cssClass);

        wv.getEngine().getLoadWorker().stateProperty().addListener((obs, o, n) -> {
            if (n == Worker.State.SUCCEEDED) {
                Platform.runLater(() -> renderMarkdown(wv, detail.getContent()));
            }
        });

        return wv;
    }

    /**
     * 添加确认按钮卡片（原生 JavaFX，紧跟确认卡片下方）；已存在时忽略（同一时刻至多一个）。
     * 点击 ≡ 用户输入「确认」发送，走同一条 HumanGate 投递链路。
     */
    public static void addConfirmBar() {
        Ctx c = ctx;
        if (c == null || c.confirmBar != null) return;

        FontIcon icon = new FontIcon(Feather.CHECK);
        icon.setIconColor(Color.web("#64748b"));
        Button btn = new Button("确认执行", icon);
        // 低调描边按钮：白底灰边，与确认卡片发丝边框同一视觉语言；hover 背景微变
        String normalStyle = "-fx-background-color: #ffffff;"
                + "-fx-border-color: #cbd5e1;"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 8;"
                + "-fx-background-radius: 8;"
                + "-fx-text-fill: #334155;"
                + "-fx-font-size: 14px;"
                + "-fx-padding: 7 20 7 20;"
                + "-fx-cursor: hand;";
        String hoverStyle = normalStyle.replace("#ffffff", "#f1f5f9");
        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        btn.setOnAction(e -> ChatManager.sendGateReply("确认"));

        // 【查看计划】钩子：文字链式次要按钮，点击跳转到当前规划文档卡片
        FontIcon planIcon = new FontIcon(Feather.FILE_TEXT);
        planIcon.setIconColor(Color.web("#64748b"));
        Button viewPlan = new Button("查看计划", planIcon);
        String linkStyle = "-fx-background-color: transparent;"
                + "-fx-border-color: transparent;"
                + "-fx-text-fill: #64748b;"
                + "-fx-font-size: 13px;"
                + "-fx-padding: 7 10 7 10;"
                + "-fx-cursor: hand;";
        viewPlan.setStyle(linkStyle);
        viewPlan.setOnMouseEntered(e -> viewPlan.setStyle(
                linkStyle + "-fx-underline: true;-fx-text-fill: #334155;"));
        viewPlan.setOnMouseExited(e -> viewPlan.setStyle(linkStyle));
        viewPlan.setOnAction(e -> scrollToPlanCard());

        HBox bar = new HBox(12, btn, viewPlan);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 0, 2, 0));
        bar.setMaxWidth(CHAT_VIEW_WIDTH);

        c.confirmBar = bar;
        addNodeBeforeLoading(c, bar);
    }

    /** 移除确认按钮卡片（按钮一次性，确认被消费后整卡移除；需 FX 线程）。 */
    public static void removeConfirmBar() {
        Ctx c = ctx;
        if (c == null || c.confirmBar == null) return;
        c.vBox.getChildren().remove(c.confirmBar);
        c.confirmBar = null;
    }

    /**
     * 跳转到当前规划文档卡片（最新一张 ROBOT_RESULT，确认等待阶段即计划卡）：
     * 若已被虚拟化先还原 WebView，滚动到位后一次性柔光高亮帮助用户定位。
     */
    public static void scrollToPlanCard() {
        Ctx c = ctx;
        if (c == null || c.lastResultIndex < 0) return;
        int idx = c.lastResultIndex;
        if (idx >= c.details.size() || idx >= c.vBox.getChildren().size()) return;

        Node node = c.vBox.getChildren().get(idx);
        if (!(node instanceof WebView)) {
            // 计划卡已被虚拟化替换为占位符：先还原再跳转
            materializeAt(c, idx, c.details.get(idx));
            node = c.vBox.getChildren().get(idx);
        }

        double contentH = c.vBox.getHeight();
        double viewportH = c.scrollPane.getViewportBounds().getHeight();
        if (contentH > viewportH) {
            double nodeY = node.getBoundsInParent().getMinY();
            double vvalue = Math.max(0, Math.min(1, (nodeY - 20) / (contentH - viewportH)));
            c.scrollPane.setVvalue(vvalue);
        }
        highlightCard(c, node);
    }

    /** 一次性柔光高亮（重复点击时重启计时，不会提前吞掉新效果）。 */
    private static void highlightCard(Ctx c, Node node) {
        if (c.highlightAnim != null) {
            c.highlightAnim.stop();
        }
        node.setEffect(new DropShadow(18, Color.web("#3b82f6", 0.55)));
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(1500), e -> node.setEffect(null)));
        c.highlightAnim = t;
        t.play();
    }

    /** 用户消息气泡（淡蓝底深色字右对齐）。 */
    private static HBox createUserBubble(ChatDetail detail) {
        Label label = new Label(detail.getContent());
        label.setWrapText(true);
        label.setMaxWidth(TEXTAREA_WIDTH * 0.78);
        label.setStyle(
                "-fx-background-color: #f8fafc;" +
                "-fx-background-radius: 18 18 4 18;" +
                "-fx-padding: 10 16 10 16;" +
                "-fx-text-fill: #475569;" +
                "-fx-font-size: 14px;" +
                "-fx-border-color: #e2e8f0;" +
                "-fx-border-radius: 18 18 4 18;" +
                "-fx-border-width: 1;"
        );
        HBox box = new HBox(label);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(2, 0, 2, 0));
        return box;
    }

    /**
     * 渲染 Markdown 到 WebView 并自动调整高度。
     * <p>
     * 管道：emoji → 占位符 → 百分号编码（JNI 安全）→ JS decodeURIComponent →
     * marked.js 解析 → 占位符替换为 Twemoji {@code <img>}。
     */
    private static void renderMarkdown(WebView wv, String markdown) {
        try {
            if (markdown == null || markdown.isEmpty()) {
                wv.getEngine().executeScript("document.getElementById('content').innerHTML='';");
                return;
            }

            markdown = replaceEmojiWithPlaceholders(markdown);
            String encoded = percentEncodeUTF8(markdown);
            wv.getEngine().executeScript(
                    "(function(){"
                            + "var md=decodeURIComponent('" + encoded + "');"
                            + "if(typeof md!=='string'||md.length===0)return;"
                            + "if(typeof marked==='undefined')return;"
                            + "try{"
                            + "var html=marked.parse(md);"
                            + "if(typeof html==='string'){"
                            + "html=html.replace(/\\{\\{EMOJI:([a-f0-9]+)\\}\\}/g,function(_,h){"
                            + "return '<img class=\"emoji\" src=\"" + TWEMOJI_CDN + "' + h + '.svg\">';"
                            + "});"
                            + "document.getElementById('content').innerHTML=html;"
                            + "}else{"
                            + "throw new Error('marked.parse returned '+typeof html);"
                            + "}"
                            + "}catch(e){"
                            + "console.error('marked parse error:',e);"
                            + "var esc=md.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');"
                            + "document.getElementById('content').innerHTML='<pre style=\"white-space:pre-wrap;font-size:13px;color:#334155\">'+esc+'</pre>';"
                            + "}"
                            + "})();");
            
            Object hObj = wv.getEngine()
                    .executeScript("document.body.scrollHeight+4");
            if (hObj instanceof Number) {
                double h = ((Number) hObj).doubleValue();
                if (h > 0) applyWebViewHeight(wv, h);
            }
        } catch (Exception e) {
            ErrorLogger.log("ChatView.renderMarkdown", e);
        }
    }

    /** UTF-8 百分号编码，使非 ASCII 字符安全穿越 JNI 桥接层。 */
    private static String percentEncodeUTF8(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            sb.append('%');
            sb.append(HEX_DIGITS[(b >> 4) & 0xF]);
            sb.append(HEX_DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    /**
     * 将 emoji 替换为 {@code {{EMOJI:hex}}} 纯 ASCII 占位符。
     * marked.js 解析不受干扰，JS 端解析完成后替换为 Twemoji {@code <img>}。
     * Java 25 中 0-9/#/* 被同时归类为 Emoji 和 Emoji_Component，必须优先排除。
     */
    private static String replaceEmojiWithPlaceholders(String text) {
        int[] codePoints = text.codePoints().toArray();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < codePoints.length) {
            int cp = codePoints[i];
            if (isPseudoEmoji(cp)) {
                sb.appendCodePoint(cp);
                i++;
            } else if (Character.isEmoji(cp) && !Character.isEmojiComponent(cp)) {
                String hex = Integer.toHexString(cp);
                sb.append("{{EMOJI:").append(hex).append("}}");
                i++;
                while (i < codePoints.length && Character.isEmojiComponent(codePoints[i])
                        && !isPseudoEmoji(codePoints[i])) {
                    i++;
                }
            } else if (Character.isEmojiComponent(cp)) {
                i++;
            } else {
                sb.appendCodePoint(cp);
                i++;
            }
        }
        return sb.toString();
    }

    // Java 25 将 0-9/#/* 归类为 Emoji_Component（keycap 序列用途），
    // 但普通文本中不应视为 emoji，必须保留。
    private static boolean isPseudoEmoji(int cp) {
        return (cp >= '0' && cp <= '9') || cp == '#' || cp == '*';
    }

    private static void applyWebViewHeight(WebView wv, double h) {
        wv.setPrefHeight(h);
        wv.setMinHeight(h);
    }

    /** 视口外 WebView 替换为轻量占位符。 */
    private static void virtualize(Ctx c) {
        var nodes = c.vBox.getChildren();
        if (nodes.size() <= MAX_ACTIVE_WEBVIEWS || c.details.isEmpty()) return;

        double vvalue = c.scrollPane.getVvalue();
        double contentH = c.vBox.getHeight();
        double viewportH = c.scrollPane.getViewportBounds().getHeight();
        if (contentH <= 0 || viewportH <= 0) return;

        double scrollY = vvalue * (contentH - viewportH);
        double viewTop = scrollY - VIRTUALIZE_BUFFER_PX;
        double viewBottom = scrollY + viewportH + VIRTUALIZE_BUFFER_PX;
        boolean wasNearBottom = vvalue >= 0.95
                || contentH <= viewportH
                || (contentH - scrollY - viewportH) < 50;

        double nodeY = 0;
        boolean changed = false;
        for (int i = 0; i < nodes.size() && i < c.details.size(); i++) {
            var node = nodes.get(i);
            double nodeH = node.getBoundsInParent().getHeight();
            if (nodeH <= 0) nodeH = DEFAULT_PLACEHOLDER_HEIGHT;

            boolean inView = isInViewport(nodeY, nodeH, viewTop, viewBottom);
            var detail = c.details.get(i);

            if (inView && node instanceof Region && !(node instanceof WebView) && !(node instanceof HBox)) {
                materializeAt(c, i, detail);
                changed = true;
            } else if (!inView && node instanceof WebView wv) {
                c.webViewMap.remove(detail.getUuid());
                wv.getEngine().loadContent("");
                nodes.set(i, createPlaceholder(nodeH));
                changed = true;
            }
            nodeY += nodeH;
        }

        if (changed && wasNearBottom) {
            Platform.runLater(() -> c.scrollPane.setVvalue(1.0));
        }
    }

    /** 在指定索引处将占位符物化为 WebView。 */
    private static void materializeAt(Ctx c, int index, ChatDetail detail) {
        String uuid = detail.getUuid();
        if (c.webViewMap.containsKey(uuid)) return;
        Region placeholder = (Region) c.vBox.getChildren().get(index);
        WebView wv = createRobotWebView(detail, placeholder.getPrefHeight(), cssClassForType(resolveType(detail)));
        c.webViewMap.put(uuid, wv);
        c.vBox.getChildren().set(index, wv);
    }

    /** 流式更新兜底：最新消息被虚拟化后重建 WebView。 */
    private static WebView materializeLatestMessage(Ctx c) {
        if (c.details.isEmpty()) return null;
        ChatDetail latest = c.details.getLast();
        if (ChatEnum.USER.name().equals(latest.getType()) || c.webViewMap.containsKey(latest.getUuid())) return null;
        WebView wv = createRobotWebView(latest, DEFAULT_PLACEHOLDER_HEIGHT, cssClassForType(resolveType(latest)));
        c.webViewMap.put(latest.getUuid(), wv);
        addNodeBeforeLoading(c, wv);
        return wv;
    }

    private static ChatEnum resolveType(ChatDetail detail) {
        try { return ChatEnum.valueOf(detail.getType()); }
        catch (IllegalArgumentException e) { return ChatEnum.ROBOT; }
    }

    private static Region createPlaceholder(double height) {
        Region r = new Region();
        r.setPrefHeight(height);
        r.setMinHeight(height);
        r.setMaxHeight(height);
        r.setPrefWidth(CHAT_VIEW_WIDTH);
        return r;
    }

    private static boolean isInViewport(double nodeY, double nodeH, double viewTop, double viewBottom) {
        return (nodeY + nodeH > viewTop) && (nodeY < viewBottom);
    }

    private static String loadResource(String path) {
        try (InputStream is = ChatView.class.getModule().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ErrorLogger.log("ChatView.loadResource(" + path + ")", e);
            throw new RuntimeException("加载资源失败: " + path, e);
        }
    }

    /** Base64 data: URI 加载，规避 JNI 多字节 Unicode 损坏。 */
    private static void loadHtmlToWebView(WebView wv, String cssClass) {
        String template = HTML_TEMPLATE_PREFIX + cssClass + HTML_TEMPLATE_SUFFIX;
        String b64 = Base64.getEncoder().encodeToString(template.getBytes(StandardCharsets.UTF_8));
        wv.getEngine().load("data:text/html;charset=utf-8;base64," + b64);
    }

    // ==================== 内部类 ====================

    /** 聊天视图运行时状态。 */
    static final class Ctx {
        final ScrollPane scrollPane;
        final VBox vBox;
        final Map<String, WebView> webViewMap = new ConcurrentHashMap<>();
        final List<ChatDetail> details = new CopyOnWriteArrayList<>();
        VBox loadingHBox;
        Label loadingLabel;
        HBox confirmBar;
        int lastResultIndex = -1;
        Timeline highlightAnim;
        List<Label> progressCards = new java.util.ArrayList<>();
        ListChangeListener<ChatDetail> chatListListener;

        Ctx(ScrollPane scrollPane, VBox vBox) {
            this.scrollPane = scrollPane;
            this.vBox = vBox;
        }
    }
}