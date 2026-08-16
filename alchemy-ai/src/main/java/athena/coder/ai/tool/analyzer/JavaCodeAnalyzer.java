package athena.coder.ai.tool.analyzer;

import athena.coder.ai.tool.util.PatternRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

public class JavaCodeAnalyzer extends AbstractCodeAnalyzer {

    public JavaCodeAnalyzer() {
        super("Java", ".java");
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        Set<String> importedClasses = new HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            String nextLine = i + 1 < lines.size() ? lines.get(i + 1).trim() : "";
            checkEmptyCatch(line, nextLine, lineNum, problems);

            if (line.contains("System.out.print") || line.contains("System.err.print")) {
                problems.add(new CodeProblem(lineNum, "INFO", "生产代码中不应使用 System.out/err，建议使用日志框架"));
            }

            if (line.contains(".printStackTrace()")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 printStackTrace()，建议使用日志框架记录异常"));
            }

            Matcher importMatcher = PatternRegistry.getInstance().importStatement().matcher(line);
            if (importMatcher.find()) {
                importedClasses.add(importMatcher.group(1));
            }

            if (i > 0 && line.startsWith("public ") && line.contains("(") && !line.contains("class ") &&
                    !line.contains("interface ") && !line.contains("static ")) {
                String prevLine = lines.get(i - 1).trim();
                if (!prevLine.equals("@Override") && !prevLine.contains("@")) {
                    if (line.contains("toString()") || line.contains("equals(") ||
                            line.contains("hashCode()") || line.contains("compareTo(")) {
                        problems.add(new CodeProblem(lineNum, "WARNING", "方法可能缺少 @Override 注解"));
                    }
                }
            }
        }

        detectTodoFixme(lines, problems);

        for (String imp : importedClasses) {
            String simpleName = imp.substring(imp.lastIndexOf('.') + 1);
            if (simpleName.equals("*")) continue;
            boolean used = false;
            for (String line : lines) {
                if (!line.trim().startsWith("import") && line.contains(simpleName)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                problems.add(new CodeProblem(0, "WARNING", "未使用的导入: " + imp));
            }
        }
    }
}
