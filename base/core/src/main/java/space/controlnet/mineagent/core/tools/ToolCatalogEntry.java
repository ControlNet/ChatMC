package space.controlnet.mineagent.core.tools;

public record ToolCatalogEntry(
        String providerId,
        String groupId,
        String toolName,
        String description
) {
    public ToolCatalogEntry {
        providerId = providerId == null ? "" : providerId;
        groupId = groupId == null ? "" : groupId;
        toolName = toolName == null ? "" : toolName;
        description = description == null ? "" : description;
    }
}
