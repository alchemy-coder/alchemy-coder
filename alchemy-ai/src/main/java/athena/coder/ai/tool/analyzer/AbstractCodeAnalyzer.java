package athena.coder.ai.tool.analyzer;

import athena.coder.ai.spi.ErrorLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractCodeAnalyzer implements CodeAnalyzer {

    private static final Pattern TODO_REGEX = Pattern.compile(
            "TODO|FIXME|HACK|XXX|OPTIMIZE|BUG|WORKAROUND", Pattern.CASE_INSENSITIVE);

    protected abstract String getAnalyzerName();

    protected Logger getLogger() {
        return Logger.getLogger(getClass().getName());
    }

    @Override
    public List<CodeProblem> analyze(List<String> lines) {
        List<CodeProblem> problems = new ArrayList<>();
        try {
            doAnalyze(lines, problems);
        } catch (Exception e) {
            ErrorLogger.warn(getAnalyzerName(), "代码分析异常: " + e.getMessage());
        }
        return problems;
    }

    protected abstract void doAnalyze(List<String> lines, List<CodeProblem> problems);

    protected void detectTodoFixme(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#") || line.startsWith("--")) {
                continue;
            }
            Matcher matcher = TODO_REGEX.matcher(line);
            if (matcher.find()) {
                problems.add(new CodeProblem(i + 1, "INFO", "存在待处理标记: " + line));
            }
        }
    }
}