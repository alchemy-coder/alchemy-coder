package athena.coder.ai.assistant.agent.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownStrippingDeserializerTest {

    public static class Inner {
        public String x;
    }

    public static class Payload {
        @JsonDeserialize(using = MarkdownStrippingDeserializer.class)
        public Inner inner;
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void stringWrappedJson_isStrippedAndDeserialized() throws Exception {
        Payload p = mapper.readValue(
                "{\"inner\":\"```json\\n{\\\"x\\\":\\\"hello\\\"}\\n```\"}",
                Payload.class);
        assertEquals("hello", p.inner.x);
    }

    @Test
    void bareObject_isPassedThrough() throws Exception {
        Payload p = mapper.readValue("{\"inner\":{\"x\":\"world\"}}", Payload.class);
        assertEquals("world", p.inner.x);
    }
}
