package space.controlnet.chatae.core.tools;

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

    public record Ae2ListArgs(String query, boolean craftableOnly, int limit, String pageToken) {
    }

    public record Ae2CraftArgs(String itemId, long count, String cpuName) {
    }

    public record Ae2JobArgs(String jobId) {
    }
}
