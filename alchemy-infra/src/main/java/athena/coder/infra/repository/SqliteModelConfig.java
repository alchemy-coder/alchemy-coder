package athena.coder.infra.repository;

import athena.coder.ai.spi.ModelConfigPort;
import athena.coder.entity.model.ModelType;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * model 表数据访问层（{@link ModelConfigPort} 的 SQLite 实现）
 */
public final class SqliteModelConfig implements ModelConfigPort {

    public SqliteModelConfig() {
    }

    /**
     * 按 name+version 查 api_key，不存在返回 null
     */
    @Override
    public String findApiKey(ModelType type, String name, String version) {
        return getJdbi().withHandle(handle ->
                handle.createQuery("SELECT api_key FROM model WHERE type = :type AND name = :name AND version = :version AND deleted_at IS NULL")
                        .bind("type", type.dbValue())
                        .bind("name", name)
                        .bind("version", version)
                        .mapTo(String.class)
                        .findOne()
                        .orElse(null));
    }

    /**
     * 查询指定类型的默认模型 [name, version]，没有则返回 null
     */
    public String[] findDefaultModel(ModelType type) {
        return getJdbi().withHandle(handle ->
                handle.createQuery("SELECT name, version FROM model WHERE type = :type AND is_default = 1 AND deleted_at IS NULL")
                        .bind("type", type.dbValue())
                        .map((rs, ctx) -> new String[]{rs.getString("name"), rs.getString("version")})
                        .findOne()
                        .orElse(null));
    }

    /**
     * 保存/更新模型配置（upsert: 按 type+name+version），设为默认时自动取消同类型其他默认
     */
    public void saveModel(ModelType type, String name, String version, String apiKey, boolean isDefault) {
        getJdbi().useTransaction(handle -> {
            if (isDefault) {
                handle.createUpdate("UPDATE model SET is_default = 0, update_at = datetime('now','localtime') WHERE type = :type AND is_default = 1 AND deleted_at IS NULL")
                        .bind("type", type.dbValue())
                        .execute();
            }
            handle.createUpdate("UPDATE model SET deleted_at = datetime('now','localtime') WHERE type = :type AND name = :name AND version = :version AND deleted_at IS NULL")
                    .bind("type", type.dbValue())
                    .bind("name", name)
                    .bind("version", version)
                    .execute();
            handle.createUpdate("INSERT INTO model (type, name, version, api_key, is_default, create_at) VALUES (:type, :name, :version, :apiKey, :isDefault, datetime('now','localtime'))")
                    .bind("type", type.dbValue())
                    .bind("name", name)
                    .bind("version", version)
                    .bind("apiKey", apiKey)
                    .bind("isDefault", isDefault ? 1 : 0)
                    .execute();
        });
    }
}