package athena.coder.ai.workflow.entity;

import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.LLMModelEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowStateTest {

    private static Map<String, Object> base() {
        Map<String, Object> m = new HashMap<>();
        m.put(WorkflowState.INIT_TASK_ID, 1L);
        m.put(WorkflowState.INIT_WORK_FULL_PATH, "/tmp/proj");
        m.put(WorkflowState.INIT_USER_MESSAGE, "实现登录");
        m.put(WorkflowState.INIT_MODEL_TYPE, LLMModelEnum.QIANWEN37MAX);
        m.put(WorkflowState.INIT_BOT_RESPONSE, (BiConsumer<String, ChatEnum>) (msg, type) -> {});
        return m;
    }

    @Test
    void constructor_missingEachRequiredField_throwsNpe() {
        List<String> required = List.of(
                WorkflowState.INIT_TASK_ID,
                WorkflowState.INIT_WORK_FULL_PATH,
                WorkflowState.INIT_USER_MESSAGE,
                WorkflowState.INIT_MODEL_TYPE,
                WorkflowState.INIT_BOT_RESPONSE);
        for (String key : required) {
            Map<String, Object> m = base();
            m.remove(key);
            assertThrows(NullPointerException.class, () -> new WorkflowState(m), "缺少字段应抛 NPE: " + key);
        }
    }

    @Test
    void getters_exposeInjectedValues() {
        WorkflowState s = new WorkflowState(base());
        assertEquals(1L, s.getTaskId());
        assertEquals("/tmp/proj", s.getWorkFullPath());
        assertEquals("实现登录", s.getUserMessage());
        assertEquals(LLMModelEnum.QIANWEN37MAX, s.getModelType());
    }

    @Test
    void getStringValue_nullOrNumber() {
        WorkflowState s = new WorkflowState(base());
        assertNull(s.getStringValue("notExist"));

        Map<String, Object> m = base();
        m.put("num", 123);
        assertEquals("123", new WorkflowState(m).getStringValue("num"));
    }

    @Test
    void getIntValue_numberStringInvalid() {
        WorkflowState s = new WorkflowState(base());
        assertEquals(0, s.getIntValue("notExist"));

        assertEquals(42, stateWith("n", 42).getIntValue("n"));
        assertEquals(7, stateWith("s", "7").getIntValue("s"));
        assertEquals(0, stateWith("bad", "abc").getIntValue("bad"));
    }

    @Test
    void buildRoutedMessage_blankFallsBackToUserMessage() {
        assertEquals("实现登录", new WorkflowState(base()).buildRoutedMessage());
        assertEquals("实现登录", stateWith(WorkflowState.ROUTE_CONTEXT, "   ").buildRoutedMessage());
    }

    @Test
    void buildRoutedMessage_prefixesIntent() {
        String out = stateWith(WorkflowState.ROUTE_CONTEXT, "修复登录 bug").buildRoutedMessage();
        assertEquals("意图摘要: 修复登录 bug\n\n用户原始消息: 实现登录", out);
    }

    @Test
    void outputBotResponse_invokesConsumer() {
        AtomicReference<String> msg = new AtomicReference<>();
        AtomicReference<ChatEnum> type = new AtomicReference<>();
        Map<String, Object> m = base();
        m.put(WorkflowState.INIT_BOT_RESPONSE, (BiConsumer<String, ChatEnum>) (a, b) -> {
            msg.set(a);
            type.set(b);
        });
        new WorkflowState(m).outputBotResponse("hi", ChatEnum.ROBOT_PROGRESS);
        assertEquals("hi", msg.get());
        assertEquals(ChatEnum.ROBOT_PROGRESS, type.get());
    }

    @Test
    void projectFacts_absentThenPresent() {
        WorkflowState absent = new WorkflowState(base());
        assertNull(absent.getStringValue(WorkflowState.PROJECT_FACTS));

        String json = "{\"overview\":\"单体项目\"}";
        assertEquals(json, stateWith(WorkflowState.PROJECT_FACTS, json).getStringValue(WorkflowState.PROJECT_FACTS));
    }

    private static WorkflowState stateWith(String key, Object value) {
        Map<String, Object> m = base();
        m.put(key, value);
        return new WorkflowState(m);
    }
}
