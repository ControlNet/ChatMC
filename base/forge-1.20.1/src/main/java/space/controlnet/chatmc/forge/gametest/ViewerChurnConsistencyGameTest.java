package space.controlnet.chatmc.forge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.gametest.GameTestRuntimeLease;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionVisibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("chatmc")
public final class ViewerChurnConsistencyGameTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID VIEWER_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID VIEWER_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String OWNER_NAME = "viewer_owner";
    private static final String VIEWER_A_NAME = "viewer_a";
    private static final String VIEWER_B_NAME = "viewer_b";

    private ViewerChurnConsistencyGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "chatmc_task8_viewer")
    public static void multiViewerSnapshotConsistencyUnderChurn(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> multiViewerSnapshotConsistencyUnderChurnInternal(helper));
    }

    private static void multiViewerSnapshotConsistencyUnderChurnInternal(GameTestHelper helper) {
        resetSharedNetworkState(false);

        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(OWNER_ID, OWNER_NAME));
        ServerPlayer viewerA = FakePlayerFactory.get(helper.getLevel(), new GameProfile(VIEWER_A_ID, VIEWER_A_NAME));
        ServerPlayer viewerB = FakePlayerFactory.get(helper.getLevel(), new GameProfile(VIEWER_B_ID, VIEWER_B_NAME));

        MinecraftServer server = helper.getLevel().getServer();
        requireTrue("task8/setup/server-present", server != null);
        ChatMCNetwork.setServer(server);
        ensurePlayerResolvableByChatNetwork("task8/setup/player-lookup/owner", server, owner);
        ensurePlayerResolvableByChatNetwork("task8/setup/player-lookup/viewer-a", server, viewerA);
        ensurePlayerResolvableByChatNetwork("task8/setup/player-lookup/viewer-b", server, viewerB);

        Map<UUID, List<Integer>> deliveredSequences = new HashMap<>();
        deliveredSequences.put(owner.getUUID(), new ArrayList<>());
        deliveredSequences.put(viewerA.getUUID(), new ArrayList<>());
        deliveredSequences.put(viewerB.getUUID(), new ArrayList<>());

        try {
            ChatMCNetwork.onTerminalOpened(owner);
            UUID sessionId = ChatMCNetwork.SESSIONS.getActiveSessionId(owner.getUUID())
                    .orElseThrow(() -> new AssertionError("task8/setup/owner-active-session -> missing active session"));
            ChatMCNetwork.SESSIONS.setVisibility(sessionId, SessionVisibility.PUBLIC, Optional.empty());

            invokeHandleOpenSession(viewerA, sessionId);
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

            invokeHandleOpenSession(viewerB, sessionId);
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
            requireEquals(
                    "task8/churn/viewer-a-no-updates-after-close",
                    viewerACountAfterFirstClose,
                    deliveredSequences.get(viewerA.getUUID()).size()
            );

            invokeHandleOpenSession(viewerA, sessionId);
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
            requireEquals(
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
            requireEquals(
                    "task8/churn/viewer-a-no-updates-after-second-close",
                    viewerACountAfterSecondClose,
                    deliveredSequences.get(viewerA.getUUID()).size()
            );

            requireEquals(
                    "task8/final/owner-sequence",
                    List.of(1, 2, 3, 4, 5),
                    deliveredSequences.get(owner.getUUID())
            );
            requireEquals(
                    "task8/final/viewer-a-sequence",
                    List.of(1, 2, 4),
                    deliveredSequences.get(viewerA.getUUID())
            );
            requireEquals(
                    "task8/final/viewer-b-sequence",
                    List.of(2, 3),
                    deliveredSequences.get(viewerB.getUUID())
            );

            requireMonotonicAndUnique("task8/final/owner-monotonic", deliveredSequences.get(owner.getUUID()));
            requireMonotonicAndUnique("task8/final/viewer-a-monotonic", deliveredSequences.get(viewerA.getUUID()));
            requireMonotonicAndUnique("task8/final/viewer-b-monotonic", deliveredSequences.get(viewerB.getUUID()));

            helper.succeed();
        } finally {
            resetSharedNetworkState(true);
        }
    }

    private static void recordBroadcastStep(
            String assertionPrefix,
            UUID sessionId,
            int sequence,
            Set<UUID> expectedRecipients,
            Map<UUID, List<Integer>> deliveredSequences
    ) {
        Set<UUID> recipientsBefore = viewersForSession(sessionId);
        requireEquals(assertionPrefix + "/recipients-before", expectedRecipients, recipientsBefore);

        ChatMCNetwork.SESSIONS.appendMessage(
                sessionId,
                new ChatMessage(ChatRole.USER, "task8-seq-" + sequence, sequence)
        );
        invokeBroadcastSessionSnapshot(sessionId);

        Set<UUID> recipientsAfter = viewersForSession(sessionId);
        requireEquals(assertionPrefix + "/recipients-after", expectedRecipients, recipientsAfter);

        for (UUID recipientId : recipientsBefore) {
            deliveredSequences.computeIfAbsent(recipientId, ignored -> new ArrayList<>()).add(sequence);
        }
    }

    private static void requireSessionSubscriptions(String assertionName, UUID sessionId, Set<UUID> expectedViewers) {
        Set<UUID> viewers = viewersForSession(sessionId);
        requireEquals(assertionName + "/viewers-for-session", expectedViewers, viewers);

        Map<UUID, UUID> byViewer = sessionByViewer();
        for (UUID viewerId : expectedViewers) {
            requireEquals(assertionName + "/viewer-mapping/" + viewerId, sessionId, byViewer.get(viewerId));
        }
    }

    private static Set<UUID> viewersForSession(UUID sessionId) {
        Set<UUID> viewers = viewersBySession().get(sessionId);
        if (viewers == null) {
            return Set.of();
        }
        return new HashSet<>(viewers);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Set<UUID>> viewersBySession() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("VIEWERS_BY_SESSION");
            field.setAccessible(true);
            return (Map<UUID, Set<UUID>>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("task8/reflection/read-viewers-by-session", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> sessionByViewer() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_BY_VIEWER");
            field.setAccessible(true);
            return (Map<UUID, UUID>) field.get(null);
        } catch (Exception exception) {
            throw new AssertionError("task8/reflection/read-session-by-viewer", exception);
        }
    }

    private static void invokeHandleOpenSession(ServerPlayer player, UUID sessionId) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod(
                    "handleOpenSession",
                    ServerPlayer.class,
                    UUID.class
            );
            method.setAccessible(true);
            method.invoke(null, player, sessionId);
        } catch (Exception exception) {
            throw new AssertionError("task8/reflection/invoke-handle-open-session", rootCause(exception));
        }
    }

    private static void invokeBroadcastSessionSnapshot(UUID sessionId) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("broadcastSessionSnapshot", UUID.class);
            method.setAccessible(true);
            method.invoke(null, sessionId);
        } catch (Exception exception) {
            throw new AssertionError("task8/reflection/invoke-broadcast", rootCause(exception));
        }
    }

    private static void requireMonotonicAndUnique(String assertionName, List<Integer> sequence) {
        Set<Integer> unique = new HashSet<>(sequence);
        requireEquals(assertionName + "/unique-size", sequence.size(), unique.size());
        for (int i = 1; i < sequence.size(); i++) {
            int previous = sequence.get(i - 1);
            int current = sequence.get(i);
            requireTrue(assertionName + "/strictly-increasing/" + i, current > previous);
        }
    }

    private static void resetSharedNetworkState(boolean releaseLease) {
        ChatMCNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        clearViewerState();
        if (releaseLease) {
            GameTestRuntimeLease.release();
        }
    }

    private static void clearViewerState() {
        viewersBySession().clear();
        sessionByViewer().clear();
    }

    private static void clearSessionLocale() {
        try {
            Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("task8/cleanup/clear-session-locale", exception);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void ensurePlayerResolvableByChatNetwork(
            String assertionPrefix,
            MinecraftServer server,
            ServerPlayer player
    ) {
        UUID playerId = requireNonNull(assertionPrefix + "/id", player.getUUID());
        if (ChatMCNetwork.findPlayer(playerId).isPresent()) {
            return;
        }

        Object playerList = requireNonNull(assertionPrefix + "/player-list", server.getPlayerList());
        requireTrue(
                assertionPrefix + "/inject-player-lookup",
                injectPlayerLookup(playerList, playerId, player)
        );
        requireTrue(assertionPrefix + "/lookup-present", ChatMCNetwork.findPlayer(playerId).isPresent());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean injectPlayerLookup(Object playerList, UUID playerId, ServerPlayer player) {
        Class<?> currentClass = playerList.getClass();
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                field.setAccessible(true);

                try {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        Map map = (Map) field.get(playerList);
                        if (map == null) {
                            continue;
                        }

                        Object previous = map.put(playerId, player);
                        if (ChatMCNetwork.findPlayer(playerId).isPresent()) {
                            return true;
                        }

                        if (previous == null) {
                            map.remove(playerId);
                        } else {
                            map.put(playerId, previous);
                        }
                    } else if (List.class.isAssignableFrom(field.getType())) {
                        List list = (List) field.get(playerList);
                        if (list == null) {
                            continue;
                        }

                        boolean added = false;
                        if (!list.contains(player)) {
                            list.add(player);
                            added = true;
                        }

                        if (ChatMCNetwork.findPlayer(playerId).isPresent()) {
                            return true;
                        }

                        if (added) {
                            list.remove(player);
                        }
                    }
                } catch (IllegalAccessException | UnsupportedOperationException | ClassCastException ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return false;
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    private static void requireTrue(String assertionName, boolean condition) {
        if (!condition) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void requireEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
