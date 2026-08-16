package athena.coder.ai.tool;

import athena.coder.ai.tool.base.FileSystemBasedTool;
import athena.coder.ai.tool.util.FileTraversalHelper;
import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.spi.ErrorLogger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecurityScannerTool extends FileSystemBasedTool {

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "INFO", 1,
            "LOW", 2,
            "MEDIUM", 3,
            "HIGH", 4,
            "CRITICAL", 5
    );

    private static final Map<String, String> PATTERN_DESCRIPTIONS = Map.of(
            "sql-injection", "SQL注入漏洞可能导致数据泄露或被篡改",
            "xss", "XSS漏洞可被用于窃取用户会话或执行恶意脚本",
            "command-injection", "命令注入可能允许攻击者在服务器上执行任意命令",
            "path-traversal", "路径遍历可能允许访问系统上的敏感文件",
            "hardcoded-secrets", "硬编码的密钥、密码或Token应存储在安全的配置管理系统中",
            "unsafe-deserialization", "不安全的反序列化可能导致远程代码执行(RCE)",
            "weak-crypto", "使用弱加密算法或不安全的随机数生成器"
    );

    public SecurityScannerTool() {
        super();
    }

    @Tool("执行全面的安全扫描，支持OWASP Top 10漏洞检测")
    public String sastScan(
            @P("扫描目标：文件或目录路径") String target,
            @P("最低严重级别：INFO/LOW/MEDIUM/HIGH/CRITICAL，默认MEDIUM") String minSeverity,
            @P("规则集名称：owasp-top10/cwe-top25/custom/all，默认owasp-top10") String ruleSet) {

        return executeSafely(() -> {
            Path targetPath = resolveAndValidate(target);
            checkFileExists(targetPath);

            String effectiveMinSeverity = (minSeverity != null && !minSeverity.isBlank()) ? minSeverity.toUpperCase() : "MEDIUM";
            String effectiveRuleSet = (ruleSet != null && !ruleSet.isBlank()) ? ruleSet.toLowerCase() : "owasp-top10";

            List<ScanResult> allFindings = new ArrayList<>();

            List<Path> filesToScan;
            if (safeIsDirectory(targetPath)) {
                filesToScan = FileTraversalHelper.findCodeFiles(targetPath, 20, config.getMaxScanFiles());
            } else if (isSourceFile(targetPath)) {
                filesToScan = List.of(targetPath);
            } else {
                throw new ToolValidationException(getToolName(), ErrorCode.UNSUPPORTED_TYPE, "不是支持的源代码文件: " + target);
            }

            for (Path file : filesToScan) {
                String relativePath = getAllowedWorkDir().relativize(file).toString();
                try {
                    long fileSize = safeFileSize(file);
                    long maxSize = config.getMaxFileSize();
                    if (fileSize > maxSize || fileSize == 0) continue;

                    String content = safeReadString(file);
                    allFindings.addAll(scanFile(content, relativePath, effectiveRuleSet));
                } catch (Exception e) {
                    logFine("安全扫描文件失败: " + relativePath + " - " + e.getMessage());
                }
            }

            List<ScanResult> filteredFindings = filterBySeverity(allFindings, effectiveMinSeverity);

            return formatReport(filteredFindings, effectiveMinSeverity, target);
        }, "sastScan");
    }

    @Tool("检测特定类型的安全漏洞")
    public String checkPattern(
            @P("漏洞模式名称：sql-injection/xss/command-injection/path-traversal/hardcoded-secrets/" +
                    "unsafe-deserialization/weak-crypto/csrf/insecure-config") String patternName,
            @P("目标文件或目录") String target) {

        return executeSafely(() -> {
            if (patternName == null || patternName.isBlank()) {
                throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "漏洞模式名称");
            }

            Path targetPath = resolveAndValidate(target);
            checkFileExists(targetPath);

            List<ScanResult> findings = new ArrayList<>();
            Pattern[] patterns = getPatternsByName(patternName);

            if (patterns == null || patterns.length == 0) {
                return ERR_PREFIX + "未知的漏洞模式: " + patternName;
            }

            List<Path> filesToScan;
            if (safeIsDirectory(targetPath)) {
                filesToScan = FileTraversalHelper.findCodeFiles(targetPath, 20, config.getMaxScanFiles());
            } else {
                filesToScan = List.of(targetPath);
            }

            for (Path file : filesToScan) {
                String content = safeReadString(file);
                String relativePath = getAllowedWorkDir().relativize(file).toString();

                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(content);
                    while (matcher.find()) {
                        int lineNum = calculateLineNumber(content, matcher.start());
                        findings.add(new ScanResult(
                                patternName,
                                "HIGH",
                                matcher.group().trim(),
                                relativePath,
                                lineNum,
                                getPatternDescription(patternName)
                        ));
                    }
                }
            }

            return formatReport(findings, "INFO", target);
        }, "checkPattern");
    }

    private List<ScanResult> scanFile(String content, String filePath, String ruleSet) {
        List<ScanResult> findings = new ArrayList<>();

        switch (ruleSet) {
            case "all":
                addOwaspTop10Findings(findings, content, filePath);
                addCweTop25Findings(findings, content, filePath);
                break;

            case "cwe-top25":
            case "custom":
                addCweTop25Findings(findings, content, filePath);
                break;

            case "owasp-top10":
                addOwaspTop10Findings(findings, content, filePath);
                break;

            default:
                ErrorLogger.warn("SecurityScannerTool", "未知规则集: " + ruleSet + "，使用默认 owasp-top10");
                addOwaspTop10Findings(findings, content, filePath);
                break;
        }

        return findings;
    }

    private void addOwaspTop10Findings(List<ScanResult> findings, String content, String filePath) {
        addFindingsForPatterns(findings, content, filePath, patternRegistry.sqlInjectionPatterns(), "SQL注入", "HIGH");
        addFindingsForPatterns(findings, content, filePath, patternRegistry.xssPatterns(), "XSS跨站脚本", "HIGH");
        addFindingsForPatterns(findings, content, filePath, patternRegistry.commandInjectionPatterns(), "命令注入", "CRITICAL");
        addFindingsForPatterns(findings, content, filePath, patternRegistry.pathTraversalPatterns(), "路径遍历", "MEDIUM");
        addFindingsForPatterns(findings, content, filePath, patternRegistry.hardcodedSecretPatterns(), "硬编码密钥", "HIGH");
    }

    private void addCweTop25Findings(List<ScanResult> findings, String content, String filePath) {
        addFindingsForPatterns(findings, content, filePath, patternRegistry.unsafeDeserializationPatterns(), "不安全反序列化", "HIGH");
        addFindingsForPatterns(findings, content, filePath, patternRegistry.weakCryptoPatterns(), "弱加密算法", "MEDIUM");

        Pattern sensitivePattern = patternRegistry.sensitiveValuePattern();
        Matcher sensitiveMatcher = sensitivePattern.matcher(content);
        while (sensitiveMatcher.find()) {
            int lineNum = calculateLineNumber(content, sensitiveMatcher.start());
            findings.add(new ScanResult(
                    "sensitive-data",
                    "MEDIUM",
                    maskSensitiveValue(sensitiveMatcher.group()),
                    filePath,
                    lineNum,
                    "可能包含敏感信息（密码、密钥、Token等）"
            ));
        }
    }

    private void addFindingsForPatterns(List<ScanResult> findings, String content, String filePath,
                                        Pattern[] patterns, String vulnerabilityType, String severity) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                int lineNum = calculateLineNumber(content, matcher.start());
                findings.add(new ScanResult(
                        vulnerabilityType.toLowerCase().replace("-", ""),
                        severity,
                        matcher.group().trim(),
                        filePath,
                        lineNum,
                        getPatternDescription(vulnerabilityType.toLowerCase())
                ));
            }
        }
    }

    private Pattern[] getPatternsByName(String patternName) {
        return switch (patternName.toLowerCase()) {
            case "sql-injection" -> patternRegistry.sqlInjectionPatterns();
            case "xss" -> patternRegistry.xssPatterns();
            case "command-injection" -> patternRegistry.commandInjectionPatterns();
            case "path-traversal" -> patternRegistry.pathTraversalPatterns();
            case "hardcoded-secrets" -> patternRegistry.hardcodedSecretPatterns();
            case "unsafe-deserialization" -> patternRegistry.unsafeDeserializationPatterns();
            case "weak-crypto" -> patternRegistry.weakCryptoPatterns();
            default -> new Pattern[0];
        };
    }

    private String getPatternDescription(String patternName) {
        return PATTERN_DESCRIPTIONS.getOrDefault(patternName, "潜在安全问题");
    }

    private boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return isCodeFile(fileName);
    }

    private int calculateLineNumber(String content, int position) {
        int line = 1;
        for (int i = 0; i < Math.min(position, content.length()); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private List<ScanResult> filterBySeverity(List<ScanResult> findings, String minSeverity) {
        int minLevel = SEVERITY_ORDER.getOrDefault(minSeverity.toUpperCase(), 1);

        return findings.stream()
                .filter(f -> SEVERITY_ORDER.getOrDefault(f.severity.toUpperCase(), 0) >= minLevel)
                .sorted((a, b) -> {
                    int levelA = SEVERITY_ORDER.getOrDefault(a.severity.toUpperCase(), 0);
                    int levelB = SEVERITY_ORDER.getOrDefault(b.severity.toUpperCase(), 0);
                    return Integer.compare(levelB, levelA);
                })
                .toList();
    }

    private String formatReport(List<ScanResult> findings, String minSeverity, String target) {
        if (findings.isEmpty()) {
            return OK_PREFIX + String.format("安全扫描完成 ✓\n目标: %s\n严重级别: %s+\n未发现安全问题", target, minSeverity);
        }

        StringBuilder report = new StringBuilder();
        report.append("安全扫描报告\n");
        report.append(String.format("目标: %s\n", target));
        report.append(String.format("严重级别: %s+\n", minSeverity));
        report.append(String.format("发现 %d 个问题\n\n", findings.size()));

        Map<String, Long> typeCounts = findings.stream()
                .collect(java.util.stream.Collectors.groupingBy(f -> f.type, java.util.stream.Collectors.counting()));

        report.append("--- 问题类型统计 ---\n");
        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> report.append(String.format("  %-25s %d 个\n", entry.getKey(), entry.getValue())));

        report.append("\n--- 详细发现 ---\n");
        for (int i = 0; i < findings.size(); i++) {
            ScanResult finding = findings.get(i);
            report.append(String.format("\n[%d] %s (%s)\n", i + 1, finding.type, finding.severity));
            report.append(String.format("    文件: %s:%d\n", finding.filePath, finding.lineNumber));
            report.append(String.format("    代码: %s\n", finding.matchedCode.length() > 80 ?
                    finding.matchedCode.substring(0, 80) + "..." : finding.matchedCode));
            report.append(String.format("    描述: %s\n", finding.description));
        }

        report.append("\n--- 建议修复优先级 ---\n");
        report.append("1. 🔴 CRITICAL: 立即修复\n");
        report.append("2. 🟠 HIGH: 尽快修复（本周内）\n");
        report.append("3. 🟡 MEDIUM: 计划修复（本月内）\n");
        report.append("4. 🟢 LOW: 可选修复（下个迭代）\n");

        return enforceOutputLimit(report.toString());
    }

    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 8) return "***";
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    private record ScanResult(String type, String severity, String matchedCode, String filePath, int lineNumber,
                              String description) {
    }
}