package athena.coder.infra.repository;

import athena.coder.entity.tree.QuestEntity;

import java.util.List;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * quest_list 表数据访问层（项目/任务树节点）
 */
public final class QuestRepository {

    private QuestRepository() {
    }

    /**
     * 插入节点，返回自增 id
     */
    public static long insert(QuestEntity entity) {
        return getJdbi().withHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO quest_list (parent_id, title, type, expand)
                                VALUES (:parentId, :title, :type, :expand)
                                """)
                        .bind("parentId", entity.getParentId())
                        .bind("title", entity.getTitle())
                        .bind("type", entity.getType())
                        .bind("expand", entity.getExpand())
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one());
    }

    /**
     * 按 id 查询节点（排除软删除），不存在返回 null
     */
    public static QuestEntity findById(Long id) {
        return getJdbi().withHandle(handle ->
                handle.createQuery("SELECT * FROM quest_list WHERE id = :id AND deleted_at IS NULL")
                        .bind("id", id)
                        .mapToBean(QuestEntity.class)
                        .findOne()
                        .orElse(null));
    }

    /**
     * 刷新 update_at 为当前时间（最近使用排序）
     */
    public static void touch(List<Long> ids) {
        getJdbi().useHandle(handle ->
                handle.createUpdate("UPDATE quest_list SET update_at = datetime('now', 'localtime') WHERE id IN (<ids>) AND deleted_at IS NULL")
                        .bindList("ids", ids)
                        .execute());
    }

    /**
     * 更新 expand 扩展字段
     */
    public static void updateExpand(Long id, String expand) {
        getJdbi().useHandle(handle ->
                handle.createUpdate("UPDATE quest_list SET expand = :expand WHERE id = :id")
                        .bind("expand", expand)
                        .bind("id", id)
                        .execute());
    }

    /**
     * 全部节点（排除软删除），按 update_at 倒序
     */
    public static List<QuestEntity> listAll() {
        return getJdbi().withHandle(handle ->
                handle.createQuery("SELECT * FROM quest_list WHERE deleted_at IS NULL ORDER BY update_at DESC")
                        .mapToBean(QuestEntity.class)
                        .list());
    }
}
