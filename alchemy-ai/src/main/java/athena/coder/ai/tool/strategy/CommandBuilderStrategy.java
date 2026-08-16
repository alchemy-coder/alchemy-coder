package athena.coder.ai.tool.strategy;

import java.util.List;

/**
 * 项目类型命令构建策略接口
 * <p>
 * 消除不同工具类中重复的项目类型判断和命令构建逻辑
 */
public interface CommandBuilderStrategy {

    List<String> buildTestCommand(String testFilter);

    List<String> buildCompileCommand();

    List<String> buildDiagnosticsCommand();

    default List<String> buildCoverageCommand() {
        throw new UnsupportedOperationException(getProjectType() + " 不支持覆盖率报告生成");
    }

    String getProjectType();
}