package athena.coder.ai.workflow.node.debug;

import athena.coder.ai.assistant.agent.debug.FixApplyAgent;
import athena.coder.ai.assistant.agent.result.coder.CoderResult;
import athena.coder.ai.tool.util.GitHelper;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.workflow.node.AbstractAgentNode;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.exception.RocAgentException;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 缺陷修复工作流 - 修复执行节点
 * <p>
 * 职责：按修复策略（或已确认的缺陷修复计划）以最小改动修复缺陷。
 * git 安全编排（隔离/提交/回滚/恢复）统一委托 {@link GitHelper#isolateAndCommit}。
 */
public class FixApplyNode extends AbstractAgentNode {

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?:^|\\s)(?!https?://)([\\w./\\-]+/[\\w./\\-]*\\.(?:java|kt|py|js|ts|go|rs|xml|json|yml|yaml|properties|gradle|md|txt|html|css|sql|sh))", Pattern.MULTILINE);

    @Override
    protected String stepLabel() {
        return "[修复]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        if (!GitHelper.isGitRepo(ctx.projectPath())) {
            throw new RocAgentException("FixApplyNode: 项目目录不是 git 仓库: " + ctx.projectPath());
        }
        String plan = state.getStringValue(PLAN);
        String fixStrategy = state.getStringValue(FIX_STRATEGY);

        notifyModelCalling(state);
        logStart(ctx, "开始修复");

        // 组装任务描述：fixStrategy 优先，其次 plan（首轮修复直接依据缺陷修复计划）
        String taskDescription = (fixStrategy != null && !fixStrategy.isBlank()) ? fixStrategy : plan;
        if (taskDescription == null || taskDescription.isBlank()) {
            throw new RocAgentException("FixApplyNode 无法获取任务描述（plan 和 fixStrategy 均为空）");
        }

        FixApplyAgent assistant = newChatAssistant(ctx.modelType(), FixApplyAgent.class);
        GitHelper.IsolationResult<CoderResult> isolation = GitHelper.isolateAndCommit(
                ctx.projectPath(), "AI-FIXER: task-" + ctx.taskId(),
                () -> invokeWithRetry(assistant, ctx, taskDescription));
        CoderResult coderResult = isolation.workResult();

        // 从强类型结果中提取 changedFiles
        String changedFiles = coderResult.changedFilesAsCsv();
        if (changedFiles.isBlank()) {
            ErrorLogger.warn("FixApplyNode", "CoderResult.changedFiles 为空，尝试 fallback 提取");
            changedFiles = fallbackExtractFiles(coderResult);
        }

        logInfo("FixApplyNode 完成: changedFiles=" + changedFiles + ", diffRef=" + isolation.diffRef());

        // 输出变更文件列表给用户
        if (!changedFiles.isBlank()) {
            String[] files = changedFiles.split(",");
            StringBuilder fileMsg = new StringBuilder("修复完成，变更 " + files.length + " 个文件：\n");
            for (int i = 0; i < Math.min(files.length, 10); i++) {
                fileMsg.append("  - ").append(files[i].trim()).append("\n");
            }
            if (files.length > 10) {
                fileMsg.append("  - ...等共 ").append(files.length).append(" 个文件\n");
            }
            notifyResult(state, "[完成]", fileMsg.toString());
        } else {
            notifyResult(state, "[完成]", "修复完成");
        }

        String acceptanceCriteria = state.getStringValue(ACCEPTANCE_CRITERIA);

        return Map.of(
                CHANGED_FILES, changedFiles,
                CHANGED_DIFF_REF, isolation.diffRef(),
                AI_COMMIT, isolation.aiCommit(),
                ORIGINAL_REQUIREMENT, plan != null ? plan : "",
                ACCEPTANCE_CRITERIA, acceptanceCriteria != null ? acceptanceCriteria : ""
        );
    }

    /**
     * 隔离期内调用 Agent：首调失败 → 清理 AI 工作区 → 带纠错指令重试一次；
     * 重试仍失败时上抛，由 {@link GitHelper#isolateAndCommit} 统一完成清理与用户改动恢复
     */
    private CoderResult invokeWithRetry(FixApplyAgent assistant, NodeContext ctx, String taskDescription) throws Exception {
        try {
            return assistant.fix(taskDescription, ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT));
        } catch (Exception e) {
            ErrorLogger.log("FixApplyNode", e, ctx.taskId(), "FixApplyAgent", null);
            GitHelper.cleanAiWorkspace(ctx.projectPath());
            try {
                return assistant.fix(
                        "你上次的输出不正确，请重新执行修复任务并按JSON格式输出。任务描述: " + taskDescription,
                        ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT));
            } catch (Exception retryEx) {
                throw new RocAgentException("FixApplyAgent 调用失败: " + retryEx.getMessage(), retryEx);
            }
        }
    }

    /**
     * Fallback：当 CoderResult.changedFiles 为空时，尝试从 notes 字段提取文件路径
     */
    private String fallbackExtractFiles(CoderResult result) {
        String text = result.notes() != null ? result.notes() : "";
        if (text.isBlank()) return "";
        Matcher fm = FILE_PATH_PATTERN.matcher(text);
        var files = new java.util.ArrayList<String>();
        while (fm.find() && files.size() < 50) {
            files.add(fm.group(1));
        }
        if (!files.isEmpty()) {
            logInfo("Fallback 提取到 " + files.size() + " 个文件路径");
            return String.join(",", files);
        }
        return "";
    }
}
