package athena.coder.ai.tool.analyzer;

import java.util.List;

public class RustCodeAnalyzer extends AbstractCodeAnalyzer {

    public RustCodeAnalyzer() {
        super("Rust", ".rs");
    }

    @Override
    protected void doAnalyze(List<String> lines, List<CodeProblem> problems) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNum = i + 1;

            if (line.contains(".unwrap()") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "unwrap() 会导致 panic，建议使用 ? 操作符或 match/if-let"));
            }

            if (line.contains(".expect(") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "INFO", "expect() 仍会 panic，确保在生产代码中有合理的错误处理"));
            }

            if (line.contains("unsafe ") || line.contains("unsafe{")) {
                problems.add(new CodeProblem(lineNum, "WARNING", "unsafe 代码需要特别审查，确保不变量被维护"));
            }

            if (line.contains("println!(") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "INFO", "生产代码建议使用 log/tracing crate 而非 println!"));
            }

            if (line.startsWith("let _ =") || line.contains("let _ ")) {
                problems.add(new CodeProblem(lineNum, "INFO", "存在被忽略的变量或 Result，确认是否有意为之"));
            }

            if (line.contains(".clone()") && !line.startsWith("//")) {
                problems.add(new CodeProblem(lineNum, "INFO", "频繁 clone() 影响性能，考虑使用引用或 Rc/Arc"));
            }
        }

        detectTodoFixme(lines, problems);
    }
}
