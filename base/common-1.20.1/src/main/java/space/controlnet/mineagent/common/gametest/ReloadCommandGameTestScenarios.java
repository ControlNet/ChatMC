package space.controlnet.mineagent.common.gametest;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.commands.MineAgentCommands;
import space.controlnet.mineagent.core.recipes.RecipeIndexManager;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class ReloadCommandGameTestScenarios {
    private ReloadCommandGameTestScenarios() {
    }

    public static void reloadCommandSmokeRebuildsRecipeIndex(net.minecraft.gametest.framework.GameTestHelper helper) {
        AgentGameTestSupport.initializeRuntime(helper);
        RecipeIndexManager recipeIndexManager = AgentGameTestSupport.recipeIndexManager();

        try {
            AgentGameTestSupport.rebuildReadySnapshot(recipeIndexManager, "task15/reload-command/setup/index-ready");
            AgentGameTestSupport.requireTrue(
                    "task15/reload-command/setup/index-ready-confirmed",
                    MineAgent.RECIPE_INDEX.isReady()
            );

            MinecraftServer server = AgentGameTestSupport.requireNonNull(
                    "task15/reload-command/setup/server",
                    helper.getLevel().getServer()
            );

            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            invokeRegisterMineAgentCommands(dispatcher);

            CompletableFuture<Void> previousFuture = MineAgent.RECIPE_INDEX.indexingFuture().orElse(null);
            int commandResult = executeReload(dispatcher, server);
            AgentGameTestSupport.requireEquals(
                    "task15/reload-command/command-result",
                    1,
                    commandResult
            );

            CompletableFuture<Void> reloadFuture = AgentGameTestSupport.awaitNewIndexingFuture(
                    "task15/reload-command/new-indexing-future",
                    previousFuture,
                    Duration.ofSeconds(5)
            );
            AgentGameTestSupport.awaitFuture(
                    "task15/reload-command/future-completes",
                    reloadFuture,
                    Duration.ofSeconds(30)
            );
            AgentGameTestSupport.requireTrue(
                    "task15/reload-command/index-ready-after-command",
                    MineAgent.RECIPE_INDEX.isReady()
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.rebuildReadySnapshot(recipeIndexManager, "task15/reload-command/cleanup/index-ready-reset");
            AgentGameTestSupport.resetRuntime();
        }
    }

    private static void invokeRegisterMineAgentCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            Method register = MineAgentCommands.class.getDeclaredMethod(
                    "register",
                    CommandDispatcher.class,
                    net.minecraft.commands.CommandBuildContext.class,
                    Commands.CommandSelection.class
            );
            register.setAccessible(true);
            register.invoke(null, dispatcher, null, Commands.CommandSelection.ALL);
        } catch (Exception exception) {
            throw new AssertionError("task15/reload-command/register-mineagent-command", AgentGameTestSupport.rootCause(exception));
        }
    }

    private static int executeReload(CommandDispatcher<CommandSourceStack> dispatcher, MinecraftServer server) {
        try {
            return dispatcher.execute(
                    "mineagent reload",
                    server.createCommandSourceStack().withPermission(2)
            );
        } catch (Exception exception) {
            throw new AssertionError("task15/reload-command/execute-reload", AgentGameTestSupport.rootCause(exception));
        }
    }
}
