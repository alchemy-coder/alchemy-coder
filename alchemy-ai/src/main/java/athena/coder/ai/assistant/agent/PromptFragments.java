package athena.coder.ai.assistant.agent;

/**
 * 提示词公共片段：集中管理输出规则 / 环境行 / 输出 schema / 证据块，
 * 消灭跨 agent 复制粘贴导致的契约漂移。
 * <p>
 * 全部为编译期常量，可在注解中以 + 拼接使用。
 * 证据块（EVIDENCE_*）追加在 @UserMessage 模板末尾（D1：动态大上下文走用户消息，
 * 静态环境行留 @SystemMessage 利 KV-cache）；块内 {{xxx}} 必须与方法 @V 参数一一对应。
 */
public final class PromptFragments {

    private PromptFragments() {
    }

    /** 统一输出规则（唯一版本） */
    public static final String JSON_OUTPUT_RULE = """
            **输出规则（最高优先级，违反即失败）：你的整个回复必须是纯JSON对象。
            不允许任何前缀文字、解释、总结或Markdown代码块。
            禁止在输出内容中使用任何 emoji 表情符号。**
            """;

    /** 统一环境行（固定顺序与命名：工作目录 | 项目类型 | 日期） */
    public static final String ENV_LINE = """

            ## 环境
            - 工作目录：{{workDir}} | 项目类型：{{projectType}} | 日期：{{curDate}}
            """;

    // ===== 共享输出 schema（与 result record 字段对齐）=====

    public static final String CODER_SCHEMA = """

            ## 输出格式
            {
              "status": "SUCCESS|PARTIAL|FAILED",
              "completedTasks": [1, 2, 3],
              "failedTasks": [],
              "changedFiles": ["文件路径列表"],
              "compilationStatus": "PASS|FAIL",
              "notes": "补充说明（含发现的业务疑似缺陷）"
            }
            """;

    public static final String TESTER_SCHEMA = """

            ## 输出格式
            {
              "status": "PASS|FAIL|SKIP|ERROR",
              "summary": { "totalTests": 0, "passed": 0, "failed": 0, "skipped": 0, "errors": 0, "duration": "12.5s" },
              "coverage": { "testedFiles": ["已测试文件"], "untestedFiles": ["未测试文件"], "requirementCoverage": "FULL|PARTIAL|NONE", "notes": "覆盖度说明" },
              "failures": [{ "testName": "com.example.XxxTest#testMethod", "relatedSourceFile": "相关源文件", "expected": "期望", "actual": "实际", "errorType": "异常类型", "stackTrace": "关键堆栈（前15行）" }],
              "executionLog": { "command": "完整命令", "exitCode": 0, "stdout": "输出摘要（≤500字符）", "stderr": "错误摘要（≤500字符）" }
            }
            """;

    public static final String ANALYST_SCHEMA = """

            ## 输出格式
            {
              "debugSessionId": "{{sessionId}}",
              "errorClassification": { "category": "COMPILE_ERROR|RUNTIME_EXCEPTION|LOGIC_ERROR|ENVIRONMENT_ISSUE", "severity": "CRITICAL|HIGH|MEDIUM|LOW", "errorType": "异常类名", "errorMessage": "简要描述" },
              "rootCauseAnalysis": { "primaryCause": "根因一句话", "contributingFactors": ["factor1"], "evidenceChain": ["证据1"], "confidenceScore": 0.85 },
              "fixStrategy": { "targetFile": "src/main/java/.../File.java", "targetLineRange": "起始行-结束行", "actionType": "ADD|MODIFY|DELETE|REFACTOR", "specificContent": "具体修改内容", "rationale": "修改理由", "riskAssessment": "LOW|MEDIUM|HIGH", "verificationSteps": ["步骤1"] },
              "shouldEscalate": false,
              "escalationInfo": "疑似缺陷位置与证据（仅 shouldEscalate=true 时填写，否则为 null）"
            }
            """;

    /** 审查输出骨架（stageResults 由各审查场景在各自提示词中补充专属阶段） */
    public static final String REVIEW_SCHEMA = """

            ## 输出格式
            {
              "reviewSessionId": "{{sessionId}}",
              "verdict": "APPROVED|APPROVED_WITH_NOTES|REQUEST_CHANGES|BLOCKED",
              "summary": "2-3句话总结",
              "stageResults": { 场景专属审查阶段 },
              "issues": [{ "id": "ISSUE-001", "severity": "BLOCKER|CRITICAL|MAJOR|MINOR|INFO", "category": "场景专属类别", "file": "相对路径/文件名", "line": "行号", "description": "问题描述", "suggestion": "修改建议" }],
              "requirementDetail": {
                "featureCoverage": [{ "requirement": "要点", "status": "COVERED|PARTIAL|MISSED", "evidence": "证据" }],
                "acceptanceChecklist": [{ "criterion": "标准", "status": "PASSED|FAILED|CANNOT_VERIFY", "evidence": "验证方式" }]
              },
              "improvements": [{ "category": "PERFORMANCE|SECURITY|MAINTAINABILITY", "priority": "HIGH|MEDIUM|LOW", "description": "建议", "effort": "SMALL|MEDIUM|LARGE" }]
            }
            """;

    public static final String REPORT_SCHEMA = """

            ## 输出格式
            {
              "sessionId": "{{sessionId}}",
              "report": {
                "title": "简洁标题（15字以内）",
                "overview": "2-3句概述",
                "executionSummary": { "originalGoal": "", "achievementStatus": "FULLY_ACHIEVED|PARTIALLY_ACHIEVED|BLOCKED" },
                "changes": [{ "file": "", "action": "ADDED|MODIFIED|DELETED", "summary": "" }],
                "qualityMetrics": { "testing": { "status": "PASS|FAIL|SKIP|ERROR", "summary": "" }, "review": { "verdict": "", "issuesFound": 0 } },
                "riskAssessment": { "overallRisk": "LOW|MEDIUM|HIGH", "knownIssues": [] }
              },
              "commitMessage": { "type": "{{commitType}}", "scope": "模块名", "subject": "简短标题", "body": "详细说明", "fullMessage": "完整拼接" },
              "branchSuggestion": { "name": "{{branchPrefix}}/xxx", "basedOn": "main", "reasoning": "" }
            }
            """;

    // ===== 证据块（追加在 @UserMessage 模板末尾，{{xxx}} 与 @V 参数一一对应）=====

    /** 审查证据（含测试证据，编码/测试补全审查用） */
    public static final String REVIEW_EVIDENCE = """


            ## 审查证据
            ### 需求/计划快照
            {{originalRequirement}}

            ### 变更摘要
            {{changeSummary}}

            ### 测试结果
            {{testResult}}

            ### 验收标准
            {{acceptanceCriteria}}
            """;

    /** 审查证据（无测试环节，文档审查用） */
    public static final String REVIEW_EVIDENCE_DOC = """


            ## 审查证据
            ### 需求/计划快照
            {{originalRequirement}}

            ### 变更摘要
            {{changeSummary}}

            ### 验收标准
            {{acceptanceCriteria}}
            """;

    /** 测试依据 */
    public static final String TEST_EVIDENCE = """


            ## 测试依据
            ### 变更文件
            {{changedFiles}}

            ### 变更详情(diff引用)
            {{changedDiffRef}}

            ### 验收标准
            {{acceptanceCriteria}}
            """;

    /** 分析依据 */
    public static final String ANALYST_EVIDENCE = """


            ## 分析依据
            ### 测试结果
            {{testResult}}

            ### 变更文件
            {{changedFiles}}

            ### 变更详情(diff引用)
            {{changedDiffRef}}

            ### 验收标准
            {{acceptanceCriteria}}

            ### 历史修复记录
            {{previousFixes}}
            """;

    /** 报告依据（编码/测试补全） */
    public static final String REPORT_EVIDENCE = """


            ## 报告依据
            ### 需求/计划快照
            {{originalRequirement}}

            ### 变更摘要
            {{changeSummary}}

            ### 测试结果
            {{testResult}}

            ### 审查结论
            {{reviewResult}}
            """;

    /** 报告依据（缺陷修复，含修复策略） */
    public static final String REPORT_EVIDENCE_FIX = """


            ## 报告依据
            ### 需求/计划快照
            {{originalRequirement}}

            ### 变更摘要
            {{changeSummary}}

            ### 修复策略
            {{fixStrategy}}

            ### 测试结果
            {{testResult}}
            """;

    /** 报告依据（文档，无测试环节） */
    public static final String REPORT_EVIDENCE_DOC = """


            ## 报告依据
            ### 需求/计划快照
            {{originalRequirement}}

            ### 变更摘要
            {{changeSummary}}

            ### 审查结论
            {{reviewResult}}
            """;
}
