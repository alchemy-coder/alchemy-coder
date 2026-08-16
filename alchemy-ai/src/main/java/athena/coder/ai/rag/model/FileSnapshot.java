package athena.coder.ai.rag.model;

public record FileSnapshot(String filePath, long mtime, long size) {
}