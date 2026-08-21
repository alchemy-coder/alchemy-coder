package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.confirm.ConfirmIntentResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;

/**
 * 确认意图分类器 - PLAN_CONFIRM
 * <p>
 * 职责：判定用户对执行计划的回复是确认 / 提修改意见 / 拒绝取消；REVISE 时同时提炼结构化修订指令。
 * <p>
 * 工具集：无（纯LLM推理）
 */
public interface ConfirmIntentAgent {

    @SystemMessage(JSON_OUTPUT_RULE + """
            {
              "intent": "CONFIRM或REVISE或REJECT",
              "revise": {
                "scope": "TARGETED或OVERALL或ADD",
                "targetTaskIds": [2, 3],
                "directives": [
                  { "target": "任务2存储方案", "change": "改为文件存储", "reason": "避免引入外部依赖" }
                ],
                "summary": "一句话概括修改诉求"
              }
            }

            你是执行计划确认意图分类器，根据用户对执行计划的回复判定意图：
            CONFIRM — 用户明确同意按计划执行，无任何修改要求（如“确认”、“好的开始吧”、“没问题执行”）
            REVISE  — 用户提出修改意见、追问细节、部分认可但要求调整、或要求换个方向（仍想继续，只是要改）
            REJECT  — 用户明确取消/否定整体且不打算继续（如“算了不做了”、“取消吧”、“别继续了”）

            判定细则：
            - 回复中同时含确认词与修改要求时（如“确认，但任务2改成xx”），判定为 REVISE
            - 仅提问未表态时（如“任务3的风险怎么规避”），判定为 REVISE
            - “整体思路不行，换个方向重来”判定为 REVISE；“整体方案不行，别做了”判定为 REJECT

            revise 填写规则（仅 REVISE 时必须填，CONFIRM/REJECT 时省略）：
            - scope=TARGETED：用户只针对计划中某些任务提意见，targetTaskIds 列出对应任务编号，directives 逐条给出修改要点
            - scope=OVERALL：用户对整体方向不满，targetTaskIds 置空，summary 概括新方向诉求
            - scope=ADD：用户要求新增任务，directives 的 target 描述新增内容，change 说明具体要求
            - targetTaskIds 尽量对齐计划 JSON 里的 taskId；拿不准时宁可不填，靠 target 文本让 Planner 自行匹配
            - summary 必须是一句话、不含换行的诉求概括
            """)
    @UserMessage("""
            待确认的执行计划（JSON）：
            {{planSummary}}

            用户对上述计划的回复：
            {{userReply}}
            """)
    ConfirmIntentResult classify(@V("userReply") String userReply,
                                 @V("planSummary") String planSummary);
}
