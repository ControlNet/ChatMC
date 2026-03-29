package space.controlnet.chatmc.forge.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.gametest.GameTestRuntimeLease;
import space.controlnet.chatmc.common.session.ChatMCSessionsSavedData;
import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionMetadata;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.SessionVisibility;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.ToolCall;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder("chatmc")
public final class ToolArgsBoundaryEndToEndGameTest {
    private static final int MAX_TOOL_ARGS_JSON_LENGTH = 65_536;

    private ToolArgsBoundaryEndToEndGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "chatmc", timeoutTicks = 400)
    public static void toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpus(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpusInternal(helper));
    }

    private static void toolArgsBoundaryEndToEnd_65535_65536_65537_withUtfCorpusInternal(GameTestHelper helper) {
        resetSharedNetworkState(false);

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
                requireTrue(
                        "task10/utf/" + utfEdgeCase.id() + "/at-65536-contains-seed",
                        atBoundary.contains(utfEdgeCase.seed())
                );
                requireTrue(
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
                requireEquals(
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
            resetSharedNetworkState(true);
        }
    }

    private static void assertAcceptsAcrossParseNetworkPersistence(
            String assertionPrefix,
            String toolName,
            String argsJson,
            int expectedLength
    ) {
        requireEquals(assertionPrefix + "/expected-length", expectedLength, argsJson.length());

        invokeParseBoundaryValidate(toolName, argsJson);

        SessionSnapshot snapshot = snapshotWithProposal(assertionPrefix + "/snapshot", toolName, argsJson);
        SessionSnapshot networkRoundTrip = invokeNetworkRoundTrip(snapshot);
        String networkArgs = requireProposalArgs(assertionPrefix + "/network-roundtrip", networkRoundTrip);
        requireEquals(assertionPrefix + "/network-roundtrip/args-length", expectedLength, networkArgs.length());
        requireEquals(assertionPrefix + "/network-roundtrip/args-equals", argsJson, networkArgs);

        PersistedSessions persistedRoundTrip = invokePersistenceRoundTrip(snapshot);
        ChatMCNetwork.SESSIONS.loadFromSave(persistedRoundTrip);

        SessionSnapshot loaded = ChatMCNetwork.SESSIONS.get(snapshot.metadata().sessionId())
                .orElseThrow(() -> new AssertionError(assertionPrefix + "/persist-roundtrip -> missing session"));
        String persistedArgs = requireProposalArgs(assertionPrefix + "/persist-roundtrip", loaded);
        requireEquals(assertionPrefix + "/persist-roundtrip/args-length", expectedLength, persistedArgs.length());
        requireEquals(assertionPrefix + "/persist-roundtrip/args-equals", argsJson, persistedArgs);
    }

    private static void assertRejectsAt65537WithSignals(
            String assertionPrefix,
            String toolName,
            String oversizeArgs
    ) {
        requireEquals(assertionPrefix + "/oversize-length", MAX_TOOL_ARGS_JSON_LENGTH + 1, oversizeArgs.length());

        Throwable parseThrown = assertThrows(
                assertionPrefix + "/parse-rejects",
                () -> invokeParseBoundaryValidate(toolName, oversizeArgs)
        );
        requireEquals(
                assertionPrefix + "/parse-message",
                "PARSE_BOUNDARY_TOOL_ARGS_TOO_LARGE: tool='" + toolName + "', argsJson.length=65537, max=65536",
                parseThrown.getMessage()
        );

        SessionSnapshot oversizeSnapshot = snapshotWithProposal(assertionPrefix + "/oversize", toolName, oversizeArgs);

        Throwable networkThrown = assertThrows(
                assertionPrefix + "/network-encode-rejects",
                () -> invokeNetworkWriteSnapshot(oversizeSnapshot)
        );
        assertContains(assertionPrefix + "/network-encode-signal", networkThrown.getMessage(), "NETWORK_BOUNDARY_TOOL_ARGS_TOO_LARGE");
        assertContains(assertionPrefix + "/network-encode-phase", networkThrown.getMessage(), "phase='encode'");
        assertContains(assertionPrefix + "/network-encode-length", networkThrown.getMessage(), "argsJson.length=65537");

        Throwable persistWriteThrown = assertThrows(
                assertionPrefix + "/persist-write-rejects",
                () -> invokeWriteSession(oversizeSnapshot)
        );
        assertContains(assertionPrefix + "/persist-write-signal", persistWriteThrown.getMessage(), "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE");
        assertContains(assertionPrefix + "/persist-write-phase", persistWriteThrown.getMessage(), "phase='write'");
        assertContains(assertionPrefix + "/persist-write-length", persistWriteThrown.getMessage(), "argsJson.length=65537");

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
        assertContains(assertionPrefix + "/persist-read-signal", persistReadThrown.getMessage(), "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE");
        assertContains(assertionPrefix + "/persist-read-phase", persistReadThrown.getMessage(), "phase='read'");
        assertContains(assertionPrefix + "/persist-read-length", persistReadThrown.getMessage(), "argsJson.length=65537");
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
            throw new AssertionError("task10/reflection/invoke-network-write-snapshot", rootCause(exception));
        }
    }

    private static SessionSnapshot invokeNetworkReadSnapshot(FriendlyByteBuf buf) {
        try {
            Method method = ChatMCNetwork.class.getDeclaredMethod("readSnapshot", FriendlyByteBuf.class);
            method.setAccessible(true);
            return (SessionSnapshot) method.invoke(null, buf);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-network-read-snapshot", rootCause(exception));
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
            throw new AssertionError("task10/reflection/invoke-persist-write-session", rootCause(exception));
        }
    }

    private static SessionSnapshot invokeReadSession(CompoundTag root) {
        try {
            Method method = ChatMCSessionsSavedData.class.getDeclaredMethod("readSession", CompoundTag.class);
            method.setAccessible(true);
            return (SessionSnapshot) method.invoke(null, root);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-persist-read-session", rootCause(exception));
        }
    }

    private static void invokeParseBoundaryValidate(String toolName, String argsJson) {
        try {
            Class<?> boundaryClass = Class.forName("space.controlnet.chatmc.core.agent.ToolCallArgsParseBoundary");
            Method method = boundaryClass.getDeclaredMethod("validate", String.class, String.class);
            method.setAccessible(true);
            method.invoke(null, toolName, argsJson);
        } catch (Exception exception) {
            throw new AssertionError("task10/reflection/invoke-parse-boundary-validate", rootCause(exception));
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
        requireTrue(assertionName + "/seed-not-empty", seed != null && !seed.isEmpty());
        requireTrue(assertionName + "/seed-fits-target", seed.length() <= targetLength);

        StringBuilder builder = new StringBuilder(targetLength);
        int fullRepeats = targetLength / seed.length();
        for (int i = 0; i < fullRepeats; i++) {
            builder.append(seed);
        }

        int remaining = targetLength - builder.length();
        if (remaining > 0) {
            builder.append("a".repeat(remaining));
        }

        String payload = builder.toString();
        requireEquals(assertionName + "/target-length", targetLength, payload.length());
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

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void resetSharedNetworkState(boolean releaseLease) {
        ChatMCNetwork.SESSIONS.loadFromSave(new PersistedSessions(1, List.of(), Map.of()));
        clearSessionLocale();
        if (releaseLease) {
            GameTestRuntimeLease.release();
        }
    }

    private static void clearSessionLocale() {
        try {
            java.lang.reflect.Field field = ChatMCNetwork.class.getDeclaredField("SESSION_LOCALE");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> localeMap = (Map<UUID, String>) field.get(null);
            localeMap.clear();
        } catch (Exception exception) {
            throw new AssertionError("task10/cleanup/clear-session-locale", exception);
        }
    }

    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void requireTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void requireEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record UtfEdgeCase(String id, String seed) {
    }
}
