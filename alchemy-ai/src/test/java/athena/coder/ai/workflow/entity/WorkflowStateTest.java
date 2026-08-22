package athena.coder.ai.workflow.entity;

import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
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
    void typedStringGetter_absentReturnsNull() {
        WorkflowState s = new WorkflowState(base());
        assertNull(s.getPlan());
        assertNull(s.getProjectFacts());
        assertNull(s.getChangedFiles());
        assertNull(s.getTestResult());
        assertNull(s.getReviewResult());
        assertNull(s.getSummarizeResult());
    }

    @Test
    void typedStringGetter_numberCoercedToString() {
        assertEquals("123", stateWith(WorkflowState.PLAN, 123).getPlan());
    }

    @Test
    void typedIntGetter_absentReturnsZero() {
        WorkflowState s = new WorkflowState(base());
        assertEquals(0, s.getDebugLoopCount());
        assertEquals(0, s.getReviewLoopCount());
        assertEquals(0, s.getPlanConfirmCount());
    }

    @Test
    void typedIntGetter_numberStringInvalid() {
        assertEquals(42, stateWith(WorkflowState.DEBUG_LOOP_COUNT, 42).getDebugLoopCount());
        assertEquals(7, stateWith(WorkflowState.DEBUG_LOOP_COUNT, "7").getDebugLoopCount());
        assertEquals(0, stateWith(WorkflowState.DEBUG_LOOP_COUNT, "abc").getDebugLoopCount());
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
        assertNull(absent.getProjectFacts());

        String json = "{\"overview\":\"单体项目\"}";
        assertEquals(json, stateWith(WorkflowState.PROJECT_FACTS, json).getProjectFacts());
    }

    @Test
    void workflowMode_absentThenPresent() {
        WorkflowState absent = new WorkflowState(base());
        assertNull(absent.getWorkflowMode());

        assertEquals(WorkflowMode.CODE_WORKFLOW, stateWith(WorkflowState.WORKFLOW_MODE, WorkflowMode.CODE_WORKFLOW).getWorkflowMode());
    }

    private static WorkflowState stateWith(String key, Object value) {
        Map<String, Object> m = base();
        m.put(key, value);
        return new WorkflowState(m);
    }
}
