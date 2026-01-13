package space.controlnet.chatae.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.common.llm.LlmRuntimeManager;
import space.controlnet.chatae.common.llm.PromptRuntime;

public final class ChatAECommands {
    private ChatAECommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register(ChatAECommands::register);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("chatae")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            PromptRuntime.reload(server);
                            LlmRuntimeManager.reload(server);
                            ChatAE.RECIPE_INDEX.rebuildAsync(server);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("ChatAE reloaded"), true);
                            return 1;
                        }));
        dispatcher.register(root);
    }
}
