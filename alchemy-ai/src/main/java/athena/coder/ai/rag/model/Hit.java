package athena.coder.ai.rag.model;

/**
 * 检索命中：chunk id + 来源文件 + 原文
 */
public record Hit(long chunkId, String filePath, String text) {
}