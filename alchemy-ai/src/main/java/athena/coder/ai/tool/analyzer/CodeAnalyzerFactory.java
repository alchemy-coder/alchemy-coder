package athena.coder.ai.tool.analyzer;

import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeAnalyzerFactory {

    private static final Map<String, CodeAnalyzer> ANALYZERS = new HashMap<>();

    static {
        register(new JavaCodeAnalyzer());
        register(new GoCodeAnalyzer());
        register(new PythonCodeAnalyzer());
        register(new RustCodeAnalyzer());
        register(new JavaScriptCodeAnalyzer());
        register(new TypeScriptCodeAnalyzer());
        // JSX/TSX 复用 JS/TS 分析器（同一实例，避免重复构建）
        ANALYZERS.put(".jsx", ANALYZERS.get(".js"));
        ANALYZERS.put(".tsx", ANALYZERS.get(".ts"));
    }

    private static void register(CodeAnalyzer analyzer) {
        ANALYZERS.put(analyzer.supportedExtension(), analyzer);
    }

    private static CodeAnalyzer getAnalyzer(String fileExtension) {
        if (fileExtension == null || fileExtension.isBlank()) {
            return null;
        }
        return ANALYZERS.get(fileExtension.toLowerCase());
    }

    public static List<CodeProblem> analyze(String fileExtension, List<String> lines) {
        CodeAnalyzer analyzer = getAnalyzer(fileExtension);
        if (analyzer == null) {
            throw new ToolValidationException("CodeAnalyzerFactory", ErrorCode.UNSUPPORTED_TYPE, fileExtension);
        }
        return analyzer.analyze(lines);
    }

    public static boolean isSupportedLanguage(String fileExtension) {
        return fileExtension != null && ANALYZERS.containsKey(fileExtension.toLowerCase());
    }

}
