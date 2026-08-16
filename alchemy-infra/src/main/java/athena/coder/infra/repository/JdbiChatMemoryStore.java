package athena.coder.infra.repository;

import athena.coder.ai.spi.ErrorLogger;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * 持久化 ChatMemoryStore 实现
 * 保留内存缓存层（ConcurrentHashMap）+ 数据库持久化（chat_memory 表），
 * 每条消息存储为独立的一行记录。
 */
public class JdbiChatMemoryStore implements ChatMemoryStore {

    private static final Logger LOG = Logger.getLogger(JdbiChatMemoryStore.class.getName());

    private final String agentType;

    /**
     * 内存缓存层
     */
//    private final Map<Object, List<ChatMessage>> cache = new ConcurrentHashMap<>();
    private JdbiChatMemoryStore(String agentType) {
        this.agentType = agentType;
    }

    public static JdbiChatMemoryStore getInstance(String agentType) {
        return new JdbiChatMemoryStore(agentType);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        LOG.log(Level.FINE, "从数据库加载聊天记忆: memoryId={}", memoryId);

        List<ChatMessage> messages = new ArrayList<>();
        try {
            List<String> jsonList = getJdbi().withHandle(handle ->
                    handle.createQuery("""
                                    SELECT message_content FROM chat_memory
                                    WHERE memory_id = :memoryId AND agent_type = :agentType AND is_deleted = 0
                                    ORDER BY id ASC
                                    """)
                            .bind("memoryId", memoryId.toString())
                            .bind("agentType", agentType)
                            .mapTo(String.class)
                            .list()
            );

            for (String json : jsonList) {
                ChatMessage msg = deserializeSingleMessage(json);
                if (msg != null) {
                    messages.add(msg);
                }
            }
        } catch (Exception e) {
            ErrorLogger.log("PersistentChatMemoryStore.getMessages", e, null, agentType, null);
        }

        return new ArrayList<>(messages);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        LOG.log(Level.FINE, "更新数据库聊天记忆: memoryId={}", memoryId);

        List<ChatMessage> copy = new ArrayList<>(messages);

        try {
            getJdbi().useHandle(handle -> {
                handle.createUpdate("DELETE FROM chat_memory WHERE memory_id = :memoryId AND is_deleted = 0 and agent_type=:agentType")
                        .bind("memoryId", memoryId.toString())
                        .bind("agentType", agentType)
                        .execute();

                for (ChatMessage msg : copy) {
                    String json = serializeSingleMessage(msg);
                    handle.createUpdate("""
                                    INSERT INTO chat_memory (memory_id, agent_type, message_content, is_deleted, create_at, update_at)
                                    VALUES (:memoryId, :agentType, :messageContent, 0, datetime('now', 'localtime'), datetime('now', 'localtime'))
                                    """)
                            .bind("memoryId", memoryId.toString())
                            .bind("agentType", agentType)
                            .bind("messageContent", json)
                            .execute();
                }
            });
        } catch (Exception e) {
            ErrorLogger.log("PersistentChatMemoryStore.updateMessages", e, null, agentType, null);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            getJdbi().useHandle(handle ->
                    handle.createUpdate("UPDATE chat_memory SET is_deleted = 1 WHERE memory_id = :memoryId AND is_deleted = 0 and agent_type=:agentType")
                            .bind("memoryId", memoryId.toString())
                            .bind("agentType", agentType)
                            .execute()
            );
        } catch (Exception e) {
            ErrorLogger.log("PersistentChatMemoryStore.deleteMessages", e, null, agentType, null);
        }
        LOG.info("已清除 memoryId=" + memoryId + " 的对话历史");
    }


    private String serializeSingleMessage(ChatMessage message) {
        return ChatMessageSerializer.messageToJson(message);
    }

    private ChatMessage deserializeSingleMessage(String json) {
        try {
            return ChatMessageDeserializer.messageFromJson(json);
        } catch (Exception e) {
            ErrorLogger.log("PersistentChatMemoryStore.deserialize", e, null, agentType, null);
            return null;
        }
    }
}