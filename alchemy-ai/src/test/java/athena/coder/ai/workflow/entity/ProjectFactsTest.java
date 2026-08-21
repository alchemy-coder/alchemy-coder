package athena.coder.ai.workflow.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFactsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String json(ProjectFacts facts) throws Exception {
        return MAPPER.writeValueAsString(facts);
    }

    @Test
    void fromJson_nullOrBlank_returnsNull() {
        assertNull(ProjectFacts.fromJson(null));
        assertNull(ProjectFacts.fromJson(""));
        assertNull(ProjectFacts.fromJson("   "));
    }

    @Test
    void fromJson_invalidJson_returnsNull() {
        assertNull(ProjectFacts.fromJson("not json"));
        assertNull(ProjectFacts.fromJson("{"));
    }

    @Test
    void fromJson_validJson_parsesFields() throws Exception {
        ProjectFacts facts = new ProjectFacts(
                "一个单体 Spring Boot 项目",
                List.of(new ProjectFacts.ModuleFact("alchemy-ai", "alchemy-ai", "核心 AI 模块")),
                List.of(new ProjectFacts.FileFact("pom.xml", "构建配置", List.of("alchemy-ai", "alchemy-infra"))),
                List.of("alchemy-ai -> alchemy-infra"),
                List.of("JDK 25"));
        ProjectFacts parsed = ProjectFacts.fromJson(json(facts));
        assertEquals("一个单体 Spring Boot 项目", parsed.overview());
        assertEquals(1, parsed.modules().size());
        assertEquals("alchemy-ai", parsed.modules().get(0).name());
        assertEquals(1, parsed.files().size());
        assertEquals(List.of("alchemy-ai", "alchemy-infra"), parsed.files().get(0).keySymbols());
        assertEquals(List.of("JDK 25"), parsed.gotchas());
    }

    @Test
    void toPromptBlock_blankOrInvalid_returnsEmpty() {
        assertEquals("", ProjectFacts.toPromptBlock(null));
        assertEquals("", ProjectFacts.toPromptBlock("   "));
        assertEquals("", ProjectFacts.toPromptBlock("not json"));
    }

    @Test
    void toPromptBlock_valid_rendersAllSections() throws Exception {
        ProjectFacts facts = new ProjectFacts(
                "概览内容",
                List.of(new ProjectFacts.ModuleFact("m1", "/m1", "模块职责")),
                List.of(new ProjectFacts.FileFact("src/Foo.java", "业务", List.of("Foo", "bar"))),
                List.of("a -> b"),
                List.of("注意点1"));
        String block = ProjectFacts.toPromptBlock(json(facts));
        assertTrue(block.contains("概览内容"));
        assertTrue(block.contains("模块："));
        assertTrue(block.contains("m1"));
        assertTrue(block.contains("关键文件："));
        assertTrue(block.contains("src/Foo.java"));
        assertTrue(block.contains("Foo, bar"));
        assertTrue(block.contains("依赖关系："));
        assertTrue(block.contains("a -> b"));
        assertTrue(block.contains("注意点："));
        assertTrue(block.contains("注意点1"));
    }

    @Test
    void toPromptBlock_truncatesFilesAndSymbols() throws Exception {
        List<ProjectFacts.FileFact> manyFiles = IntStream.range(0, 35)
                .mapToObj(i -> new ProjectFacts.FileFact("F" + i + ".java", "r", List.of("s1", "s2", "s3", "s4")))
                .toList();
        ProjectFacts facts = new ProjectFacts(null, null, manyFiles, null, null);
        String block = ProjectFacts.toPromptBlock(json(facts));
        // 只渲染前 30 个文件，附截断提示
        assertTrue(block.contains("F29.java"));
        assertFalse(block.contains("F30.java"));
        assertTrue(block.contains("…等共 35 个文件"));
        // 每文件符号只保留 3 个
        assertTrue(block.contains("s1, s2, s3"));
        assertFalse(block.contains("s4"));
    }
}
