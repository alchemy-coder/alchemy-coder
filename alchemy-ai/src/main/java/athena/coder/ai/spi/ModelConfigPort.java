package athena.coder.ai.spi;

import athena.coder.entity.model.ModelType;

/**
 * 模型配置查询端口：实现位于 infra 层（SqliteModelConfig），由组合根装配。
 */
public interface ModelConfigPort {

    /**
     * 按类型 + name + version 查 api_key，不存在返回 null
     */
    String findApiKey(ModelType type, String name, String version);
}