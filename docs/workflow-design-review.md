# 子工作流设计评审报告

> 评审范围：`alchemy-ai/src/main/java/athena/coder/ai/workflow` 包（含 `workflow` 子包的子工作流编排）
> 评审视角：可优化 / 可精简 / 一致性 / 可测试性
> 日期：2026-08-22

---

## 1. 现状架构概览

```
MasterWorkflow（主图）
  USER_FACE → ROUTER → PLANNER → PLAN_CONFIRM ──确认──> 按 WORKFLOW_MODE 分流
                                                        │
          ┌──────────────────┬──────────────────┬────────┴────────┐
     CODE_WORKFLOW    DEBUG_WORKFLOW      WORD_WORKFLOW    TEST_WORKFLOW
     (CoderWorkflow) (DebuggerWorkflow) (WordWorkflow)  (TesterWorkflow)
          └──────────────────┴──────────────────┴─────────────────┘
                              全部 toEnd → END
```

子工作流实现 `NodeAction`，作为主图节点内嵌；各自内部跑一个 `StateGraph` 子图（每次请求 `new` + `compile` + `invoke`），最终产物经 `ReportFormatter` 以 `ROBOT_REPORT` 输出，返回空 `Map` 结束。

**三种子图拓扑：**

| 子工作流 | 拓扑 | 抽象方式 |
|---|---|---|
| CoderWorkflow | CODER → TESTER → (REVIEWER \| DEBUGGER) → SUMMARIZER（完整闭环） | `buildQualityLoop` |
| TesterWorkflow | 同上（与编码完全同构） | `buildQualityLoop` |
| DebuggerWorkflow | CODER → TESTER → (SUMMARIZER \| DEBUGGER) → SUMMARIZER（无 REVIEWER） | 手写 |
| WordWorkflow | CODER → REVIEWER → SUMMARIZER（无 TESTER/DEBUGGER） | 手写 |

核心抽象亮点：`GraphDSL`（构建期校验边目标）、`routeBySignal/selfTargets`（信号零映射）、`QualityLoopNodes`（节点配置驱动，`XxxNode.code()/test()/fix()/doc()` 工厂）。

---

## 2. 发现的问题

### P0 — 高价值精简

#### 2.1 拓扑编排存在三份重复，`buildQualityLoop` 只覆盖 2/4 子工作流

`AbstractSubWorkflow.buildQualityLoop` 抽象了「完整闭环（含 REVIEWER）」这一种拓扑，但 `DebuggerWorkflow` 与 `WordWorkflow` 各自手写了一套几乎相同的 `g.node/fromStart/edge/route/toEnd` 编排，路由语义存在漂移风险。

具体差异只有一点：TESTER 的通过目标（`REVIEWER` vs `SUMMARIZER`）+ 是否注册 REVIEWER 节点。

- `DebuggerWorkflow.buildGraph`（14 行）与 `buildQualityLoop` 逐行同构，仅去掉 REVIEWER、把 TESTER 通过目标换成 SUMMARIZER。
- `WordWorkflow.buildGraph`（10 行）是 CODER→REVIEWER→SUMMARIZER 的更简形态。

**建议**：给 `buildQualityLoop` 增加 `boolean withReviewer` 参数（或拆 `buildFixLoop`），让 DebuggerWorkflow 复用同一模板；WordWorkflow 形态差异较大且已足够短，可保留手写或抽一个 `buildDocLoop` 二选一。

```java
protected final void buildQualityLoop(GraphDSL g, QualityLoopNodes n, boolean withReviewer)
        throws GraphStateException {
    g.node(CODER, n.coder());
    g.node(TESTER, n.tester());
    g.node(DEBUGGER, n.debugger());
    g.node(SUMMARIZER, n.summarizer());
    if (withReviewer) g.node(REVIEWER, n.reviewer());

    g.fromStart(CODER);
    g.edge(CODER, TESTER);
    Enum<?> testPassTarget = withReviewer ? REVIEWER : SUMMARIZER;
    g.route(TESTER, routeBySignal(), selfTargets(testPassTarget, DEBUGGER));
    g.route(DEBUGGER, routeBySignal(), selfTargets(CODER, SUMMARIZER));
    if (withReviewer) g.route(REVIEWER, routeBySignal(), selfTargets(SUMMARIZER, CODER));
    g.toEnd(SUMMARIZER);
}
```

`QualityLoopNodes` 需相应放宽 reviewer 可空（或拆两个 record / 用 `Optional`）。DebuggerWorkflow 收敛为：

```java
buildQualityLoop(g, new QualityLoopNodes(
        WriterNode.fix(), TestNode.fix(), AnalystNode.fix(), null, ReportNode.fix()), false);
```

#### 2.2 `actionVerb` 是死字段（4 个 config record 均未读取）

`WriterConfig`、`TestConfig`、`AnalystConfig`、`ReportConfig` 均声明了 `actionVerb` 字段，且在各自工厂里传了字面量（"执行"/"测试"/"调试分析"/"总结"…），但全工程（main + test）**没有任何地方调用 `.actionVerb()`**。纯死代码，增加每个 factory 一个无意义参数。

**建议**：删除字段 + 构造参数 + 各工厂对应字面量。

#### 2.3 最终报告被渲染两次（ReportNode 内联渲染 与 ReportFormatter 重复）

- `ReportNode.doApply`（L88–101）从 `SUMMARIZE_RESULT` JSON 提取 `report/title`、`report/overview`、`commitMessage/fullMessage`，拼成 markdown 用 `notifyResult` 输出（ROBOT_RESULT 卡片）。
- `AbstractSubWorkflow.collectResults` → `ReportFormatter.format` 又对同一份 JSON 重新提取 `report/title`、`report/overview`、`riskAssessment/overallRisk`、`commitMessage/fullMessage`、`branchSuggestion/name`，渲染成最终 ROBOT_REPORT 卡片。

`title/overview/fullMessage` 三处内容被解析并渲染了两次，呈现逻辑分裂在两个类里，后续改报告格式要同步改两处。

**建议**：让 `ReportFormatter` 成为唯一的报告呈现路径。`ReportNode` 保留轻量的「已完成」进度提示即可，不再自行反序列化并渲染报告正文（删掉 L88–101 的 `reportMsg` 组装，或只输出一句固定文案）。

---

### P1 — 一致性与内聚

#### 2.4 `collectResults` 的类级 javadoc 与实现矛盾

`AbstractSubWorkflow` 类注释第 4 条写「`collectResults`：把子图产物 merge 回主图」，但实现恒定 `return Map.of()`（私有方法自己的注释又写「无需 merge 回主图」）。命名与注释均误导。

**建议**：类注释改为「子图产物经 side-effect 落盘（`ROBOT_REPORT`）输出，不 merge 回主图」；方法改名 `renderFinalReport`（"collect" 名不副实）。

#### 2.5 图编排 DSL 工具归属不当

`routeBySignal()` / `selfTargets()` 是图编排 DSL 工具，却放在 `AbstractSubWorkflow`（一个 `NodeAction` 实现）里，MasterWorkflow 通过静态导入跨包使用。DSL 工具与 `GraphDSL` 才是同层概念。

**建议**：迁移到 `GraphDSL`（静态方法），`AbstractSubWorkflow` / `MasterWorkflow` 通过 `GraphDSL.xxx` 调用，职责更清晰，也减少 `AbstractSubWorkflow` 的表面 API。

#### 2.6 同一异常被双重记录日志

`AbstractAgentNode.apply` 的 catch 已 `ErrorLogger.log(nodeName, e, ...)` 后重新抛出；`AbstractSubWorkflow.apply` 的 catch 又 `ErrorLogger.log(workflowName(), e, state.getTaskId(), null, null)` 一次。子工作流内节点异常会落两条日志。

**建议**：二选一 —— 节点层保留 ERROR 详细日志、子工作流层只记 WARN 且带 workflowName 上下文（或反过来）。避免同 taskId 双份 ERROR。

#### 2.7 每次请求重建并 compile 子图

`AbstractSubWorkflow.apply` 每次请求都 `new StateGraph` + `buildGraph` + `compile`（注释明确「不做缓存」）。图结构是静态的，compile 是纯浪费（虽然单次成本低）。

**建议**：若 `langgraph4j` 的 `CompiledGraph` 线程安全，按 workflow 类型做一次 `static` 惰性编译缓存，`invoke` 只传新 state。需先确认 `CompiledGraph` 的并发语义，收益有限，属低优先。

---

### P2 — 健壮性与可测试性

#### 2.8 子工作流编排层零测试

现有测试覆盖了 `entity/gate/node/report`，但 **`workflow` 子包（CoderWorkflow/DebuggerWorkflow/TesterWorkflow/WordWorkflow/GraphDSL/AbstractSubWorkflow/MasterWorkflow）没有任何测试**。`GraphDSL` 的核心价值——构建期校验边目标（写错节点名当场抛 `GraphStateException`）——恰恰没有测试守护。

**建议**：补 `GraphDSL` 单测（未注册节点 / 路由目标越界 / selfTargets 附加 END 兜底）；补每个子工作流的「构图即通过校验」冒烟测试（`compile()` 不抛异常即可，无需真正 invoke）。

#### 2.9 缺省信号静默走 END，可能掩盖漏写 `NEXT_NODE` 的 bug

`routeBySignal()` 在 `NEXT_NODE` 缺失时 `orElse(END)`。对条件边而言，节点忘了写 `NEXT_NODE` 会静默结束流程而不是暴露问题（CODER→TESTER 是静态边所以 CODER 例外，但 TESTER/DEBUGGER/REVIEWER 都是条件边）。

**建议**：缺失信号时至少 `ErrorLogger.warn`（指明 `from` 节点漏写信号），而非纯静默。`routeBySignal` 目前是无状态的静态方法拿不到 from 节点名，可改为 `routeBySignal(from.name())` 带上下文。

#### 2.10 `ProjectFacts` 每个下游节点重复反序列化

PLANNER 写一次 `PROJECT_FACTS` JSON，下游 5 个节点（Writer/Test/Analyst/Review/Report）各自 `ProjectFacts.toPromptBlock(...)` → `fromJson`（完整 Jackson 反序列化）→ `render`，同一 JSON 被反序列化 5 次。

**建议**：在 `WorkflowState` 上做一次惰性解析缓存（首次访问 parse + 缓存 `ProjectFacts`），或用 `transient` 字段缓存渲染结果。收益很小，属微优化。

---

## 3. 架构层面的更大重构方向（可选，stretch）

- **Stringly-typed 状态袋**：`WorkflowState` 用 25+ 个 `String` 常量 key + `getStringValue/getIntValue` 承载跨节点数据，节点之间靠约定字符串传递，无编译期类型安全。可演进为强类型访问器（如 `getPlan()` / `setNextNode(NodeEnum)` / `getTesterStatus()`），把散落各节点的 `String.valueOf` / 强转收敛到 `WorkflowState` 一处。
- **`NodeEnum` 与 `WorkflowMode` 双枚举**：子工作流节点名已统一改用 `WorkflowMode.name()` 消除了「靠名字对齐」的脆弱约定（当前注释已确认），方向正确，无需再动。

---

## 4. 建议执行顺序

| # | 项 | 影响 | 成本 | 优先级 |
|---|---|---|---|---|
| 2.2 | 删除 `actionVerb` 死字段 | 低风险清理 | 极小 | 立即 |
| 2.4 | 修正 `collectResults` 注释/命名 | 消除误导 | 极小 | 立即 |
| 2.1 | `buildQualityLoop` 加 `withReviewer`，DebuggerWorkflow 复用 | 消除拓扑重复 | 小 | 高 |
| 2.3 | 报告单一渲染路径（ReportFormatter） | 消除双渲染 | 小 | 高 |
| 2.5 | DSL 工具迁入 `GraphDSL` | 内聚 | 小 | 中 |
| 2.6 | 消除异常双重日志 | 可观测性 | 小 | 中 |
| 2.8 | 补编排层测试（尤其 GraphDSL） | 防回归 | 中 | 中 |
| 2.9 | 缺省信号告警 | 健壮性 | 小 | 中 |
| 2.7 / 2.10 | 图缓存 / ProjectFacts 缓存 | 微优化 | 小 | 低 |
| 3 | 强类型状态访问器 | 长期可维护性 | 大 | 低/分期 |

---

## 5. 结论

该子工作流体系总体设计清晰：`GraphDSL` 的构建期校验、`selfTargets` 零映射、`XxxNode.code()/test()/fix()/doc()` 配置驱动都是亮点。主要可精简点集中在 **拓扑抽象的覆盖不完整（2.1）**、**报告双渲染（2.3）**、以及**少量死代码与文档矛盾（2.2/2.4）**。前三项做完即可显著降低维护成本，且风险低、可独立提交。
