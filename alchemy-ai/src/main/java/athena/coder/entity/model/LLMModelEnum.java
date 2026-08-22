package athena.coder.entity.model;

/**
 * 语言大模型枚举：model/version 对应 model 表的 name/version。
 */
public enum LLMModelEnum {

    QIANWEN37MAX("qianwen", "qwen3.7-max"),
    QIANWEN35FLASH("qianwen", "qwen3.5-flash"),
    DEEPSEEKV4PRO("deepseek", "deepseek-v4-pro"),
    ;

    private final String model;
    private final String version;

    LLMModelEnum(String model, String version) {
        this.model = model;
        this.version = version;
    }

    public String getModel() {
        return model;
    }

    public String getVersion() {
        return version;
    }

    public static LLMModelEnum fromNameVersion(String name, String version) {
        for (LLMModelEnum e : values()) {
            if (e.model.equals(name) && e.version.equals(version)) {
                return e;
            }
        }
        return null;
    }
}