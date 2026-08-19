package athena.coder.ai.workflow.report;

import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.entity.chat.ChatEnum;
import athena.coder.entity.model.ModelEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    private static WorkflowState state(Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>();
        m.put(WorkflowState.INIT_TASK_ID, 1L);
        m.put(WorkflowState.INIT_WORK_FULL_PATH, "/tmp/proj");
        m.put(WorkflowState.INIT_USER_MESSAGE, "需求");
        m.put(WorkflowState.INIT_MODEL_TYPE, ModelEnum.QIANWEN37MAX);
        m.put(WorkflowState.INIT_BOT_RESPONSE, (BiConsumer<String, ChatEnum>) (a, b) -> {});
        if (extra != null) {
            m.putAll(extra);
        }
        return new WorkflowState(m);
    }

    @Test
    void format_rendersTitleOverviewRisk() {
        String json = """
                {"report":{"title":"登录功能","overview":"实现登录与登出","riskAssessment":{"overallRisk":"中"}}}
                """;
        String out = ReportFormatter.format(state(null), json, "coder");
        assertTrue(out.contains("**登录功能**"), out);
        assertTrue(out.contains("实现登录与登出"), out);
        assertTrue(out.contains("**风险等级**: 中"), out);
    }

    @Test
    void format_rendersChangedFilesAsList_whenNoDiffRef() {
        String out = ReportFormatter.format(state(Map.of(
                WorkflowState.CHANGED_FILES, "A.java,B.java"
        )), "{}", "coder");
        assertTrue(out.contains("### 变更文件"), out);
        assertTrue(out.contains("- `A.java`"), out);
        assertTrue(out.contains("- `B.java`"), out);
    }

    @Test
    void format_rendersTestSummary() {
        String out = ReportFormatter.format(state(Map.of(
                WorkflowState.TEST_RESULT, "{\"status\":\"PASS\",\"summary\":\"全部通过\"}"
        )), "{}", "coder");
        assertTrue(out.contains("### 测试结果"), out);
        assertTrue(out.contains("✅ 通过"), out);
        assertTrue(out.contains("全部通过"), out);
    }

    @Test
    void format_rendersReviewSummary() {
        String out = ReportFormatter.format(state(Map.of(
                WorkflowState.REVIEW_RESULT, "{\"verdict\":\"APPROVED_WITH_NOTES\",\"summary\":\"有小建议\"}"
        )), "{}", "coder");
        assertTrue(out.contains("### 审查结论"), out);
        assertTrue(out.contains("✅ 通过（有建议）"), out);
    }

    @Test
    void format_rendersCommitAndBranch() {
        String json = """
                {"commitMessage":{"fullMessage":"feat: login"},"branchSuggestion":{"name":"feat/login"}}
                """;
        String out = ReportFormatter.format(state(null), json, "coder");
        assertTrue(out.contains("feat: login"), out);
        assertTrue(out.contains("`feat/login`"), out);
    }

    @Test
    void format_invalidJson_fallsBackToRaw() {
        String bad = "not-json{{{";
        String out = ReportFormatter.format(state(null), bad, "coder");
        assertTrue(out.contains(bad), out);
    }
}
