package space.controlnet.chatmc.fabric.gametest;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.commands.ChatMCCommands;
import space.controlnet.chatmc.common.menu.AiTerminalMenu;
import space.controlnet.chatmc.common.recipes.RecipeIndexReloadListener;
import space.controlnet.chatmc.common.session.ChatMCSessionsSavedData;
import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.common.terminal.TerminalContextResolver;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.proposal.ApprovalDecision;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.recipes.RecipeIndexManager;
import space.controlnet.chatmc.core.recipes.RecipeIndexSnapshot;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionMetadata;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.SessionVisibility;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolPayload;
import space.controlnet.chatmc.core.tools.ToolRender;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class ChatMCFabricRuntimeGameTests {
    private static final int MAX_TOOL_ARGS_JSON_LENGTH = 65_536;

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID VIEWER_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID VIEWER_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String OWNER_NAME = "viewer_owner";
    private static final String VIEWER_A_NAME = "viewer_a";
    private static final String VIEWER_B_NAME = "viewer_b";

    private static final UUID THREAD_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID TIMEOUT_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final String THREAD_PLAYER_NAME = "thread_confine";
    private static final String TIMEOUT_PLAYER_NAME = "timeout_confine";
    private static final UUID AGENT_RESPONSE_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000181");
    private static final UUID AGENT_TOOL_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000182");
    private static final UUID AGENT_PARSE_ERROR_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000183");
    private static final String AGENT_RESPONSE_PLAYER_NAME = "agent_response";
    private static final String AGENT_TOOL_PLAYER_NAME = "agent_tool";
    private static final String AGENT_PARSE_ERROR_PLAYER_NAME = "agent_parse_error";
    private static final long FORCED_TIMEOUT_DELAY_MS = 31_000L;
    private static final long FORCED_FAILURE_DELAY_MS = 200L;
    private static final int TIMEOUT_WORKER_START_DELAY_TICKS = 40;
    private static final int TIMEOUT_ASSERT_DELAY_TICKS = 720;

    private ChatMCFabricRuntimeGameTests() {
    }

    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task11/command-menu/setup/server",
                helper.getLevel().getServer()
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task11/command-menu/setup/player",
                server,
                player
        );

        try {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            invokeRegisterChatMcCommands(dispatcher);
            executeChatMcOpen(dispatcher, player);

            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task11/command-menu/open/active-session -> missing active session"));
            ChatMCFabricGameTestSupport.requireEquals(
                    "task11/command-menu/open/viewer-map",
                    sessionId,
                    ChatMCFabricGameTestSupport.sessionByViewer().get(player.getUUID())
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task11/command-menu/open/viewers-for-session",
                    Set.of(player.getUUID()),
                    ChatMCFabricGameTestSupport.viewersForSession(sessionId)
            );

            AiTerminalMenu menu = new AiTerminalMenu(37, player.getInventory(), null, player.blockPosition(), null);
            menu.removed(player);

            ChatMCFabricGameTestSupport.requireTrue(
                    "task11/command-menu/close/viewer-map-cleared",
                    !ChatMCFabricGameTestSupport.sessionByViewer().containsKey(player.getUUID())
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task11/command-menu/close/session-viewers-cleared",
                    ChatMCFabricGameTestSupport.viewersForSession(sessionId).isEmpty()
            );

            ChatMCNetwork.onTerminalOpened(player);
            UUID reopenedSessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task11/command-menu/reopen/active-session -> missing active session"));
            ChatMCFabricGameTestSupport.requireEquals(
                    "task11/command-menu/reopen/preserves-session",
                    sessionId,
                    reopenedSessionId
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task11/command-menu/reopen/viewer-map",
                    reopenedSessionId,
                    ChatMCFabricGameTestSupport.sessionByViewer().get(player.getUUID())
            );

            helper.succeed();
        } finally {
            ChatMCFabricGameTestSupport.resetRuntime();
        }
    }

    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task11/deleted-session-queued-append/setup/server",
                helper.getLevel().getServer()
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task11/deleted-session-queued-append/setup/player",
                server,
                player
        );

        UUID sessionId = ChatMCNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
                .metadata()
                .sessionId();

        CountDownLatch appendQueued = new CountDownLatch(1);
        AtomicReference<Throwable> workerError = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                ChatMCNetwork.appendMessageAndBroadcast(
                        sessionId,
                        new ChatMessage(ChatRole.TOOL, "queued-tool-payload", System.currentTimeMillis())
                );
            } catch (Throwable throwable) {
                workerError.set(throwable);
            } finally {
                appendQueued.countDown();
            }
        }, uniqueName("task11-deleted-session-append-worker"));
        worker.start();

        try {
            ChatMCFabricGameTestSupport.requireTrue(
                    "task11/deleted-session-queued-append/worker-enqueued-runnable",
                    appendQueued.await(2, TimeUnit.SECONDS)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("task11/deleted-session-queued-append/await-worker", exception);
        }
        ChatMCFabricGameTestSupport.requireNull(
                "task11/deleted-session-queued-append/worker-error",
                workerError.get()
        );

        ChatMCNetwork.SESSIONS.delete(sessionId);

        helper.runAfterDelay(2, () -> {
            try {
                ChatMCFabricGameTestSupport.requireTrue(
                        "task11/deleted-session-queued-append/worker-completed",
                        !worker.isAlive()
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task11/deleted-session-queued-append/session-still-deleted",
                        ChatMCNetwork.SESSIONS.get(sessionId).isEmpty()
                );
                helper.succeed();
            } finally {
                ChatMCFabricGameTestSupport.resetRuntime();
            }
        });
    }

    public static void agentSystemReliabilityAcrossRealLoopPaths(GameTestHelper helper) {
        runDirectResponseReliabilityStage(helper);
    }

    public static void chatPacketDirectResponseExecutesRealAgentLoop(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task18/direct/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                AGENT_RESPONSE_PLAYER_ID,
                AGENT_RESPONSE_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task18/direct/setup/player",
                server,
                player
        );
        ChatMCFabricGameTestSupport.rebuildReadySnapshot(
                ChatMCFabricGameTestSupport.recipeIndexManager(),
                "task18/direct/setup/index-ready"
        );

        ScriptedChatModel model = new ScriptedChatModel(
                "{\"thinking\":\"Answer directly\",\"tool\":\"response\",\"args\":{\"message\":\"Use eight planks around the perimeter.\"}}"
        );
        ChatModel previousModel = ChatMCFabricGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/direct/setup/active-session -> missing active session"));

            ChatMCFabricGameTestSupport.invokeHandleChatPacket(
                    player,
                    "How do I craft a chest?",
                    "en_us",
                    ""
            );

            helper.runAfterDelay(20, () -> {
                try {
                    SessionSnapshot snapshot = ChatMCFabricGameTestSupport.requireSnapshot(
                            "task18/direct/final-snapshot",
                            sessionId
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/final-state",
                            SessionState.DONE,
                            snapshot.state()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/message-count",
                            2,
                            snapshot.messages().size()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/user-role",
                            ChatRole.USER,
                            snapshot.messages().get(0).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/user-message",
                            "How do I craft a chest?",
                            snapshot.messages().get(0).text()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/assistant-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(1).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/assistant-message",
                            "Use eight planks around the perimeter.",
                            snapshot.messages().get(1).text()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/model-request-count",
                            1,
                            model.requestCount()
                    );
                    ChatMCFabricGameTestSupport.requireContains(
                            "task18/direct/prompt-includes-user-message",
                            model.firstPrompt(),
                            "How do I craft a chest?"
                    );
                    helper.succeed();
                } finally {
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                }
            });
        } catch (Throwable throwable) {
            ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
            ChatMCFabricGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    public static void chatPacketToolLoopExecutesToolAndResponds(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task18/tool/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                AGENT_TOOL_PLAYER_ID,
                AGENT_TOOL_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task18/tool/setup/player",
                server,
                player
        );
        ChatMCFabricGameTestSupport.rebuildReadySnapshot(
                ChatMCFabricGameTestSupport.recipeIndexManager(),
                "task18/tool/setup/index-ready"
        );

        String providerId = uniqueName("task18-tool-provider");
        String toolName = uniqueName("task18-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.IMMEDIATE_SUCCESS);
        ToolRegistry.register(providerId, provider);

        ScriptedChatModel model = new ScriptedChatModel(
                "{\"thinking\":\"Need to inspect inventory\",\"tool\":\"" + toolName + "\",\"args\":{\"slot\":2}}",
                "{\"tool\":\"response\",\"args\":{\"message\":\"Inventory lookup finished.\"}}"
        );
        ChatModel previousModel = ChatMCFabricGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/tool/setup/active-session -> missing active session"));

            ChatMCFabricGameTestSupport.invokeHandleChatPacket(
                    player,
                    "Check my inventory",
                    "en_us",
                    ""
            );

            helper.runAfterDelay(20, () -> {
                try {
                    SessionSnapshot snapshot = ChatMCFabricGameTestSupport.requireSnapshot(
                            "task18/tool/final-snapshot",
                            sessionId
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/final-state",
                            SessionState.DONE,
                            snapshot.state()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/model-request-count",
                            2,
                            model.requestCount()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/provider-execute-count",
                            1,
                            provider.executeCount()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/message-count",
                            3,
                            snapshot.messages().size()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/user-role",
                            ChatRole.USER,
                            snapshot.messages().get(0).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/tool-role",
                            ChatRole.TOOL,
                            snapshot.messages().get(1).role()
                    );
                    ChatMCFabricGameTestSupport.requireContains(
                            "task18/tool/tool-payload-has-tool-name",
                            snapshot.messages().get(1).text(),
                            toolName
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/assistant-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(2).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/assistant-message",
                            "Inventory lookup finished.",
                            snapshot.messages().get(2).text()
                    );
                    helper.succeed();
                } finally {
                    ToolRegistry.unregister(providerId);
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                }
            });
        } catch (Throwable throwable) {
            ToolRegistry.unregister(providerId);
            ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
            ChatMCFabricGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    public static void chatPacketInvalidModelOutputFailsGracefully(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task18/error/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                AGENT_PARSE_ERROR_PLAYER_ID,
                AGENT_PARSE_ERROR_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task18/error/setup/player",
                server,
                player
        );
        ChatMCFabricGameTestSupport.rebuildReadySnapshot(
                ChatMCFabricGameTestSupport.recipeIndexManager(),
                "task18/error/setup/index-ready"
        );

        ScriptedChatModel model = new ScriptedChatModel("not-json-at-all");
        ChatModel previousModel = ChatMCFabricGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/error/setup/active-session -> missing active session"));

            ChatMCFabricGameTestSupport.invokeHandleChatPacket(
                    player,
                    "Do something unreliable",
                    "en_us",
                    ""
            );

            helper.runAfterDelay(20, () -> {
                try {
                    SessionSnapshot snapshot = ChatMCFabricGameTestSupport.requireSnapshot(
                            "task18/error/final-snapshot",
                            sessionId
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/final-state",
                            SessionState.FAILED,
                            snapshot.state()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/model-request-count",
                            1,
                            model.requestCount()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/last-error",
                            Optional.of("LLM failed to produce a decision"),
                            snapshot.lastError()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/message-count",
                            2,
                            snapshot.messages().size()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/error-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(1).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/error-message",
                            "Error: LLM failed to produce a decision",
                            snapshot.messages().get(1).text()
                    );
                    helper.succeed();
                } finally {
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                }
            });
        } catch (Throwable throwable) {
            ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
            ChatMCFabricGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    public static void proposalBindingUnavailableApprovalFailsDeterministically(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task6/binding-unavailable/setup/server",
                helper.getLevel().getServer()
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task6/binding-unavailable/setup/player",
                server,
                player
        );

        UUID sessionId = ChatMCNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
                .metadata()
                .sessionId();

        TerminalBinding binding = new TerminalBinding(
                helper.getLevel().dimension().location().toString(),
                0,
                64,
                0,
                Optional.of("NORTH")
        );

        Proposal proposal = new Proposal(
                "proposal-binding-unavailable",
                RiskLevel.SAFE_MUTATION,
                "binding unavailable proposal",
                new ToolCall("mc.binding_probe", "{\"probe\":true}"),
                System.currentTimeMillis(),
                ProposalDetails.empty()
        );

        Map<TerminalBinding, space.controlnet.chatmc.core.terminal.TerminalContext> liveBindings = new HashMap<>();
        liveBindings.put(binding, new space.controlnet.chatmc.core.terminal.TerminalContext() {
        });

        AtomicReference<TerminalContextResolver> resolverRef = ChatMCFabricGameTestSupport.resolverRef();
        TerminalContextResolver previousResolver = resolverRef.getAndSet(new MapBackedResolver(liveBindings));

        try {
            ChatMCFabricGameTestSupport.requireTrue(
                    "task6/binding-unavailable/start-thinking",
                    ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            ChatMCFabricGameTestSupport.invokeHandleAgentLoopResult(
                    player,
                    AgentLoopResult.withProposal(proposal, 1),
                    sessionId,
                    binding,
                    "en_us"
            );

            SessionSnapshot waiting = ChatMCFabricGameTestSupport.requireSnapshot(
                    "task6/binding-unavailable/waiting",
                    sessionId
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/wait-state",
                    SessionState.WAIT_APPROVAL,
                    waiting.state()
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/wait-proposal-id",
                    Optional.of(proposal.id()),
                    waiting.pendingProposal().map(Proposal::id)
            );

            liveBindings.remove(binding);
            ChatMCFabricGameTestSupport.requireTrue(
                    "task6/binding-unavailable/binding-invalidated",
                    !liveBindings.containsKey(binding)
            );

            ChatMCFabricGameTestSupport.invokeHandleApprovalDecision(
                    player,
                    new C2SApprovalDecisionPacket(
                            ChatMCFabricGameTestSupport.protocolVersion(),
                            proposal.id(),
                            ApprovalDecision.APPROVE
                    )
            );

            SessionSnapshot failed = ChatMCFabricGameTestSupport.requireSnapshot(
                    "task6/binding-unavailable/failed",
                    sessionId
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/final-state",
                    SessionState.FAILED,
                    failed.state()
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/final-error",
                    Optional.of("bound terminal unavailable"),
                    failed.lastError()
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task6/binding-unavailable/clears-proposal",
                    failed.pendingProposal().isEmpty()
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task6/binding-unavailable/clears-binding",
                    failed.proposalBinding().isEmpty()
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/decision-count",
                    1,
                    failed.decisions().size()
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/decision-approve-recorded",
                    ApprovalDecision.APPROVE,
                    failed.decisions().get(0).decision()
            );

            ChatMessage lastMessage = failed.messages().get(failed.messages().size() - 1);
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/error-role",
                    ChatRole.ASSISTANT,
                    lastMessage.role()
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task6/binding-unavailable/error-message",
                    "Error: bound terminal unavailable",
                    lastMessage.text()
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task6/binding-unavailable/no-tool-side-effects",
                    failed.messages().stream().noneMatch(message -> message.role() == ChatRole.TOOL)
            );

            helper.succeed();
        } finally {
            resolverRef.set(previousResolver);
            ChatMCFabricGameTestSupport.resetRuntime();
        }
    }

    public static void indexingGateRecoveryAcrossReload(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);
        RecipeIndexManager recipeIndexManager = ChatMCFabricGameTestSupport.recipeIndexManager();

        try {
            ChatMCFabricGameTestSupport.rebuildReadySnapshot(recipeIndexManager, "task7/setup/index-ready-baseline");
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/setup/index-ready-confirmed",
                    ChatMC.RECIPE_INDEX.isReady()
            );

            ServerPlayer player = FakePlayer.get(helper.getLevel());
            player.setPos(0.5D, 2.0D, 0.5D);
            ChatMCNetwork.onTerminalOpened(player);

            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task7/setup/active-session -> missing active session"));

            SessionSnapshot initial = ChatMCFabricGameTestSupport.requireSnapshot("task7/setup/initial", sessionId);
            ChatMCFabricGameTestSupport.requireEquals(
                    "task7/setup/initial-state",
                    SessionState.IDLE,
                    initial.state()
            );

            BlockingRebuildBarrier barrier = new BlockingRebuildBarrier();
            CompletableFuture<Void> pendingRebuild = recipeIndexManager.rebuildAsync(() -> {
                barrier.signalStarted();
                barrier.awaitRelease("task7/rebuild/pending-await-release", Duration.ofSeconds(3));
                return emptySnapshot();
            });

            barrier.awaitStarted("task7/rebuild/pending-started", Duration.ofSeconds(3));
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/rebuild/index-not-ready-while-pending",
                    !ChatMC.RECIPE_INDEX.isReady()
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/rebuild/pending-future-not-done",
                    !pendingRebuild.isDone()
            );

            ChatMCNetwork.onTerminalOpened(player);
            ChatMCNetwork.sendSessionSnapshot(player);

            SessionSnapshot gated = ChatMCFabricGameTestSupport.requireSnapshot("task7/gate/state", sessionId);
            ChatMCFabricGameTestSupport.requireEquals(
                    "task7/gate/enters-indexing",
                    SessionState.INDEXING,
                    gated.state()
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/gate/request-path-blocked-while-indexing",
                    !ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            barrier.release();
            ChatMCFabricGameTestSupport.awaitFuture(
                    "task7/rebuild/pending-future-completes",
                    pendingRebuild,
                    Duration.ofSeconds(8)
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/rebuild/index-ready-after-release",
                    ChatMC.RECIPE_INDEX.isReady()
            );

            ChatMCNetwork.sendSessionSnapshot(player);
            SessionSnapshot recovered = ChatMCFabricGameTestSupport.requireSnapshot("task7/recovery/state", sessionId);
            ChatMCFabricGameTestSupport.requireEquals(
                    "task7/recovery/returns-to-idle",
                    SessionState.IDLE,
                    recovered.state()
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/recovery/request-path-reopened",
                    ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            ChatMCNetwork.SESSIONS.setState(sessionId, SessionState.DONE);

            MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                    "task7/reload/server-present",
                    helper.getLevel().getServer()
            );
            RecipeIndexReloadListener reloadListener = new RecipeIndexReloadListener(ChatMC.RECIPE_INDEX, () -> server);
            CompletableFuture<Void> previousFuture = ChatMC.RECIPE_INDEX.indexingFuture().orElse(null);

            CompletableFuture<Void> reloadDispatch = reloadListener.reload(
                    new ImmediatePreparationBarrier(),
                    server.getResourceManager(),
                    InactiveProfiler.INSTANCE,
                    InactiveProfiler.INSTANCE,
                    Runnable::run,
                    Runnable::run
            );
            ChatMCFabricGameTestSupport.awaitFuture(
                    "task7/reload/reload-dispatch-completes",
                    reloadDispatch,
                    Duration.ofSeconds(2)
            );

            CompletableFuture<Void> reloadRebuildFuture = ChatMCFabricGameTestSupport.awaitNewIndexingFuture(
                    "task7/reload/new-indexing-future",
                    previousFuture,
                    Duration.ofSeconds(5)
            );
            ChatMCFabricGameTestSupport.awaitFuture(
                    "task7/reload/rebuild-future-completes",
                    reloadRebuildFuture,
                    Duration.ofSeconds(30)
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/reload/index-ready-after-completion",
                    ChatMC.RECIPE_INDEX.isReady()
            );

            ChatMCNetwork.onTerminalOpened(player);
            ChatMCNetwork.sendSessionSnapshot(player);
            SessionSnapshot secondCycle = ChatMCFabricGameTestSupport.requireSnapshot(
                    "task7/non-sticky/second-cycle-state",
                    sessionId
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/non-sticky/second-cycle-does-not-stick-indexing",
                    secondCycle.state() != SessionState.INDEXING
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task7/non-sticky/second-cycle-request-path-open",
                    ChatMCNetwork.SESSIONS.tryStartThinking(sessionId)
            );

            helper.succeed();
        } finally {
            ChatMCFabricGameTestSupport.rebuildReadySnapshot(recipeIndexManager, "task7/cleanup/index-ready-reset");
            ChatMCFabricGameTestSupport.resetRuntime();
        }
    }

    public static void multiViewerSnapshotConsistencyUnderChurn(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task8/setup/server-present",
                helper.getLevel().getServer()
        );

        ServerPlayer owner = ChatMCFabricGameTestSupport.createServerPlayer(helper, OWNER_ID, OWNER_NAME);
        ServerPlayer viewerA = ChatMCFabricGameTestSupport.createServerPlayer(helper, VIEWER_A_ID, VIEWER_A_NAME);
        ServerPlayer viewerB = ChatMCFabricGameTestSupport.createServerPlayer(helper, VIEWER_B_ID, VIEWER_B_NAME);

        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task8/setup/player-lookup/owner",
                server,
                owner
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task8/setup/player-lookup/viewer-a",
                server,
                viewerA
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task8/setup/player-lookup/viewer-b",
                server,
                viewerB
        );

        Map<UUID, List<Integer>> deliveredSequences = new HashMap<>();
        deliveredSequences.put(owner.getUUID(), new ArrayList<>());
        deliveredSequences.put(viewerA.getUUID(), new ArrayList<>());
        deliveredSequences.put(viewerB.getUUID(), new ArrayList<>());

        try {
            ChatMCNetwork.onTerminalOpened(owner);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(owner.getUUID())
                    .orElseThrow(() -> new AssertionError("task8/setup/owner-active-session -> missing active session"));
            ChatMCNetwork.SESSIONS.setVisibility(sessionId, SessionVisibility.PUBLIC, Optional.empty());

            ChatMCFabricGameTestSupport.invokeHandleOpenSession(viewerA, sessionId);
            requireSessionSubscriptions(
                    "task8/setup/viewer-a-open",
                    sessionId,
                    Set.of(owner.getUUID(), viewerA.getUUID())
            );

            recordBroadcastStep(
                    "task8/update/seq-1",
                    sessionId,
                    1,
                    Set.of(owner.getUUID(), viewerA.getUUID()),
                    deliveredSequences
            );

            ChatMCFabricGameTestSupport.invokeHandleOpenSession(viewerB, sessionId);
            requireSessionSubscriptions(
                    "task8/setup/viewer-b-open",
                    sessionId,
                    Set.of(owner.getUUID(), viewerA.getUUID(), viewerB.getUUID())
            );

            recordBroadcastStep(
                    "task8/update/seq-2",
                    sessionId,
                    2,
                    Set.of(owner.getUUID(), viewerA.getUUID(), viewerB.getUUID()),
                    deliveredSequences
            );

            ChatMCNetwork.onTerminalClosed(viewerA);
            requireSessionSubscriptions(
                    "task8/churn/viewer-a-closed",
                    sessionId,
                    Set.of(owner.getUUID(), viewerB.getUUID())
            );
            int viewerACountAfterFirstClose = deliveredSequences.get(viewerA.getUUID()).size();

            recordBroadcastStep(
                    "task8/update/seq-3",
                    sessionId,
                    3,
                    Set.of(owner.getUUID(), viewerB.getUUID()),
                    deliveredSequences
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task8/churn/viewer-a-no-updates-after-close",
                    viewerACountAfterFirstClose,
                    deliveredSequences.get(viewerA.getUUID()).size()
            );

            ChatMCFabricGameTestSupport.invokeHandleOpenSession(viewerA, sessionId);
            requireSessionSubscriptions(
                    "task8/churn/viewer-a-reopened",
                    sessionId,
                    Set.of(owner.getUUID(), viewerA.getUUID(), viewerB.getUUID())
            );

            ChatMCNetwork.onTerminalClosed(viewerB);
            requireSessionSubscriptions(
                    "task8/churn/viewer-b-closed",
                    sessionId,
                    Set.of(owner.getUUID(), viewerA.getUUID())
            );
            int viewerBCountAfterClose = deliveredSequences.get(viewerB.getUUID()).size();

            recordBroadcastStep(
                    "task8/update/seq-4",
                    sessionId,
                    4,
                    Set.of(owner.getUUID(), viewerA.getUUID()),
                    deliveredSequences
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task8/churn/viewer-b-no-updates-after-close",
                    viewerBCountAfterClose,
                    deliveredSequences.get(viewerB.getUUID()).size()
            );

            ChatMCNetwork.onTerminalClosed(viewerA);
            requireSessionSubscriptions(
                    "task8/churn/viewer-a-closed-second-time",
                    sessionId,
                    Set.of(owner.getUUID())
            );
            int viewerACountAfterSecondClose = deliveredSequences.get(viewerA.getUUID()).size();

            recordBroadcastStep(
                    "task8/update/seq-5",
                    sessionId,
                    5,
                    Set.of(owner.getUUID()),
                    deliveredSequences
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task8/churn/viewer-a-no-updates-after-second-close",
                    viewerACountAfterSecondClose,
                    deliveredSequences.get(viewerA.getUUID()).size()
            );

            ChatMCFabricGameTestSupport.requireEquals(
                    "task8/final/owner-sequence",
                    List.of(1, 2, 3, 4, 5),
                    deliveredSequences.get(owner.getUUID())
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task8/final/viewer-a-sequence",
                    List.of(1, 2, 4),
                    deliveredSequences.get(viewerA.getUUID())
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task8/final/viewer-b-sequence",
                    List.of(2, 3),
                    deliveredSequences.get(viewerB.getUUID())
            );

            requireMonotonicAndUnique("task8/final/owner-monotonic", deliveredSequences.get(owner.getUUID()));
            requireMonotonicAndUnique("task8/final/viewer-a-monotonic", deliveredSequences.get(viewerA.getUUID()));
            requireMonotonicAndUnique("task8/final/viewer-b-monotonic", deliveredSequences.get(viewerB.getUUID()));

            helper.succeed();
        } finally {
            ChatMCFabricGameTestSupport.resetRuntime();
        }
    }

    public static void asyncToolInvocationMarshalsToServerThread(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task9/server-thread/setup/server",
                helper.getLevel().getServer()
        );

        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                THREAD_PLAYER_ID,
                THREAD_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task9/server-thread/setup/player",
                server,
                player
        );

        String providerId = uniqueName("task9-thread-provider");
        String toolName = uniqueName("task9-thread-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.IMMEDIATE_SUCCESS);
        ToolRegistry.register(providerId, provider);

        Object sessionContext = ChatMCFabricGameTestSupport.newMcSessionContext(player.getUUID());
        Thread expectedServerThread = Thread.currentThread();

        AtomicReference<ToolOutcome> outcomeRef = new AtomicReference<>();
        AtomicReference<Throwable> workerErrorRef = new AtomicReference<>();

        Thread worker = new Thread(
                () -> invokeExecuteTool(sessionContext, toolName, outcomeRef, workerErrorRef),
                uniqueName("task9-thread-worker")
        );
        worker.start();

        helper.runAfterDelay(20, () -> {
            try {
                ChatMCFabricGameTestSupport.requireTrue(
                        "task9/server-thread/worker-completed",
                        !worker.isAlive()
                );
                ChatMCFabricGameTestSupport.requireNull(
                        "task9/server-thread/worker-error",
                        workerErrorRef.get()
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task9/server-thread/execute-count",
                        1,
                        provider.executeCount()
                );

                Thread observedProviderThread = ChatMCFabricGameTestSupport.requireNonNull(
                        "task9/server-thread/provider-thread",
                        provider.firstExecutionThread()
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task9/server-thread/provider-thread-is-server-thread",
                        expectedServerThread,
                        observedProviderThread
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task9/server-thread/provider-thread-not-worker",
                        observedProviderThread != worker
                );

                assertOutcomeSuccess("task9/server-thread/outcome", outcomeRef.get());
                helper.succeed();
            } finally {
                ToolRegistry.unregister(providerId);
                ChatMCFabricGameTestSupport.resetRuntime();
            }
        });
    }

    public static void timeoutAndFailureContractsRemainStableUnderForcedDelay(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task9/timeout/setup/server",
                helper.getLevel().getServer()
        );

        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                TIMEOUT_PLAYER_ID,
                TIMEOUT_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task9/timeout/setup/player",
                server,
                player
        );

        String providerId = uniqueName("task9-timeout-provider");
        String toolName = uniqueName("task9-timeout-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.FORCED_TIMEOUT_DELAY);
        ToolRegistry.register(providerId, provider);

        Object sessionContext = ChatMCFabricGameTestSupport.newMcSessionContext(player.getUUID());
        Thread expectedServerThread = Thread.currentThread();

        AtomicReference<ToolOutcome> timeoutOutcomeRef = new AtomicReference<>();
        AtomicReference<Throwable> timeoutErrorRef = new AtomicReference<>();
        AtomicLong timeoutElapsedMs = new AtomicLong(-1L);

        Thread timeoutWorker = new Thread(
                () -> invokeExecuteToolWithTiming(
                        sessionContext,
                        toolName,
                        timeoutOutcomeRef,
                        timeoutErrorRef,
                        timeoutElapsedMs
                ),
                uniqueName("task9-timeout-worker")
        );
        helper.runAfterDelay(TIMEOUT_WORKER_START_DELAY_TICKS, timeoutWorker::start);

        AtomicReference<ToolOutcome> failureOutcomeRef = new AtomicReference<>();
        AtomicReference<Throwable> failureErrorRef = new AtomicReference<>();
        AtomicLong failureElapsedMs = new AtomicLong(-1L);
        AtomicReference<Thread> failureWorkerRef = new AtomicReference<>();

        helper.runAfterDelay(TIMEOUT_ASSERT_DELAY_TICKS, () -> {
            try {
                ChatMCFabricGameTestSupport.requireTrue(
                        "task9/timeout/worker-completed",
                        !timeoutWorker.isAlive()
                );
                ChatMCFabricGameTestSupport.requireNull(
                        "task9/timeout/worker-error",
                        timeoutErrorRef.get()
                );

                assertOutcomeError(
                        "task9/timeout/outcome-contract",
                        timeoutOutcomeRef.get(),
                        "tool_timeout",
                        "tool execution timeout"
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task9/timeout/elapsed-at-least-29s",
                        timeoutElapsedMs.get() >= 29_000L
                );
                ChatMCFabricGameTestSupport.requireEquals(
                        "task9/timeout/provider-thread-is-server-thread",
                        expectedServerThread,
                        ChatMCFabricGameTestSupport.requireNonNull(
                                "task9/timeout/provider-first-thread",
                                provider.firstExecutionThread()
                        )
                );

                provider.setMode(ToolExecutionMode.DELAYED_FAILURE);

                Thread failureWorker = new Thread(
                        () -> invokeExecuteToolWithTiming(
                                sessionContext,
                                toolName,
                                failureOutcomeRef,
                                failureErrorRef,
                                failureElapsedMs
                        ),
                        uniqueName("task9-failure-worker")
                );
                failureWorkerRef.set(failureWorker);
                failureWorker.start();

                helper.runAfterDelay(20, () -> {
                    try {
                        Thread currentFailureWorker = ChatMCFabricGameTestSupport.requireNonNull(
                                "task9/failure/worker-created",
                                failureWorkerRef.get()
                        );
                        ChatMCFabricGameTestSupport.requireTrue(
                                "task9/failure/worker-completed",
                                !currentFailureWorker.isAlive()
                        );
                        ChatMCFabricGameTestSupport.requireNull(
                                "task9/failure/worker-error",
                                failureErrorRef.get()
                        );

                        assertOutcomeError(
                                "task9/failure/outcome-contract",
                                failureOutcomeRef.get(),
                                "tool_execution_failed",
                                "tool execution failed"
                        );
                        ChatMCFabricGameTestSupport.requireTrue(
                                "task9/failure/elapsed-honors-forced-delay",
                                failureElapsedMs.get() >= FORCED_FAILURE_DELAY_MS
                        );
                        ChatMCFabricGameTestSupport.requireEquals(
                                "task9/failure/execute-count",
                                2,
                                provider.executeCount()
                        );
                        ChatMCFabricGameTestSupport.requireEquals(
                                "task9/failure/provider-thread-is-server-thread",
                                expectedServerThread,
                                ChatMCFabricGameTestSupport.requireNonNull(
                                        "task9/failure/provider-last-thread",
                                        provider.lastExecutionThread()
                                )
                        );

                        helper.succeed();
                    } finally {
                        ToolRegistry.unregister(providerId);
                        ChatMCFabricGameTestSupport.resetRuntime();
                    }
                });
            } catch (Throwable throwable) {
                ToolRegistry.unregister(providerId);
                ChatMCFabricGameTestSupport.resetRuntime();
                throw throwable;
            }
        });
    }

    public static void toolArgsBoundaryEndToEnd(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        try {
            String ascii65535 = repeat('a', MAX_TOOL_ARGS_JSON_LENGTH - 1);
            String ascii65536 = repeat('a', MAX_TOOL_ARGS_JSON_LENGTH);
            String ascii65537 = repeat('a', MAX_TOOL_ARGS_JSON_LENGTH + 1);

            assertAcceptsAcrossParseNetworkPersistence(
                    "task10/ascii/65535",
                    "mc.task10.boundary.ascii.65535",
                    ascii65535,
                    MAX_TOOL_ARGS_JSON_LENGTH - 1
            );
            assertAcceptsAcrossParseNetworkPersistence(
                    "task10/ascii/65536",
                    "mc.task10.boundary.ascii.65536",
                    ascii65536,
                    MAX_TOOL_ARGS_JSON_LENGTH
            );
            assertRejectsAt65537WithSignals(
                    "task10/ascii/65537",
                    "mc.task10.boundary.ascii.65537",
                    ascii65537
            );

            List<UtfEdgeCase> utfCorpus = List.of(
                    new UtfEdgeCase("emoji-surrogate-pair", "😀"),
                    new UtfEdgeCase("cjk-three-byte", "界"),
                    new UtfEdgeCase("combining-mark", "e\u0301")
            );

            for (UtfEdgeCase utfEdgeCase : utfCorpus) {
                String atBoundary = buildPayloadWithSeedAtLength(
                        "task10/utf/" + utfEdgeCase.id() + "/build-at-65536",
                        utfEdgeCase.seed(),
                        MAX_TOOL_ARGS_JSON_LENGTH
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task10/utf/" + utfEdgeCase.id() + "/at-65536-contains-seed",
                        atBoundary.contains(utfEdgeCase.seed())
                );
                ChatMCFabricGameTestSupport.requireTrue(
                        "task10/utf/" + utfEdgeCase.id() + "/at-65536-utf8-byte-expansion",
                        atBoundary.getBytes(StandardCharsets.UTF_8).length > atBoundary.length()
                );

                assertAcceptsAcrossParseNetworkPersistence(
                        "task10/utf/" + utfEdgeCase.id() + "/65536",
                        "mc.task10.boundary.utf." + utfEdgeCase.id() + ".65536",
                        atBoundary,
                        MAX_TOOL_ARGS_JSON_LENGTH
                );

                String oversize = atBoundary + "a";
                ChatMCFabricGameTestSupport.requireEquals(
                        "task10/utf/" + utfEdgeCase.id() + "/65537-length",
                        MAX_TOOL_ARGS_JSON_LENGTH + 1,
                        oversize.length()
                );
                assertRejectsAt65537WithSignals(
                        "task10/utf/" + utfEdgeCase.id() + "/65537",
                        "mc.task10.boundary.utf." + utfEdgeCase.id() + ".65537",
                        oversize
                );
            }

            helper.succeed();
        } finally {
            ChatMCFabricGameTestSupport.resetRuntime();
        }
    }

    public static void sessionVisibilityDeleteRebindUnderRuntimeConditions(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task13/setup/server",
                helper.getLevel().getServer()
        );

        ServerPlayer owner = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                "visibility_owner"
        );
        ServerPlayer viewer = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                UUID.fromString("00000000-0000-0000-0000-000000000112"),
                "visibility_viewer"
        );

        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task13/setup/owner-lookup",
                server,
                owner
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task13/setup/viewer-lookup",
                server,
                viewer
        );

        try {
            ChatMCNetwork.onTerminalOpened(owner);
            UUID sharedSessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(owner.getUUID())
                    .orElseThrow(() -> new AssertionError("task13/setup/shared-session -> missing owner active session"));
            UUID backupSessionId = ChatMCNetwork.SESSIONS.create(owner.getUUID(), owner.getGameProfile().getName())
                    .metadata()
                    .sessionId();
            ChatMCFabricGameTestSupport.invokeHandleOpenSession(owner, sharedSessionId);

            ChatMCNetwork.SESSIONS.setVisibility(sharedSessionId, SessionVisibility.PUBLIC, Optional.empty());
            ChatMCFabricGameTestSupport.invokeHandleOpenSession(viewer, sharedSessionId);

            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/open/viewer-subscribed",
                    sharedSessionId,
                    ChatMCFabricGameTestSupport.sessionByViewer().get(viewer.getUUID())
            );

            ChatMCNetwork.SESSIONS.setVisibility(sharedSessionId, SessionVisibility.PRIVATE, Optional.empty());
            ChatMCFabricGameTestSupport.invokeBroadcastSessionSnapshot(sharedSessionId);

            ChatMCFabricGameTestSupport.requireTrue(
                    "task13/privacy/viewer-removed-after-private",
                    !ChatMCFabricGameTestSupport.sessionByViewer().containsKey(viewer.getUUID())
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/privacy/remaining-viewers",
                    Set.of(owner.getUUID()),
                    ChatMCFabricGameTestSupport.viewersForSession(sharedSessionId)
            );

            ChatMCNetwork.SESSIONS.setVisibility(sharedSessionId, SessionVisibility.PUBLIC, Optional.empty());
            ChatMCFabricGameTestSupport.invokeHandleOpenSession(viewer, sharedSessionId);
            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/reopen/viewer-resubscribed",
                    sharedSessionId,
                    ChatMCFabricGameTestSupport.sessionByViewer().get(viewer.getUUID())
            );

            ChatMCFabricGameTestSupport.invokeHandleDeleteSession(owner, sharedSessionId);

            ChatMCFabricGameTestSupport.requireTrue(
                    "task13/delete/session-removed",
                    ChatMCNetwork.SESSIONS.get(sharedSessionId).isEmpty()
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/delete/owner-rebound-to-backup",
                    backupSessionId,
                    ChatMCNetwork.SESSIONS.getActiveSessionId(owner.getUUID())
                            .orElseThrow(() -> new AssertionError("task13/delete/owner-active -> missing"))
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/delete/owner-viewer-map-rebound",
                    backupSessionId,
                    ChatMCFabricGameTestSupport.sessionByViewer().get(owner.getUUID())
            );

            UUID viewerReboundSessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(viewer.getUUID())
                    .orElseThrow(() -> new AssertionError("task13/delete/viewer-active -> missing"));
            ChatMCFabricGameTestSupport.requireTrue(
                    "task13/delete/viewer-gets-fresh-session",
                    !viewerReboundSessionId.equals(sharedSessionId)
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/delete/viewer-map-rebound",
                    viewerReboundSessionId,
                    ChatMCFabricGameTestSupport.sessionByViewer().get(viewer.getUUID())
            );
            ChatMCFabricGameTestSupport.requireEquals(
                    "task13/delete/viewer-fresh-session-viewers",
                    Set.of(viewer.getUUID()),
                    ChatMCFabricGameTestSupport.viewersForSession(viewerReboundSessionId)
            );
            ChatMCFabricGameTestSupport.requireTrue(
                    "task13/delete/deleted-session-viewers-cleared",
                    ChatMCFabricGameTestSupport.viewersForSession(sharedSessionId).isEmpty()
            );

            helper.succeed();
        } finally {
            ChatMCFabricGameTestSupport.resetRuntime();
        }
    }

    static RecipeIndexSnapshot emptySnapshot() {
        return new RecipeIndexSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static void invokeRegisterChatMcCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            Method register = ChatMCCommands.class.getDeclaredMethod(
                    "register",
                    CommandDispatcher.class,
                    net.minecraft.commands.CommandBuildContext.class,
                    Commands.CommandSelection.class
            );
            register.setAccessible(true);
            register.invoke(null, dispatcher, null, Commands.CommandSelection.ALL);
        } catch (Exception exception) {
            throw new AssertionError("task11/command-menu/register-chatmc-command", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static void executeChatMcOpen(CommandDispatcher<CommandSourceStack> dispatcher, ServerPlayer player) {
        try {
            dispatcher.execute("chatmc open", player.createCommandSourceStack());
        } catch (Exception exception) {
            throw new AssertionError("task11/command-menu/execute-chatmc-open", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static void runDirectResponseReliabilityStage(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task18/direct/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                AGENT_RESPONSE_PLAYER_ID,
                AGENT_RESPONSE_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task18/direct/setup/player",
                server,
                player
        );
        ChatMCFabricGameTestSupport.rebuildReadySnapshot(
                ChatMCFabricGameTestSupport.recipeIndexManager(),
                "task18/direct/setup/index-ready"
        );

        ScriptedChatModel model = new ScriptedChatModel(
                "{\"thinking\":\"Answer directly\",\"tool\":\"response\",\"args\":{\"message\":\"Use eight planks around the perimeter.\"}}"
        );
        ChatModel previousModel = ChatMCFabricGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/direct/setup/active-session -> missing active session"));

            ChatMCFabricGameTestSupport.invokeHandleChatPacket(player, "How do I craft a chest?", "en_us", "");

            helper.runAfterDelay(20, () -> {
                try {
                    SessionSnapshot snapshot = ChatMCFabricGameTestSupport.requireSnapshot(
                            "task18/direct/final-snapshot",
                            sessionId
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/final-state",
                            SessionState.DONE,
                            snapshot.state()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/message-count",
                            2,
                            snapshot.messages().size()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/user-role",
                            ChatRole.USER,
                            snapshot.messages().get(0).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/user-message",
                            "How do I craft a chest?",
                            snapshot.messages().get(0).text()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/assistant-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(1).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/assistant-message",
                            "Use eight planks around the perimeter.",
                            snapshot.messages().get(1).text()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/direct/model-request-count",
                            1,
                            model.requestCount()
                    );
                    ChatMCFabricGameTestSupport.requireContains(
                            "task18/direct/prompt-includes-user-message",
                            model.firstPrompt(),
                            "How do I craft a chest?"
                    );

                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                    runToolLoopReliabilityStage(helper);
                } catch (Throwable throwable) {
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                    throw throwable;
                }
            });
        } catch (Throwable throwable) {
            ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
            ChatMCFabricGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void runToolLoopReliabilityStage(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task18/tool/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                AGENT_TOOL_PLAYER_ID,
                AGENT_TOOL_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task18/tool/setup/player",
                server,
                player
        );
        ChatMCFabricGameTestSupport.rebuildReadySnapshot(
                ChatMCFabricGameTestSupport.recipeIndexManager(),
                "task18/tool/setup/index-ready"
        );

        String providerId = uniqueName("task18-tool-provider");
        String toolName = uniqueName("task18-tool");
        ControlledToolProvider provider = new ControlledToolProvider(toolName, ToolExecutionMode.IMMEDIATE_SUCCESS);
        ToolRegistry.register(providerId, provider);

        ScriptedChatModel model = new ScriptedChatModel(
                "{\"thinking\":\"Need to inspect inventory\",\"tool\":\"" + toolName + "\",\"args\":{\"slot\":2}}",
                "{\"tool\":\"response\",\"args\":{\"message\":\"Inventory lookup finished.\"}}"
        );
        ChatModel previousModel = ChatMCFabricGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/tool/setup/active-session -> missing active session"));

            ChatMCFabricGameTestSupport.invokeHandleChatPacket(player, "Check my inventory", "en_us", "");

            helper.runAfterDelay(20, () -> {
                try {
                    SessionSnapshot snapshot = ChatMCFabricGameTestSupport.requireSnapshot(
                            "task18/tool/final-snapshot",
                            sessionId
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/final-state",
                            SessionState.DONE,
                            snapshot.state()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/model-request-count",
                            2,
                            model.requestCount()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/provider-execute-count",
                            1,
                            provider.executeCount()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/message-count",
                            3,
                            snapshot.messages().size()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/user-role",
                            ChatRole.USER,
                            snapshot.messages().get(0).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/tool-role",
                            ChatRole.TOOL,
                            snapshot.messages().get(1).role()
                    );
                    ChatMCFabricGameTestSupport.requireContains(
                            "task18/tool/tool-payload-has-tool-name",
                            snapshot.messages().get(1).text(),
                            toolName
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/assistant-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(2).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/tool/assistant-message",
                            "Inventory lookup finished.",
                            snapshot.messages().get(2).text()
                    );

                    ToolRegistry.unregister(providerId);
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                    runInvalidModelReliabilityStage(helper);
                } catch (Throwable throwable) {
                    ToolRegistry.unregister(providerId);
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                    throw throwable;
                }
            });
        } catch (Throwable throwable) {
            ToolRegistry.unregister(providerId);
            ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
            ChatMCFabricGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void runInvalidModelReliabilityStage(GameTestHelper helper) {
        ChatMCFabricGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = ChatMCFabricGameTestSupport.requireNonNull(
                "task18/error/setup/server",
                helper.getLevel().getServer()
        );
        ServerPlayer player = ChatMCFabricGameTestSupport.createServerPlayer(
                helper,
                AGENT_PARSE_ERROR_PLAYER_ID,
                AGENT_PARSE_ERROR_PLAYER_NAME
        );
        ChatMCFabricGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task18/error/setup/player",
                server,
                player
        );
        ChatMCFabricGameTestSupport.rebuildReadySnapshot(
                ChatMCFabricGameTestSupport.recipeIndexManager(),
                "task18/error/setup/index-ready"
        );

        ScriptedChatModel model = new ScriptedChatModel("not-json-at-all");
        ChatModel previousModel = ChatMCFabricGameTestSupport.installChatModel(model);

        try {
            ChatMCNetwork.onTerminalOpened(player);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task18/error/setup/active-session -> missing active session"));

            ChatMCFabricGameTestSupport.invokeHandleChatPacket(player, "Do something unreliable", "en_us", "");

            helper.runAfterDelay(20, () -> {
                try {
                    SessionSnapshot snapshot = ChatMCFabricGameTestSupport.requireSnapshot(
                            "task18/error/final-snapshot",
                            sessionId
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/final-state",
                            SessionState.FAILED,
                            snapshot.state()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/model-request-count",
                            1,
                            model.requestCount()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/last-error",
                            Optional.of("LLM failed to produce a decision"),
                            snapshot.lastError()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/message-count",
                            2,
                            snapshot.messages().size()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/error-role",
                            ChatRole.ASSISTANT,
                            snapshot.messages().get(1).role()
                    );
                    ChatMCFabricGameTestSupport.requireEquals(
                            "task18/error/error-message",
                            "Error: LLM failed to produce a decision",
                            snapshot.messages().get(1).text()
                    );
                    helper.succeed();
                } finally {
                    ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
                    ChatMCFabricGameTestSupport.resetRuntime();
                }
            });
        } catch (Throwable throwable) {
            ChatMCFabricGameTestSupport.restoreChatModel(previousModel);
            ChatMCFabricGameTestSupport.resetRuntime();
            throw throwable;
        }
    }

    private static void recordBroadcastStep(
            String assertionPrefix,
            UUID sessionId,
            int sequence,
            Set<UUID> expectedRecipients,
            Map<UUID, List<Integer>> deliveredSequences
    ) {
        Set<UUID> recipientsBefore = ChatMCFabricGameTestSupport.viewersForSession(sessionId);
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/recipients-before",
                expectedRecipients,
                recipientsBefore
        );

        ChatMCNetwork.SESSIONS.appendMessage(
                sessionId,
                new ChatMessage(ChatRole.USER, "task8-seq-" + sequence, sequence)
        );
        ChatMCFabricGameTestSupport.invokeBroadcastSessionSnapshot(sessionId);

        Set<UUID> recipientsAfter = ChatMCFabricGameTestSupport.viewersForSession(sessionId);
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/recipients-after",
                expectedRecipients,
                recipientsAfter
        );

        for (UUID recipientId : recipientsBefore) {
            deliveredSequences.computeIfAbsent(recipientId, ignored -> new ArrayList<>()).add(sequence);
        }
    }

    private static void requireSessionSubscriptions(String assertionName, UUID sessionId, Set<UUID> expectedViewers) {
        Set<UUID> viewers = ChatMCFabricGameTestSupport.viewersForSession(sessionId);
        ChatMCFabricGameTestSupport.requireEquals(
                assertionName + "/viewers-for-session",
                expectedViewers,
                viewers
        );

        Map<UUID, UUID> byViewer = ChatMCFabricGameTestSupport.sessionByViewer();
        for (UUID viewerId : expectedViewers) {
            ChatMCFabricGameTestSupport.requireEquals(
                    assertionName + "/viewer-mapping/" + viewerId,
                    sessionId,
                    byViewer.get(viewerId)
            );
        }
    }

    private static void requireMonotonicAndUnique(String assertionName, List<Integer> sequence) {
        Set<Integer> unique = new HashSet<>(sequence);
        ChatMCFabricGameTestSupport.requireEquals(
                assertionName + "/unique-size",
                sequence.size(),
                unique.size()
        );
        for (int index = 1; index < sequence.size(); index++) {
            int previous = sequence.get(index - 1);
            int current = sequence.get(index);
            ChatMCFabricGameTestSupport.requireTrue(
                    assertionName + "/strictly-increasing/" + index,
                    current > previous
            );
        }
    }

    private static void invokeExecuteTool(
            Object sessionContext,
            String toolName,
            AtomicReference<ToolOutcome> outcomeRef,
            AtomicReference<Throwable> errorRef
    ) {
        try {
            outcomeRef.set(
                    ChatMCFabricGameTestSupport.invokeMcSessionExecuteTool(
                            sessionContext,
                            new ToolCall(toolName, "{}"),
                            true
                    )
            );
        } catch (Throwable throwable) {
            errorRef.set(ChatMCFabricGameTestSupport.rootCause(throwable));
        }
    }

    private static void invokeExecuteToolWithTiming(
            Object sessionContext,
            String toolName,
            AtomicReference<ToolOutcome> outcomeRef,
            AtomicReference<Throwable> errorRef,
            AtomicLong elapsedMsRef
    ) {
        long startedAt = System.nanoTime();
        try {
            outcomeRef.set(
                    ChatMCFabricGameTestSupport.invokeMcSessionExecuteTool(
                            sessionContext,
                            new ToolCall(toolName, "{}"),
                            true
                    )
            );
        } catch (Throwable throwable) {
            errorRef.set(ChatMCFabricGameTestSupport.rootCause(throwable));
        } finally {
            elapsedMsRef.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        }
    }

    private static void assertOutcomeSuccess(String assertionName, ToolOutcome outcome) {
        ToolOutcome nonNullOutcome = ChatMCFabricGameTestSupport.requireNonNull(assertionName + "/outcome", outcome);
        ToolResult result = ChatMCFabricGameTestSupport.requireNonNull(assertionName + "/result", nonNullOutcome.result());
        ChatMCFabricGameTestSupport.requireTrue(assertionName + "/success", result.success());
    }

    private static void assertOutcomeError(
            String assertionName,
            ToolOutcome outcome,
            String expectedCode,
            String expectedMessage
    ) {
        ToolOutcome nonNullOutcome = ChatMCFabricGameTestSupport.requireNonNull(assertionName + "/outcome", outcome);
        ToolResult result = ChatMCFabricGameTestSupport.requireNonNull(assertionName + "/result", nonNullOutcome.result());
        ChatMCFabricGameTestSupport.requireTrue(assertionName + "/must-be-failure", !result.success());
        ChatMCFabricGameTestSupport.requireEquals(
                assertionName + "/error-code",
                expectedCode,
                ChatMCFabricGameTestSupport.requireNonNull(assertionName + "/error", result.error()).code()
        );
        ChatMCFabricGameTestSupport.requireEquals(
                assertionName + "/error-message",
                expectedMessage,
                ChatMCFabricGameTestSupport.requireNonNull(
                        assertionName + "/error-message-present",
                        result.error()
                ).message()
        );
    }

    private static void assertAcceptsAcrossParseNetworkPersistence(
            String assertionPrefix,
            String toolName,
            String argsJson,
            int expectedLength
    ) {
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/expected-length",
                expectedLength,
                argsJson.length()
        );

        invokeParseBoundaryValidate(toolName, argsJson);

        SessionSnapshot snapshot = snapshotWithProposal(assertionPrefix + "/snapshot", toolName, argsJson);
        SessionSnapshot networkRoundTrip = invokeNetworkRoundTrip(snapshot);
        String networkArgs = requireProposalArgs(assertionPrefix + "/network-roundtrip", networkRoundTrip);
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/network-roundtrip/args-length",
                expectedLength,
                networkArgs.length()
        );
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/network-roundtrip/args-equals",
                argsJson,
                networkArgs
        );

        PersistedSessions persistedRoundTrip = invokePersistenceRoundTrip(snapshot);
        ChatMCNetwork.SESSIONS.loadFromSave(persistedRoundTrip);

        SessionSnapshot loaded = ChatMCNetwork.SESSIONS.get(snapshot.metadata().sessionId())
                .orElseThrow(() -> new AssertionError(assertionPrefix + "/persist-roundtrip -> missing session"));
        String persistedArgs = requireProposalArgs(assertionPrefix + "/persist-roundtrip", loaded);
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/persist-roundtrip/args-length",
                expectedLength,
                persistedArgs.length()
        );
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/persist-roundtrip/args-equals",
                argsJson,
                persistedArgs
        );
    }

    private static void assertRejectsAt65537WithSignals(
            String assertionPrefix,
            String toolName,
            String oversizeArgs
    ) {
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/oversize-length",
                MAX_TOOL_ARGS_JSON_LENGTH + 1,
                oversizeArgs.length()
        );

        Throwable parseThrown = assertThrows(
                assertionPrefix + "/parse-rejects",
                () -> invokeParseBoundaryValidate(toolName, oversizeArgs)
        );
        ChatMCFabricGameTestSupport.requireEquals(
                assertionPrefix + "/parse-message",
                "PARSE_BOUNDARY_TOOL_ARGS_TOO_LARGE: tool='" + toolName + "', argsJson.length=65537, max=65536",
                parseThrown.getMessage()
        );

        SessionSnapshot oversizeSnapshot = snapshotWithProposal(assertionPrefix + "/oversize", toolName, oversizeArgs);

        Throwable networkThrown = assertThrows(
                assertionPrefix + "/network-encode-rejects",
                () -> invokeNetworkWriteSnapshot(oversizeSnapshot)
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/network-encode-signal",
                networkThrown.getMessage(),
                "NETWORK_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/network-encode-phase",
                networkThrown.getMessage(),
                "phase='encode'"
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/network-encode-length",
                networkThrown.getMessage(),
                "argsJson.length=65537"
        );

        Throwable persistWriteThrown = assertThrows(
                assertionPrefix + "/persist-write-rejects",
                () -> invokeWriteSession(oversizeSnapshot)
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/persist-write-signal",
                persistWriteThrown.getMessage(),
                "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/persist-write-phase",
                persistWriteThrown.getMessage(),
                "phase='write'"
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/persist-write-length",
                persistWriteThrown.getMessage(),
                "argsJson.length=65537"
        );

        SessionSnapshot atBoundary = snapshotWithProposal(
                assertionPrefix + "/persist-read-setup",
                toolName,
                oversizeArgs.substring(0, MAX_TOOL_ARGS_JSON_LENGTH)
        );
        CompoundTag encoded = invokeWriteSession(atBoundary);
        encoded.getCompound("proposal").putString("argsJson", oversizeArgs);

        Throwable persistReadThrown = assertThrows(
                assertionPrefix + "/persist-read-rejects",
                () -> invokeReadSession(encoded)
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/persist-read-signal",
                persistReadThrown.getMessage(),
                "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/persist-read-phase",
                persistReadThrown.getMessage(),
                "phase='read'"
        );
        ChatMCFabricGameTestSupport.requireContains(
                assertionPrefix + "/persist-read-length",
                persistReadThrown.getMessage(),
                "argsJson.length=65537"
        );
    }

    private static SessionSnapshot invokeNetworkRoundTrip(SessionSnapshot snapshot) {
        FriendlyByteBuf encoded = invokeNetworkWriteSnapshot(snapshot);
        return invokeNetworkReadSnapshot(encoded);
    }

    private static FriendlyByteBuf invokeNetworkWriteSnapshot(SessionSnapshot snapshot) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("writeSnapshot", FriendlyByteBuf.class, SessionSnapshot.class);
            method.setAccessible(true);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            method.invoke(null, buffer, snapshot);
            return buffer;
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-network-write-snapshot", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static SessionSnapshot invokeNetworkReadSnapshot(FriendlyByteBuf buf) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("readSnapshot", FriendlyByteBuf.class);
            method.setAccessible(true);
            return (SessionSnapshot) method.invoke(null, buf);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-network-read-snapshot", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static PersistedSessions invokePersistenceRoundTrip(SessionSnapshot snapshot) {
        PersistedSessions persisted = new PersistedSessions(
                1,
                List.of(snapshot),
                Map.of(snapshot.metadata().ownerId(), snapshot.metadata().sessionId())
        );
        ChatMCSessionsSavedData savedData = new ChatMCSessionsSavedData();
        savedData.setData(persisted);
        CompoundTag root = new CompoundTag();
        savedData.save(root);
        return ChatMCSessionsSavedData.load(root).data();
    }

    private static CompoundTag invokeWriteSession(SessionSnapshot snapshot) {
        try {
            Method method = ChatMCSessionsSavedData.class.getDeclaredMethod("writeSession", SessionSnapshot.class);
            method.setAccessible(true);
            return (CompoundTag) method.invoke(null, snapshot);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-persist-write-session", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static SessionSnapshot invokeReadSession(CompoundTag root) {
        try {
            Method method = ChatMCSessionsSavedData.class.getDeclaredMethod("readSession", CompoundTag.class);
            method.setAccessible(true);
            return (SessionSnapshot) method.invoke(null, root);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-persist-read-session", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static void invokeParseBoundaryValidate(String toolName, String argsJson) {
        try {
            Class<?> boundaryClass = Class.forName("space.controlnet.chatmc.core.agent.ToolCallArgsParseBoundary");
            Method method = boundaryClass.getDeclaredMethod("validate", String.class, String.class);
            method.setAccessible(true);
            method.invoke(null, toolName, argsJson);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-parse-boundary-validate", ChatMCFabricGameTestSupport.rootCause(exception));
        }
    }

    private static SessionSnapshot snapshotWithProposal(String idSeed, String toolName, String argsJson) {
        UUID sessionId = UUID.nameUUIDFromBytes((idSeed + "/session").getBytes(StandardCharsets.UTF_8));
        UUID ownerId = UUID.nameUUIDFromBytes((idSeed + "/owner").getBytes(StandardCharsets.UTF_8));
        SessionMetadata metadata = new SessionMetadata(
                sessionId,
                ownerId,
                "task10-owner",
                SessionVisibility.PRIVATE,
                Optional.empty(),
                "task10-boundary",
                1_700_000_000_000L,
                1_700_000_000_001L
        );

        Proposal proposal = new Proposal(
                "task10-proposal-" + sessionId,
                RiskLevel.SAFE_MUTATION,
                "task10 boundary proposal",
                new ToolCall(toolName, argsJson),
                1_700_000_000_010L,
                new ProposalDetails("action", "minecraft:stick", 1L, List.of(), "task10")
        );

        return new SessionSnapshot(
                metadata,
                List.of(),
                SessionState.WAIT_APPROVAL,
                Optional.of(proposal),
                Optional.of(new TerminalBinding("minecraft:overworld", 0, 64, 0, Optional.of("north"))),
                List.of(),
                Optional.empty()
        );
    }

    private static String requireProposalArgs(String assertionName, SessionSnapshot snapshot) {
        return snapshot.pendingProposal()
                .map(Proposal::toolCall)
                .map(ToolCall::argsJson)
                .orElseThrow(() -> new AssertionError(assertionName + " -> expected proposal args"));
    }

    private static String buildPayloadWithSeedAtLength(String assertionName, String seed, int targetLength) {
        ChatMCFabricGameTestSupport.requireTrue(
                assertionName + "/seed-not-empty",
                seed != null && !seed.isEmpty()
        );
        ChatMCFabricGameTestSupport.requireTrue(
                assertionName + "/seed-fits-target",
                seed.length() <= targetLength
        );

        StringBuilder builder = new StringBuilder(targetLength);
        int fullRepeats = targetLength / seed.length();
        for (int index = 0; index < fullRepeats; index++) {
            builder.append(seed);
        }

        int remaining = targetLength - builder.length();
        if (remaining > 0) {
            builder.append("a".repeat(remaining));
        }

        String payload = builder.toString();
        ChatMCFabricGameTestSupport.requireEquals(
                assertionName + "/target-length",
                targetLength,
                payload.length()
        );
        return payload;
    }

    private static Throwable assertThrows(String assertionName, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (throwable instanceof AssertionError assertionError && assertionError.getCause() != null) {
                return assertionError.getCause();
            }
            return throwable;
        }
        throw new AssertionError(assertionName + " -> expected exception");
    }

    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static final class MapBackedResolver implements TerminalContextResolver {
        private final Map<TerminalBinding, space.controlnet.chatmc.core.terminal.TerminalContext> liveBindings;

        private MapBackedResolver(Map<TerminalBinding, space.controlnet.chatmc.core.terminal.TerminalContext> liveBindings) {
            this.liveBindings = liveBindings;
        }

        @Override
        public Optional<space.controlnet.chatmc.core.terminal.TerminalContext> fromPlayer(ServerPlayer player) {
            return Optional.empty();
        }

        @Override
        public Optional<space.controlnet.chatmc.core.terminal.TerminalContext> fromPlayerAtBinding(
                ServerPlayer player,
                TerminalBinding binding
        ) {
            return Optional.ofNullable(liveBindings.get(binding));
        }
    }

    private static final class BlockingRebuildBarrier {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void signalStarted() {
            started.countDown();
        }

        private void awaitStarted(String assertionName, Duration timeout) {
            ChatMCFabricGameTestSupport.awaitLatch(assertionName, started, timeout);
        }

        private void awaitRelease(String assertionName, Duration timeout) {
            ChatMCFabricGameTestSupport.awaitLatch(assertionName, release, timeout);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class ImmediatePreparationBarrier implements PreparableReloadListener.PreparationBarrier {
        @Override
        public <T> CompletableFuture<T> wait(T value) {
            return CompletableFuture.completedFuture(value);
        }
    }

    private enum ToolExecutionMode {
        IMMEDIATE_SUCCESS,
        FORCED_TIMEOUT_DELAY,
        DELAYED_FAILURE
    }

    private static final class ControlledToolProvider implements ToolProvider {
        private final AgentTool toolSpec;
        private final AtomicReference<ToolExecutionMode> mode;
        private final AtomicInteger executeCount = new AtomicInteger();
        private final AtomicReference<Thread> firstExecutionThread = new AtomicReference<>();
        private final AtomicReference<Thread> lastExecutionThread = new AtomicReference<>();

        private ControlledToolProvider(String toolName, ToolExecutionMode initialMode) {
            this.toolSpec = new StaticAgentTool(toolName);
            this.mode = new AtomicReference<>(initialMode);
        }

        private int executeCount() {
            return executeCount.get();
        }

        private Thread firstExecutionThread() {
            return firstExecutionThread.get();
        }

        private Thread lastExecutionThread() {
            return lastExecutionThread.get();
        }

        private void setMode(ToolExecutionMode nextMode) {
            mode.set(nextMode);
        }

        @Override
        public List<AgentTool> specs() {
            return List.of(toolSpec);
        }

        @Override
        public ToolOutcome execute(Optional<space.controlnet.chatmc.core.terminal.TerminalContext> terminal, ToolCall call, boolean approved) {
            executeCount.incrementAndGet();
            Thread currentThread = Thread.currentThread();
            firstExecutionThread.compareAndSet(null, currentThread);
            lastExecutionThread.set(currentThread);

            ToolExecutionMode currentMode = mode.get();
            return switch (currentMode) {
                case IMMEDIATE_SUCCESS -> ToolOutcome.result(ToolResult.ok("{\"ok\":true}"));
                case FORCED_TIMEOUT_DELAY -> {
                    blockAtLeast(FORCED_TIMEOUT_DELAY_MS);
                    yield ToolOutcome.result(ToolResult.ok("{\"delayed\":\"timeout\"}"));
                }
                case DELAYED_FAILURE -> {
                    blockAtLeast(FORCED_FAILURE_DELAY_MS);
                    throw new IllegalStateException("task9/forced-delay/failure");
                }
            };
        }

        private static void blockAtLeast(long millis) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    return;
                }
                LockSupport.parkNanos(remaining);
            }
        }
    }

    private static final class StaticAgentTool implements AgentTool {
        private final String name;

        private StaticAgentTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Task9 controlled test tool";
        }

        @Override
        public String argsSchema() {
            return "{}";
        }

        @Override
        public List<String> argsDescription() {
            return List.of();
        }

        @Override
        public String resultSchema() {
            return "{}";
        }

        @Override
        public List<String> resultDescription() {
            return List.of();
        }

        @Override
        public List<String> examples() {
            return List.of();
        }

        @Override
        public ToolRender render(ToolPayload payload) {
            return null;
        }
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<String> scriptedResponses = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final List<String> prompts = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicInteger requestCount = new AtomicInteger();

        private ScriptedChatModel(String... scriptedResponses) {
            this.scriptedResponses.addAll(List.of(scriptedResponses));
        }

        private int requestCount() {
            return requestCount.get();
        }

        private String firstPrompt() {
            return prompts.isEmpty() ? "" : prompts.get(0);
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requestCount.incrementAndGet();
            prompts.add(chatRequest.messages().toString());

            String response = scriptedResponses.poll();
            if (response == null) {
                throw new IllegalStateException("task18/scripted-model/no-scripted-response");
            }

            return ChatResponse.builder()
                    .aiMessage(new AiMessage(response))
                    .build();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record UtfEdgeCase(String id, String seed) {
    }
}
