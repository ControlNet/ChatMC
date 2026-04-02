package space.controlnet.mineagent.common.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.controlnet.mineagent.core.agent.PromptId;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class PromptFileManagerRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void task24_promptFileManager_discoveryAndClasspathLookup_areDeterministic() {
        @SuppressWarnings("unchecked")
        Set<String> locales = (Set<String>) invokeStatic("discoverLocales", new Class<?>[]{net.minecraft.server.MinecraftServer.class}, new Object[]{null});
        assertTrue("task24/prompt/discover-locales-has-en-us", locales.contains("en_us"));

        @SuppressWarnings("unchecked")
        Set<String> classpathLocales = (Set<String>) invokeStatic("discoverLocalesFromClasspath", new Class<?>[]{});
        assertTrue("task24/prompt/classpath-locales-has-en-us", classpathLocales.contains("en_us"));

        @SuppressWarnings("unchecked")
        java.util.Map<String, String> enMap = (java.util.Map<String, String>) invokeStatic(
                "readLangMapFromClasspath",
                new Class<?>[]{String.class},
                "en_us"
        );
        assertTrue("task24/prompt/lang-map-present", enMap != null && !enMap.isEmpty());
        assertTrue("task24/prompt/lang-key-present", enMap.containsKey("prompt.mineagent.agent.reason"));

        Object missingMap = invokeStatic("readLangMapFromClasspath", new Class<?>[]{String.class}, "zz_zz");
        assertEquals("task24/prompt/lang-map-missing", null, missingMap);

        String defaultPrompt = (String) invokeStatic(
                "loadDefaultPrompt",
                new Class<?>[]{net.minecraft.server.MinecraftServer.class, PromptId.class, String.class},
                null,
                PromptId.AGENT_REASON,
                "zz_zz"
        );
        assertTrue("task24/prompt/default-fallback-nonblank", defaultPrompt != null && !defaultPrompt.isBlank());
    }

    @Test
    void task24_promptFileManager_filePrecedenceAndDefaultWrite_areStable() throws Exception {
        Path configDir = tempDir.resolve("mineagent-prompts");
        Files.createDirectories(configDir);

        Path defaultPath = configDir.resolve("agent.reason.en_us.default.prompt");
        Path override = configDir.resolve("agent.reason.prompt");
        Path localeOverride = configDir.resolve("agent.reason.en_us.prompt");

        Files.writeString(defaultPath, "default-content", StandardCharsets.UTF_8);
        Files.writeString(localeOverride, "locale-content", StandardCharsets.UTF_8);
        Files.writeString(override, "override-content", StandardCharsets.UTF_8);

        String chosen = (String) invokeStatic(
                "readFirstExisting",
                new Class<?>[]{Path.class, PromptId.class, String.class, Path.class, net.minecraft.server.MinecraftServer.class},
                configDir,
                PromptId.AGENT_REASON,
                "en_us",
                defaultPath,
                null
        );
        assertEquals("task24/prompt/override-precedence", "override-content", chosen);

        Files.delete(override);
        String localeChosen = (String) invokeStatic(
                "readFirstExisting",
                new Class<?>[]{Path.class, PromptId.class, String.class, Path.class, net.minecraft.server.MinecraftServer.class},
                configDir,
                PromptId.AGENT_REASON,
                "en_us",
                defaultPath,
                null
        );
        assertEquals("task24/prompt/locale-precedence", "locale-content", localeChosen);

        Files.delete(localeOverride);
        String defaultChosen = (String) invokeStatic(
                "readFirstExisting",
                new Class<?>[]{Path.class, PromptId.class, String.class, Path.class, net.minecraft.server.MinecraftServer.class},
                configDir,
                PromptId.AGENT_REASON,
                "en_us",
                defaultPath,
                null
        );
        assertEquals("task24/prompt/default-precedence", "default-content", defaultChosen);

        Path writtenDefault = configDir.resolve("agent.reason.zz_zz.default.prompt");
        invokeStatic(
                "ensureDefaultPrompt",
                new Class<?>[]{Path.class, PromptId.class, String.class, net.minecraft.server.MinecraftServer.class},
                writtenDefault,
                PromptId.AGENT_REASON,
                "zz_zz",
                null
        );
        assertTrue("task24/prompt/default-file-written", Files.exists(writtenDefault));
        assertTrue("task24/prompt/default-file-has-content", !Files.readString(writtenDefault, StandardCharsets.UTF_8).isBlank());
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = PromptFileManager.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception exception) {
            throw new AssertionError("task24/prompt/invoke-static/" + methodName, exception);
        }
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
