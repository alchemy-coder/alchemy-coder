package athena.coder.ai.tool.analyzer;

import java.util.List;

public class JavaScriptCodeAnalyzer extends AbstractCodeAnalyzer {

    public JavaScriptCodeAnalyzer() {
        super("JavaScript", ".js");
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            checkConsoleOutput(line, lineNum, problems);

            if (line.contains(" == ") && !line.contains(" === ") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "建议使用 === 替代 ==，避免类型强制转换"));
            }

            checkEmptyCatch(line, i + 1 < lines.size() ? lines.get(i + 1).trim() : "", lineNum, problems);

            if (line.startsWith("var ") || line.matches(".*\\bvar\\s+\\w+.*")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 var，应使用 let 或 const"));
            }

            checkEvalUsage(line, lineNum, problems);
        }

        detectTodoFixme(lines, problems);
    }
}
