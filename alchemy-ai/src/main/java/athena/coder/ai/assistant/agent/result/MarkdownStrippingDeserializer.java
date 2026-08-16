package athena.coder.ai.assistant.agent.result;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

import java.io.IOException;

/**
 * 通用反序列化器：剥离 LLM 输出的 markdown 包裹后，委托给 Jackson 标准机制反序列化为目标类型。
 * <p>
 * 用法：在目标类上加 {@code @JsonDeserialize(using = MarkdownStrippingDeserializer.class)}
 */
public class MarkdownStrippingDeserializer extends JsonDeserializer<Object> implements ContextualDeserializer {

    /**
     * 纯净 mapper：忽略 @JsonDeserialize 注解，避免递归调用自身
     */
    private static final ObjectMapper CLEAN_MAPPER = createCleanMapper();

    private final JavaType targetType;

    public MarkdownStrippingDeserializer() {
        this.targetType = null;
    }

    private MarkdownStrippingDeserializer(JavaType targetType) {
        this.targetType = targetType;
    }

    private static ObjectMapper createCleanMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 仅屏蔽 @JsonDeserialize 以避免递归调用自身；其余注解（@JsonIgnoreProperties/@JsonProperty/@JsonCreator）保持默认行为
        mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public Object findDeserializer(Annotated a) {
                return null;
            }
        });
        return mapper;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctx, BeanProperty property) {
        return new MarkdownStrippingDeserializer(ctx.getContextualType());
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String raw;
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            raw = p.getValueAsString();
        } else {
            // LLM 直接输出了 JSON 对象（非字符串包裹），读取整棵树转为 JSON 文本
            raw = p.readValueAsTree().toString();
        }
        String json = MarkdownUtils.stripMarkdown(raw);
        return CLEAN_MAPPER.readValue(json, targetType);
    }
}