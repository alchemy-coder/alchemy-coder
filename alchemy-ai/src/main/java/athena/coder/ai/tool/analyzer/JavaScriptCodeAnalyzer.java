package athena.coder.ai.tool.analyzer;

import java.util.List;

public class JavaScriptCodeAnalyzer extends AbstractCodeAnalyzer {

    @Override
    protected String getAnalyzerName() {
        return "JavaScript";
    }

    @Override
    public String supportedExtension() {
        return ".js";
    }

    @Override
    public String supportedLanguage() {
        return "JavaScript";
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            if (line.contains("console.log(") || line.contains("console.warn(") || line.contains("console.error(")) {
                problems.add(new CodeProblem(lineNum, "INFO", "生产代码应使用日志库替代 console 输出"));
            }

            if (line.contains(" == ") && !line.contains(" === ") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "建议使用 === 替代 ==，避免类型强制转换"));
            }

            if (line.startsWith("catch") && (line.endsWith("{") || line.endsWith("() {"))) {
                if (i + 1 < lines.size() && lines.get(i + 1).trim().equals("}")) {
                    problems.add(new CodeProblem(lineNum, "WARNING", "空的 catch 块，应处理或记录错误"));
                }
            }

            if (line.startsWith("var ") || line.matches(".*\\bvar\\s+\\w+.*")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 var，应使用 let 或 const"));
            }

            if (line.contains("eval(") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "eval() 存在安全风险，避免使用"));
            }
        }

        detectTodoFixme(lines, problems);
    }
}