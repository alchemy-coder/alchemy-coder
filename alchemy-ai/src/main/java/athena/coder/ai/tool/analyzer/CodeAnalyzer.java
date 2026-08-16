package athena.coder.ai.tool.analyzer;

import java.util.List;

public interface CodeAnalyzer {
    String supportedExtension();

    String supportedLanguage();

    List<CodeProblem> analyze(List<String> lines);
}