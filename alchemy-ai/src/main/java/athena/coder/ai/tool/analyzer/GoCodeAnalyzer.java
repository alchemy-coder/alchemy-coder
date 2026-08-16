package athena.coder.ai.tool.analyzer;

import java.util.List;

public class GoCodeAnalyzer extends AbstractCodeAnalyzer {

    public GoCodeAnalyzer() {
        super("Go", ".go");
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            if (line.contains("_ =") || line.startsWith("_ ,")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "忽略了 error 返回值，Go 中应显式处理错误"));
            }

            if (line.contains("panic(") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免在生产代码中使用 panic，应返回 error"));
            }

            if (line.contains("log.Fatal") || line.contains("os.Exit")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "库代码中避免使用 log.Fatal/os.Exit，应由调用方决定"));
            }

            if (line.contains("fmt.Print") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "INFO", "生产代码建议使用 log 包而非 fmt.Print"));
            }

            if (line.matches("^_\\s*=.*")) {
                problems.add(new CodeProblem(lineNum, "INFO", "存在被忽略的变量，确认是否有意为之"));
            }
        }

        detectTodoFixme(lines, problems);
    }
}
