package space.controlnet.mineagent.common.boundary;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.session.MineAgentSessionsSavedData;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.session.SessionMetadata;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.SessionState;
import space.controlnet.mineagent.core.session.SessionVisibility;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ToolArgsBoundaryBehaviorTest {
    private static final int MAX_ARGS_LENGTH = 65_536;

    @Test
    void task15_boundaryBehavior_networkAndPersistValidation_acceptsMaxAndRejectsOversize() {
        String atLimit = repeat('a', MAX_ARGS_LENGTH);
        String aboveLimit = repeat('a', MAX_ARGS_LENGTH + 1);

        invokeNetworkBoundaryValidation("mc.boundary", atLimit, "encode");
        invokeNetworkBoundaryValidation("mc.boundary", atLimit, "decode");
        invokePersistBoundaryValidation("mc.boundary", atLimit, "write");
        invokePersistBoundaryValidation("mc.boundary", atLimit, "read");

        Throwable networkEncode = assertThrows(
                "task15/boundary-behavior/network-encode-oversize",
                () -> invokeNetworkBoundaryValidation("mc.boundary", aboveLimit, "encode")
        );
        assertContains("task15/boundary-behavior/network-encode-signal",
                networkEncode.getMessage(),
                "NETWORK_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        assertContains("task15/boundary-behavior/network-encode-phase",
                networkEncode.getMessage(),
                "phase='encode'"
        );
        assertContains("task15/boundary-behavior/network-encode-length",
                networkEncode.getMessage(),
                "argsJson.length=65537"
        );

        Throwable persistRead = assertThrows(
                "task15/boundary-behavior/persist-read-oversize",
                () -> invokePersistBoundaryValidation("mc.boundary", aboveLimit, "read")
        );
        assertContains("task15/boundary-behavior/persist-read-signal",
                persistRead.getMessage(),
                "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        assertContains("task15/boundary-behavior/persist-read-phase",
                persistRead.getMessage(),
                "phase='read'"
        );
    }

    @Test
    void task15_boundaryBehavior_networkEncodePathRejectsOversizeProposalArgs() {
        SessionSnapshot oversize = snapshotWithProposal("proposal-network-oversize", repeat('x', MAX_ARGS_LENGTH + 1));

        Throwable thrown = assertThrows(
                "task15/boundary-behavior/network-write-snapshot-oversize",
                () -> invokeWriteSnapshot(oversize)
        );

        assertContains("task15/boundary-behavior/network-write-snapshot-signal",
                thrown.getMessage(),
                "NETWORK_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        assertContains("task15/boundary-behavior/network-write-snapshot-phase",
                thrown.getMessage(),
                "phase='encode'"
        );
    }

    @Test
    void task15_boundaryBehavior_persistenceWriteAndReadPathsRejectOversizeProposalArgs() {
        SessionSnapshot oversize = snapshotWithProposal("proposal-persist-oversize", repeat('y', MAX_ARGS_LENGTH + 1));
        Throwable writeThrown = assertThrows(
                "task15/boundary-behavior/persist-write-session-oversize",
                () -> invokeWriteSession(oversize)
        );
        assertContains("task15/boundary-behavior/persist-write-signal",
                writeThrown.getMessage(),
                "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        assertContains("task15/boundary-behavior/persist-write-phase",
                writeThrown.getMessage(),
                "phase='write'"
        );

        SessionSnapshot atLimit = snapshotWithProposal("proposal-persist-at-limit", repeat('z', MAX_ARGS_LENGTH));
        CompoundTag encoded = invokeWriteSession(atLimit);
        encoded.getCompound("proposal").putString("argsJson", repeat('z', MAX_ARGS_LENGTH + 1));

        Throwable readThrown = assertThrows(
                "task15/boundary-behavior/persist-read-session-oversize",
                () -> invokeReadSession(encoded)
        );
        assertContains("task15/boundary-behavior/persist-read-session-signal",
                readThrown.getMessage(),
                "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE"
        );
        assertContains("task15/boundary-behavior/persist-read-session-phase",
                readThrown.getMessage(),
                "phase='read'"
        );
    }

    @Test
    void task15_boundaryBehavior_utfEdgeSemantics_useStringLengthNotUtf8Bytes() {
        String emojiAtLimit = "😀".repeat(MAX_ARGS_LENGTH / 2);
        String emojiAboveLimit = "😀".repeat((MAX_ARGS_LENGTH / 2) + 1);

        assertEquals("task15/boundary-behavior/utf-at-limit-char-length", MAX_ARGS_LENGTH, emojiAtLimit.length());
        assertEquals("task15/boundary-behavior/utf-above-limit-char-length", MAX_ARGS_LENGTH + 2, emojiAboveLimit.length());

        invokeNetworkBoundaryValidation("mc.emoji", emojiAtLimit, "encode");
        invokePersistBoundaryValidation("mc.emoji", emojiAtLimit, "write");

        Throwable network = assertThrows(
                "task15/boundary-behavior/utf-network-oversize",
                () -> invokeNetworkBoundaryValidation("mc.emoji", emojiAboveLimit, "encode")
        );
        Throwable persist = assertThrows(
                "task15/boundary-behavior/utf-persist-oversize",
                () -> invokePersistBoundaryValidation("mc.emoji", emojiAboveLimit, "write")
        );

        assertContains("task15/boundary-behavior/utf-network-length",
                network.getMessage(),
                "argsJson.length=65538"
        );
        assertContains("task15/boundary-behavior/utf-persist-length",
                persist.getMessage(),
                "argsJson.length=65538"
        );
    }

    private static void invokeNetworkBoundaryValidation(String toolName, String argsJson, String phase) {
        try {
            ensureMinecraftBootstrap();
            Method method = MineAgentNetwork.class.getDeclaredMethod(
                    "validateToolArgsBoundary",
                    String.class,
                    String.class,
                    String.class
            );
            method.setAccessible(true);
            method.invoke(null, toolName, argsJson, phase);
        } catch (Exception exception) {
            throw new AssertionError("task15/boundary-behavior/invoke-network-validate", rootCause(exception));
        }
    }

    private static void invokePersistBoundaryValidation(String toolName, String argsJson, String phase) {
        try {
            Method method = MineAgentSessionsSavedData.class.getDeclaredMethod(
                    "validateToolArgsBoundary",
                    String.class,
                    String.class,
                    String.class
            );
            method.setAccessible(true);
            method.invoke(null, toolName, argsJson, phase);
        } catch (Exception exception) {
            throw new AssertionError("task15/boundary-behavior/invoke-persist-validate", rootCause(exception));
        }
    }

    private static void invokeWriteSnapshot(SessionSnapshot snapshot) {
        try {
            ensureMinecraftBootstrap();
            Method method = MineAgentNetwork.class.getDeclaredMethod("writeSnapshot", FriendlyByteBuf.class, SessionSnapshot.class);
            method.setAccessible(true);
            method.invoke(null, new FriendlyByteBuf(Unpooled.buffer()), snapshot);
        } catch (Exception exception) {
            throw new AssertionError("task15/boundary-behavior/invoke-write-snapshot", rootCause(exception));
        }
    }

    private static CompoundTag invokeWriteSession(SessionSnapshot snapshot) {
        try {
            Method method = MineAgentSessionsSavedData.class.getDeclaredMethod("writeSession", SessionSnapshot.class);
            method.setAccessible(true);
            return (CompoundTag) method.invoke(null, snapshot);
        } catch (Exception exception) {
            throw new AssertionError("task15/boundary-behavior/invoke-write-session", rootCause(exception));
        }
    }

    private static SessionSnapshot invokeReadSession(CompoundTag root) {
        try {
            Method method = MineAgentSessionsSavedData.class.getDeclaredMethod("readSession", CompoundTag.class);
            method.setAccessible(true);
            return (SessionSnapshot) method.invoke(null, root);
        } catch (Exception exception) {
            throw new AssertionError("task15/boundary-behavior/invoke-read-session", rootCause(exception));
        }
    }

    private static SessionSnapshot snapshotWithProposal(String proposalId, String argsJson) {
        UUID sessionId = UUID.nameUUIDFromBytes(proposalId.getBytes(StandardCharsets.UTF_8));
        SessionMetadata metadata = new SessionMetadata(
                sessionId,
                UUID.fromString("00000000-0000-0000-0000-000000001501"),
                "task15-owner",
                SessionVisibility.PRIVATE,
                Optional.empty(),
                "Task 15 Boundary",
                100L,
                200L
        );
        Proposal proposal = new Proposal(
                proposalId,
                RiskLevel.SAFE_MUTATION,
                "boundary-summary",
                new ToolCall("mc.boundary", argsJson),
                1_700_000_000_000L,
                new ProposalDetails("action", "minecraft:stick", 1L, List.of(), "note")
        );
        return new SessionSnapshot(
                metadata,
                List.of(),
                SessionState.WAIT_APPROVAL,
                Optional.of(proposal),
                Optional.of(new TerminalBinding("minecraft:overworld", 1, 64, 1, Optional.of("north"))),
                List.of(),
                Optional.empty()
        );
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

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task15/boundary-behavior/bootstrap", exception);
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

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
