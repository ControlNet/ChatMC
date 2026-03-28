package space.controlnet.chatmc.core.agent;

import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public final class ConversationHistoryBuilderRegressionTest {
    @Test
    void task16_historyBuilder_preservesEntireSessionOrder() {
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.SYSTEM, "session-opened", 1L),
                ChatMessage.user("how do I craft a chest?", 2L),
                new ChatMessage(ChatRole.TOOL,
                        "{\"thinking\":\"Need a recipe lookup\",\"tool\":\"mc.find_recipes\",\"args\":{\"query\":\"minecraft:chest\"},\"output\":{\"recipes\":[{\"result\":\"minecraft:chest\"}]}}",
                        3L),
                ChatMessage.assistant("Craft <item id=\"minecraft:chest\"> with 8 planks.", 4L)
        );

        assertEquals(
                "System: session-opened\n"
                        + "User: how do I craft a chest?\n"
                        + "Tool Result: {\"thinking\":\"Need a recipe lookup\",\"tool\":\"mc.find_recipes\",\"args\":{\"query\":\"minecraft:chest\"},\"output\":{\"recipes\":[{\"result\":\"minecraft:chest\"}]}}\n"
                        + "Assistant: Craft <item id=\"minecraft:chest\"> with 8 planks.",
                ConversationHistoryBuilder.build(messages)
        );
    }

    @Test
    void task16_llmConfigSurface_noLongerMentionsHistoryWindowSetting() {
        String parserSource = readSource("base/core/src/main/java/space/controlnet/chatmc/core/agent/LlmConfigParser.java");
        String configSource = readSource("base/core/src/main/java/space/controlnet/chatmc/core/agent/LlmConfig.java");

        assertFalse(parserSource.contains("maxHistoryMessages"));
        assertFalse(configSource.contains("maxHistoryMessages"));
    }

    private static String readSource(String path) {
        try {
            Path direct = Path.of(path);
            if (Files.exists(direct)) {
                return Files.readString(direct);
            }

            Path fromModule = Path.of("..").resolve("..").resolve(path).normalize();
            if (Files.exists(fromModule)) {
                return Files.readString(fromModule);
            }

            throw new AssertionError("read-source missing: " + path + " (checked " + direct + " and " + fromModule + ")");
        } catch (Exception exception) {
            throw new AssertionError("read-source failed: " + path, exception);
        }
    }
}
