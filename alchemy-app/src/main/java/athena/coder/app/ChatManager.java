package athena.coder.app;

import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.workflow.MasterWorkflow;
import athena.coder.ai.workflow.gate.HumanGate;
import athena.coder.infra.repository.ChatRepository;
import athena.coder.infra.repository.QuestRepository;
import athena.coder.entity.chat.ChatDetail;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.ModelEnum;
import athena.coder.entity.tree.QuestEntity;
import athena.coder.ui.content.chatview.ChatView;
import javafx.application.Platform;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import static athena.coder.ai.workflow.entity.WorkflowState.*;
import static athena.coder.app.AppState.*;
import static athena.coder.app.ProjectManager.getProjectPath;

/**
 * 聊天入口：用户消息发送、MasterWorkflow 驱动、机器人回复的展示与持久化。
 */
public class ChatManager {

    private static final ExecutorService CHAT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-executor");
        t.setDaemon(true);
        return t;
    });

    private ChatManager() {
    }

    /**
     * 发送入口：校验 → 持久化 → 刷新 UI → 提交工作流
     */
    public static void send() {
        String msg = sendMsg.get();
        if (msg == null || msg.isBlank()) {
            return;
        }
        // 存在待确认的人工确认门：用户回复直接投递给门，不启动新工作流
        if (HumanGate.hasPending(curTaskId.get())) {
            sendGateReply(msg);
            return;
        }
        if (isExecuteTask.get()) {
            return;
        }
        isExecuteTask.set(true);
        chatSendUUid.set(UUID.randomUUID().toString());

        long projectId = curProject.get();
        long taskId = curTaskId.get();

        ChatDetail userChat = newChat(taskId, ChatEnum.USER, msg, null);
        ChatDetail robotChat = null;
        if (projectId == 0) {
            // 未选择项目，仅提示引导
            robotChat = newChat(taskId, ChatEnum.ROBOT, "请先选择您的项目", chatSendUUid.get());
            isExecuteTask.set(false);
        } else {
            if (taskId == 0) {
                taskId = createTask(projectId, msg);
                userChat.setChatId(taskId);
            }
            ChatRepository.insert(userChat);
        }

        chatList.getValue().add(userChat);
        if (robotChat != null) {
            chatList.getValue().add(robotChat);
        }

        sendMsg.set("");
        curTaskId.set(taskId);
        chatModel.setValue(true);
        treeListFresh.setValue(treeListFresh.getValue() + 1);

        if (projectId != 0) {
            sendMsgToModel(msg);
        }
    }

    /**
     * 确认等待分支：持久化用户回复、刷新 UI 后投递给确认门（不启动新工作流，
     * 不改动 isExecuteTask，工作流线程恢复后自然走完原清理逻辑）。
     * <p>
     * 输入框回复与确认按钮卡片共用此入口，链路完全一致。需在 JavaFX 线程调用
     * （输入框发送与按钮 onAction 均在 FX 线程）。
     */
    public static void sendGateReply(String msg) {
        long taskId = curTaskId.get();
        // 防护残留按钮卡误点/重复投递：无待确认门时直接忽略，不产生任何副作用
        if (!HumanGate.hasPending(taskId)) {
            return;
        }
        chatSendUUid.set(UUID.randomUUID().toString());

        ChatDetail userChat = newChat(taskId, ChatEnum.USER, msg, null);
        ChatRepository.insert(userChat);
        chatList.getValue().add(userChat);

        sendMsg.set("");
        // 确认已被消费：移除确认按钮卡片（按钮一次性，历史回看不再出现）
        ChatView.removeConfirmBar();
        // 工作流即将恢复执行（确认放行或重新规划），恢复 loading 指示器
        Platform.runLater(ChatView::addLoadingIndicator);
        HumanGate.answer(taskId, msg);
    }

    /**
     * 新建任务节点并返回任务 id
     */
    private static long createTask(long projectId, String title) {
        QuestEntity task = new QuestEntity();
        task.setType("TASK");
        task.setTitle(title);
        task.setParentId(projectId);
        return QuestRepository.insert(task);
    }

    private static void sendMsgToModel(String msg) {
        // 在 JavaFX 线程上捕获 uuid，避免跨线程读取 SimpleStringProperty 的可见性问题
        String uuid = chatSendUUid.get();
        CHAT_EXECUTOR.submit(() -> {
            try {
                MasterWorkflow masterWorkflow = new MasterWorkflow();
                Map<String, Object> initData = new HashMap<>();
                BiConsumer<String, ChatEnum> robotResponseWriter = createBotResponseWriter(uuid);
                initData.put(INIT_TASK_ID, curTaskId.get());
                initData.put(INIT_WORK_FULL_PATH, getProjectPath());
                initData.put(INIT_USER_MESSAGE, msg);
                initData.put(INIT_MODEL_TYPE, ModelEnum.DEEPSEEKV4PRO);
                initData.put(INIT_BOT_RESPONSE, robotResponseWriter);

                // 即时反馈：用户发消息后立刻显示 loading 指示器
                Platform.runLater(ChatView::addLoadingIndicator);

                masterWorkflow.start(initData);
                // 工作流正常结束后移除 loading 指示器
                Platform.runLater(ChatView::removeLoadingIndicator);
            } catch (Exception e) {
                ErrorLogger.log("ChatManager.workflow", e, curTaskId.get(), null, msg);
                // 异常时移除 loading 指示器与确认按钮卡（门可能随工作流死亡，按钮卡不能残留）
                Platform.runLater(() -> {
                    ChatView.removeLoadingIndicator();
                    ChatView.removeConfirmBar();
                });
                String errorMsg = "抱歉，处理您的请求时出现异常：" + e.getMessage();
                ChatDetail errorChat = newChat(curTaskId.get(), ChatEnum.ROBOT_ERROR, errorMsg, uuid);
                Platform.runLater(() -> chatList.addLast(errorChat));
                saveRobotAnswer(errorMsg, ChatEnum.ROBOT_ERROR, uuid);
            }
            Platform.runLater(() -> isExecuteTask.set(false));
        });
    }

    /**
     * 工作流输出回调：同步 UI + 持久化 + 更新 loading 文字
     */
    private static @NonNull BiConsumer<String, ChatEnum> createBotResponseWriter(String uuid) {
        return (BiConsumer<String, ChatEnum> & Serializable) (content, type) -> {
            ChatDetail robotChat = newChat(curTaskId.get(), type, content, uuid);
            Platform.runLater(() -> chatList.addLast(robotChat));
            saveRobotAnswer(content, type, uuid);
            // 同步更新 loading 指示器文字（节点步骤 / 工具调用）
            if (type == ChatEnum.ROBOT_PROGRESS) {
                ChatView.updateLoadingStep(content);
            } else if (type == ChatEnum.ROBOT_CONFIRM) {
                // 进入人工确认等待：工作流将长时间阻塞等用户回复，移除 loading 避免误导，并追加原生确认按钮卡片
                Platform.runLater(() -> {
                    ChatView.removeLoadingIndicator();
                    ChatView.addConfirmBar();
                });
            }
        };
    }

    /**
     * 机器人消息持久化：PROGRESS 不存 / 其他 UPSERT（同一 uuid 同类型重复写入时更新内容）。
     */
    private static void saveRobotAnswer(String content, ChatEnum type, String uuid) {
        if (type == ChatEnum.ROBOT_PROGRESS) {
            return;
        }
        try {
            ChatRepository.upsert(newChat(curTaskId.get(), type, content, uuid));
        } catch (Exception e) {
            // DB 写入失败不应阻断消息展示
            ErrorLogger.log("ChatManager.saveRobotAnswer", e, curTaskId.get(), null, null);
        }
    }

    /**
     * 按任务 id 重新加载聊天记录
     */
    public static void refreshChatDataByChatId(Long chatId) {
        chatList.clear();
        chatList.setAll(ChatRepository.listByChatId(chatId));
    }

    // ==================== ChatDetail 构建 ====================

    private static ChatDetail newChat(Long chatId, ChatEnum type, String content, String uuid) {
        ChatDetail chat = new ChatDetail();
        chat.setChatId(chatId);
        chat.setType(type.name());
        chat.setContent(content);
        chat.setUuid(uuid);
        return chat;
    }
}
