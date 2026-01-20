package space.controlnet.chatmc.ae.common.terminal;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import space.controlnet.chatmc.ae.core.terminal.AiTerminalData;
import space.controlnet.chatmc.common.terminal.TerminalHost;

public interface AeTerminalHost extends TerminalHost, IActionHost, ICraftingRequester {
    AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, @Nullable String pageToken);

    AiTerminalData.AeListResult listCraftables(String query, int limit, @Nullable String pageToken);

    AiTerminalData.AeCraftSimulation simulateCraft(net.minecraft.world.entity.player.Player player, String itemId, long count);

    AiTerminalData.AeCraftRequest requestCraft(net.minecraft.world.entity.player.Player player, String itemId, long count, @Nullable String cpuName);

    AiTerminalData.AeJobStatus jobStatus(String jobId);

    AiTerminalData.AeJobStatus cancelJob(String jobId);

    BlockPos getHostPos();

    @Nullable
    Level getHostLevel();

    boolean isRemovedHost();
}
