package athena.coder.infra.repository;

import athena.coder.infra.entity.chat.ChatDetail;

import java.util.List;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * chat_detail 表数据访问层
 */
public final class ChatRepository {

    private ChatRepository() {
    }

    public static void insert(ChatDetail cd) {
        getJdbi().useHandle(h -> h.createUpdate("""
                        INSERT INTO chat_detail (chat_id, type, content, uuid)
                        VALUES (:chatId, :type, :content, :uuid)
                        """)
                .bind("chatId", cd.getChatId()).bind("type", cd.getType())
                .bind("content", cd.getContent()).bind("uuid", cd.getUuid())
                .execute());
    }

    /**
     * UPSERT：命中唯一索引 (chat_id, type, uuid) 时仅更新内容
     */
    public static void upsert(ChatDetail cd) {
        getJdbi().useHandle(h -> h.createUpdate("""
                        INSERT INTO chat_detail (chat_id, type, content, uuid)
                        VALUES (:chatId, :type, :content, :uuid)
                        ON CONFLICT(chat_id, type, uuid) WHERE deleted_at IS NULL
                        DO UPDATE SET content = excluded.content, update_at = datetime('now', 'localtime')
                        """)
                .bind("chatId", cd.getChatId()).bind("type", cd.getType())
                .bind("content", cd.getContent()).bind("uuid", cd.getUuid())
                .execute());
    }

    /**
     * 按任务 id 查询全部消息（排除软删除），按时间正序
     */
    public static List<ChatDetail> listByChatId(Long chatId) {
        return getJdbi().withHandle(handle ->
                handle.createQuery("SELECT * FROM chat_detail WHERE chat_id = :chatId AND deleted_at IS NULL ORDER BY create_at ASC")
                        .bind("chatId", chatId)
                        .mapToBean(ChatDetail.class)
                        .list());
    }
}