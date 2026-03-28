package space.controlnet.chatmc.ae.core.tools;

/**
 * AE2 tool argument DTOs for tool calls.
 */
public final class AeToolArgs {
    private AeToolArgs() {
    }

    public record AeListArgs(String query, boolean craftableOnly, int limit, String pageToken) {
    }

    public record AeCraftArgs(String itemId, long count, String cpuName) {
    }

    public record AeJobArgs(String jobId) {
    }
}
