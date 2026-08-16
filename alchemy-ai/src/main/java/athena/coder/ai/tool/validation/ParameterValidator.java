package athena.coder.ai.tool.validation;

import athena.coder.ai.tool.AbstractBaseTool;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolSecurityException;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.spi.ErrorLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParameterValidator {
    private static final Logger LOG = Logger.getLogger(ParameterValidator.class.getName());

    private final AbstractBaseTool tool;
    private final ConcurrentMap<String, Parameter[]> parameterCache = new ConcurrentHashMap<>();

    public ParameterValidator(AbstractBaseTool tool) {
        this.tool = tool;
    }

    public void validateParameters(String methodName, Object[] args) throws ToolValidationException, ToolSecurityException {
        Parameter[] parameters = parameterCache.computeIfAbsent(methodName, this::findToolParameters);
        if (parameters == null) {
            ErrorLogger.warn("ParameterValidator", "未找到方法参数: " + methodName + "，跳过自动验证");
            return;
        }

        for (int i = 0; i < parameters.length; i++) {
            if (i >= args.length) break;

            Parameter param = parameters[i];
            Object value = args[i];

            validateNotBlank(param, value);
            validateFilePath(param, value);
            validateRange(param, value);
            validatePattern(param, value);
        }
    }

    private void validateNotBlank(Parameter param, Object value) throws ToolValidationException {
        NotBlank notBlank = param.getAnnotation(NotBlank.class);
        if (notBlank == null) return;

        String fieldName = notBlank.fieldName().isBlank() ? param.getName() : notBlank.fieldName();

        if (value == null) {
            throw new ToolValidationException(tool.getToolName(), ErrorCode.PARAM_MISSING,
                    fieldName + ": " + notBlank.message());
        }

        if (value instanceof String && ((String) value).isBlank()) {
            throw new ToolValidationException(tool.getToolName(), ErrorCode.PARAM_MISSING,
                    fieldName + ": " + notBlank.message());
        }
    }

    @SuppressWarnings("unchecked")
    private void validateFilePath(Parameter param, Object value) throws ToolValidationException, ToolSecurityException {
        FilePath filePath = param.getAnnotation(FilePath.class);
        if (filePath == null || !(value instanceof String)) return;

        String pathStr = (String) value;
        if (pathStr == null || pathStr.isBlank()) return;

        Path resolved = tool.resolveAndValidate(pathStr);

        if (filePath.mustExist()) {
            tool.checkFileExists(resolved);
        }

        if (!filePath.allowBinary()) {
            tool.checkNotBinary(resolved);
        }
    }

    private void validateRange(Parameter param, Object value) throws ToolValidationException {
        Range range = param.getAnnotation(Range.class);
        if (range == null || !(value instanceof Number)) return;

        int intValue = ((Number) value).intValue();
        if (intValue < range.min() || intValue > range.max()) {
            throw new ToolValidationException(tool.getToolName(), ErrorCode.PARAM_INVALID,
                    param.getName() + ": " + String.format(range.message(), range.min(), range.max()));
        }
    }

    private void validatePattern(Parameter param, Object value) throws ToolValidationException {
        PatternRegex patternRegex = param.getAnnotation(PatternRegex.class);
        if (patternRegex == null || !(value instanceof String)) return;

        String strValue = (String) value;
        if (!strValue.matches(patternRegex.regexp())) {
            throw new ToolValidationException(tool.getToolName(), ErrorCode.INVALID_FORMAT,
                    param.getName() + ": " + patternRegex.message());
        }
    }

    private Parameter[] findToolParameters(String methodName) {
        try {
            Method[] methods = tool.getClass().getDeclaredMethods();
            return Arrays.stream(methods)
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .map(Method::getParameters)
                    .orElse(null);
        } catch (Exception e) {
            ErrorLogger.warn("ParameterValidator.findToolParameters", "查找方法参数失败: " + methodName + " - " + e.getMessage());
            return null;
        }
    }
}