package athena.coder.entity;


/**
 * 全局常量与状态入口
 * <p>
 * MainUi：纯常量（窗口尺寸等）
 * InPutUi / ChatUi：可变状态属性已代理到 {@link AppState}，
 * 现有代码通过 static import 仍可正常使用，新代码建议直接使用 AppState。
 */
public interface BasicConstants {

    class MainUi {
        public static final String TITLE = "Code Assistant";
        public static final String VERSION = "V 1.0.0";
        public static final double WIDTH = 1380;
        public static final double HEIGHT = 900;
    }

    class InPutUi {
        // 纯常量
        public static final double CONTENT_WIDTH = 800;
        public static final double TEXTAREA_WIDTH = CONTENT_WIDTH;
        public static final double TEXTAREA_HEIGHT = 120;

        // 可变状态 → 代理到 AppState（向后兼容）
        //       private static final AppState STATE = AppState.getInstance();
//        public static SimpleBooleanProperty isExecuteTask = STATE.isExecuteTaskProperty();
//        public static SimpleStringProperty curProjectTitle = STATE.curProjectTitleProperty();
//        public static SimpleLongProperty curProject = STATE.curProjectProperty();
//        public static SimpleIntegerProperty treeListFresh = STATE.treeListFreshProperty();
//        public static SimpleStringProperty sendMsg = STATE.sendMsgProperty();
//        public static SimpleLongProperty curTaskId = STATE.curTaskIdProperty();
    }

    class ChatUi {
        //      private static final AppState STATE = AppState.getInstance();
//        public static final SimpleListProperty<ChatDetail> chatList = STATE.chatListProperty();
//        public static final SimpleBooleanProperty chatModel = STATE.chatModelProperty();
//        public static final SimpleStringProperty chatSendUUid = STATE.chatSendUUidProperty();
//        public static final SimpleLongProperty chatViewRefresh = STATE.chatViewRefreshProperty();
    }
}
