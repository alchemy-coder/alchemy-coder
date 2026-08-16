package athena.coder.ai.tool.cache;

import athena.coder.ai.tool.util.FileTraversalHelper;
import athena.coder.ai.tool.util.FileTypeConstants;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FileIndexCache {

    private static final long CACHE_TTL_MS = 60_000L;

    private final ConcurrentHashMap<Path, CachedResult<List<Path>>> codeFilesCache = new ConcurrentHashMap<>();

    public List<Path> getCodeFiles(Path workDir) {
        CachedResult<List<Path>> cached = codeFilesCache.get(workDir);
        if (cached != null && !cached.isExpired()) {
            return cached.getData();
        }

        return codeFilesCache.compute(workDir, (key, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }
            return new CachedResult<>(scanCodeFiles(key), CACHE_TTL_MS);
        }).getData();
    }

    private List<Path> scanCodeFiles(Path workDir) {
        return FileTraversalHelper.findFiles(workDir, file -> {
            String fileName = file.getFileName().toString().toLowerCase();
            return FileTypeConstants.isCodeFile(fileName);
        }, 20, 1000);
    }

    private static class CachedResult<T> {
        private final T data;
        private final long createTime;
        private final long ttlMillis;

        CachedResult(T data, long ttlMillis) {
            this.data = data;
            this.createTime = System.currentTimeMillis();
            this.ttlMillis = ttlMillis;
        }

        T getData() {
            return data;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createTime > ttlMillis;
        }
    }
}