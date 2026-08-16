package athena.coder.ai.tool.analyzer;

import athena.coder.ai.tool.util.PatternRegistry;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CodeAnalyzerFactory {

    private static final Logger LOG = Logger.getLogger(CodeAnalyzerFactory.class.getName());

    private static final Map<String, CodeAnalyzer> ANALYZERS_RAW = new HashMap<>();
    private static final Map<String, CodeAnalyzer> ANALYZERS;

    static {
        registerAnalyzer(new JavaCodeAnalyzer(PatternRegistry.getInstance()));
        registerAnalyzer(new GoCodeAnalyzer());
        registerAnalyzer(new PythonCodeAnalyzer());
        registerAnalyzer(new RustCodeAnalyzer());
        registerAnalyzer(new JavaScriptCodeAnalyzer());
        registerAnalyzer(new TypeScriptCodeAnalyzer());

        ANALYZERS_RAW.put(".jsx", new JavaScriptCodeAnalyzer());
        ANALYZERS_RAW.put(".tsx", new TypeScriptCodeAnalyzer());

        ANALYZERS = Collections.unmodifiableMap(new HashMap<>(ANALYZERS_RAW));
    }

    private static void registerAnalyzer(CodeAnalyzer analyzer) {
        ANALYZERS_RAW.put(analyzer.supportedExtension(), analyzer);
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