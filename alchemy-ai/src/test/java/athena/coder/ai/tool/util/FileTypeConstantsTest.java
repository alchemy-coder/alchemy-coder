package athena.coder.ai.tool.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypeConstantsTest {

    @Test
    void isCodeFile() {
        assertTrue(FileTypeConstants.isCodeFile("A.java"));
        assertTrue(FileTypeConstants.isCodeFile("b.GO"));   // 大小写不敏感
        assertFalse(FileTypeConstants.isCodeFile("README.md"));
        assertFalse(FileTypeConstants.isCodeFile("noext"));
    }

    @Test
    void isBinaryFile() {
        assertTrue(FileTypeConstants.isBinaryFile(Path.of("a.png")));
        assertTrue(FileTypeConstants.isBinaryFile(Path.of("b.JAR")));
        assertFalse(FileTypeConstants.isBinaryFile(Path.of("c.java")));
        assertFalse(FileTypeConstants.isBinaryFile(Path.of("noext")));
    }

    @Test
    void codeFileFilter() {
        assertTrue(FileTypeConstants.CODE_FILE_FILTER.test(Path.of("X.java")));
        assertFalse(FileTypeConstants.CODE_FILE_FILTER.test(Path.of("X.md")));
    }

    @Test
    void configFileFilter() {
        assertTrue(FileTypeConstants.CONFIG_FILE_FILTER.test(Path.of("pom.xml")));
        assertTrue(FileTypeConstants.CONFIG_FILE_FILTER.test(Path.of("app.json")));
        assertTrue(FileTypeConstants.CONFIG_FILE_FILTER.test(Path.of("app.yaml")));
        assertFalse(FileTypeConstants.CONFIG_FILE_FILTER.test(Path.of("A.java")));
    }
}
