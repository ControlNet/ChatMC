package space.controlnet.mineagent.common.tools.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.testing.TimeoutUtility;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.common.tools.ToolRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class HttpToolThreadAffinityRegressionTest {
    @Test
    void task2_httpCallingThreadAffinity_allowsExecutionWithoutServerThread() throws Exception {
        ensureMinecraftBootstrap();
        ToolRegistry.registerOrReplace("http", new HttpToolProvider());
        try {
            MineAgentNetwork.setServer(null);

            try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task2-thread-affinity")) {
                fixture.addFixedResponse("/status", HttpToolLoopbackFixture.FixedResponse.text(
                        200,
                        "ok",
                        StandardCharsets.UTF_8,
                        null
                ));

                Object context = newMcSessionContext(UUID.fromString("00000000-0000-0000-0000-000000002001"));
                AtomicReference<Object> outcomeRef = new AtomicReference<>();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();
                Thread worker = new Thread(() -> {
                    try {
                        outcomeRef.set(invokeMcSessionExecuteTool(
                                context,
                                newToolCall(HttpToolRequest.TOOL_NAME,
                                        "{\"url\":\"" + fixture.uri("/status") + "\"}"),
                                true
                        ));
                    } catch (Throwable throwable) {
                        errorRef.set(throwable);
                    }
                }, uniqueName("http-caller-worker"));

                worker.start();
                TimeoutUtility.awaitThreadCompletion("task2/http/calling-thread-affinity", worker, Duration.ofSeconds(2));
                if (errorRef.get() != null) {
                    throw new AssertionError("task2/http/calling-thread-affinity/unexpected-error", errorRef.get());
                }

                assertEquals("task2/http/calling-thread-affinity/registry-affinity",
                        ToolProvider.ExecutionAffinity.CALLING_THREAD,
                        ToolRegistry.getExecutionAffinity(HttpToolRequest.TOOL_NAME));
                assertOutcomeSuccess("task2/http/calling-thread-affinity/outcome", outcomeRef.get());

                JsonObject payload = requirePayloadJsonObject("task2/http/calling-thread-affinity/payload", outcomeRef.get());
                JsonObject request = payload.getAsJsonObject("request");
                JsonObject response = payload.getAsJsonObject("response");
                assertEquals("task2/http/calling-thread-affinity/payload-kind", "http_result", payload.get("kind").getAsString());
                assertEquals("task2/http/calling-thread-affinity/request-url", fixture.uri("/status").toString(),
                        request.get("url").getAsString());
                assertEquals("task2/http/calling-thread-affinity/request-method", "GET", request.get("method").getAsString());
                assertEquals("task2/http/calling-thread-affinity/response-status", 200,
                        response.get("statusCode").getAsInt());
                assertEquals("task2/http/calling-thread-affinity/response-body", "ok",
                        response.get("bodyText").getAsString());
                assertEquals("task2/http/calling-thread-affinity/truncated", false, payload.get("truncated").getAsBoolean());
            }
        } finally {
            ToolRegistry.unregister("http");
        }
    }

    private static Object newMcSessionContext(UUID playerId) {
        try {
            Class<?> contextClass = Class.forName("space.controlnet.mineagent.common.agent.McSessionContext");
            Constructor<?> constructor = contextClass.getDeclaredConstructor(UUID.class);
            constructor.setAccessible(true);
            return constructor.newInstance(playerId);
        } catch (Exception exception) {
            throw new AssertionError("task2/http/new-session-context", exception);
        }
    }

    private static Object newToolCall(String toolName, String argsJson) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.mineagent.core.tools.ToolCall");
            Constructor<?> constructor = toolCallClass.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(toolName, argsJson);
        } catch (Exception exception) {
            throw new AssertionError("task2/http/new-tool-call", exception);
        }
    }

    private static Object invokeMcSessionExecuteTool(Object context, Object call, boolean approved) {
        try {
            Class<?> toolCallClass = Class.forName("space.controlnet.mineagent.core.tools.ToolCall");
            Method method = context.getClass().getDeclaredMethod("executeTool", Optional.class, toolCallClass,
                    boolean.class);
            method.setAccessible(true);
            return method.invoke(context, Optional.empty(), call, approved);
        } catch (Exception exception) {
            throw new AssertionError("task2/http/invoke-session-execute", exception);
        }
    }

    private static void assertOutcomeSuccess(String assertionName, Object outcome) {
        Object nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        Object result = invokeZeroArg(nonNullOutcome, "result", assertionName + "/result");
        boolean success = (Boolean) invokeZeroArg(result, "success", assertionName + "/success");
        assertEquals(assertionName + "/must-be-success", true, success);
    }

    private static JsonObject requirePayloadJsonObject(String assertionName, Object outcome) {
        Object nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        Object result = invokeZeroArg(nonNullOutcome, "result", assertionName + "/result");
        String payloadJson = (String) invokeZeroArg(result, "payloadJson", assertionName + "/payload-json");
        Object parsed = JsonParser.parseString(requireNonNull(assertionName + "/payload-json-non-null", payloadJson));
        if (parsed instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        throw new AssertionError(assertionName + " -> payload must be a JSON object");
    }

    private static Object invokeZeroArg(Object target, String methodName, String assertionName) {
        Object nonNullTarget = requireNonNull(assertionName + "/target", target);
        try {
            Method method = nonNullTarget.getClass().getMethod(methodName);
            return method.invoke(nonNullTarget);
        } catch (Exception exception) {
            throw new AssertionError(assertionName + " -> invoke " + methodName + " failed", exception);
        }
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task2/http/bootstrap", exception);
        }
    }

    private static String uniqueName(String prefix) {
        return prefix + "-task2-" + UUID.randomUUID();
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
