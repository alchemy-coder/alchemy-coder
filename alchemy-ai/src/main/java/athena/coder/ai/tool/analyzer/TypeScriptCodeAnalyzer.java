package athena.coder.ai.tool.analyzer;

import java.util.List;

public class TypeScriptCodeAnalyzer extends AbstractCodeAnalyzer {

    public TypeScriptCodeAnalyzer() {
        super("TypeScript", ".ts");
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            checkConsoleOutput(line, lineNum, problems);

            if (line.contains(": any") || line.contains("as any") || line.contains("<any>")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 any 类型，应使用明确的类型声明"));
            }

            checkEmptyCatch(line, i + 1 < lines.size() ? lines.get(i + 1).trim() : "", lineNum, problems);

            checkEvalUsage(line, lineNum, problems);

            if (line.contains("@ts-ignore")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 @ts-ignore，应修复类型错误"));
            }
        }

        detectTodoFixme(lines, problems);
    }
}
