# Athena-Coder

基于 LangGraph4j 有状态工作流编排的多 Agent 智能编码助手，支持代码生成、Bug 修复、文档撰写、测试补全等场景，具备 RAG 混合检索与多语言项目分析能力。

## 核心功能

- **多 Agent 协作编排**：Router 智能调度，Planner → Coder → Tester → Reviewer/Debugger 流水线自动执行
- **有状态工作流**：基于 LangGraph4j 构建，支持条件路由、熔断保护、节点级重试与兜底
- **RAG 混合检索**：向量语义检索 + FTS5 关键词检索双路召回，RRF 融合排序，精准定位代码上下文
- **多语言项目支持**：内置 Java / Python / Go / Rust / JavaScript / TypeScript 六种语言的依赖分析、代码分析与命令构建策略
- **智能熔断保护**：可配置的 Agent 重试次数与熔断阈值，防止 LLM 调用异常扩散
- **本地 SQLite 持久化**：聊天记录、错误日志、向量索引、模型配置全量本地存储，可追溯、可审计

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 25 |
| 构建 | Maven | 3.x |
| UI | JavaFX + Atlantafx + Ikonli | 25.0.3 / 2.1.0 / 12.4.0 |
| LLM 框架 | LangChain4j | 1.16.2 |
| 工作流 | LangGraph4j | 1.8.20 |
| 数据库 | SQLite + JDBI + HikariCP | 3.53.2.0 / 3.53.0 / 7.1.0 |
| 测试 | JUnit Jupiter | 5.12.2 |

## 架构概览

```
athena.coder/
├── ui/                          # UI 层 (JavaFX)
│   ├── MainUI.java              # 主窗口
│   ├── leftmenu/                # 左侧任务树
│   ├── content/chatview/        # 聊天视图 (WebView 渲染 Markdown)
│   ├── content/createquestview/ # 任务创建视图
│   └── modelselect/             # 模型选择面板
├── ai/
│   ├── assistant/agent/         # Agent 层
│   │   ├── RouterAgent.java     # 路由调度：意图识别与工作流选择
│   │   ├── PlannerAgent.java    # 规划师：需求拆解与执行计划生成
│   │   ├── CoderAgent.java      # 编码器：代码编写与修改
│   │   ├── TesterAgent.java     # 测试员：单元测试执行与结果收集
│   │   ├── DebuggerAgent.java   # 调试员：错误分析与修复策略
│   │   ├── ReviewerAgent.java   # 审查员：代码规范与需求对齐检查
│   │   └── SummarizerAgent.java # 总结员：生成执行报告
│   ├── workflow/                # 工作流层
│   │   ├── MasterWorkflow.java  # 主工作流编排入口
│   │   ├── workflow/            # 子工作流 (Coder/Debugger/Word/Tester)
│   │   └── node/                # 工作流节点实现
│   ├── rag/                     # RAG 检索层
│   │   ├── RagManager.java      # 混合检索门面 (向量+FTS5, RRF融合)
│   │   └── ProjectIndexer.java  # 增量索引器
│   └── tool/                    # 工具层 (11 个工具)
│       ├── FileOperationTool.java    # 文件读写
│       ├── ProjectAnalysisTool.java  # 项目结构分析
│       ├── CodeSearchTool.java       # 代码搜索 (grep)
│       ├── TestExecutionTool.java    # 测试执行
│       ├── DiagnosticTool.java       # 诊断分析
│       ├── GitTool.java              # Git 操作
│       ├── DependencyManagerTool.java # 依赖管理
│       ├── SecurityScannerTool.java  # 安全扫描
│       ├── APITestClientTool.java    # API 测试
│       ├── LogAnalysisTool.java      # 日志分析
│       └── BasicTerminalTool.java    # 终端命令
├── core/                        # 核心层
│   ├── ChatManager.java         # 聊天会话管理
│   ├── AppState.java            # 应用状态管理
│   ├── ProjectManager.java      # 项目管理
│   ├── ErrorLogger.java         # 异步错误日志
│   └── repository/              # 数据访问层 (SQLite)
└── entity/                      # 实体层
    ├── model/                   # 模型/向量模型枚举
    ├── chat/                    # 聊天实体
    └── tree/                    # 任务树实体
```

## Agent 职责速查

| Agent | 职责 | 输出产物 |
|-------|------|---------|
| UserFace | 用户意图澄清与需求补全 | 结构化需求描述 |
| Router | 意图识别、工作流模板选择 | 工作流模式 + 路由决策 |
| Planner | 需求拆解、生成执行计划与验收标准 | 执行计划 JSON + 验收标准 |
| Coder | 编写/修改代码，生成 diff | 代码变更 + Git commit |
| Tester | 执行单元测试，收集测试结果 | 测试报告 JSON |
| Debugger | 分析错误日志，制定修复策略 | 修复方案（不直接改代码） |
| Reviewer | 代码规范、安全、需求对齐审查 | 审查意见 JSON |
| Summarizer | 汇总各阶段结果，生成执行报告 | 执行报告 |

## 工作流拓扑

四种工作流模式，由 Router 根据用户意图自动选择：

### CODE_WORKFLOW（编码工作流）

```
START → PLANNER → CODER → TESTER → REVIEWER → SUMMARIZER → END
                              ↘ DEBUGGER → CODER ↗       ↗
```

支持代码编写、重构、功能新增等场景。TESTER 失败时自动进入 DEBUGGER 修复循环，最多重试 3 次；REVIEWER 不通过时回退 CODER。

### DEBUG_WORKFLOW（修复工作流）

```
START → PLANNER → CODER → TESTER → DEBUGGER → CODER → SUMMARIZER → END
```

针对 Bug 修复场景，DEBUGGER 分析错误后回退 CODER 重新修改，循环直到修复或熔断。

### WORD_WORKFLOW（文档工作流）

```
START → PLANNER → CODER → REVIEWER → SUMMARIZER → END
```

跳过测试环节，适用于文档撰写、代码审查、README 生成等非编码任务。

### TEST_WORKFLOW（测试工作流）

```
START → PLANNER → CODER → TESTER → SUMMARIZER → END
```

跳过审查环节，专注于补全测试用例、提升覆盖率。

### 熔断保护参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 最大重试次数 | 3 | Agent 调用失败后最大重试次数 |
| 熔断阈值 | 2 | 连续失败达到阈值后跳过该节点 |
| 超时时间 | 300s | 单次 LLM 调用超时 |

## 配置指南

### 模型选择

启动后通过左侧面板的模型选择视图配置 LLM 和 Embedding 模型。API Key 加密存储在本地 SQLite 数据库中，后续启动无需重复配置。

### 支持的聊天模型

| 模型 | 标识 | 提供商 |
|------|------|--------|
| 千问 3.7-Max | qwen3.7-max | 阿里云 DashScope |
| 千问 3.5-Flash | qwen3.5-flash | 阿里云 DashScope |
| DeepSeek-V4-Pro | deepseek-v4-pro | DeepSeek |

### 支持的向量模型

| 模型 | 标识 | 用途 |
|------|------|------|
| 千问 Embedding V4 | text-embedding-v4 | RAG 语义检索 |
| OpenAI Embedding 3 Small | text-embedding-3-small | RAG 语义检索 |

## 快速开始

### 前置条件

1. 完成模型配置（见上方配置指南），至少配置一个聊天模型
2. 确保 JDK 25 和 Maven 3.x 已安装

### 环境要求

- JDK 25+
- Maven 3.x
- 操作系统：macOS / Windows / Linux

### 构建

```bash
mvn clean compile
```

### 运行

```bash
mvn javafx:run
```

## 扩展指南

### 扩展新语言支持

1. 实现 `DependencyStrategy` 接口（依赖解析），参考 `MavenDependencyStrategy`
2. 实现 `CommandBuilderStrategy` 接口（命令构建），参考 `MavenCommandBuilder`
3. 实现 `CodeAnalyzer` 接口（代码分析），参考 `JavaCodeAnalyzer`
4. 在对应工厂类中注册（`DependencyStrategyFactory` / `CommandBuilderFactory` / `CodeAnalyzerFactory`）

### 新增 Agent

1. 继承 `AbstractAgentNode`，实现 `doApply` 方法
2. 在 `NodeEnum` 中注册新节点
3. 在对应 Workflow 的 `addNode` / `addEdge` 中编排节点

### 新增 Tool

1. 继承 `AbstractBaseTool`，使用 `@Tool` 注解标记方法
2. 在 `ToolRegistry` 中注册

## 开发说明

### JavaFX 线程规范

UI 更新必须在 JavaFX Application Thread 上执行，耗时操作使用 `Platform.runLater()` 异步回调。Agent 调用和 RAG 检索在后台线程执行，不阻塞 UI。

### 数据库单连接池

SQLite 使用 HikariCP 单连接池（`maximumPoolSize=1`），避免写锁冲突。所有数据库操作通过 `DbManager.getJdbi()` 获取 JDBI 实例。

### 错误处理

所有 Agent 异常通过 `ErrorLogger.log()` 异步写入 `error_log` 表，不打印到控制台。RAG 检索异常静默降级返回空结果，不阻断主链路。