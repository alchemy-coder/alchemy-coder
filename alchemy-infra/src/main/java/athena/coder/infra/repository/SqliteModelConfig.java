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
}