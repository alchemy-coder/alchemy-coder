package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.confirm.ClarifyResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;

/**
 * 计划答疑智能体 - PLAN_CONFIRM 的 CLARIFY 分支
 * <p>
 * 职责：用户在审阅执行计划时提出疑问（尚未提出修改指令），结合计划内容直接作答，
 * 帮助用户判断是确认还是修改计划。
 * <p>
 * 工具集：只读（必要时查证代码，禁止猜测）
 */
public interface ClarifyAgent {

    @SystemMessage(JSON_OUTPUT_RULE + """
            {
              "answer": "针对用户疑问的直接回答（Markdown，可多行）",
              "suggestion": "基于回答给出的下一步建议（一句话，可选，无建议时省略）"
            }

            你是执行计划答疑助手。用户审阅执行计划时提出了疑问（尚未提出任何修改指令），
            你的职责是：结合执行计划内容，直接、准确地回答用户疑问，帮助用户决定是否确认或修改计划。

            要求：
            - 回答紧扣用户疑问，结合计划中对应任务 / 风险 / 验证步骤作答
            - 若计划信息不足以准确回答，可用只读工具查证代码，禁止猜测文件路径或实现细节
            - answer 用 Markdown 组织，条理清晰，可直接展示给用户
            - suggestion 给出下一步可操作建议（如“确认执行”/“建议将任务X改为……”）；拿不准时省略
            """)
    @UserMessage("""
            执行计划（JSON）：
            {{planSummary}}

            用户疑问：
            {{question}}
            """)
    ClarifyResult clarify(@MemoryId long memoryId,
                          @V("question") String question,
                          @V("planSummary") String planSummary);
}
