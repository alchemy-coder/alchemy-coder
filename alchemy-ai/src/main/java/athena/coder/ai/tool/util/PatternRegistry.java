package athena.coder.ai.tool.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PatternRegistry {

    private static final PatternRegistry INSTANCE = new PatternRegistry();

    private final Map<String, Pattern> cache = new ConcurrentHashMap<>();
    private final Map<String, Pattern[]> arrayCache = new ConcurrentHashMap<>();

    private PatternRegistry() {
    }

    public static PatternRegistry getInstance() {
        return INSTANCE;
    }

    public Pattern importStatement() {
        return getOrCreate("import", "import\\s+(?:static\\s+)?([\\w.]+);");
    }

    public Pattern javaClassDeclaration() {
        return getOrCreate("javaClass",
                "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(class|interface|enum)\\s+(\\w+)(?:\\s+extends\\s+(\\w+))?(?:\\s+implements\\s+([\\w,\\s]+))?");
    }

    public Pattern logLevel() {
        return getOrCreate("logLevel", "\\b(DEBUG|TRACE|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\b",
                Pattern.CASE_INSENSITIVE);
    }

    public Pattern exceptionPattern() {
        return getOrCreate("exception", "(?:Exception|Error|Throwable)\\[?[:\\s]?(\\w+(?:\\.\\w+)*)",
                Pattern.MULTILINE);
    }

    public Pattern stackTraceFrame() {
        return getOrCreate("stackFrame", "\\s+at\\s+([\\w$]+(?:\\.[\\w$]+)+)\\(([\\w.]+):(\\d+)\\)");
    }

    public Pattern[] sqlInjectionPatterns() {
        return getOrCreateArray("sqlInjection", new String[]{
                "(?i)(String\\s+sql\\s*=\\s*\"[^\"]*\\+\\s*)",
                "(?i)\\.execute(Query|Update)\\(.*\\+\\s*)",
                "(?i)\\.rawQuery\\(.*\\+",
                "(?i)format\\(.*SELECT.*%s",
                "(?i)f\".*SELECT.*\\{",
                "(?i)\\$\\{.*}(?=.*(SELECT|INSERT|UPDATE|DELETE))"
        });
    }

    public Pattern[] xssPatterns() {
        return getOrCreateArray("xss", new String[]{
                "(?i)innerHTML\\s*=\\s*\\+",
                "(?i)document\\.write\\(.*request|params",
                "(?i)\\.html\\(.*request|params|query",
                "(?i)v-html\\s*=",
                "(?i)dangerouslySetInnerHTML"
        });
    }

    public Pattern[] commandInjectionPatterns() {
        return getOrCreateArray("commandInjection", new String[]{
                "(?i)Runtime\\.getRuntime\\(\\)\\.exec\\(.*\\+",
                "(?i)ProcessBuilder\\(.*\\+",
                "(?i)exec\\(.*\\$|\\{|f\"",
                "(?i)subprocess\\.(call|run|Popen)\\(.*shell=True",
                "(?i)os\\.system\\(",
                "(?i)exec\\(.*user"
        });
    }

    public Pattern[] pathTraversalPatterns() {
        return getOrCreateArray("pathTraversal", new String[]{
                "(?i)new\\s+File\\(.*request|param|input|user",
                "(?i)Files\\.read\\(Paths\\.get\\(.*request|param",
                "(?i)open\\(.*request|param|input|user.*['\"]\\s*[+/]",
                "(?i)\\..\\/\\.\\."
        });
    }

    public Pattern[] hardcodedSecretPatterns() {
        return getOrCreateArray("hardcodedSecrets", new String[]{
                "(?i)(password|passwd|pwd)\\s*[=:]+\\s*[\"'][^\"']{3,}[\"']",
                "(?i)(secret|api[_-]?key|apikey|access[_-]?key)\\s*[=:]+\\s*[\"'][^\"']{10,}[\"']",
                "(?i)(token|auth[_-]?token|private[_-]?key)\\s*[=:]+\\s*[\"'][^\"']{20,}[\"']",
                "(?i)(aws_access_key_id|aws_secret_access_key)\\s*[=:]+\\s*[\"'][^\"']+[\"']",
                "AKIA[0-9A-Z]{16}",
                "(?i)sk-[a-f0-9]{32}",
                "(?i)ghp_[a-zA-Z0-9]{36}"
        });
    }

    public Pattern[] unsafeDeserializationPatterns() {
        return getOrCreateArray("unsafeDeser", new String[]{
                "(?i)ObjectInputStream.*readUnshared",
                "(?i)YAML\\.load\\(.*request|param|input",
                "(?i)pickle\\.loads?\\(.*request|param|input",
                "(?i)marshal\\.load\\(.*request|param|input",
                "(?i)JSON\\.parse\\(.*eval"
        });
    }

    public Pattern[] weakCryptoPatterns() {
        return getOrCreateArray("weakCrypto", new String[]{
                "(?i)Cipher\\.getInstance\\([\"'](DES|RC4|MD5|ECB)[\"']\\)",
                "(?i)MessageDigest\\.getInstance\\([\"'](MD4|MD5)[\"']\\)",
                "(?im)hashlib\\.(md5|sha1)\\(",
                "(?i)SecureRandom\\(\\)",
                "(?i)Math\\.random\\(\\)"
        });
    }

    public Pattern sensitiveValuePattern() {
        return getOrCreate("sensitiveValue",
                "(password|passwd|pwd|secret|api[_-]?key|token|auth|credential|private[_-]?key|access[_-]?key)\\s*[=:]+\\s*\\S+",
                Pattern.CASE_INSENSITIVE);
    }

    private Pattern getOrCreate(String key, String regex) {
        return getOrCreate(key, regex, 0);
    }

    private Pattern getOrCreate(String key, String regex, int flags) {
        return cache.computeIfAbsent(key, k -> Pattern.compile(regex, flags));
    }

    private Pattern[] getOrCreateArray(String prefix, String[] regexes) {
        return arrayCache.computeIfAbsent(prefix, k -> {
            Pattern[] patterns = new Pattern[regexes.length];
            for (int i = 0; i < regexes.length; i++) {
                patterns[i] = getOrCreate(prefix + "_" + i, regexes[i], Pattern.MULTILINE);
            }
            return patterns;
        });
    }

    public Pattern compile(String regex, int flags) {
        String key = regex + "|" + flags;
        return cache.computeIfAbsent(key, k -> Pattern.compile(regex, flags));
    }

    public boolean isValidRegex(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}