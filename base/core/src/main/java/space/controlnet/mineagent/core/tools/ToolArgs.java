package space.controlnet.mineagent.core.tools;

/**
 * Tool argument DTOs for tool calls.
 */
public final class ToolArgs {
    private ToolArgs() {
    }

    public record McFindRecipesArgs(
            String itemId,
            String pageToken,
            int limit
    ) {
    }

    public record McFindUsageArgs(
            String itemId,
            String pageToken,
            int limit
    ) {
    }

    public record ResponseArgs(String message) {
    }
}
