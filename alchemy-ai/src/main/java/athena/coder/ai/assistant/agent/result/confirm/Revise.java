package athena.coder.ai.assistant.agent.result.confirm;

import java.util.List;

/**
 * 用户对执行计划提出修改意见时的结构化修订指令（REVISE 意图专用）。
 * <p>
 * 由 {@code ConfirmIntentAgent} 从用户回复中提炼，供 Planner 精准重规划：
 * scope 决定重规划策略（定向修改 / 整体推翻 / 追加任务），directives 是逐条修改要点，
 * summary 是一句话诉求概览（供 Planner 快速对齐）。用户原文由节点侧单独保留兜底。
 */
public record Revise(
        String scope,
        List<Integer> targetTaskIds,
        List<Directive> directives,
        String summary) {

    /** 定向修改：只调整 targetTaskIds 对应的任务，其余保留 */
    public static final String SCOPE_TARGETED = "TARGETED";
    /** 整体推翻：重新设计（保留原需求） */
    public static final String SCOPE_OVERALL = "OVERALL";
    /** 追加任务：在现有计划基础上新增任务 */
    public static final String SCOPE_ADD = "ADD";

    /** 单条修改要点：改哪里、改成什么、为什么 */
    public record Directive(String target, String change, String reason) {
    }
}
