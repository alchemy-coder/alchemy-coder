package athena.coder.ai.tool.analyzer;

import java.util.List;

public class PythonCodeAnalyzer extends AbstractCodeAnalyzer {

    public PythonCodeAnalyzer() {
        super("Python", ".py");
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            if (line.equals("except:") || line.equals("except :")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用裸 except，应捕获具体异常类型"));
            }

            if (line.contains("eval(") || line.contains("exec(")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "eval/exec 存在安全风险，可能被注入恶意代码"));
            }

            if (line.matches("def\\s+\\w+\\(.*=\\s*\\[].*\\)") ||
                    line.matches("def\\s+\\w+\\(.*=\\s+\\{}.*\\)")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "可变对象作为默认参数可能导致意外行为，建议使用 None"));
            }

            if (line.equals("pass")) {
                problems.add(new CodeProblem(lineNum, "INFO", "空的 pass 语句，建议添加注释说明或实际处理逻辑"));
            }

            if (line.startsWith("print(") || line.startsWith("print ")) {
                problems.add(new CodeProblem(lineNum, "INFO", "生产代码建议使用 logging 模块而非 print"));
            }

            if (line.startsWith("from ") && line.contains(" import *")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "避免使用 import *，应显式导入所需名称"));
            }

            if (line.startsWith("def ") && !line.contains("->") && !line.startsWith("def _")) {
                problems.add(new CodeProblem(lineNum, "INFO", "函数缺少返回类型注解"));
            }
        }

        detectTodoFixme(lines, problems);
    }
}
