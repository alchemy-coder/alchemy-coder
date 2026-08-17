package athena.coder.ai.assistant.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteCountEstimatorTest {

    private final ByteCountEstimator estimator = new ByteCountEstimator();

    @Test
    void text_nullOrEmpty_returnsZero() {
        assertEquals(0, estimator.estimateTokenCountInText(null));
        assertEquals(0, estimator.estimateTokenCountInText(""));
    }

    @Test
    void text_roundsUpToMinOne() {
        assertEquals(1, estimator.estimateTokenCountInText("abc"));      // 3/4=0 -> max(1,0)
        assertEquals(1, estimator.estimateTokenCountInText("abcd"));     // 4/4=1
        assertEquals(2, estimator.estimateTokenCountInText("abcdefgh")); // 8/4=2
    }

    @Test
    void message_usesTextContent() {
        assertEquals(0, estimator.estimateTokenCountInMessage(null));
        assertEquals(2, estimator.estimateTokenCountInMessage(new UserMessage("12345678")));
        assertEquals(1, estimator.estimateTokenCountInMessage(new SystemMessage("abcd")));
        assertEquals(1, estimator.estimateTokenCountInMessage(new AiMessage("efgh")));
    }

    @Test
    void messages_sum() {
        List<ChatMessage> messages = List.of(new SystemMessage("abcd"), new AiMessage("efgh"));
        assertEquals(2, estimator.estimateTokenCountInMessages(messages));
    }
}
