package space.controlnet.mineagent.common.gametest;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.commands.MineAgentCommands;
import space.controlnet.mineagent.common.menu.AiTerminalMenu;
import space.controlnet.mineagent.common.terminal.TerminalHost;
import space.controlnet.mineagent.core.net.c2s.C2SUpdateSessionPacket;
import space.controlnet.mineagent.core.session.ChatMessage;
import space.controlnet.mineagent.core.session.ChatRole;
import space.controlnet.mineagent.core.session.SessionListScope;
import space.controlnet.mineagent.core.session.SessionVisibility;
import space.controlnet.mineagent.core.session.SessionSummary;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class SessionLifecycleGameTestScenarios {
    private static final UUID COMMAND_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String COMMAND_PLAYER_NAME = "command_menu";
    private static final UUID DELETED_SESSION_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final String DELETED_SESSION_PLAYER_NAME = "deleted_session";
    private static final UUID VISIBILITY_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final UUID VISIBILITY_VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000112");
    private static final String VISIBILITY_OWNER_NAME = "visibility_owner";
    private static final String VISIBILITY_VIEWER_NAME = "visibility_viewer";
    private static final UUID DELETE_FALLBACK_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000113");
    private static final String DELETE_FALLBACK_PLAYER_NAME = "delete_fallback";
    private static final UUID MENU_VALIDITY_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000114");
    private static final String MENU_VALIDITY_PLAYER_NAME = "menu_validity";
    private static final UUID LIST_CYCLE_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000115");
    private static final UUID LIST_CYCLE_VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000116");
    private static final String LIST_CYCLE_OWNER_NAME = "list_cycle_owner";
    private static final String LIST_CYCLE_VIEWER_NAME = "list_cycle_viewer";

    private SessionLifecycleGameTestScenarios() {
    }

    public static void commandMenuOpenCloseLifecycleCleanup(GameTestHelper helper, GameTestPlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, COMMAND_PLAYER_ID, COMMAND_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task11/command-menu/setup/server",
                helper.getLevel().getServer()
        );
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task11/command-menu/setup/player",
                server,
                player
        );

        try {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            invokeRegisterMineAgentCommands(dispatcher);
            executeChatMcOpen(dispatcher, player);

            UUID sessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task11/command-menu/open/active-session -> missing active session"));
            AgentGameTestSupport.requireEquals(
                    "task11/command-menu/open/viewer-map",
                    sessionId,
                    AgentGameTestSupport.sessionByViewer().get(player.getUUID())
            );
            AgentGameTestSupport.requireEquals(
                    "task11/command-menu/open/viewers-for-session",
                    Set.of(player.getUUID()),
                    AgentGameTestSupport.viewersForSession(sessionId)
            );

            AiTerminalMenu menu = new AiTerminalMenu(37, player.getInventory(), null, player.blockPosition(), null);
            menu.removed(player);

            AgentGameTestSupport.requireTrue(
                    "task11/command-menu/close/viewer-map-cleared",
                    !AgentGameTestSupport.sessionByViewer().containsKey(player.getUUID())
            );
            AgentGameTestSupport.requireTrue(
                    "task11/command-menu/close/session-viewers-cleared",
                    AgentGameTestSupport.viewersForSession(sessionId).isEmpty()
            );

            MineAgentNetwork.onTerminalOpened(player);
            UUID reopenedSessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task11/command-menu/reopen/active-session -> missing active session"));
            AgentGameTestSupport.requireEquals(
                    "task11/command-menu/reopen/preserves-session",
                    sessionId,
                    reopenedSessionId
            );
            AgentGameTestSupport.requireEquals(
                    "task11/command-menu/reopen/viewer-map",
                    reopenedSessionId,
                    AgentGameTestSupport.sessionByViewer().get(player.getUUID())
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.resetRuntime();
        }
    }

    public static void deletedSessionQueuedAppendDoesNotRecreate(GameTestHelper helper, GameTestPlayerFactory playerFactory) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, DELETED_SESSION_PLAYER_ID, DELETED_SESSION_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task11/deleted-session-queued-append/setup/server",
                helper.getLevel().getServer()
        );
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task11/deleted-session-queued-append/setup/player",
                server,
                player
        );

        UUID sessionId = MineAgentNetwork.SESSIONS.create(player.getUUID(), player.getGameProfile().getName())
                .metadata()
                .sessionId();

        CountDownLatch appendQueued = new CountDownLatch(1);
        AtomicReference<Throwable> workerError = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                MineAgentNetwork.appendMessageAndBroadcast(
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
            AgentGameTestSupport.requireTrue(
                    "task11/deleted-session-queued-append/worker-enqueued-runnable",
                    appendQueued.await(2, TimeUnit.SECONDS)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("task11/deleted-session-queued-append/await-worker", exception);
        }
        AgentGameTestSupport.requireNonNull(
                "task11/deleted-session-queued-append/worker-error-ref",
                workerError
        );
        AgentGameTestSupport.requireEquals(
                "task11/deleted-session-queued-append/worker-error",
                null,
                workerError.get()
        );

        MineAgentNetwork.SESSIONS.delete(sessionId);

        helper.runAfterDelay(2, () -> {
            try {
                AgentGameTestSupport.requireTrue(
                        "task11/deleted-session-queued-append/worker-completed",
                        !worker.isAlive()
                );
                AgentGameTestSupport.requireTrue(
                        "task11/deleted-session-queued-append/session-still-deleted",
                        MineAgentNetwork.SESSIONS.get(sessionId).isEmpty()
                );
                helper.succeed();
            } finally {
                AgentGameTestSupport.resetRuntime();
            }
        });
    }

    public static void deleteLastActiveSessionFallbackCreatesNewSession(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, DELETE_FALLBACK_PLAYER_ID, DELETE_FALLBACK_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task15/delete-fallback/setup/server",
                helper.getLevel().getServer()
        );
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork(
                "task15/delete-fallback/setup/player",
                server,
                player
        );

        try {
            MineAgentNetwork.onTerminalOpened(player);
            UUID deletedSessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task15/delete-fallback/setup/active-session -> missing active session"));

            AgentGameTestSupport.invokeHandleDeleteSession(player, deletedSessionId);

            UUID reboundSessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(player.getUUID())
                    .orElseThrow(() -> new AssertionError("task15/delete-fallback/rebound/active-session -> missing active session"));

            AgentGameTestSupport.requireTrue(
                    "task15/delete-fallback/rebound/creates-fresh-session",
                    !reboundSessionId.equals(deletedSessionId)
            );
            AgentGameTestSupport.requireTrue(
                    "task15/delete-fallback/rebound/deleted-session-removed",
                    MineAgentNetwork.SESSIONS.get(deletedSessionId).isEmpty()
            );
            AgentGameTestSupport.requireEquals(
                    "task15/delete-fallback/rebound/viewer-map",
                    reboundSessionId,
                    AgentGameTestSupport.sessionByViewer().get(player.getUUID())
            );
            AgentGameTestSupport.requireEquals(
                    "task15/delete-fallback/rebound/session-viewers",
                    Set.of(player.getUUID()),
                    AgentGameTestSupport.viewersForSession(reboundSessionId)
            );

            long ownedSessions = MineAgentNetwork.SESSIONS.listAll().stream()
                    .filter(snapshot -> snapshot.metadata().ownerId().equals(player.getUUID()))
                    .count();
            AgentGameTestSupport.requireEquals(
                    "task15/delete-fallback/rebound/owner-session-count",
                    1L,
                    ownedSessions
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.resetRuntime();
        }
    }

    public static void menuValidityTracksRealHostLivenessConditions(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer player = playerFactory.create(helper, MENU_VALIDITY_PLAYER_ID, MENU_VALIDITY_PLAYER_NAME);
        player.setPos(0.5D, 2.0D, 0.5D);

        try {
            MutableTerminalHost host = new MutableTerminalHost(new BlockPos(0, 2, 0), helper.getLevel());
            AiTerminalMenu menu = new AiTerminalMenu(38, player.getInventory(), host, host.getHostPos(), null);

            AgentGameTestSupport.requireTrue(
                    "task15/menu-validity/nearby-host-valid",
                    menu.stillValid(player)
            );

            player.setPos(16.5D, 2.0D, 0.5D);
            AgentGameTestSupport.requireTrue(
                    "task15/menu-validity/distant-host-invalid",
                    !menu.stillValid(player)
            );

            player.setPos(0.5D, 2.0D, 0.5D);
            AgentGameTestSupport.requireTrue(
                    "task15/menu-validity/nearby-host-recovers",
                    menu.stillValid(player)
            );

            host.setRemoved(true);
            AgentGameTestSupport.requireTrue(
                    "task15/menu-validity/removed-host-invalid",
                    !menu.stillValid(player)
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.resetRuntime();
        }
    }

    public static void sessionVisibilityDeleteRebindUnderRuntimeConditions(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        MinecraftServer server = AgentGameTestSupport.requireNonNull(
                "task13/setup/server",
                helper.getLevel().getServer()
        );

        ServerPlayer owner = playerFactory.create(helper, VISIBILITY_OWNER_ID, VISIBILITY_OWNER_NAME);
        ServerPlayer viewer = playerFactory.create(helper, VISIBILITY_VIEWER_ID, VISIBILITY_VIEWER_NAME);
        owner.setPos(0.5D, 2.0D, 0.5D);
        viewer.setPos(1.5D, 2.0D, 0.5D);

        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task13/setup/owner-lookup", server, owner);
        AgentGameTestSupport.ensurePlayerResolvableByChatNetwork("task13/setup/viewer-lookup", server, viewer);

        try {
            MineAgentNetwork.onTerminalOpened(owner);
            UUID sharedSessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(owner.getUUID())
                    .orElseThrow(() -> new AssertionError("task13/setup/shared-session -> missing owner active session"));
            UUID backupSessionId = MineAgentNetwork.SESSIONS.create(owner.getUUID(), owner.getGameProfile().getName())
                    .metadata()
                    .sessionId();
            AgentGameTestSupport.invokeHandleOpenSession(owner, sharedSessionId);

            MineAgentNetwork.SESSIONS.setVisibility(sharedSessionId, SessionVisibility.PUBLIC, Optional.empty());
            AgentGameTestSupport.invokeHandleOpenSession(viewer, sharedSessionId);
            AgentGameTestSupport.requireEquals(
                    "task13/open/viewer-subscribed",
                    sharedSessionId,
                    AgentGameTestSupport.sessionByViewer().get(viewer.getUUID())
            );

            MineAgentNetwork.SESSIONS.setVisibility(sharedSessionId, SessionVisibility.PRIVATE, Optional.empty());
            AgentGameTestSupport.invokeBroadcastSessionSnapshot(sharedSessionId);

            AgentGameTestSupport.requireTrue(
                    "task13/privacy/viewer-removed-after-private",
                    !AgentGameTestSupport.sessionByViewer().containsKey(viewer.getUUID())
            );
            AgentGameTestSupport.requireEquals(
                    "task13/privacy/remaining-viewers",
                    Set.of(owner.getUUID()),
                    AgentGameTestSupport.viewersForSession(sharedSessionId)
            );

            MineAgentNetwork.SESSIONS.setVisibility(sharedSessionId, SessionVisibility.PUBLIC, Optional.empty());
            AgentGameTestSupport.invokeHandleOpenSession(viewer, sharedSessionId);
            AgentGameTestSupport.requireEquals(
                    "task13/reopen/viewer-resubscribed",
                    sharedSessionId,
                    AgentGameTestSupport.sessionByViewer().get(viewer.getUUID())
            );

            AgentGameTestSupport.invokeHandleDeleteSession(owner, sharedSessionId);

            AgentGameTestSupport.requireTrue(
                    "task13/delete/session-removed",
                    MineAgentNetwork.SESSIONS.get(sharedSessionId).isEmpty()
            );
            AgentGameTestSupport.requireEquals(
                    "task13/delete/owner-rebound-to-backup",
                    backupSessionId,
                    MineAgentNetwork.SESSIONS.getActiveSessionId(owner.getUUID())
                            .orElseThrow(() -> new AssertionError("task13/delete/owner-active -> missing"))
            );
            AgentGameTestSupport.requireEquals(
                    "task13/delete/owner-viewer-map-rebound",
                    backupSessionId,
                    AgentGameTestSupport.sessionByViewer().get(owner.getUUID())
            );

            UUID viewerReboundSessionId = MineAgentNetwork.SESSIONS.getActiveSessionId(viewer.getUUID())
                    .orElseThrow(() -> new AssertionError("task13/delete/viewer-active -> missing"));
            AgentGameTestSupport.requireTrue(
                    "task13/delete/viewer-gets-fresh-session",
                    !viewerReboundSessionId.equals(sharedSessionId)
            );
            AgentGameTestSupport.requireEquals(
                    "task13/delete/viewer-map-rebound",
                    viewerReboundSessionId,
                    AgentGameTestSupport.sessionByViewer().get(viewer.getUUID())
            );
            AgentGameTestSupport.requireEquals(
                    "task13/delete/viewer-fresh-session-viewers",
                    Set.of(viewer.getUUID()),
                    AgentGameTestSupport.viewersForSession(viewerReboundSessionId)
            );
            AgentGameTestSupport.requireTrue(
                    "task13/delete/deleted-session-viewers-cleared",
                    AgentGameTestSupport.viewersForSession(sharedSessionId).isEmpty()
            );

            helper.succeed();
        } finally {
            AgentGameTestSupport.resetRuntime();
        }
    }

    public static void sessionVisibilitySessionListUpdateCycle(
            GameTestHelper helper,
            GameTestPlayerFactory playerFactory
    ) {
        AgentGameTestSupport.initializeRuntime(helper);

        ServerPlayer owner = playerFactory.create(helper, LIST_CYCLE_OWNER_ID, LIST_CYCLE_OWNER_NAME);
        ServerPlayer viewer = playerFactory.create(helper, LIST_CYCLE_VIEWER_ID, LIST_CYCLE_VIEWER_NAME);
        owner.setPos(0.5D, 2.0D, 0.5D);
        viewer.setPos(1.5D, 2.0D, 0.5D);

        try {
            UUID sessionId = MineAgentNetwork.SESSIONS.create(owner.getUUID(), owner.getGameProfile().getName())
                    .metadata()
                    .sessionId();

            AgentGameTestSupport.requireTrue(
                    "task15/session-list/before-public/viewer-public-empty",
                    AgentGameTestSupport.sessionListForScope(viewer, SessionListScope.PUBLIC).isEmpty()
            );
            AgentGameTestSupport.requireTrue(
                    "task15/session-list/before-team/viewer-team-empty",
                    AgentGameTestSupport.sessionListForScope(viewer, SessionListScope.TEAM).isEmpty()
            );

            AgentGameTestSupport.invokeHandleUpdateSession(
                    owner,
                    new C2SUpdateSessionPacket(
                            AgentGameTestSupport.protocolVersion(),
                            sessionId,
                            Optional.of("Operations Board"),
                            Optional.of(SessionVisibility.PUBLIC)
                    )
            );

            SessionSummary ownerSummaryAfterPublic = requireSingleSummary(
                    "task15/session-list/owner-my-after-public",
                    AgentGameTestSupport.sessionListForScope(owner, SessionListScope.MY)
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/owner-my-title-updated",
                    "Operations Board",
                    ownerSummaryAfterPublic.title()
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/owner-my-visibility-public",
                    SessionVisibility.PUBLIC,
                    ownerSummaryAfterPublic.visibility()
            );

            SessionSummary viewerPublicSummary = requireSingleSummary(
                    "task15/session-list/viewer-public-after-public",
                    AgentGameTestSupport.sessionListForScope(viewer, SessionListScope.PUBLIC)
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/viewer-public-session-id",
                    sessionId,
                    viewerPublicSummary.sessionId()
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/viewer-public-title",
                    "Operations Board",
                    viewerPublicSummary.title()
            );

            SessionSummary viewerAllSummary = requireSingleSummary(
                    "task15/session-list/viewer-all-after-public",
                    AgentGameTestSupport.sessionListForScope(viewer, SessionListScope.ALL)
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/viewer-all-visibility",
                    SessionVisibility.PUBLIC,
                    viewerAllSummary.visibility()
            );

            AgentGameTestSupport.invokeHandleUpdateSession(
                    owner,
                    new C2SUpdateSessionPacket(
                            AgentGameTestSupport.protocolVersion(),
                            sessionId,
                            Optional.empty(),
                            Optional.of(SessionVisibility.TEAM)
                    )
            );

            SessionSummary ownerSummaryAfterTeamAttempt = requireSingleSummary(
                    "task15/session-list/owner-my-after-team-attempt",
                    AgentGameTestSupport.sessionListForScope(owner, SessionListScope.MY)
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/team-noop-title-preserved",
                    "Operations Board",
                    ownerSummaryAfterTeamAttempt.title()
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/team-noop-visibility-still-public",
                    SessionVisibility.PUBLIC,
                    ownerSummaryAfterTeamAttempt.visibility()
            );
            AgentGameTestSupport.requireTrue(
                    "task15/session-list/team-noop-team-id-still-empty",
                    ownerSummaryAfterTeamAttempt.teamId().isEmpty()
            );

            SessionSummary viewerPublicSummaryAfterTeamAttempt = requireSingleSummary(
                    "task15/session-list/viewer-public-after-team-attempt",
                    AgentGameTestSupport.sessionListForScope(viewer, SessionListScope.PUBLIC)
            );
            AgentGameTestSupport.requireEquals(
                    "task15/session-list/viewer-public-remains-visible",
                    sessionId,
                    viewerPublicSummaryAfterTeamAttempt.sessionId()
            );
            AgentGameTestSupport.requireTrue(
                    "task15/session-list/viewer-team-still-empty-after-team-attempt",
                    AgentGameTestSupport.sessionListForScope(viewer, SessionListScope.TEAM).isEmpty()
            );

            helper.succeed();
        } finally {
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
            throw new AssertionError("task11/command-menu/register-mineagent-command", AgentGameTestSupport.rootCause(exception));
        }
    }

    private static void executeChatMcOpen(CommandDispatcher<CommandSourceStack> dispatcher, ServerPlayer player) {
        try {
            dispatcher.execute("mineagent open", player.createCommandSourceStack());
        } catch (Exception exception) {
            throw new AssertionError("task11/command-menu/execute-mineagent-open", AgentGameTestSupport.rootCause(exception));
        }
    }

    private static SessionSummary requireSingleSummary(String assertionName, List<SessionSummary> summaries) {
        AgentGameTestSupport.requireEquals(assertionName + "/size", 1, summaries.size());
        return summaries.get(0);
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static final class MutableTerminalHost implements TerminalHost {
        private final BlockPos hostPos;
        private final net.minecraft.world.level.Level hostLevel;
        private boolean removed;

        private MutableTerminalHost(BlockPos hostPos, net.minecraft.world.level.Level hostLevel) {
            this.hostPos = hostPos;
            this.hostLevel = hostLevel;
        }

        @Override
        public BlockPos getHostPos() {
            return hostPos;
        }

        @Override
        public net.minecraft.world.level.Level getHostLevel() {
            return hostLevel;
        }

        @Override
        public boolean isRemovedHost() {
            return removed;
        }

        private void setRemoved(boolean removed) {
            this.removed = removed;
        }
    }
}
