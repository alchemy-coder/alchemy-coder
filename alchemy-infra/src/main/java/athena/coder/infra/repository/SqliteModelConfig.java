package athena.coder.infra.repository;

import athena.coder.ai.spi.ModelConfigPort;
import athena.coder.entity.model.ModelEntity;
import athena.coder.entity.model.ModelEnum;

import java.util.Objects;

import static athena.coder.infra.DbManager.getJdbi;

/**
 * model 表数据访问层（{@link ModelConfigPort} 的 SQLite 实现）
 */
public final class SqliteModelConfig implements ModelConfigPort {

    public SqliteModelConfig() {
    }

    /**
     * 按模型枚举查询模型配置，不存在抛出 NPE（模型配置为系统必备数据）
     */
    @Override
    public ModelEntity findByModel(ModelEnum modelEnum) {
        return Objects.requireNonNull(getJdbi().withHandle(handle ->
                handle.createQuery("SELECT * FROM model WHERE name = :name AND version = :version AND deleted_at IS NULL")
                        .bind("name", modelEnum.getModel())
                        .bind("version", modelEnum.getVersion())
                        .mapToBean(ModelEntity.class)
                        .findOne()
                        .orElse(null)));
    }

    /**
     * 按 name+version 查 api_key（供 embedding 等非 ModelEnum 模型使用），不存在返回 null
     */
    @Override
    public String findApiKey(String name, String version) {
        return getJdbi().withHandle(handle ->
                handle.createQuery("SELECT api_key FROM model WHERE name = :name AND version = :version AND deleted_at IS NULL")
                        .bind("name", name)
                        .bind("version", version)
                        .mapTo(String.class)
                        .findOne()
                        .orElse(null));
    }
}
