package space.controlnet.mineagent.ae.common.part;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

public final class AiTerminalPartModelIdsRegressionTest {
    @Test
    void task18_aiTerminalPartModelIds_constantsRemainStable() {
        assertEquals("task18/ae-model-ids/off-namespace", "mineagentae", AiTerminalPartModelIds.MODEL_OFF.getNamespace());
        assertEquals("task18/ae-model-ids/off-path", "part/ai_terminal_off", AiTerminalPartModelIds.MODEL_OFF.getPath());
        assertEquals("task18/ae-model-ids/on-namespace", "mineagentae", AiTerminalPartModelIds.MODEL_ON.getNamespace());
        assertEquals("task18/ae-model-ids/on-path", "part/ai_terminal_on", AiTerminalPartModelIds.MODEL_ON.getPath());
    }

    @Test
    void task18_aiTerminalPartModelIds_privateConstructor_isAccessibleForCoverage() {
        try {
            Constructor<AiTerminalPartModelIds> constructor = AiTerminalPartModelIds.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (Exception exception) {
            throw new AssertionError("task18/ae-model-ids/private-constructor", exception);
        }
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
