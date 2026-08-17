package athena.coder.ai.spi;

/**
 * 模型配置查询端口：实现位于 infra 层（SqliteModelConfig），由组合根装配。
 */
public interface ModelConfigPort {

    /**
     * 按 name+version 查 api_key，不存在返回 null
     */
    String findApiKey(String name, String version);
}
