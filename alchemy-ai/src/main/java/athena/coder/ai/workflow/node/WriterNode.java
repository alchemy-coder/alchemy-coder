package athena.coder.ai.workflow.node;

import athena.coder.ai.assistant.agent.GenericWriterAgent;
import athena.coder.ai.assistant.agent.result.coder.CoderResult;
import athena.coder.ai.tool.config.AgentToolPolicy;
import athena.coder.ai.tool.util.GitHelper;
import athena.coder.ai.workflow.entity.ProjectFacts;
import athena.coder.ai.workflow.entity.StepRole;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.exception.RocAgentException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static athena.coder.ai.assistant.model.factory.AiAssistantFactory.newChatAssistant;
import static athena.coder.ai.workflow.entity.WorkflowState.*;

/**
 * 通用编写节点（合并原 CodeWriterNode / TestWriteNode / DocWriteNode / FixApplyNode）
 * <p>
 * 场景差异经 {@link WriterConfig} 注入：使命 scenario、硬约束 hardConstraint、提交前缀 commitPrefix、
 * 任务描述来源 feedbackSource（修复策略优先 or 审查打回意见）。写角色统一使用 {@link GenericWriterAgent}，
 * git 安全编排统一委托 {@link GitHelper#isolateAndCommit}。
 */
public class WriterNode extends AbstractAgentNode {

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?:^|\\s)(?!https?://)([\\w./\\-]+/[\\w./\\-]*\\.(?:java|kt|py|js|ts|go|rs|xml|json|yml|yaml|properties|gradle|md|txt|html|css|sql|sh))",
            Pattern.MULTILINE);

    private final WriterConfig config;

    public WriterNode(WriterConfig config) {
        this.config = config;
    }

    /** 场景化工厂：编码 */
    public static WriterNode code() {
        return new WriterNode(WriterConfig.code());
    }

    /** 场景化工厂：测试补全 */
    public static WriterNode test() {
        return new WriterNode(WriterConfig.test());
    }

    /** 场景化工厂：文档 */
    public static WriterNode doc() {
        return new WriterNode(WriterConfig.doc());
    }

    /** 场景化工厂：缺陷修复 */
    public static WriterNode fix() {
        return new WriterNode(WriterConfig.fix());
    }

    @Override
    protected StepRole stepRole() {
        return config.stepRole();
    }

    @Override
    protected Map<String, Object> doApply(WorkflowState state, NodeContext ctx) throws Exception {
        if (!GitHelper.isGitRepo(ctx.projectPath())) {
            throw new RocAgentException(getClass().getSimpleName() + ": 项目目录不是 git 仓库: " + ctx.projectPath());
        }
        notifyModelCalling(state);

        String taskDescription = assembleTaskDescription(state);
        String projectFacts = ProjectFacts.toPromptBlock(state.getStringValue(PROJECT_FACTS));

        GenericWriterAgent assistant = newChatAssistant(ctx.modelType(), GenericWriterAgent.class, config.policy());
        GitHelper.IsolationResult<CoderResult> isolation = GitHelper.isolateAndCommit(
                ctx.projectPath(), config.commitPrefix() + ": task-" + ctx.taskId(),
                () -> invokeWithRetry(assistant, ctx, taskDescription, projectFacts));
        CoderResult coderResult = isolation.workResult();

        String changedFiles = coderResult.changedFilesAsCsv();
        if (changedFiles.isBlank()) {
            ErrorLogger.warn(getClass().getSimpleName(), "CoderResult.changedFiles 为空，尝试 fallback 提取");
            changedFiles = fallbackExtractFiles(coderResult);
        }

        if (!changedFiles.isBlank()) {
            String[] files = changedFiles.split(",");
            StringBuilder fileMsg = new StringBuilder(config.doneMsg()).append("，变更 ").append(files.length).append(" 个文件：\n");
            for (int i = 0; i < Math.min(files.length, 10); i++) {
                fileMsg.append("  - ").append(files[i].trim()).append("\n");
            }
            if (files.length > 10) {
                fileMsg.append("  - ...等共 ").append(files.length).append(" 个文件\n");
            }
            notifyResult(state, "[完成]", fileMsg.toString());
        } else {
            notifyResult(state, "[完成]", config.doneMsg());
        }

        String plan = state.getStringValue(PLAN);
        String acceptanceCriteria = state.getStringValue(ACCEPTANCE_CRITERIA);

        return Map.of(
                CHANGED_FILES, changedFiles,
                CHANGED_DIFF_REF, isolation.diffRef(),
                ORIGINAL_REQUIREMENT, plan != null ? plan : "",
                ACCEPTANCE_CRITERIA, acceptanceCriteria != null ? acceptanceCriteria : ""
        );
    }

    private String assembleTaskDescription(WorkflowState state) {
        String plan = state.getStringValue(PLAN);
        return switch (config.feedbackSource()) {
            case FIX_STRATEGY -> {
                String fixStrategy = state.getStringValue(FIX_STRATEGY);
                String task = (fixStrategy != null && !fixStrategy.isBlank()) ? fixStrategy : plan;
                if (task == null || task.isBlank()) {
                    throw new RocAgentException(getClass().getSimpleName() + " 无法获取任务描述（plan 和 fixStrategy 均为空）");
                }
                yield task;
            }
            case REVIEW_FEEDBACK -> {
                if (plan == null || plan.isBlank()) {
                    throw new RocAgentException(getClass().getSimpleName() + " 无法获取任务描述（plan 为空）");
                }
                String feedback = state.getStringValue(REVIEW_RESULT);
                yield (feedback != null && !feedback.isBlank())
                        ? plan + "\n\n上一轮审查意见（请针对性修改）:\n" + feedback
                        : plan;
            }
        };
    }

    /**
     * 隔离期内调用 Agent：首调失败 → 清理 AI 工作区 → 带纠错指令重试一次；
     * 重试仍失败时上抛，由 {@link GitHelper#isolateAndCommit} 统一完成清理与用户改动恢复
     */
    private CoderResult invokeWithRetry(GenericWriterAgent assistant, NodeContext ctx, String taskDescription, String projectFacts) throws Exception {
        try {
            return assistant.write(taskDescription, ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT),
                    config.scenario(), config.hardConstraint(), projectFacts);
        } catch (Exception e) {
            ErrorLogger.log(getClass().getSimpleName(), e, ctx.taskId(), "GenericWriterAgent", null);
            GitHelper.cleanAiWorkspace(ctx.projectPath());
            try {
                return assistant.write(
                        "你上次的输出不正确，请重新执行" + config.retryVerb() + "并按JSON格式输出。任务描述: " + taskDescription,
                        ctx.projectPath(), ctx.projectType(), LocalDate.now().format(DATE_FMT),
                        config.scenario(), config.hardConstraint(), projectFacts);
            } catch (Exception retryEx) {
                throw new RocAgentException("GenericWriterAgent 调用失败: " + retryEx.getMessage(), retryEx);
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
        List<String> files = new ArrayList<>();
        while (fm.find() && files.size() < 50) {
            files.add(fm.group(1));
        }
        if (!files.isEmpty()) {
            return String.join(",", files);
        }
        return "";
    }

    /**
     * 写角色配置：使命/硬约束/提交前缀/任务描述来源/工具权限
     */
    public record WriterConfig(
            String scenario,
            String hardConstraint,
            String commitPrefix,
            StepRole stepRole,
            String doneMsg,
            String retryVerb,
            FeedbackSource feedbackSource,
            AgentToolPolicy policy) {

        /** 任务描述来源：修复策略优先 or 审查打回意见 */
        public enum FeedbackSource { FIX_STRATEGY, REVIEW_FEEDBACK }

        public static WriterConfig code() {
            return new WriterConfig("编码工作流：编写业务代码", "不要修改测试文件，专注业务代码编写",
                    "AI-CODER", StepRole.CODER, "编码完成", "任务",
                    FeedbackSource.FIX_STRATEGY, AgentToolPolicy.CODE_WRITER);
        }

        public static WriterConfig test() {
            return new WriterConfig("测试补全工作流：补写测试用例", "禁止修改被测业务代码，只能新增或修改测试文件",
                    "AI-TESTER", StepRole.TEST_WRITER, "补测完成", "补测任务",
                    FeedbackSource.FIX_STRATEGY, AgentToolPolicy.WRITER);
        }

        public static WriterConfig doc() {
            return new WriterConfig("文档工作流：编写文档/注释", "禁止修改逻辑代码，只能修改文档和注释类文件",
                    "AI-DOCER", StepRole.DOC_WRITER, "文档编写完成", "文档任务",
                    FeedbackSource.REVIEW_FEEDBACK, AgentToolPolicy.DOC_WRITER);
        }

        public static WriterConfig fix() {
            return new WriterConfig("缺陷修复工作流：最小改动修复缺陷", "只修不重构，仅修改修复策略指出的位置，禁止顺手优化或补测试",
                    "AI-FIXER", StepRole.FIXER, "修复完成", "修复任务",
                    FeedbackSource.FIX_STRATEGY, AgentToolPolicy.WRITER);
        }
    }
}
