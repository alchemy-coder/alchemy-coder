package athena.coder.ai.workflow.report;

import athena.coder.ai.spi.ErrorLogger;
import athena.coder.ai.tool.util.GitHelper;
import athena.coder.ai.workflow.entity.ReviewVerdict;
import athena.coder.ai.workflow.entity.WorkflowState;
import athena.coder.exception.RocAgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 子工作流最终报告格式化：SUMMARIZE_RESULT JSON + 子图状态 → 结构化 Markdown。
 * <p>
 * 从 AbstractSubWorkflow 剥离的纯呈现逻辑，可独立单测；任何解析失败均降级，不阻断主流程。
 */
public final class ReportFormatter {

    private static final ObjectMapper REPORT_MAPPER = new ObjectMapper();

    /** git diff --stat 行内 "+N" 统计 */
    private static final Pattern PLUS_STAT = Pattern.compile("(\\d+)\\s*\\+");
    /** git diff --stat 行内 "-N" 统计 */
    private static final Pattern MINUS_STAT = Pattern.compile("(\\d+)\\s*-");

    private ReportFormatter() {
    }

    /**
     * 将 SUMMARIZE_RESULT JSON + 子图状态数据格式化为结构化 Markdown 报告
     *
     * @param subState            子图最终状态（变更文件/测试/审查等数据来源）
     * @param summarizeResultJson SUMMARIZER 节点产出的 JSON
     * @param workflowName        子工作流名称（仅用于降级告警日志）
     */
    public static String format(WorkflowState subState, String summarizeResultJson, String workflowName) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n---\n");
        sb.append("## 📊 执行报告\n\n");

        try {
            JsonNode root = REPORT_MAPPER.readTree(summarizeResultJson);

            // 报告标题和摘要
            JsonNode report = root.path("report");
            if (!report.isMissingNode()) {
                String title = report.path("title").asText(null);
                if (title != null) {
                    sb.append("**").append(title).append("**\n\n");
                }
                String overview = report.path("overview").asText(null);
                if (overview != null) {
                    sb.append(overview).append("\n\n");
                }
                // 风险评估
                String risk = report.at("/riskAssessment/overallRisk").asText(null);
                if (risk != null) {
                    sb.append("**风险等级**: ").append(risk).append("\n\n");
                }
            }

            // 变更文件（优先使用 git diff --stat 渲染为表格）
            String changedFiles = subState.getChangedFiles();
            String changedDiffRef = subState.getChangedDiffRef();
            appendChangedFilesTable(sb, subState, changedFiles, changedDiffRef, workflowName);

            // 测试结果
            String testResult = subState.getTestResult();
            if (testResult != null && !testResult.isBlank()) {
                appendTestSummary(sb, testResult);
            }

            // 审查结论
            String reviewResult = subState.getReviewResult();
            if (reviewResult != null && !reviewResult.isBlank()) {
                appendReviewSummary(sb, reviewResult);
            }

            // Commit Message
            JsonNode commitMsg = root.path("commitMessage");
            if (!commitMsg.isMissingNode()) {
                String fullMessage = commitMsg.path("fullMessage").asText(null);
                if (fullMessage != null) {
                    sb.append("### Commit Message\n");
                    sb.append("```\n").append(fullMessage).append("\n```\n\n");
                }
            }

            // 分支建议
            JsonNode branch = root.path("branchSuggestion");
            String branchName = branch.path("name").asText(null);
            if (branchName != null) {
                sb.append("**建议分支**: `").append(branchName).append("`\n\n");
            }

        } catch (Exception e) {
            ErrorLogger.warn(workflowName + ".formatFinalReport", "格式化总结报告失败，回退为原始输出: " + e.getMessage());
            sb.append(summarizeResultJson);
        }

        return sb.toString();
    }

    private static void appendChangedFilesTable(StringBuilder sb, WorkflowState subState,
                                                String changedFiles, String changedDiffRef, String workflowName) {
        if ((changedFiles == null || changedFiles.isBlank())
                && (changedDiffRef == null || changedDiffRef.isBlank())) {
            return;
        }

        String projectPath = subState.getWorkFullPath();

        // 优先使用 git diff --stat 生成带 +/- 统计的表格
        if (changedDiffRef != null && !changedDiffRef.isBlank() && projectPath != null && !projectPath.isBlank()) {
            try {
                String statOutput = GitHelper.runGit(projectPath, "diff", "--stat", changedDiffRef);
                if (statOutput != null && !statOutput.isBlank()) {
                    sb.append("### 变更文件\n");
                    sb.append("| 文件 | +/- |\n");
                    sb.append("|------|-----|\n");
                    String[] lines = statOutput.split("\\n");
                    int totalInsertions = 0;
                    int totalDeletions = 0;
                    int fileCount = 0;
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isBlank()) continue;
                        // 最后一行是总计摘要: "3 files changed, 195 insertions(+), 7 deletions(-)"
                        if (line.contains("file") && line.contains("changed") && !line.contains("|")) {
                            continue;
                        }
                        // 解析 "file.java | 5 ++--" 格式
                        int pipeIdx = line.indexOf('|');
                        if (pipeIdx < 0) continue;
                        String fileName = line.substring(0, pipeIdx).trim();
                        String stats = line.substring(pipeIdx + 1).trim();
                        sb.append("| `").append(fileName).append("` | ").append(stats).append(" |\n");
                        fileCount++;
                        // 累加+/-
                        Matcher im = PLUS_STAT.matcher(stats);
                        if (im.find()) totalInsertions += Integer.parseInt(im.group(1));
                        Matcher dm = MINUS_STAT.matcher(stats);
                        if (dm.find()) totalDeletions += Integer.parseInt(dm.group(1));
                    }
                    if (fileCount > 0) {
                        sb.append("| **合计** | **").append(fileCount).append(" 个文件** | **+")
                                .append(totalInsertions).append("/-").append(totalDeletions).append("** |\n");
                    }
                    sb.append("\n");
                    return;
                }
            } catch (Exception e) {
                ErrorLogger.warn(workflowName + ".appendChangedFilesTable", "git diff --stat 执行失败，回退为列表模式: " + e.getMessage());
            }
        }

        // Fallback: 简单列表（无 diffRef 或 git 命令失败时）
        if (changedFiles != null && !changedFiles.isBlank()) {
            sb.append("### 变更文件\n");
            for (String file : changedFiles.split(",")) {
                sb.append("- `").append(file.trim()).append("`\n");
            }
            sb.append("\n");
        }
    }

    private static void appendTestSummary(StringBuilder sb, String testResultJson) {
        try {
            JsonNode test = REPORT_MAPPER.readTree(testResultJson);
            String status = test.path("status").asText("UNKNOWN");
            sb.append("### 测试结果\n");
            sb.append("- 状态: ").append(formatTestStatus(status)).append("\n");
            JsonNode summary = test.path("summary");
            if (summary.isTextual()) {
                sb.append("- 摘要: ").append(summary.asText()).append("\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            // 解析失败不影响主流程
            ErrorLogger.warn("ReportFormatter.appendTestSummary", "测试结果解析失败: " + e.getMessage());
        }
    }

    private static void appendReviewSummary(StringBuilder sb, String reviewResultJson) {
        try {
            JsonNode review = REPORT_MAPPER.readTree(reviewResultJson);
            String verdict = review.path("verdict").asText("UNKNOWN");
            sb.append("### 审查结论\n");
            sb.append("- 结果: ").append(formatVerdict(verdict)).append("\n");
            String summary = review.path("summary").asText(null);
            if (summary != null && !summary.isBlank()) {
                sb.append("- 摘要: ").append(summary.length() > 150 ? summary.substring(0, 150) + "..." : summary).append("\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            // 解析失败不影响主流程
            ErrorLogger.warn("ReportFormatter.appendReviewSummary", "审查结果解析失败: " + e.getMessage());
        }
    }

    private static String formatTestStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PASS" -> "✅ 通过";
            case "FAIL" -> "❌ 失败";
            case "ERROR" -> "❌ 错误";
            case "SKIP" -> "⚠️ 跳过";
            default -> status;
        };
    }

    private static String formatVerdict(String verdict) {
        ReviewVerdict v;
        try {
            v = ReviewVerdict.from(verdict);
        } catch (RocAgentException e) {
            return verdict;
        }
        return switch (v) {
            case APPROVED -> "✅ 通过";
            case APPROVED_WITH_NOTES -> "✅ 通过（有建议）";
            case REQUEST_CHANGES -> "🔄 打回修改";
            case BLOCKED -> "🚫 阻塞";
        };
    }
}
