package athena.coder.ai.rag.model;

public record RagChunk(long id, String filePath, String content, float[] vector) {
}