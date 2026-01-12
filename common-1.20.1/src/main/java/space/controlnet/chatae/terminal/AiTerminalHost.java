package space.controlnet.chatae.terminal;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import space.controlnet.chatae.core.terminal.AiTerminalData;

/**
 * Common contract for AI Terminal hosts (block or AE2 part).
 */
public interface AiTerminalHost extends IActionHost, ICraftingRequester {
    AiTerminalData.Ae2ListResult listItems(String query, boolean craftableOnly, int limit, @Nullable String pageToken);

    AiTerminalData.Ae2ListResult listCraftables(String query, int limit, @Nullable String pageToken);

    AiTerminalData.Ae2CraftSimulation simulateCraft(net.minecraft.world.entity.player.Player player, String itemId, long count);

    AiTerminalData.Ae2CraftRequest requestCraft(net.minecraft.world.entity.player.Player player, String itemId, long count, @Nullable String cpuName);

    AiTerminalData.Ae2JobStatus jobStatus(String jobId);

    AiTerminalData.Ae2JobStatus cancelJob(String jobId);

    BlockPos getHostPos();

    @Nullable
    Level getHostLevel();

    boolean isRemovedHost();
}
