package space.controlnet.mineagent.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.menu.AiTerminalMenu;
import space.controlnet.mineagent.common.llm.McRuntimeManager;
import space.controlnet.mineagent.common.llm.PromptRuntime;
import space.controlnet.mineagent.common.tools.mcp.McpRuntimeManager;

public final class MineAgentCommands {
    private MineAgentCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register(MineAgentCommands::register);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mineagent")
                .then(Commands.literal("open")
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
                                    @Override
                                    public net.minecraft.network.chat.Component getDisplayName() {
                                        return net.minecraft.network.chat.Component.translatable("ui.mineagent.terminal.title");
                                    }

                                    @Override
                                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                                        return new AiTerminalMenu(id, inv, null, p.blockPosition(), null);
                                    }

                                    @Override
                                    public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
                                        buf.writeBlockPos(player.blockPosition());
                                        buf.writeBoolean(false);
                                    }
                                });
                                MineAgentNetwork.onTerminalOpened(serverPlayer);
                            }
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            PromptRuntime.reload(server);
                            McRuntimeManager.reload(server);
                            McpRuntimeManager.reload(server);
                            MineAgentNetwork.broadcastToolCatalogToViewers();
                            MineAgent.RECIPE_INDEX.rebuildAsync(server);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("MineAgent reloaded"), true);
                            return 1;
                        }));
        dispatcher.register(root);
    }
}
