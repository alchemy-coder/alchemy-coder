package athena.coder.ai.spi;

import athena.coder.entity.model.ModelEntity;
import athena.coder.entity.model.ModelEnum;

/**
 * 模型配置查询端口：实现位于 infra 层（SqliteModelConfig），由组合根装配。
 */
public interface ModelConfigPort {

    /**
     * 按模型枚举查询模型配置，不存在抛出 NPE（模型配置为系统必备数据）
     */
    ModelEntity findByModel(ModelEnum modelEnum);

    /**
     * 按 name+version 查 api_key（供 embedding 等非 ModelEnum 模型使用），不存在返回 null
     */
    String findApiKey(String name, String version);
}
