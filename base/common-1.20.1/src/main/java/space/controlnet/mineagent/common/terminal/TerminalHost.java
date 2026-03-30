package space.controlnet.mineagent.common.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Base terminal host contract used by the UI/menu layer.
 */
public interface TerminalHost {
    BlockPos getHostPos();

    @Nullable
    Level getHostLevel();

    boolean isRemovedHost();
}
