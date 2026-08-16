package athena.coder.ai.workflow.node;

import athena.coder.ai.workflow.entity.NodeEnum;
import athena.coder.ai.spi.ErrorLogger;
import athena.coder.exception.RocAgentException;

/**
 * 测试裁决节点基类：按测试状态统一路由，消除各测试节点重复的 switch 逻辑。
 * <p>
 * PASS/SKIP → 构造器指定的 passTarget（编码/补测流程为 REVIEWER，缺陷修复流程为 SUMMARIZER），
 * FAIL/ERROR → DEBUGGER，未知状态 → 业务异常。
 */
public abstract class AbstractTesterNode extends AbstractAgentNode {

    private final NodeEnum passTarget;

    protected AbstractTesterNode(NodeEnum passTarget) {
        this.passTarget = passTarget;
    }

    /**
     * 根据测试状态决定下一个节点
     */
    protected String determineNextNode(String status) {
        return switch (status) {
            case "PASS" -> {
                logInfo("✅ 测试通过，路由到 " + passTarget.name());
                yield passTarget.name();
            }
            case "SKIP" -> {
                ErrorLogger.warn(getClass().getSimpleName(), "测试被跳过，路由到 " + passTarget.name() + "（需人工评估是否影响质量）");
                yield passTarget.name();
            }
            case "FAIL", "ERROR" -> {
                ErrorLogger.warn(getClass().getSimpleName(), "测试失败/错误，路由到 DEBUGGER 进行分析");
                yield NodeEnum.DEBUGGER.name();
            }
            default -> {
                throw new RocAgentException("未知的测试状态: " + status);
            }
        };
    }
}
