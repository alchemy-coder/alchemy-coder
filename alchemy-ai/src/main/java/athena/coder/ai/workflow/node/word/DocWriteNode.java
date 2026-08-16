package athena.coder.ai.workflow.node.word;

import athena.coder.ai.assistant.agent.result.coder.CoderResult;
import athena.coder.ai.assistant.agent.GenericWriterAgent;
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
 * 文档工作流 - 文档编写节点
 * <p>
 * 职责：按已确认的文档计划（或审查打回意见）编写文档/注释类产物（禁止改逻辑代码）。
 * git 安全编排（隔离/提交/回滚/恢复）统一委托 {@link GitHelper#isolateAndCommit}。
 */
public class DocWriteNode extends AbstractAgentNode {

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?:^|\\s)(?!https?://)([\\w./\\-]+/[\\w./\\-]*\\.(?:java|kt|py|js|ts|go|rs|xml|json|yml|yaml|properties|gradle|md|txt|html|css|sql|sh))", Pattern.MULTILINE);

    @Override
    protected String stepLabel() {
        return "[文档]";
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        if (!GitHelper.isGitRepo(ctx.projectPath())) {
            throw new RocAgentException("DocWriteNode: 项目目录不是 git 仓库: " + ctx.projectPath());
        }
        String plan = state.getStringValue(PLAN);
        String reviewFeedback = state.getStringValue(REVIEW_RESULT);

        notifyModelCalling(state);
        logStart(ctx, "开始编写文档");

        // 组装任务描述：打回场景携带审查意见，首轮直接依据已确认计划
        String taskDescription = (plan != null && !plan.isBlank()) ? plan : null;
        if (taskDescription == null) {
            throw new RocAgentException("DocWriteNode 无法获取任务描述（plan 为空）");
        }
        if (reviewFeedback != null && !reviewFeedback.isBlank()) {
            taskDescription = taskDescription + "\n\n上一轮审查意见（请针对性修改）:\n" + reviewFeedback;
        }

        final String finalTaskDescription = taskDescription;
        GenericWriterAgent assistant = newChatAssistant(ctx.modelType(), GenericWriterAgent.class);
        GitHelper.IsolationResult<CoderResult> isolation = GitHelper.isolateAndCommit(
                ctx.projectPath(), "AI-DOCER: task-" + ctx.taskId(),
                () -> invokeWithRetry(assistant, ctx, finalTaskDescription));
        CoderResult coderResult = isolation.workResult();

        // 从强类型结果中提取 changedFiles
        String changedFiles = coderResult.changedFilesAsCsv();
        if (changedFiles.isBlank()) {
            ErrorLogger.warn("DocWriteNode", "CoderResult.changedFiles 为空，尝试 fallback 提取");
            changedFiles = fallbackExtractFiles(coderResult);
        }

        logInfo("DocWriteNode 完成: changedFiles=" + changedFiles + ", diffRef=" + isolation.diffRef());

        // 输出变更文件列表给用户
        if (!changedFiles.isBlank()) {
            String[] files = changedFiles.split(",");
            StringBuilder fileMsg = new StringBuilder("文档编写完成，变更 " + files.length + " 个文件：\n");
            for (int i = 0; i < Math.min(files.length, 10); i++) {
                fileMsg.append("  - ").append(files[i].trim()).append("\n");
            }
            if (files.length > 10) {
                fileMsg.append("  - ...等共 ").append(files.length).append(" 个文件\n");
            }
            notifyResult(state, "[完成]", fileMsg.toString());
        } else {
            notifyResult(state, "[完成]", "文档编写完成");
        }

        String acceptanceCriteria = state.getStringValue(ACCEPTANCE_CRITERIA);

        return Map.of(
                CHANGED_FILES, changedFiles,
                CHANGED_DIFF_REF, isolation.diffRef(),
                AI_COMMIT, isolation.aiCommit(),
                ORIGINAL_REQUIREMENT, plan,
                ACCEPTANCE_CRITERIA, acceptanceCriteria != null ? acceptanceCriteria : ""
        );
    }

    /**
     * 隔离期内调用 Agent：首调失败 → 清理 AI 工作区 → 带纠错指令重试一次；
     * 重试仍失败时上抛，由 {@link GitHelper#isolateAndCommit} 统一完成清理与用户改动恢复
     */
    private CoderResult invokeWithRetry(GenericWriterAgent assistant, NodeContext ctx, String taskDescription) throws Exception {
        try {
            return assistant.write(taskDescription, ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT),
                    "文档工作流：编写文档/注释", "禁止修改逻辑代码，只能修改文档和注释类文件");
        } catch (Exception e) {
            ErrorLogger.log("DocWriteNode", e, ctx.taskId(), "DocWriteAgent", null);
            GitHelper.cleanAiWorkspace(ctx.projectPath());
            try {
                return assistant.write(
                        "你上次的输出不正确，请重新执行文档任务并按JSON格式输出。任务描述: " + taskDescription,
                        ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT),
                        "文档工作流：编写文档/注释", "禁止修改逻辑代码，只能修改文档和注释类文件");
            } catch (Exception retryEx) {
                throw new RocAgentException("DocWriteAgent 调用失败: " + retryEx.getMessage(), retryEx);
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