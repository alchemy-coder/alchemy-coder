package athena.coder.ai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 项目标识散列工具：项目路径 → 稳定短 key，用于隔离不同项目的持久化数据（向量库/快照）。
 */
public final class ProjectKeyUtil {

    private ProjectKeyUtil() {
    }

    /**
     * 项目绝对路径 → SHA-256 前 8 字节 hex。
     */
    public static String projectKey(String projectPath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(projectPath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
