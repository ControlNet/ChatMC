package space.controlnet.mineagent.core.tools;

import java.util.List;

/**
 * Rendered tool output for UI display.
 */
public record ToolRender(
        String summaryKey,
        List<String> summaryArgs,
        List<String> lines,
        String error
) {
    public ToolRender {
        summaryArgs = summaryArgs == null ? List.of() : List.copyOf(summaryArgs);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
