package athena.coder.ai.tool;

import athena.coder.ai.tool.exception.ErrorCode;
import athena.coder.ai.tool.exception.ToolSecurityException;
import athena.coder.ai.tool.exception.ToolValidationException;
import athena.coder.ai.tool.validation.NotBlank;
import athena.coder.ai.tool.validation.PatternRegex;
import athena.coder.ai.spi.ErrorLogger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class APITestClientTool extends AbstractBaseTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESPONSE_SIZE = 1024 * 1024;

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final Pattern[] BLOCKED_URL_PATTERNS = {
            Pattern.compile("(^|\\.)internal\\.example\\.com"),
            Pattern.compile("(^|\\.)local$"),
            Pattern.compile("^https?://(?:10\\.|172\\.(?:1[6-9]|2[0-9]|3[01])|192\\.168\\.)"),
            Pattern.compile("^https?://169\\.254\\."),
            Pattern.compile("^https?://localhost(?::\\d+)?")
    };

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "Host", "Content-Length", "Transfer-Encoding", "Connection", "Accept-Encoding");

    private static final Set<Integer> RESTRICTED_PORTS = Set.of(
            22, 23, 25, 53, 135, 139, 445, 1433, 1521, 3306, 5432, 6379, 27017);

    private final HttpClient httpClient;

    public APITestClientTool() {
        super(false);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool("发送 HTTP 请求并返回响应结果。支持 GET、POST、PUT、DELETE 等方法。")
    public String sendRequest(
            @P("目标 URL（仅支持 http/https）") @NotBlank(fieldName = "URL") String url,
            @P("HTTP 方法：GET/POST/PUT/DELETE/PATCH") @PatternRegex(regexp = "^(GET|POST|PUT|DELETE|PATCH)$", message = "不支持的 HTTP 方法") String method,
            @P("请求头，JSON 格式: {\"Content-Type\": \"application/json\"}") String headersJson,
            @P("请求体内容（POST/PUT/PATCH 时使用）") String body) {

        return executeWithAutoValidation(() -> {
            validateUrl(url);

            Map<String, String> headers = parseHeaders(headersJson);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));

            switch (method.toUpperCase()) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    if (body != null && !body.isBlank()) {
                        validateRequestBody(body, headers.getOrDefault("Content-Type", ""));
                        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                    }
                    break;
                case "PUT":
                    if (body != null && !body.isBlank()) {
                        validateRequestBody(body, headers.getOrDefault("Content-Type", ""));
                        requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
                    }
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                case "PATCH":
                    if (body != null && !body.isBlank()) {
                        validateRequestBody(body, headers.getOrDefault("Content-Type", ""));
                        requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        requestBuilder.method("PATCH", HttpRequest.BodyPublishers.noBody());
                    }
                    break;
                default:
                    throw new ToolValidationException(getToolName(), ErrorCode.INVALID_FORMAT, method);
            }

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }

            long startTime = System.currentTimeMillis();

            try {
                HttpResponse<String> response = httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

                long elapsedMs = System.currentTimeMillis() - startTime;

                String responseBody = response.body();
                if (responseBody != null && responseBody.length() > MAX_RESPONSE_SIZE) {
                    responseBody = responseBody.substring(0, MAX_RESPONSE_SIZE) +
                            "\n\n[... 响应已截断 (超过 1MB) ...]";
                }

                StringBuilder result = new StringBuilder();
                result.append("=== HTTP 响应 ===\n\n");
                result.append(String.format("状态码: %d %s\n", response.statusCode(),
                        getHttpStatusText(response.statusCode())));
                result.append(String.format("耗时: %dms\n\n", elapsedMs));

                result.append("--- 响应头 ---\n");
                response.headers().map().forEach((key, values) ->
                        result.append(String.format("%s: %s\n", key, String.join(", ", values))));

                if (responseBody != null && !responseBody.isEmpty()) {
                    result.append("\n--- 响应体 ---\n");
                    result.append(responseBody);
                }

                logInfo(String.format("HTTP %s %s -> %d (%dms)",
                        method, url, response.statusCode(), elapsedMs));

                return enforceOutputLimit(result.toString());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ERR_PREFIX + "请求被中断";
            } catch (Exception e) {
                ErrorLogger.log("APITestClientTool.sendRequest(" + url + ")", e);
                return ERR_PREFIX + "请求失败: " + e.getMessage();
            }
        }, "sendRequest", url, method, headersJson, body);
    }

    @Tool("对 URL 进行 Base64 编码（用于 Basic Auth 等）")
    public String base64Encode(
            @P("要编码的字符串") @NotBlank(fieldName = "编码内容") String text) {

        return executeWithAutoValidation(() -> {
            String encoded = Base64.getEncoder().encodeToString(text.getBytes());
            return OK_PREFIX + encoded;
        }, "base64Encode", text);
    }

    @Tool("对 Base64 字符串进行解码")
    public String base64Decode(
            @P("Base64 编码的字符串") @NotBlank(fieldName = "Base64字符串") @PatternRegex(regexp = "^[A-Za-z0-9+/=]+$", message = "无效的 Base64 格式") String encodedText) {

        return executeWithAutoValidation(() -> {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedText);
            String decoded = new String(decodedBytes);
            return OK_PREFIX + decoded;
        }, "base64Decode", encodedText);
    }

    private void validateRequestBody(String body, String contentType) throws ToolValidationException {
        if (body == null || body.isEmpty()) return;

        long maxBodySize = 512 * 1024; // 512KB
        if (body.length() > maxBodySize) {
            throw new ToolValidationException(getToolName(), ErrorCode.FILE_TOO_LARGE,
                    body.length() / 1024, maxBodySize / 1024);
        }

        if (contentType.contains("application/json")) {
            try {
                OBJECT_MAPPER.readTree(body);
            } catch (Exception e) {
                ErrorLogger.warn("APITestClientTool.validateRequestBody", "JSON 格式校验失败: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String headersJson) {
        Map<String, String> headers = new HashMap<>();

        if (headersJson == null || headersJson.isBlank()) {
            return headers;
        }

        try {
            headers = OBJECT_MAPPER.readValue(headersJson, Map.class);
        } catch (Exception e) {
            ErrorLogger.warn("APITestClientTool.parseHeaders", "解析请求头 JSON 失败: " + e.getMessage());
        }

        BLOCKED_HEADERS.forEach(headers::remove);

        return headers;
    }

    private String getHttpStatusText(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    private void validateUrl(String url) throws ToolValidationException, ToolSecurityException {
        if (url == null || url.isBlank()) {
            throw new ToolValidationException(getToolName(), ErrorCode.PARAM_MISSING, "URL");
        }

        try {
            URI uri = URI.create(url);

            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
                throw new ToolValidationException(getToolName(), ErrorCode.INVALID_FORMAT,
                        "仅允许 HTTP/HTTPS 协议，当前协议: " + scheme);
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new ToolValidationException(getToolName(), ErrorCode.INVALID_FORMAT,
                        "URL 格式无效，缺少主机名");
            }

            for (Pattern pattern : BLOCKED_URL_PATTERNS) {
                if (pattern.matcher(host).find()) {
                    throw new ToolSecurityException(getToolName(), ErrorCode.COMMAND_BLOCKED,
                            "禁止访问内网/本地地址: " + host);
                }
            }

            int port = uri.getPort();
            if (port > 0 && isRestrictedPort(port)) {
                throw new ToolSecurityException(getToolName(), ErrorCode.COMMAND_BLOCKED,
                        "禁止访问受限端口: " + port);
            }

        } catch (IllegalArgumentException e) {
            throw new ToolValidationException(getToolName(), ErrorCode.INVALID_FORMAT,
                    "URL 格式无效: " + url);
        }
    }

    private boolean isRestrictedPort(int port) {
        return RESTRICTED_PORTS.contains(port);
    }
}