package space.controlnet.chatmc.ae.core.terminal;

import space.controlnet.chatmc.core.terminal.TerminalContext;

public interface AeTerminalContext extends TerminalContext {
    AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, String pageToken);

    AiTerminalData.AeListResult listCraftables(String query, int limit, String pageToken);

    AiTerminalData.AeCraftSimulation simulateCraft(String itemId, long count);

    AiTerminalData.AeCraftRequest requestCraft(String itemId, long count, String cpuName);

    AiTerminalData.AeJobStatus jobStatus(String jobId);

    AiTerminalData.AeJobStatus cancelJob(String jobId);
}
