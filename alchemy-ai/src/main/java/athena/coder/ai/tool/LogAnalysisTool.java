package athena.coder.ai.tool;

import athena.coder.ai.tool.base.FileSystemBasedTool;
import athena.coder.ai.tool.validation.FilePath;
import athena.coder.ai.tool.validation.NotBlank;
import athena.coder.ai.tool.validation.Range;
import athena.coder.ai.spi.ErrorLogger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalysisTool extends FileSystemBasedTool {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")
    };

    private volatile DateTimeFormatter cachedSuccessfulFormatter;

    public LogAnalysisTool() {
        super();
    }

    @Tool("分析日志文件，统计错误、警告、异常等信息")
    public String analyzeLog(
            @P("日志文件路径") @NotBlank(fieldName = "日志文件路径") @FilePath(mustExist = true) String logFilePath,
            @P("时间范围开始（可选），格式: '2025-01-01 00:00:00'") String startTime,
            @P("时间范围结束（可选）") String endTime,
            @P("日志级别过滤（可选）：ERROR/WARN/INFO/DEBUG，多个用逗号分隔") String logLevel) {

        return executeWithAutoValidation(() -> {
            Path path = resolveAndValidate(logFilePath);
            checkFileExists(path);

            long fileSize = safeFileSize(path);
            if (fileSize > 100 * 1024 * 1024) {
                return ERR_PREFIX + "日志文件过大（" + formatSize(fileSize) + "），建议先压缩或使用日志聚合工具";
            }

            List<String> lines = safeReadAllLines(path);

            LocalDateTime start = parseTime(startTime);
            LocalDateTime end = parseTime(endTime);
            Set<String> levels = parseLevels(logLevel);

            LogAnalysisResult result = analyzeLines(lines, start, end, levels, path.toString());

            DefaultAnalysisResult analysisResult = new DefaultAnalysisResult(result.toMarkdown());
            if (result.errorCount > 100) {
                analysisResult.addWarning("错误数量较多（" + result.errorCount + "个），建议关注高频错误并优先修复");
            }
            if (result.warningCount > result.errorCount * 2) {
                analysisResult.addRecommendation("警告数量远超错误，部分警告可能需要升级为错误处理");
            }
            if (!result.exceptionTypes.isEmpty()) {
                analysisResult.addRecommendation("发现异常，建议查看详细堆栈以定位根因");
            }

            return generateReport(analysisResult, "日志分析报告");

        }, "analyzeLog", logFilePath, startTime, endTime, logLevel);
    }

    @Tool("从日志文件中提取异常堆栈信息")
    public String extractExceptions(
            @P("日志文件路径") @NotBlank(fieldName = "日志文件路径") @FilePath(mustExist = true) String logFilePath,
            @P("最大提取数量，默认20") @Range(min = 1, max = 100) int maxCount) {

        return executeWithAutoValidation(() -> {
            Path path = resolveAndValidate(logFilePath);
            checkFileExists(path);

            int limit = Math.clamp(maxCount, 1, 100);
            List<String> lines = safeReadAllLines(path);

            List<ExceptionInfo> exceptions = extractExceptionsFromLines(lines, limit);

            if (exceptions.isEmpty()) {
                return OK_PREFIX + "未发现异常信息";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("发现 %d 个异常:\n\n", exceptions.size()));

            for (int i = 0; i < exceptions.size(); i++) {
                ExceptionInfo ex = exceptions.get(i);
                sb.append(String.format("[异常 #%d]\n", i + 1));
                sb.append(String.format("类型: %s\n", ex.type));
                sb.append(String.format("消息: %s\n", ex.message));
                sb.append(String.format("位置: %s:%d\n", ex.location, ex.lineNumber));
                sb.append(String.format("时间: %s\n", ex.timestamp != null ? ex.timestamp : "未知"));
                sb.append("---\n");
            }

            return enforceOutputLimit(sb.toString());
        }, "extractExceptions", logFilePath, maxCount);
    }

    @Tool("搜索日志中的特定模式或关键词")
    public String searchLogs(
            @P("日志文件路径") @NotBlank(fieldName = "日志文件路径") @FilePath(mustExist = true) String logFilePath,
            @P("搜索关键词或正则表达式") @NotBlank(fieldName = "搜索模式") String pattern,
            @P("上下文行数，默认3") @Range(min = 0, max = 10) int contextLines,
            @P("最大结果数，默认50") @Range(min = 1, max = 200) int maxResults) {

        return executeWithAutoValidation(() -> {
            Path path = resolveAndValidate(logFilePath);
            checkFileExists(path);

            int ctx = Math.max(0, Math.min(contextLines, 10));
            int limit = Math.max(1, Math.min(maxResults, 200));

            List<String> lines = safeReadAllLines(path);

            boolean isRegex = patternRegistry.isValidRegex(pattern);
            Pattern searchPattern = patternRegistry.compile(isRegex ? pattern : Pattern.quote(pattern),
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

            List<MatchResult> matches = searchInLines(lines, searchPattern, ctx, limit);

            if (matches.isEmpty()) {
                return ERR_PREFIX + "未找到匹配的内容";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("找到 %d 处匹配:\n\n", matches.size()));
            for (MatchResult match : matches) {
                sb.append(String.format("[行 %d]\n", match.lineNumber));
                for (String ctxLine : match.context) {
                    sb.append(ctxLine).append("\n");
                }
                sb.append("\n");
            }

            return enforceOutputLimit(sb.toString());
        }, "searchLogs", logFilePath, pattern, contextLines, maxResults);
    }

    private LogAnalysisResult analyzeLines(List<String> lines, LocalDateTime start, LocalDateTime end, Set<String> levels, String filePath) {
        LogAnalysisResult result = new LogAnalysisResult(filePath);
        result.totalLines = lines.size();

        Pattern levelPattern = patternRegistry.logLevel();
        Pattern exceptionPattern = patternRegistry.exceptionPattern();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty()) continue;

            Matcher levelMatcher = levelPattern.matcher(trimmedLine);
            if (levelMatcher.find()) {
                String level = levelMatcher.group(1).toUpperCase();

                if (!levels.isEmpty() && !levels.contains(level)) {
                    continue;
                }

                switch (level) {
                    case "ERROR":
                    case "FATAL":
                    case "CRITICAL":
                        result.errorCount++;
                        break;
                    case "WARNING":
                    case "WARN":
                        result.warningCount++;
                        break;
                    default:
                        result.infoCount++;
                }

                LocalDateTime timestamp = extractTimestampOptimized(trimmedLine);
                if (timestamp != null) {
                    if (start != null && timestamp.isBefore(start)) continue;
                    if (end != null && timestamp.isAfter(end)) continue;

                    if (result.firstTimestamp == null || timestamp.isBefore(result.firstTimestamp)) {
                        result.firstTimestamp = timestamp;
                    }
                    if (result.lastTimestamp == null || timestamp.isAfter(result.lastTimestamp)) {
                        result.lastTimestamp = timestamp;
                    }
                }

                if ("ERROR".equals(level) || "FATAL".equals(level) || "CRITICAL".equals(level)) {
                    result.errorMessages.add(new ErrorMessage(i + 1, trimmedLine, timestamp));
                }
            }

            Matcher exceptionMatcher = exceptionPattern.matcher(line);
            if (exceptionMatcher.find()) {
                result.exceptionTypes.add(exceptionMatcher.group(1));
            }
        }

        return result;
    }

    private LocalDateTime extractTimestampOptimized(String line) {
        String substring = line.substring(0, Math.min(23, line.length()));
        if (cachedSuccessfulFormatter != null) {
            try {
                String timePart = substring.trim();
                return LocalDateTime.parse(timePart, cachedSuccessfulFormatter);
            } catch (Exception e) {
            }
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                String timePart = substring.trim();
                LocalDateTime result = LocalDateTime.parse(timePart, formatter);
                cachedSuccessfulFormatter = formatter;
                return result;
            } catch (Exception e) {
            }
        }
        return null;
    }

    private List<ExceptionInfo> extractExceptionsFromLines(List<String> lines, int maxCount) {
        List<ExceptionInfo> exceptions = new ArrayList<>();
        Pattern exceptionPattern = patternRegistry.exceptionPattern();
        Pattern stackFramePattern = patternRegistry.stackTraceFrame();

        for (int i = 0; i < lines.size() && exceptions.size() < maxCount; i++) {
            String line = lines.get(i);

            Matcher matcher = exceptionPattern.matcher(line);
            if (matcher.find()) {
                String type = matcher.group(1);

                String message;
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    message = line.substring(colonIndex + 1).trim();
                } else {
                    message = "";
                }

                LocalDateTime timestamp = extractTimestampOptimized(line);
                int lineNumber = i + 1;

                String location = null;
                for (int j = i + 1; j < Math.min(i + 10, lines.size()); j++) {
                    String stackLine = lines.get(j);
                    Matcher frameMatcher = stackFramePattern.matcher(stackLine);
                    if (frameMatcher.find()) {
                        location = frameMatcher.group(1);
                        break;
                    }
                }

                exceptions.add(new ExceptionInfo(type, message, location, timestamp, lineNumber));
            }
        }

        return exceptions;
    }

    private List<MatchResult> searchInLines(List<String> lines, Pattern pattern, int contextLines, int maxResults) {
        List<MatchResult> results = new ArrayList<>();

        for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
            String line = lines.get(i);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                results.add(new MatchResult(i + 1, extractContextLines(lines, i, contextLines)));
            }
        }

        return results;
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(timeStr.trim(), formatter);
            } catch (Exception e) {
            }
        }

        ErrorLogger.warn("LogAnalysisTool.parseTime", "无法解析时间: " + timeStr);
        return null;
    }

    private Set<String> parseLevels(String levelStr) {
        Set<String> levels = new HashSet<>();
        if (levelStr == null || levelStr.isBlank()) return levels;

        for (String level : levelStr.split(",")) {
            String trimmed = level.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                levels.add(trimmed);
            }
        }

        return levels;
    }

    private String generateReport(DefaultAnalysisResult result, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n\n");
        sb.append(result.toMarkdown());

        List<String> warnings = result.getWarnings();
        if (!warnings.isEmpty()) {
            sb.append("\n--- 警告 ---\n");
            for (String warning : warnings) {
                sb.append("⚠️ ").append(warning).append("\n");
            }
        }

        List<String> recommendations = result.getRecommendations();
        if (!recommendations.isEmpty()) {
            sb.append("\n--- 建议 ---\n");
            for (String rec : recommendations) {
                sb.append("💡 ").append(rec).append("\n");
            }
        }

        return enforceOutputLimit(sb.toString());
    }

    private static class LogAnalysisResult {
        final String filePath;
        long totalLines;
        int errorCount;
        int warningCount;
        int infoCount;
        LocalDateTime firstTimestamp;
        LocalDateTime lastTimestamp;
        List<ErrorMessage> errorMessages = new ArrayList<>();
        Set<String> exceptionTypes = new HashSet<>();

        LogAnalysisResult(String filePath) {
            this.filePath = filePath;
        }

        String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("文件: %s\n", filePath));
            sb.append(String.format("总行数: %d\n", totalLines));

            if (firstTimestamp != null && lastTimestamp != null) {
                sb.append(String.format("时间范围: %s ~ %s\n",
                        firstTimestamp, lastTimestamp));
            }

            sb.append("\n--- 日志级别统计 ---\n");
            sb.append(String.format("  🔴 错误(ERROR/FATAL):   %d\n", errorCount));
            sb.append(String.format("  🟠 警告(WARNING):       %d\n", warningCount));
            sb.append(String.format("  🟢 信息(INFO/OTHER):     %d\n", infoCount));

            if (!errorMessages.isEmpty()) {
                sb.append("\n--- 最近错误消息 ---\n");
                int showCount = Math.min(errorMessages.size(), 10);
                for (int i = 0; i < showCount; i++) {
                    ErrorMessage err = errorMessages.get(errorMessages.size() - 1 - i);
                    sb.append(String.format("  [%d] 行 %d: %s\n", i + 1, err.lineNum,
                            err.message.substring(0, Math.min(err.message.length(), 120))));
                }
            }

            if (!exceptionTypes.isEmpty()) {
                Map<String, Long> typeCounts = exceptionTypes.stream()
                        .collect(java.util.stream.Collectors.groupingBy(e -> e, java.util.stream.Collectors.counting()));

                sb.append("\n--- 异常类型统计 ---\n");
                typeCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(10)
                        .forEach(entry -> sb.append(String.format("  %-40s %d 次\n", entry.getKey(), entry.getValue())));
            }

            return sb.toString();
        }
    }

    private record ErrorMessage(int lineNum, String message, LocalDateTime timestamp) {
    }

    private record ExceptionInfo(String type, String message, String location, LocalDateTime timestamp,
                                 int lineNumber) {
    }

    private record MatchResult(int lineNumber, String[] context) {
    }

    private static class DefaultAnalysisResult {
        private final String markdownContent;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> recommendations = new ArrayList<>();

        DefaultAnalysisResult(String markdownContent) {
            this.markdownContent = markdownContent;
        }

        String toMarkdown() {
            return markdownContent;
        }

        List<String> getWarnings() {
            return warnings;
        }

        List<String> getRecommendations() {
            return recommendations;
        }

        void addWarning(String warning) {
            warnings.add(warning);
        }

        void addRecommendation(String recommendation) {
            recommendations.add(recommendation);
        }
    }
}