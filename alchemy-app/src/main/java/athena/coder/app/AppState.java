package athena.coder.app;

import athena.coder.infra.entity.chat.ChatDetail;
import javafx.beans.property.*;
import javafx.collections.FXCollections;

/**
 * 应用全局状态：UI 与业务层共享的 JavaFX 属性集合。
 * <p>
 * 按职责分为 项目 / 聊天 / 任务 三组，均以静态属性暴露，
 * 通过 {@code import static athena.coder.app.AppState.*} 引用。
 */
public final class AppState {

    private AppState() {
    }

    // ==================== 项目状态 ====================

    /** 当前选中项目ID */
    public static final SimpleLongProperty curProject = new SimpleLongProperty(0);

    /** 当前选中项目标题 */
    public static final SimpleStringProperty curProjectTitle = new SimpleStringProperty("从选择项目开始吧,do it");

    // ==================== 聊天状态 ====================

    /** 聊天记录列表 */
    public static final SimpleListProperty<ChatDetail> chatList = new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 聊天视图是否激活 */
    public static final SimpleBooleanProperty chatModel = new SimpleBooleanProperty(false);

    /** 当前聊天消息UUID */
    public static final SimpleStringProperty chatSendUUid = new SimpleStringProperty();

    /** 聊天视图刷新触发器 */
    public static final SimpleLongProperty chatViewRefresh = new SimpleLongProperty(0L);

    // ==================== 任务状态 ====================

    /** 是否正在执行任务 */
    public static final SimpleBooleanProperty isExecuteTask = new SimpleBooleanProperty(false);

    /** 用户输入消息 */
    public static final SimpleStringProperty sendMsg = new SimpleStringProperty("");

    /** 当前任务ID */
    public static final SimpleLongProperty curTaskId = new SimpleLongProperty(0);

    /** 树列表刷新触发器 */
    public static final SimpleIntegerProperty treeListFresh = new SimpleIntegerProperty(0);
}