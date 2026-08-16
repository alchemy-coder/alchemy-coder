package athena.coder.ai.tool.analyzer;

import java.util.List;

public interface CodeAnalyzer {
    String supportedExtension();

    List<CodeProblem> analyze(List<String> lines);
}
