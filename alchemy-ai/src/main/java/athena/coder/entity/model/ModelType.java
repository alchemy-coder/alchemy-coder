package athena.coder.entity.model;

/**
 * 模型类型：区分语言大模型与向量大模型（对应 model 表 type 列）。
 */
public enum ModelType {

    CHAT("chat"),
    EMBEDDING("embedding"),
    ;

    private final String dbValue;

    ModelType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
