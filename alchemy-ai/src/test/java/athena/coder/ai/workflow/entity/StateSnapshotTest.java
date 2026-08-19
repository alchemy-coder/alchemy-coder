//package athena.coder.ai.workflow.entity;
//
//import athena.coder.entity.chat.ChatEnum;
//import athena.coder.entity.model.ModelEnum;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.function.BiConsumer;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//class StateSnapshotTest {
//
//    private static final ObjectMapper MAPPER = new ObjectMapper();
//
//    @Test
//    void toJson_nullOrEmpty_returnsEmptyObject() {
//        assertEquals("{}", StateSnapshot.toJson(null));
//        assertEquals("{}", StateSnapshot.toJson(Map.of()));
//    }
//
//    @Test
//    void toJson_persistsAllValueKinds() throws Exception {
//        Map<String, Object> m = new LinkedHashMap<>();
//        m.put("str", "hello");
//        m.put("num", 42);
//        m.put("flag", true);
//        m.put("model", ModelEnum.QIANWEN37MAX);
//        m.put("nothing", null);
//
//        JsonNode node = MAPPER.readTree(StateSnapshot.toJson(m));
//        assertEquals("hello", node.get("str").asText());
//        assertTrue(node.get("num").isNumber());
//        assertEquals(42, node.get("num").asInt());
//        assertTrue(node.get("flag").isBoolean());
//        assertTrue(node.get("flag").asBoolean());
//        assertEquals("QIANWEN37MAX", node.get("model").asText());
//        assertTrue(node.get("nothing").isNull());
//    }
//
//    @Test
//    void toJson_excludesBiConsumer_keepsOthers() throws Exception {
//        Map<String, Object> m = new LinkedHashMap<>();
//        m.put("plan", "do it");
//        m.put("bot", (BiConsumer<String, ChatEnum>) (msg, type) -> {});
//        m.put("next", "ROUTER");
//
//        JsonNode node = MAPPER.readTree(StateSnapshot.toJson(m));
//        assertFalse(node.has("bot"));
//        assertEquals("do it", node.get("plan").asText());
//        assertEquals("ROUTER", node.get("next").asText());
//    }
//
//    @Test
//    void toJson_collection_serializedAsArray() throws Exception {
//        Map<String, Object> m = Map.of("list", List.of("a", "b"));
//        JsonNode node = MAPPER.readTree(StateSnapshot.toJson(m));
//        assertTrue(node.get("list").isArray());
//        assertEquals("a", node.get("list").get(0).asText());
//        assertEquals("b", node.get("list").get(1).asText());
//    }
//
//    @Test
//    void toJson_longString_persistedInFull() throws Exception {
//        String longStr = "a".repeat(9000);
//        Map<String, Object> m = Map.of("big", longStr);
//        JsonNode node = MAPPER.readTree(StateSnapshot.toJson(m));
//        assertEquals(longStr, node.get("big").asText());
//    }
//}
