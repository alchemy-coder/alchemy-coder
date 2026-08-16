package athena.coder.ai.tool.security;

import athena.coder.ai.tool.util.PatternRegistry;
import athena.coder.ai.tool.config.ToolConfigCenter;
import athena.coder.ai.spi.ErrorLogger;

import java.util.logging.Logger;
import java.util.regex.Pattern;

public class OutputSanitizer {

    private static final Logger LOG = Logger.getLogger(OutputSanitizer.class.getName());

    private final ToolConfigCenter config;
    private final PatternRegistry patternRegistry;

    private String currentToolName = "unknown";

    public OutputSanitizer(ToolConfigCenter config, PatternRegistry patternRegistry) {
        this.config = config;
        this.patternRegistry = patternRegistry;
    }

    public void setContext(String toolName) {
        this.currentToolName = toolName;
    }

    public String process(String output) {
        if (output == null) return "(空)";

        output = enforceSizeLimit(output);

        if (config.isOutputSanitizationEnabled()) {
            output = sanitizeSensitiveInfo(output);
        }

        return output;
    }

    private String enforceSizeLimit(String output) {
        int maxChars = config.getMaxOutputChars();
        if (output.length() <= maxChars) {
            return output;
        }

        ErrorLogger.warn(currentToolName + ".outputSanitized",
                "输出过大(" + output.length() + " chars)，已截断至 " + maxChars);

        return output.substring(0, maxChars) +
                "\n...[输出已截断，共" + output.length() + "字符]";
    }

    public String sanitizeSensitiveInfo(String output) {
        Pattern pattern = patternRegistry.sensitiveValuePattern();
        return pattern.matcher(output).replaceAll("$1=***");
    }
}