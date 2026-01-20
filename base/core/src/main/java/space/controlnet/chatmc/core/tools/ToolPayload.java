package space.controlnet.chatmc.core.tools;

/**
 * Parsed tool message payload used for UI formatting.
 */
public record ToolPayload(String tool, String thinking, String argsJson, String outputJson, String error) {
}
