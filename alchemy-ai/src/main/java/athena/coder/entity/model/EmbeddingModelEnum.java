package athena.coder.entity.model;

/**
 * 向量模型枚举：model/version 对应 model 表的 name/version。
 */
public enum EmbeddingModelEnum {

    QIANWEN_EMBEDDING_V4("qianwen", "text-embedding-v4"),
    ;

    private final String model;
    private final String version;

    EmbeddingModelEnum(String model, String version) {
        this.model = model;
        this.version = version;
    }

    public String getModel() {
        return model;
    }

    public String getVersion() {
        return version;
    }

    public static EmbeddingModelEnum fromNameVersion(String name, String version) {
        for (EmbeddingModelEnum e : values()) {
            if (e.model.equals(name) && e.version.equals(version)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 落库的模型标识（隔离不同模型的向量，互不兼容）
     */
    public String key() {
        return model + "/" + version;
    }
}