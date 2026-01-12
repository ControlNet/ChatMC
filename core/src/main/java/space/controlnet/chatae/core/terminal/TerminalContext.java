package space.controlnet.chatae.core.terminal;

public interface TerminalContext {
    AiTerminalData.Ae2ListResult listItems(String query, boolean craftableOnly, int limit, String pageToken);

    AiTerminalData.Ae2ListResult listCraftables(String query, int limit, String pageToken);

    AiTerminalData.Ae2CraftSimulation simulateCraft(String itemId, long count);

    AiTerminalData.Ae2CraftRequest requestCraft(String itemId, long count, String cpuName);

    AiTerminalData.Ae2JobStatus jobStatus(String jobId);

    AiTerminalData.Ae2JobStatus cancelJob(String jobId);
}
