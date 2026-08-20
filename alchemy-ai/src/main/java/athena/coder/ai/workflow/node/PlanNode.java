package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.PlannerAgent;
import athena.coder.ai.assistant.agent.result.planner.PlanResult;
import athena.coder.ai.assistant.agent.result.router.WorkflowMode;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 规划节点
 * <p>
 * 负责：
 * 1. 从 state 读取用户需求和上下文
 * 2. 调用 PlannerAgent 生成执行计划
 * 3. 将计划存入 state，静态边进入 PLAN_CONFIRM 人工确认
 */
public class PlanNode extends AbstractAgentNode {

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        String userMessage = state.buildRoutedMessage();
        // 用户拒绝过上一版计划时，组装结构化重规划请求（PLAN_FEEDBACK 由 PLAN_CONFIRM 写入）
        String planFeedback = state.getStringValue(PLAN_FEEDBACK);
        if (planFeedback != null && !planFeedback.isBlank()) {
            userMessage = buildReplanMessage(userMessage, state.getStringValue(PLAN), planFeedback);
        }
        WorkflowMode workflowMode = ctx.requireWorkflowMode();

        notifyModelCalling(state);

        PlannerAgent assistant = newChatAssistant(ctx.modelType(), PlannerAgent.class, AgentToolPolicy.PLANNER);
        AgentCall<PlanResult> call = request -> {
            PlanResult r = assistant.plan(ctx.taskId(), request, ctx.projectPath(), ctx.projectType(), workflowMode);
            // 在 AgentCall 内部校验完整性：普通 Exception 触发 callAgentWithRetry 重试
            if (r.designBlueprint() == null || r.acceptanceCriteria() == null) {
                throw new Exception("PlannerAgent 返回结果不完整（designBlueprint或acceptanceCriteria为空）");
            }
            return r;
        };

        PlanResult result = callAgentWithRetry(userMessage,
                "你上次的输出格式不正确，请重新生成执行计划并严格按JSON格式输出。用户需求: " + userMessage,
                call, null);

        JsonNode blueprintNode = result.designBlueprint();
        String designBlueprint = blueprintNode.toString();
        String acceptanceCriteria = result.acceptanceCriteria();

        // 第二层：输出结构化执行计划给用户（JSON → Markdown 表格）
        String planMarkdown = blueprintToMarkdown(blueprintNode);
        notifyResult(state, "", planMarkdown);

        // 下一跳为静态边 PLAN_CONFIRM，不写 NEXT_NODE，避免残留信号误导节点完成日志
        return Map.of(
                PLAN, designBlueprint,
                ACCEPTANCE_CRITERIA, acceptanceCriteria
        );
    }

    /**
     * 组装结构化重规划请求：原需求 / 上一版计划 / 修改意见分段隔离，
     * 避免模型将原需求与修改意见混淆；同时提供上一版计划，
     * 使意见能精确对应到具体任务（如“任务2改成xx”）
     */
    private String buildReplanMessage(String originalRequirement, String previousPlan, String feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("【场景说明】用户拒绝了上一版执行计划，请基于原需求与下方修改意见重新规划。\n\n");
        sb.append("【原需求】\n").append(originalRequirement).append("\n\n");
        if (previousPlan != null && !previousPlan.isBlank()) {
            sb.append("【上一版执行计划】\n").append(previousPlan).append("\n\n");
        }
        sb.append("【用户修改意见】\n").append(feedback).append("\n\n");
        sb.append("请保留上一版计划中用户未提出异议的部分，仅按修改意见调整相关任务。");
        return sb.toString();
    }

    /**
     * 将 designBlueprint JSON 转为结构化 Markdown，供 UI 层 marked.js 渲染。
     */
    private String blueprintToMarkdown(JsonNode bp) {
        StringBuilder md = new StringBuilder();
        md.append("## 📋 执行计划\n\n");

        // ── 概要信息 ──
        appendField(md, "**目标**", bp, "objective");
        appendField(md, "**模式**", bp, "workflowMode");
        appendField(md, "**复杂度**", bp, "estimatedComplexity");
        appendField(md, "**ID**", bp, "planId");
        String context = bp.has("contextSummary") ? bp.get("contextSummary").asText() : null;
        if (context != null && !context.isBlank()) {
            md.append("\n> ").append(context.replace("\n", "\n> ")).append("\n");
        }

        // ── 任务列表 ──
        JsonNode tasks = bp.get("tasks");
        if (tasks != null && tasks.isArray() && tasks.size() > 0) {
            md.append("\n### 📝 任务列表\n\n");
            md.append("| # | 任务 | 操作 | 风险 | 目标文件 |\n");
            md.append("|---|------|------|------|----------|\n");
            for (JsonNode t : tasks) {
                int tid = t.has("taskId") ? t.get("taskId").asInt() : 0;
                String title = fieldStr(t, "title");
                String action = actionBadge(fieldStr(t, "action"));
                String risk = riskBadge(fieldStr(t, "risk"));
                String files = targetFilesStr(t);
                md.append("| ").append(tid).append(" | ").append(title)
                        .append(" | ").append(action).append(" | ").append(risk)
                        .append(" | ").append(files).append(" |\n");
            }
            md.append("\n");

            // 每个任务的详细描述
            for (JsonNode t : tasks) {
                int tid = t.has("taskId") ? t.get("taskId").asInt() : 0;
                String title = fieldStr(t, "title");
                String desc = fieldStr(t, "description");
                String notes = fieldStr(t, "implementationNotes");
                if (desc.isBlank() && notes.isBlank()) continue;
                md.append("**任务 ").append(tid).append(" - ").append(title).append("**\n\n");
                if (!desc.isBlank()) md.append(desc).append("\n\n");
                if (!notes.isBlank()) md.append("> ").append(notes.replace("\n", "\n> ")).append("\n\n");
            }
        }

        // ── 执行图 ──
        JsonNode eg = bp.get("executionGraph");
        if (eg != null) {
            md.append("### ⚡ 执行图\n\n");
            if (eg.has("totalTasks"))
                md.append("- **总任务数**: ").append(eg.get("totalTasks").asInt()).append("\n");
            if (eg.has("criticalPath")) {
                md.append("- **关键路径**: ");
                JsonNode cp = eg.get("criticalPath");
                if (cp.isArray()) {
                    for (int i = 0; i < cp.size(); i++) {
                        if (i > 0) md.append(" → ");
                        md.append(cp.get(i).asText());
                    }
                }
                md.append("\n");
            }
            if (eg.has("parallelGroups")) {
                JsonNode pg = eg.get("parallelGroups");
                if (pg.isObject()) {
                    var it = pg.fields();
                    while (it.hasNext()) {
                        var entry = it.next();
                        md.append("- **并行组 ").append(entry.getKey()).append("**: ");
                        JsonNode arr = entry.getValue();
                        if (arr.isArray()) {
                            for (int i = 0; i < arr.size(); i++) {
                                if (i > 0) md.append(", ");
                                md.append(arr.get(i).asText());
                            }
                        }
                        md.append("\n");
                    }
                }
            }
            md.append("\n");
        }

        // ── 全局风险 ──
        JsonNode risks = bp.get("globalRisks");
        if (risks != null && risks.isArray() && risks.size() > 0) {
            md.append("### ⚠️ 风险评估\n\n");
            for (JsonNode r : risks) {
                String level = fieldStr(r, "level");
                String desc = fieldStr(r, "description");
                String mit = fieldStr(r, "mitigation");
                md.append("- ").append(riskBadge(level)).append(" ").append(desc).append("\n");
                if (!mit.isBlank()) md.append("  - 缓解: ").append(mit).append("\n");
                JsonNode vs = r.get("verificationSteps");
                if (vs != null && vs.isArray()) {
                    for (JsonNode v : vs) {
                        md.append("  - 验证: ").append(v.asText()).append("\n");
                    }
                }
            }
            md.append("\n");
        }

        return md.toString();
    }

    @Override
    protected StepRole stepRole() {
        return StepRole.PLANNER;
    }

    private static void appendField(StringBuilder md, String label, JsonNode bp, String field) {
        String v = bp.has(field) ? bp.get(field).asText() : null;
        if (v != null && !v.isBlank()) {
            md.append(label).append(": ").append(v).append("  \n");
        }
    }

    private static String fieldStr(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : "";
    }

    private static String actionBadge(String action) {
        if (action == null || action.isBlank()) return "-";
        return switch (action.toUpperCase()) {
            case "CREATE" -> "🆕 新建";
            case "MODIFY" -> "✏️ 修改";
            case "DELETE" -> "🗑️ 删除";
            default -> action;
        };
    }

    private static String riskBadge(String risk) {
        if (risk == null || risk.isBlank()) return "-";
        return switch (risk.toUpperCase()) {
            case "HIGH" -> "🔴 高";
            case "MEDIUM" -> "🟡 中";
            case "LOW" -> "🟢 低";
            default -> risk;
        };
    }

    private static String targetFilesStr(JsonNode task) {
        JsonNode files = task.get("targetFiles");
        if (files == null || !files.isArray() || files.size() == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(files.size(), 3); i++) {
            String f = files.get(i).asText();
            // 只显示文件名，不显示全路径
            int lastSlash = f.lastIndexOf('/');
            if (i > 0) sb.append(", ");
            sb.append(lastSlash >= 0 ? f.substring(lastSlash + 1) : f);
        }
        if (files.size() > 3) sb.append(" ...").append(files.size()).append("个");
        return sb.toString();
    }

}
