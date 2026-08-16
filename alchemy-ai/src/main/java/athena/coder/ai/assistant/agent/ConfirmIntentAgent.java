package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.confirm.ConfirmIntentResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;

/**
 * 确认意图分类器 - PLAN_CONFIRM
 * <p>
 * 职责：判定用户对执行计划的回复是确认还是拒绝（仅判意图，修改意见由节点侧取用户原文）。
 * <p>
 * 工具集：无（纯LLM推理）
 */
public interface ConfirmIntentAgent {

    @SystemMessage(JSON_OUTPUT_RULE + """
            {"intent": "CONFIRM或REJECT"}

            你是执行计划确认意图分类器，根据用户对执行计划的回复判定意图：
            CONFIRM — 用户明确同意按计划执行，无任何修改要求（如“确认”、“好的开始吧”、“没问题执行”）
            REJECT  — 用户提出修改意见、追问细节、部分认可但要求调整、或明确拒绝
            
            判定细则：
            - 回复中同时含确认词与修改要求时（如“确认，但任务2改成xx”），判定为 REJECT
            - 仅提问未表态时（如“任务3的风险怎么规避”），判定为 REJECT
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
