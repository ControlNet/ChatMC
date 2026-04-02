package space.controlnet.mineagent.ae.common.tools;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.AgentTool;

import java.util.List;

public final class AeToolProviderMetadataRenderRegressionTest {
    @Test
    void task25_aeToolProvider_specsExposeStableMetadataAndOptionalHelpers() {
        AeToolProvider provider = new AeToolProvider();
        List<AgentTool> tools = provider.specs();

        assertEquals("task25/spec-count", 6, tools.size());

        for (AgentTool tool : tools) {
            assertTrue("task25/tool-name-nonblank/" + tool.name(), tool.name() != null && !tool.name().isBlank());
            assertTrue("task25/tool-description-present/" + tool.name(), tool.description() != null);
            assertTrue("task25/tool-args-schema-present/" + tool.name(), tool.argsSchema() != null);
            assertTrue("task25/tool-args-description-present/" + tool.name(), tool.argsDescription() != null);
            assertTrue("task25/tool-result-schema-present/" + tool.name(), tool.resultSchema() != null);
            assertTrue("task25/tool-result-description-present/" + tool.name(), tool.resultDescription() != null);
            assertTrue("task25/tool-examples-present/" + tool.name(), tool.examples() != null);

            assertTrue("task25/tool-description-optional/" + tool.name(), tool.descriptionOptional().isPresent());
            assertTrue("task25/tool-args-schema-optional/" + tool.name(), tool.argsSchemaOptional().isPresent());
            assertTrue("task25/tool-result-schema-optional/" + tool.name(), tool.resultSchemaOptional().isPresent());
            assertTrue("task25/tool-args-lines-optional/" + tool.name(), !tool.argsDescriptionOptional().isEmpty());
            assertTrue("task25/tool-result-lines-optional/" + tool.name(), !tool.resultDescriptionOptional().isEmpty());
            assertTrue("task25/tool-examples-optional/" + tool.name(), !tool.examplesOptional().isEmpty());
        }
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
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
