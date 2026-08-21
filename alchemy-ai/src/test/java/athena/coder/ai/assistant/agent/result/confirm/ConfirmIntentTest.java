package athena.coder.ai.assistant.agent.result.confirm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfirmIntentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fromString_fourCategories() {
        assertEquals(ConfirmIntent.CONFIRM, ConfirmIntent.fromString("CONFIRM"));
        assertEquals(ConfirmIntent.REVISE, ConfirmIntent.fromString("REVISE"));
        assertEquals(ConfirmIntent.CLARIFY, ConfirmIntent.fromString("CLARIFY"));
        assertEquals(ConfirmIntent.REJECT, ConfirmIntent.fromString("REJECT"));
    }

    @Test
    void fromString_caseInsensitiveAndSynonyms() {
        assertEquals(ConfirmIntent.CONFIRM, ConfirmIntent.fromString("confirm"));
        assertEquals(ConfirmIntent.CONFIRM, ConfirmIntent.fromString("同意"));
        assertEquals(ConfirmIntent.REVISE, ConfirmIntent.fromString("modify"));
        assertEquals(ConfirmIntent.REVISE, ConfirmIntent.fromString("修改"));
        assertEquals(ConfirmIntent.CLARIFY, ConfirmIntent.fromString("ask"));
        assertEquals(ConfirmIntent.CLARIFY, ConfirmIntent.fromString("询问"));
        assertEquals(ConfirmIntent.CLARIFY, ConfirmIntent.fromString("为什么"));
        assertEquals(ConfirmIntent.REJECT, ConfirmIntent.fromString("CANCEL"));
        assertEquals(ConfirmIntent.REJECT, ConfirmIntent.fromString("取消"));
    }

    @Test
    void fromString_nullOrUnknown_returnsNull() {
        assertNull(ConfirmIntent.fromString(null));
        assertNull(ConfirmIntent.fromString(""));
        assertNull(ConfirmIntent.fromString("   "));
        assertNull(ConfirmIntent.fromString("随便说点什么"));
    }

    @Test
    void deserialize_reviseWithDirectives() throws Exception {
        String json = """
                {"intent":"REVISE","revise":{"scope":"TARGETED","targetTaskIds":[2,3],
                "directives":[{"target":"任务2存储方案","change":"改为文件存储","reason":"避免外部依赖"}],
                "summary":"任务2存储改为文件存储"}}
                """;
        ConfirmIntentResult r = mapper.readValue(json, ConfirmIntentResult.class);
        assertEquals(ConfirmIntent.REVISE, r.intent());
        assertNotNull(r.revise());
        assertEquals("TARGETED", r.revise().scope());
        assertEquals(List.of(2, 3), r.revise().targetTaskIds());
        assertEquals(1, r.revise().directives().size());
        assertEquals("任务2存储方案", r.revise().directives().get(0).target());
        assertEquals("改为文件存储", r.revise().directives().get(0).change());
        assertEquals("避免外部依赖", r.revise().directives().get(0).reason());
    }

    @Test
    void deserialize_confirmWithoutRevise() throws Exception {
        ConfirmIntentResult r = mapper.readValue("{\"intent\":\"CONFIRM\"}", ConfirmIntentResult.class);
        assertEquals(ConfirmIntent.CONFIRM, r.intent());
        assertNull(r.revise());
    }

    @Test
    void convenienceConstructor_setsReviseNull() {
        ConfirmIntentResult r = new ConfirmIntentResult(ConfirmIntent.REJECT);
        assertEquals(ConfirmIntent.REJECT, r.intent());
        assertNull(r.revise());
    }

    @Test
    void deserialize_clarifyIntentWithoutRevise() throws Exception {
        ConfirmIntentResult r = mapper.readValue("{\"intent\":\"CLARIFY\"}", ConfirmIntentResult.class);
        assertEquals(ConfirmIntent.CLARIFY, r.intent());
        assertNull(r.revise());
    }

    @Test
    void deserialize_clarifyResult() throws Exception {
        String json = """
                {"answer":"任务3的存储用文件，避免引入外部依赖","suggestion":"确认执行即可"}
                """;
        ClarifyResult r = mapper.readValue(json, ClarifyResult.class);
        assertEquals("任务3的存储用文件，避免引入外部依赖", r.answer());
        assertEquals("确认执行即可", r.suggestion());
    }
}
