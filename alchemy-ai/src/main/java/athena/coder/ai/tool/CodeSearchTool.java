package athena.coder.ai.tool;

import athena.coder.ai.rag.RagManager;
import athena.coder.ai.tool.base.FileSystemBasedTool;
import athena.coder.ai.tool.util.FileTraversalHelper;
import athena.coder.ai.tool.cache.FileIndexCache;
import athena.coder.ai.tool.validation.NotBlank;
import athena.coder.ai.tool.validation.Range;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.Content;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CodeSearchTool extends FileSystemBasedTool {

    private static final int MAX_FILE_RESULTS = 50;
    private static final int MAX_CONTENT_RESULTS = 30;
    private static final long MAX_SCAN_FILE_SIZE = 1024 * 1024;

    private final FileIndexCache fileCache;

    public CodeSearchTool() {
        super();
        this.fileCache = new FileIndexCache();
    }

    @Tool("在项目中按文件名模式搜索文件。支持通配符匹配，如 '**/*.java' 查找所有Java文件，'**/Controller.java' 查找所有Controller。")
    public String findFiles(
            @NotBlank(fieldName = "pattern") @P("文件名匹配模式，支持 * 和 ** 通配符，如: **/*.java, **/test/**") String pattern,
            @Range(min = 1, max = 200, message = "最大返回结果数应在1-200之间") @P("最大返回结果数，默认50") int maxResults) {

        return executeWithAutoValidation(() -> {
            int limit = maxResults <= 0 ? MAX_FILE_RESULTS : Math.min(maxResults, 200);

            Pattern compiled = patternRegistry.compile(wildcardToRegex(pattern, true), Pattern.CASE_INSENSITIVE);

            Path workDir = getAllowedWorkDir();

            List<Path> allFiles = FileTraversalHelper.findFiles(
                    workDir,
                    file -> {
                        String relativePath = workDir.relativize(file).toString();
                        return compiled.matcher(relativePath).matches() ||
                                compiled.matcher(file.getFileName().toString()).matches();
                    },
                    Integer.MAX_VALUE,
                    limit * 2
            );

            if (allFiles.isEmpty()) {
                return String.format("未找到匹配的文件（模式: %s）", pattern);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 个匹配文件（模式: %s）:\n", Math.min(allFiles.size(), limit), pattern));
            result.append("---\n");

            int shown = 0;
            for (Path match : allFiles) {
                if (shown >= limit) break;
                String relativePath = workDir.relativize(match).toString();
                try {
                    long size = safeFileSize(match);
                    result.append(relativePath).append(" (").append(formatSize(size)).append(")\n");
                } catch (Exception e) {
                    result.append(relativePath).append("\n");
                }
                shown++;
            }

            return enforceOutputLimit(result.toString());
        }, "findFiles", pattern, maxResults);
    }

    @Tool("在项目源代码中按文本内容或正则表达式搜索。类似 grep 命令，返回匹配行及其上下文。")
    public String searchContent(
            @NotBlank(fieldName = "query") @P("搜索的文本或正则表达式") String query,
            @P("限定搜索的文件类型，如 '*.java'、'*.xml'，为空则搜索所有文本文件") String filePattern,
            @Range(min = 0, max = 5, message = "上下文行数应在0-5之间") @P("显示匹配行前后的上下文行数，默认2") int contextLines,
            @Range(min = 1, max = 100, message = "最大返回结果数应在1-100之间") @P("最大返回结果数，默认30") int maxResults) {

        return executeWithAutoValidation(() -> {
            int limit = maxResults <= 0 ? MAX_CONTENT_RESULTS : Math.min(maxResults, 100);
            int context = Math.clamp(contextLines, 0, 5);

            final Pattern searchPattern = compileSearchPattern(query);

            final Pattern fileFilter;
            if (filePattern != null && !filePattern.isBlank()) {
                fileFilter = patternRegistry.compile(wildcardToRegex(filePattern, false), Pattern.CASE_INSENSITIVE);
            } else {
                fileFilter = null;
            }

            List<SearchMatch> allMatches = new ArrayList<>();
            Path workDir = getAllowedWorkDir();

            List<Path> filesToScan = FileTraversalHelper.findFiles(
                    workDir,
                    file -> {
                        if (fileFilter != null && !fileFilter.matcher(file.getFileName().toString()).matches()) {
                            return false;
                        }
                        if (isBinaryFile(file)) {
                            return false;
                        }
                        try {
                            long size = safeFileSize(file);
                            return size <= MAX_SCAN_FILE_SIZE && size > 0;
                        } catch (Exception e) {
                            return false;
                        }
                    },
                    Integer.MAX_VALUE,
                    10000
            );

            for (Path file : filesToScan) {
                if (allMatches.size() >= limit * 3) break;
                try {
                    searchFileStreamed(file, searchPattern, context, limit, allMatches);
                } catch (Exception e) {
                    logFine("无法读取文件: " + file + " - " + e.getMessage());
                }
            }

            if (allMatches.isEmpty()) {
                return String.format("未找到匹配的内容（查询: %s）", query);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 处匹配（查询: %s）:\n", Math.min(allMatches.size(), limit), query));
            result.append("===\n");

            int shown = 0;
            String lastFile = "";
            for (SearchMatch match : allMatches) {
                if (shown >= limit) break;

                String relativePath = workDir.relativize(match.file).toString();

                if (!relativePath.equals(lastFile)) {
                    if (shown > 0) result.append("\n");
                    result.append("[文件] ").append(relativePath).append(":\n");
                    lastFile = relativePath;
                }

                if (context > 0 && match.contextLines != null) {
                    for (String ctxLine : match.contextLines) {
                        result.append("  ").append(ctxLine).append("\n");
                    }
                } else {
                    result.append(String.format("  %d: %s\n", match.lineNumber, match.line.trim()));
                }
                result.append("\n");

                shown++;
            }

            return enforceOutputLimit(result.toString());
        }, "searchContent", query, filePattern, contextLines, maxResults);
    }

    @Tool("语义级代码搜索。基于项目代码索引（向量 + 关键词混合检索）查找最相关的代码片段。适合查找'处理用户登录的代码'、'数据库连接配置'等。")
    public String searchCodebase(
            @NotBlank(fieldName = "query") @P("自然语言描述的搜索意图，如: '用户认证逻辑'、'数据库连接池配置'") String query,
            @Range(min = 1, max = 30, message = "最大返回结果数应在1-30之间") @P("最大返回结果数，默认10") int maxResults) {

        return executeWithAutoValidation(() -> {
            int limit = maxResults <= 0 ? 10 : Math.min(maxResults, 30);

            // 优先走索引混合检索；索引未就绪/RAG 降级（返回空）时回退关键词评分
            String semanticResult = semanticSearch(query, limit);
            if (semanticResult != null) {
                return semanticResult;
            }
            return keywordSearch(query, limit);
        }, "searchCodebase", query, maxResults);
    }

    /**
     * 混合检索：向量 + FTS5 关键词双路召回、RRF 融合（RagManager 内部已静默降级）。
     * 返回 null 表示无命中（索引未建好或已降级），由调用方回退
     */
    private String semanticSearch(String query, int limit) {
        List<Content> contents = RagManager.getInstance().retrieve(query, limit);
        if (contents.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        result.append(String.format("找到 %d 个相关代码片段（查询: %s）:\n", contents.size(), query));
        result.append("===\n");
        for (Content content : contents) {
            result.append("\n").append(content.textSegment().text()).append("\n");
        }
        return enforceOutputLimit(result.toString());
    }

    /**
     * 兜底：关键词子串评分（索引未就绪/RAG 不可用时保留的降级路径）
     */
    private String keywordSearch(String query, int limit) {
        String[] keywords = query.toLowerCase().split("\\s+");

        List<Path> codeFiles = fileCache.getCodeFiles(getAllowedWorkDir());

        List<ScoredFile> scoredFiles = new ArrayList<>();
        for (Path file : codeFiles) {
            double score = scoreFile(file, keywords);
            if (score > 0) {
                scoredFiles.add(new ScoredFile(file, score));
            }
        }

        scoredFiles.sort((a, b) -> Double.compare(b.score, a.score));

        if (scoredFiles.isEmpty()) {
            return String.format("未找到相关代码（查询: %s）", query);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("找到 %d 个相关代码文件（查询: %s）:\n",
                Math.min(scoredFiles.size(), limit), query));
        result.append("===\n");

        Path workDir = getAllowedWorkDir();
        int shown = 0;
        for (ScoredFile sf : scoredFiles) {
            if (shown >= limit) break;

            String relativePath = workDir.relativize(sf.file).toString();
            result.append(String.format("\n[文件] %s (相关度: %.1f)\n", relativePath, sf.score));

            try (BufferedReader reader = Files.newBufferedReader(sf.file, StandardCharsets.UTF_8)) {
                String previewLine;
                int lineCount = 0;
                int previewLines = 20;
                while ((previewLine = reader.readLine()) != null && lineCount < previewLines) {
                    result.append(String.format("%4d | %s\n", lineCount + 1, previewLine));
                    lineCount++;
                }
                if (reader.readLine() != null) {
                    result.append("  ... (超过 ").append(previewLines).append(" 行)\n");
                }
            } catch (Exception e) {
                result.append("  [无法读取文件]\n");
            }

            shown++;
        }

        return enforceOutputLimit(result.toString());
    }

    private String wildcardToRegex(String pattern, boolean pathAware) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (pathAware && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i += 2;
                    if (i < pattern.length() && pattern.charAt(i) == '/') {
                        sb.append("(?:/|$)");
                        i++;
                    }
                } else {
                    sb.append(pathAware ? "[^/]*" : ".*");
                    i++;
                }
            } else if (c == '?') {
                sb.append(pathAware ? "[^/]" : ".");
                i++;
            } else if (".+^${}\\|()[]".indexOf(c) != -1) {
                sb.append('\\').append(c);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private Pattern compileSearchPattern(String query) {
        boolean isRegex = patternRegistry.isValidRegex(query);
        String regexPattern = isRegex ? query : Pattern.quote(query);
        return patternRegistry.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    }

    private void searchFileStreamed(Path file, Pattern pattern, int contextLines, int maxResults, List<SearchMatch> results) throws IOException {
        List<String> buffer = contextLines > 0 ? new ArrayList<>() : null;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (contextLines > 0) {
                    buffer.add(line);
                }

                if (pattern.matcher(line).find()) {
                    String[] context = null;
                    if (contextLines > 0) {
                        context = extractContextFromBuffer(file, buffer, lineNum - 1, contextLines);
                    }
                    results.add(new SearchMatch(file, lineNum, line, context));
                    if (results.size() >= maxResults * 3) break;
                }
            }
        }
    }

    private String[] extractContextFromBuffer(Path file, List<String> buffer, int matchIndex, int contextLines) {
        int start = Math.max(0, matchIndex - contextLines);
        int end = Math.min(buffer.size(), matchIndex + contextLines + 1);
        return buffer.subList(start, end).toArray(new String[0]);
    }

    private double scoreFile(Path file, String[] keywords) {
        int validCount = 0;
        for (String kw : keywords) {
            if (kw.length() >= 2) validCount++;
        }
        if (validCount == 0) return 0.0;

        double fileNameBoost = 0.0;
        String fileName = file.getFileName().toString().toLowerCase();
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && fileName.contains(keyword)) {
                fileNameBoost += 2.0;
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            double score = 0.0;
            int matchedKeywords = 0;
            boolean[] keywordMatched = new boolean[keywords.length];

            String line;
            while ((line = reader.readLine()) != null) {
                String lowerLine = line.toLowerCase();
                for (int i = 0; i < keywords.length; i++) {
                    if (!keywordMatched[i] && keywords[i].length() >= 2 && lowerLine.contains(keywords[i])) {
                        keywordMatched[i] = true;
                        score += 1.0;
                        matchedKeywords++;
                    }
                }
                if (matchedKeywords >= validCount) {
                    break;
                }
            }

            if (matchedKeywords > 0) {
                double density = (double) matchedKeywords / keywords.length;
                return score * (1.0 + density) + fileNameBoost;
            }

            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private record SearchMatch(Path file, int lineNumber, String line, String[] contextLines) {
    }

    private record ScoredFile(Path file, double score) {
    }
}