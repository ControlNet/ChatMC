package space.controlnet.chatmc.common.commands;

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
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.menu.AiTerminalMenu;
import space.controlnet.chatmc.common.llm.McRuntimeManager;
import space.controlnet.chatmc.common.llm.PromptRuntime;

public final class ChatMCCommands {
    private ChatMCCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register(ChatMCCommands::register);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("chatmc")
                .then(Commands.literal("open")
                        .executes(ctx -> {
                            Player player = ctx.getSource().getPlayerOrException();
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
                                    @Override
                                    public net.minecraft.network.chat.Component getDisplayName() {
                                        return net.minecraft.network.chat.Component.translatable("ui.chatmc.terminal.title");
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
                                ChatMCNetwork.onTerminalOpened(serverPlayer);
                            }
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            PromptRuntime.reload(server);
                            McRuntimeManager.reload(server);
                            ChatMC.RECIPE_INDEX.rebuildAsync(server);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("ChatMC reloaded"), true);
                            return 1;
                        }));
        dispatcher.register(root);
    }
}
