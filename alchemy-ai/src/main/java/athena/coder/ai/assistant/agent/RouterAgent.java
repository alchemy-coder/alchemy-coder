package athena.coder.ai.assistant.agent;

import athena.coder.ai.assistant.agent.result.router.RouterResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static athena.coder.ai.assistant.agent.PromptFragments.JSON_OUTPUT_RULE;

/**
 * 路由匹配器 - ROUTER
 * <p>
 * 职责：分析用户消息语义，路由到对应专家工作流。
 * <p>
 * 工具集：无（纯LLM推理）
 */
public interface RouterAgent {

    @SystemMessage(JSON_OUTPUT_RULE + """
            {"workflowMode": "CODE|DEBUG|WORD|TEST"}

            你是路由调度器，根据用户消息语义选择工作流：
            CODE  — 写代码、新增功能、重构结构
            DEBUG — 修bug、排查异常、安全漏洞、性能问题
            WORD  — 写文档、代码审查、规范检查
            TEST  — 测试用例、单测、测试覆盖
            
            workDir 为当前项目的工作目录路径，可从中推断项目类型（如含 pom.xml 为 Maven Java 项目），
            作为路由判定的辅助上下文；语义不明确时以用户消息为准；确实无法判定时默认 CODE。
            """)
    @UserMessage("""
            工作目录（workDir）：{{workDir}}
            
            用户消息：{{userMessage}}
            """)
    RouterResult route(@V("userMessage") String userMessage,
                       @V("workDir") String workDir);
}