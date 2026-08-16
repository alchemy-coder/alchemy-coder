package athena.coder.ai.tool.analyzer;

import java.util.List;

public class TypeScriptCodeAnalyzer extends AbstractCodeAnalyzer {

    @Override
    protected String getAnalyzerName() {
        return "TypeScript";
    }

    @Override
    public String supportedExtension() {
        return ".ts";
    }

    @Override
    public String supportedLanguage() {
        return "TypeScript";
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            if (line.contains("console.log(") || line.contains("console.warn(") || line.contains("console.error(")) {
                problems.add(new CodeProblem(lineNum, "INFO", "生产代码应使用日志库替代 console 输出"));
            }

            if (line.contains(": any") || line.contains("as any") || line.contains("<any>")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 any 类型，应使用明确的类型声明"));
            }

            if (line.startsWith("catch") && (line.endsWith("{") || line.endsWith("() {"))) {
                if (i + 1 < lines.size() && lines.get(i + 1).trim().equals("}")) {
                    problems.add(new CodeProblem(lineNum, "WARNING", "空的 catch 块，应处理或记录错误"));
                }
            }

            if (line.contains("eval(") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "eval() 存在安全风险，避免使用"));
            }

            if (line.contains("@ts-ignore")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 @ts-ignore，应修复类型错误"));
            }
        }

        detectTodoFixme(lines, problems);
    }
}