package athena.coder.ai.spi;

import athena.coder.entity.model.ModelEnum;
import athena.coder.entity.model.ModelType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultModelProviderTest {

    private static final class CapturingModels implements ModelConfigPort {
        final Map<String, String> byKey = new HashMap<>();
        final AtomicReference<ModelType> lastType = new AtomicReference<>();
        final AtomicReference<String> lastNameVersion = new AtomicReference<>();

        @Override
        public String findApiKey(ModelType type, String name, String version) {
            lastType.set(type);
            lastNameVersion.set(name + "|" + version);
            return byKey.get(type.dbValue() + "|" + name + "|" + version);
        }
    }

    @Test
    void chatModel_usesChatType_andBuilds() {
        CapturingModels m = new CapturingModels();
        m.byKey.put("chat|qianwen|qwen3.7-max", "sk-1");
        DefaultModelProvider p = new DefaultModelProvider(m);

        assertNotNull(p.chatModel(ModelEnum.QIANWEN37MAX));
        assertEquals(ModelType.CHAT, m.lastType.get());
        assertEquals("qianwen|qwen3.7-max", m.lastNameVersion.get());
    }

    @Test
    void chatModel_missingKey_throws() {
        DefaultModelProvider p = new DefaultModelProvider((t, n, v) -> null);
        assertThrows(NullPointerException.class, () -> p.chatModel(ModelEnum.QIANWEN37MAX));
    }

    @Test
    void embeddingModel_usesEmbeddingType() {
        CapturingModels m = new CapturingModels();
        m.byKey.put("embedding|qianwen|text-embedding-v4", "sk-e");
        DefaultModelProvider p = new DefaultModelProvider(m);

        assertNotNull(p.embeddingModel());
        assertEquals(ModelType.EMBEDDING, m.lastType.get());
        assertEquals("qianwen|text-embedding-v4", m.lastNameVersion.get());
    }

    @Test
    void embeddingModel_missingKey_returnsNull() {
        DefaultModelProvider p = new DefaultModelProvider((t, n, v) -> null);
        assertNull(p.embeddingModel());
    }

    @Test
    void embeddingModel_blankKey_returnsNull() {
        DefaultModelProvider p = new DefaultModelProvider((t, n, v) -> "   ");
        assertNull(p.embeddingModel());
    }

    @Test
    void embeddingStore_returnsSqliteStore() {
        DefaultModelProvider p = new DefaultModelProvider((t, n, v) -> null);
        assertNotNull(p.embeddingStore("pk"));
    }
}
