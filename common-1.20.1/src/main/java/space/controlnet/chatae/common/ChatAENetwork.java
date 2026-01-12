package space.controlnet.chatae.common;

import dev.architectury.networking.NetworkChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.common.audit.AuditLogger;
import space.controlnet.chatae.common.team.TeamAccess;
import space.controlnet.chatae.common.terminal.TerminalContextFactory;
import space.controlnet.chatae.common.tools.ToolRouter;
import space.controlnet.chatae.core.agent.LlmRateLimiter;
import space.controlnet.chatae.core.agent.ReflectiveToolCallParser;
import space.controlnet.chatae.core.agent.ToolCallParsingService;
import space.controlnet.chatae.core.audit.AuditEvent;
import space.controlnet.chatae.core.audit.AuditOutcome;
import space.controlnet.chatae.core.client.ClientSessionIndex;
import space.controlnet.chatae.core.client.ClientSessionStore;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.ApprovalDecision;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalDetails;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;
import space.controlnet.chatae.core.session.ServerSessionManager;
import space.controlnet.chatae.core.session.SessionListScope;
import space.controlnet.chatae.core.session.SessionMetadata;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.SessionSummary;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.session.SessionVisibility;
import space.controlnet.chatae.core.tools.ParseOutcome;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;
import space.controlnet.chatae.core.tools.ToolResult;
import space.controlnet.chatae.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatae.net.c2s.C2SCreateSessionPacket;
import space.controlnet.chatae.net.c2s.C2SDeleteSessionPacket;
import space.controlnet.chatae.net.c2s.C2SOpenSessionPacket;
import space.controlnet.chatae.net.c2s.C2SRequestSessionListPacket;
import space.controlnet.chatae.net.c2s.C2SSendChatPacket;
import space.controlnet.chatae.net.c2s.C2SUpdateSessionPacket;
import space.controlnet.chatae.net.s2c.S2CSessionListPacket;
import space.controlnet.chatae.net.s2c.S2CSessionSnapshotPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatAENetwork {
    private static final NetworkChannel CHANNEL = NetworkChannel.create(ChatAERegistries.id("main"));
    private static final int PROTOCOL_VERSION = 2;

    public static final ServerSessionManager SESSIONS = new ServerSessionManager();
    private static final AgentInvoker AGENT = new AgentInvoker();
    private static final ReflectiveToolCallParser LLM_PARSER = new ReflectiveToolCallParser((msg, ex) -> ChatAE.LOGGER.warn(msg, ex));
    private static final ExecutorService LLM_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chatae-llm");
        t.setDaemon(true);
        return t;
    });
    private static final long LLM_TIMEOUT_MS = 5000;
    private static final long LLM_COOLDOWN_MS = 1500;
    private static final LlmRateLimiter LLM_RATE_LIMITER = new LlmRateLimiter(LLM_COOLDOWN_MS);

    private ChatAENetwork() {
    }

    public static void init() {
        CHANNEL.register(
                C2SSendChatPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeUtf(packet.text(), 256);
                },
                buf -> new C2SSendChatPacket(buf.readVarInt(), buf.readUtf(256)),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleChatPacket(player, packet.text());
                })
        );

        CHANNEL.register(
                C2SApprovalDecisionPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeUtf(packet.proposalId(), 64);
                    buf.writeVarInt(packet.decision().ordinal());
                },
                buf -> new C2SApprovalDecisionPacket(buf.readVarInt(), buf.readUtf(64), ApprovalDecision.values()[buf.readVarInt()]),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleApprovalDecision(player, packet);
                })
        );

        CHANNEL.register(
                C2SRequestSessionListPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeVarInt(packet.scope().ordinal());
                },
                buf -> new C2SRequestSessionListPacket(buf.readVarInt(), SessionListScope.values()[buf.readVarInt()]),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    sendSessionList(player, packet.scope());
                })
        );

        CHANNEL.register(
                C2SOpenSessionPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeUUID(packet.sessionId());
                },
                buf -> new C2SOpenSessionPacket(buf.readVarInt(), buf.readUUID()),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleOpenSession(player, packet.sessionId());
                })
        );

        CHANNEL.register(
                C2SCreateSessionPacket.class,
                (packet, buf) -> buf.writeVarInt(packet.protocolVersion()),
                buf -> new C2SCreateSessionPacket(buf.readVarInt()),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleCreateSession(player);
                })
        );

        CHANNEL.register(
                C2SDeleteSessionPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeUUID(packet.sessionId());
                },
                buf -> new C2SDeleteSessionPacket(buf.readVarInt(), buf.readUUID()),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleDeleteSession(player, packet.sessionId());
                })
        );

        CHANNEL.register(
                C2SUpdateSessionPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeUUID(packet.sessionId());
                    buf.writeBoolean(packet.title().isPresent());
                    packet.title().ifPresent(title -> buf.writeUtf(title, 128));
                    buf.writeBoolean(packet.visibility().isPresent());
                    packet.visibility().ifPresent(visibility -> buf.writeVarInt(visibility.ordinal()));
                },
                buf -> {
                    int version = buf.readVarInt();
                    UUID sessionId = buf.readUUID();
                    Optional<String> title = buf.readBoolean() ? Optional.of(buf.readUtf(128)) : Optional.empty();
                    Optional<SessionVisibility> visibility = buf.readBoolean()
                            ? Optional.of(SessionVisibility.values()[buf.readVarInt()])
                            : Optional.empty();
                    return new C2SUpdateSessionPacket(version, sessionId, title, visibility);
                },
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleUpdateSession(player, packet);
                })
        );

        CHANNEL.register(
                S2CSessionListPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeVarInt(packet.sessions().size());
                    for (SessionSummary summary : packet.sessions()) {
                        writeSummary(buf, summary);
                    }
                },
                buf -> {
                    int version = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<SessionSummary> sessions = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        sessions.add(readSummary(buf));
                    }
                    return new S2CSessionListPacket(version, sessions);
                },
                (packet, context) -> context.get().queue(() -> {
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    ClientSessionIndex.set(packet.sessions());
                })
        );

        CHANNEL.register(
                S2CSessionSnapshotPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    writeSnapshot(buf, packet.snapshot());
                },
                buf -> new S2CSessionSnapshotPacket(buf.readVarInt(), readSnapshot(buf)),
                (packet, context) -> context.get().queue(() -> {
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    ClientSessionStore.set(packet.snapshot());
                })
        );
    }

    public static void shutdown() {
        LLM_EXECUTOR.shutdownNow();
    }

    public static void sendChatToServer(String text) {
        CHANNEL.sendToServer(new C2SSendChatPacket(PROTOCOL_VERSION, text));
    }

    public static void sendApprovalDecision(String proposalId, ApprovalDecision decision) {
        CHANNEL.sendToServer(new C2SApprovalDecisionPacket(PROTOCOL_VERSION, proposalId, decision));
    }

    public static void requestSessionList(SessionListScope scope) {
        CHANNEL.sendToServer(new C2SRequestSessionListPacket(PROTOCOL_VERSION, scope));
    }

    public static void openSession(UUID sessionId) {
        CHANNEL.sendToServer(new C2SOpenSessionPacket(PROTOCOL_VERSION, sessionId));
    }

    public static void createSession() {
        CHANNEL.sendToServer(new C2SCreateSessionPacket(PROTOCOL_VERSION));
    }

    public static void deleteSession(UUID sessionId) {
        CHANNEL.sendToServer(new C2SDeleteSessionPacket(PROTOCOL_VERSION, sessionId));
    }

    public static void updateSession(UUID sessionId, Optional<String> title, Optional<SessionVisibility> visibility) {
        CHANNEL.sendToServer(new C2SUpdateSessionPacket(PROTOCOL_VERSION, sessionId, title, visibility));
    }

    private static void writeSummary(FriendlyByteBuf buf, SessionSummary summary) {
        buf.writeUUID(summary.sessionId());
        buf.writeUUID(summary.ownerId());
        buf.writeUtf(summary.ownerName(), 64);
        buf.writeVarInt(summary.visibility().ordinal());
        buf.writeBoolean(summary.teamId().isPresent());
        summary.teamId().ifPresent(id -> buf.writeUtf(id, 64));
        buf.writeUtf(summary.title(), 128);
        buf.writeLong(summary.createdAtMillis());
        buf.writeLong(summary.lastActiveMillis());
    }

    private static SessionSummary readSummary(FriendlyByteBuf buf) {
        UUID sessionId = buf.readUUID();
        UUID ownerId = buf.readUUID();
        String ownerName = buf.readUtf(64);
        SessionVisibility visibility = SessionVisibility.values()[buf.readVarInt()];
        Optional<String> teamId = buf.readBoolean() ? Optional.of(buf.readUtf(64)) : Optional.empty();
        String title = buf.readUtf(128);
        long createdAt = buf.readLong();
        long lastActive = buf.readLong();
        return new SessionSummary(sessionId, ownerId, ownerName, visibility, teamId, title, createdAt, lastActive);
    }

    public static void sendSessionSnapshot(ServerPlayer player) {
        SessionSnapshot snapshot = SESSIONS.getActive(player.getUUID(), player.getGameProfile().getName());
        if (!canView(player, snapshot)) {
            snapshot = SESSIONS.create(player.getUUID(), player.getGameProfile().getName());
            SESSIONS.setActive(player.getUUID(), snapshot.metadata().sessionId());
        }
        if (!ChatAE.RECIPE_INDEX.isReady()
                && (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.DONE)) {
            snapshot = SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.INDEXING);
        }
        CHANNEL.sendToPlayer(player, new S2CSessionSnapshotPacket(PROTOCOL_VERSION, snapshot));
    }

    private static void handleChatPacket(ServerPlayer player, String text) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();

        SessionSnapshot snapshot = SESSIONS.getActive(playerId, player.getGameProfile().getName());
        if (!canView(player, snapshot)) {
            return;
        }
        SESSIONS.appendMessage(snapshot.metadata().sessionId(), new ChatMessage(ChatRole.USER, text, now));
        SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.THINKING);
        sendSessionSnapshot(player);

        parseCommandAsync(playerId, text)
                .whenComplete((outcome, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        applyParseFailure(player, "LLM error: " + error.getMessage());
                        return;
                    }
                    handleParsedOutcome(player, outcome, snapshot.metadata().sessionId());
                }));
    }

    private static void writeSnapshot(FriendlyByteBuf buf, SessionSnapshot snapshot) {
        SessionMetadata meta = snapshot.metadata();
        buf.writeUUID(meta.sessionId());
        buf.writeUUID(meta.ownerId());
        buf.writeUtf(meta.ownerName(), 64);
        buf.writeVarInt(meta.visibility().ordinal());
        buf.writeBoolean(meta.teamId().isPresent());
        meta.teamId().ifPresent(id -> buf.writeUtf(id, 64));
        buf.writeUtf(meta.title(), 128);
        buf.writeLong(meta.createdAtMillis());
        buf.writeLong(meta.lastActiveMillis());

        buf.writeVarInt(snapshot.state().ordinal());

        List<ChatMessage> messages = snapshot.messages();
        buf.writeVarInt(messages.size());
        for (ChatMessage message : messages) {
            buf.writeVarInt(message.role().ordinal());
            buf.writeUtf(message.text(), 1024);
            buf.writeLong(message.timestampMillis());
        }

        boolean hasError = snapshot.lastError().isPresent();
        buf.writeBoolean(hasError);
        if (hasError) {
            buf.writeUtf(snapshot.lastError().orElse(""), 1024);
        }

        boolean hasProposal = snapshot.pendingProposal().isPresent();
        buf.writeBoolean(hasProposal);
        if (hasProposal) {
            Proposal proposal = snapshot.pendingProposal().orElseThrow();
            buf.writeUtf(proposal.id(), 64);
            buf.writeVarInt(proposal.riskLevel().ordinal());
            buf.writeUtf(proposal.summary(), 512);
            buf.writeUtf(proposal.toolCall().toolName(), 128);
            buf.writeUtf(proposal.toolCall().argsJson(), 2048);
            buf.writeLong(proposal.createdAtMillis());
            ProposalDetails details = proposal.details();
            buf.writeUtf(details.action(), 64);
            buf.writeUtf(details.itemId(), 256);
            buf.writeLong(details.count());
            buf.writeVarInt(details.missingItems().size());
            for (String missing : details.missingItems()) {
                buf.writeUtf(missing, 256);
            }
            buf.writeUtf(details.note(), 512);
        }
    }

    private static SessionSnapshot readSnapshot(FriendlyByteBuf buf) {
        UUID sessionId = buf.readUUID();
        UUID ownerId = buf.readUUID();
        String ownerName = buf.readUtf(64);
        SessionVisibility visibility = SessionVisibility.values()[buf.readVarInt()];
        Optional<String> teamId = buf.readBoolean() ? Optional.of(buf.readUtf(64)) : Optional.empty();
        String title = buf.readUtf(128);
        long createdAt = buf.readLong();
        long lastActive = buf.readLong();
        SessionMetadata metadata = new SessionMetadata(sessionId, ownerId, ownerName, visibility, teamId, title, createdAt, lastActive);

        SessionState state = SessionState.values()[buf.readVarInt()];

        int messageCount = buf.readVarInt();
        List<ChatMessage> messages = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            ChatRole role = ChatRole.values()[buf.readVarInt()];
            String text = buf.readUtf(1024);
            long ts = buf.readLong();
            messages.add(new ChatMessage(role, text, ts));
        }

        Optional<String> error = Optional.empty();
        if (buf.readBoolean()) {
            error = Optional.of(buf.readUtf(1024));
        }

        Proposal proposal = null;
        if (buf.readBoolean()) {
            String id = buf.readUtf(64);
            RiskLevel risk = RiskLevel.values()[buf.readVarInt()];
            String summary = buf.readUtf(512);
            String toolName = buf.readUtf(128);
            String argsJson = buf.readUtf(2048);
            long proposalCreatedAt = buf.readLong();
            String action = buf.readUtf(64);
            String itemId = buf.readUtf(256);
            long count = buf.readLong();
            int missingCount = buf.readVarInt();
            List<String> missingItems = new ArrayList<>(missingCount);
            for (int i = 0; i < missingCount; i++) {
                missingItems.add(buf.readUtf(256));
            }
            String note = buf.readUtf(512);
            ProposalDetails details = new ProposalDetails(action, itemId, count, missingItems, note);
            proposal = new Proposal(id, risk, summary, new ToolCall(toolName, argsJson), proposalCreatedAt, details);
        }

        return new SessionSnapshot(metadata, messages, state, Optional.ofNullable(proposal), error);
    }

    private static void handleParsedOutcome(ServerPlayer player, ParseOutcome outcome, UUID sessionId) {
        if (outcome == null || outcome.call() == null) {
            if (outcome != null && outcome.errorMessage() != null) {
                SESSIONS.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, "Error: " + outcome.errorMessage(), System.currentTimeMillis()));
                if ("llm_timeout".equals(outcome.errorCode()) || "llm_failed".equals(outcome.errorCode())) {
                    SESSIONS.setError(sessionId, outcome.errorMessage());
                } else {
                    SESSIONS.setState(sessionId, SessionState.IDLE);
                }
            } else {
                SESSIONS.setState(sessionId, SessionState.IDLE);
            }
            sendSessionSnapshot(player);
            return;
        }

        SESSIONS.setState(sessionId, SessionState.EXECUTING);
        long start = System.currentTimeMillis();
        ToolOutcome toolOutcome = AGENT.run(player, outcome.call());
        long duration = System.currentTimeMillis() - start;

        if (toolOutcome.hasProposal()) {
            SESSIONS.setProposal(sessionId, toolOutcome.proposal());
        } else if (toolOutcome.result() != null) {
            applyToolResult(player, outcome.call(), toolOutcome.result(), "AUTO_APPROVE", duration, sessionId);
        } else {
            SESSIONS.setState(sessionId, SessionState.IDLE);
        }

        sendSessionSnapshot(player);
    }

    private static void applyParseFailure(ServerPlayer player, String message) {
        SessionSnapshot snapshot = SESSIONS.getActive(player.getUUID(), player.getGameProfile().getName());
        if (!canView(player, snapshot)) {
            return;
        }
        SESSIONS.appendMessage(snapshot.metadata().sessionId(), new ChatMessage(ChatRole.ASSISTANT, "Error: " + message, System.currentTimeMillis()));
        SESSIONS.setError(snapshot.metadata().sessionId(), message);
        sendSessionSnapshot(player);
    }

    private static void applyToolResult(ServerPlayer player, ToolCall call, ToolResult result, String decision, long durationMillis, UUID sessionId) {
        UUID playerId = player.getUUID();
        String payload = result.payloadJson();
        if (payload == null && result.error() != null) {
            payload = "Error: " + result.error().message();
        }
        if (payload != null && !payload.isBlank()) {
            SESSIONS.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, payload, System.currentTimeMillis()));
        }

        if (result.success()) {
            SESSIONS.setState(sessionId, SessionState.DONE);
        } else if (result.error() != null && "index_not_ready".equals(result.error().code())) {
            SESSIONS.setState(sessionId, SessionState.INDEXING);
        } else if (result.error() != null) {
            SESSIONS.setError(sessionId, result.error().message());
        } else {
            SESSIONS.setState(sessionId, SessionState.FAILED);
        }

        RiskLevel risk = space.controlnet.chatae.core.tools.ToolPolicy.classifyRisk(call.toolName());
        AuditOutcome outcome = result.success()
                ? AuditOutcome.SUCCESS
                : (result.error() != null && "denied".equals(result.error().code()) ? AuditOutcome.DENIED : AuditOutcome.ERROR);

        AuditLogger.instance().log(new AuditEvent(
                playerId.toString(),
                System.currentTimeMillis(),
                call.toolName(),
                call.argsJson(),
                risk,
                decision,
                durationMillis,
                outcome,
                result.error() != null ? result.error().message() : null
        ));
    }

    private static CompletableFuture<ParseOutcome> parseCommandAsync(UUID playerId, String raw) {
        return ToolCallParsingService.parseAsync(playerId, raw, LLM_PARSER, LLM_EXECUTOR, LLM_TIMEOUT_MS, LLM_RATE_LIMITER);
    }

    private static boolean canView(ServerPlayer player, SessionSnapshot snapshot) {
        return isOwner(player, snapshot)
                || (snapshot.metadata().visibility() == SessionVisibility.PUBLIC)
                || (snapshot.metadata().visibility() == SessionVisibility.TEAM && inSameTeam(player, snapshot));
    }

    private static boolean canModify(ServerPlayer player, SessionSnapshot snapshot) {
        return canView(player, snapshot);
    }

    private static boolean isOwner(ServerPlayer player, SessionSnapshot snapshot) {
        return player.getUUID().equals(snapshot.metadata().ownerId());
    }

    private static boolean inSameTeam(ServerPlayer player, SessionSnapshot snapshot) {
        if (snapshot.metadata().visibility() != SessionVisibility.TEAM) {
            return false;
        }
        if (!TeamAccess.isTeamFeatureAvailable()) {
            return false;
        }
        Optional<String> playerTeam = TeamAccess.getTeamId(player);
        return playerTeam.isPresent() && snapshot.metadata().teamId().isPresent()
                && playerTeam.get().equals(snapshot.metadata().teamId().get());
    }

    private static void sendSessionList(ServerPlayer player, SessionListScope scope) {
        List<SessionSummary> sessions = SESSIONS.listAll().stream()
                .filter(snapshot -> filterByScope(player, snapshot, scope))
                .map(ChatAENetwork::toSummary)
                .sorted(Comparator.comparing(SessionSummary::lastActiveMillis).reversed())
                .toList();
        CHANNEL.sendToPlayer(player, new S2CSessionListPacket(PROTOCOL_VERSION, sessions));
    }

    private static boolean filterByScope(ServerPlayer player, SessionSnapshot snapshot, SessionListScope scope) {
        SessionMetadata meta = snapshot.metadata();
        return switch (scope) {
            case MY -> meta.ownerId().equals(player.getUUID());
            case TEAM -> meta.visibility() == SessionVisibility.TEAM && inSameTeam(player, snapshot);
            case PUBLIC -> meta.visibility() == SessionVisibility.PUBLIC;
            case ALL -> canView(player, snapshot) || meta.ownerId().equals(player.getUUID());
        };
    }

    private static SessionSummary toSummary(SessionSnapshot snapshot) {
        SessionMetadata meta = snapshot.metadata();
        return new SessionSummary(meta.sessionId(), meta.ownerId(), meta.ownerName(), meta.visibility(), meta.teamId(), meta.title(), meta.createdAtMillis(), meta.lastActiveMillis());
    }

    private static void handleOpenSession(ServerPlayer player, UUID sessionId) {
        Optional<SessionSnapshot> snapshot = SESSIONS.get(sessionId);
        if (snapshot.isEmpty()) {
            return;
        }
        if (!canView(player, snapshot.get())) {
            return;
        }
        SESSIONS.setActive(player.getUUID(), sessionId);
        sendSessionSnapshot(player);
    }

    private static void handleCreateSession(ServerPlayer player) {
        SessionSnapshot created = SESSIONS.create(player.getUUID(), player.getGameProfile().getName());
        SESSIONS.setActive(player.getUUID(), created.metadata().sessionId());
        sendSessionSnapshot(player);
        sendSessionList(player, SessionListScope.MY);
    }

    private static void handleDeleteSession(ServerPlayer player, UUID sessionId) {
        Optional<SessionSnapshot> snapshot = SESSIONS.get(sessionId);
        if (snapshot.isEmpty()) {
            return;
        }
        if (!isOwner(player, snapshot.get())) {
            return;
        }
        SESSIONS.delete(sessionId);
        if (SESSIONS.getActiveSessionId(player.getUUID()).isEmpty()) {
            SessionSnapshot created = SESSIONS.create(player.getUUID(), player.getGameProfile().getName());
            SESSIONS.setActive(player.getUUID(), created.metadata().sessionId());
            sendSessionSnapshot(player);
        }
        sendSessionList(player, SessionListScope.MY);
    }

    private static void handleUpdateSession(ServerPlayer player, C2SUpdateSessionPacket packet) {
        Optional<SessionSnapshot> snapshot = SESSIONS.get(packet.sessionId());
        if (snapshot.isEmpty()) {
            return;
        }
        if (!isOwner(player, snapshot.get())) {
            return;
        }
        if (packet.title().isPresent()) {
            SESSIONS.rename(packet.sessionId(), packet.title().get());
        }
        if (packet.visibility().isPresent()) {
            SessionVisibility visibility = packet.visibility().get();
            Optional<String> teamId = Optional.empty();
            if (visibility == SessionVisibility.TEAM && TeamAccess.isTeamFeatureAvailable()) {
                teamId = TeamAccess.getTeamId(player);
            }
            if (visibility != SessionVisibility.TEAM || teamId.isPresent()) {
                SESSIONS.setVisibility(packet.sessionId(), visibility, teamId);
            }
        }
        sendSessionList(player, SessionListScope.MY);
        sendSessionSnapshot(player);
    }

    private static final class AgentInvoker {
        private Object runner;
        private boolean initAttempted;

        ToolOutcome run(ServerPlayer player, ToolCall call) {
            Object instance = ensureRunner();
            if (instance == null) {
                return ToolOutcome.result(ToolResult.error("no_agent", "Agent runner not available"));
            }
            try {
                java.lang.reflect.Method runMethod = instance.getClass().getMethod("run", ServerPlayer.class, ToolCall.class);
                Object result = runMethod.invoke(instance, player, call);
                return result instanceof ToolOutcome outcome ? outcome : ToolOutcome.result(ToolResult.error("invalid_result", "Agent returned invalid result"));
            } catch (Throwable t) {
                ChatAE.LOGGER.error("Agent invocation failed, disabling agent", t);
                runner = null;
                return ToolOutcome.result(ToolResult.error("agent_error", "Agent invocation failed: " + t.getMessage()));
            }
        }

        private Object ensureRunner() {
            if (runner != null || initAttempted) {
                return runner;
            }
            initAttempted = true;
            try {
                Class<?> clazz = Class.forName("space.controlnet.chatae.common.agent.AgentRunner");
                runner = clazz.getConstructor().newInstance();
                return runner;
            } catch (Throwable t) {
                ChatAE.LOGGER.warn("Agent runtime unavailable, continuing without LLM support", t);
                runner = null;
                return null;
            }
        }
    }

    private static void handleApprovalDecision(ServerPlayer player, C2SApprovalDecisionPacket packet) {
        SessionSnapshot snapshot = SESSIONS.getActive(player.getUUID(), player.getGameProfile().getName());
        if (!canView(player, snapshot)) {
            return;
        }
        Optional<Proposal> pending = snapshot.pendingProposal();
        if (pending.isEmpty()) {
            return;
        }

        Proposal proposal = pending.get();
        if (!proposal.id().equals(packet.proposalId())) {
            return;
        }

        if (packet.decision() == ApprovalDecision.DENY) {
            AuditLogger.instance().log(new AuditEvent(
                    player.getUUID().toString(),
                    System.currentTimeMillis(),
                    proposal.toolCall().toolName(),
                    proposal.toolCall().argsJson(),
                    proposal.riskLevel(),
                    packet.decision().name(),
                    0,
                    AuditOutcome.DENIED,
                    "User denied proposal"
            ));

            SESSIONS.clearProposal(snapshot.metadata().sessionId());
            SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.IDLE);
            sendSessionSnapshot(player);
            return;
        }

        SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.EXECUTING);
        long start = System.currentTimeMillis();
        ToolOutcome outcome = ToolRouter.execute(TerminalContextFactory.fromPlayer(player), proposal.toolCall(), true);
        long duration = System.currentTimeMillis() - start;

        AuditLogger.instance().log(new AuditEvent(
                player.getUUID().toString(),
                System.currentTimeMillis(),
                proposal.toolCall().toolName(),
                proposal.toolCall().argsJson(),
                proposal.riskLevel(),
                packet.decision().name(),
                duration,
                outcome.result() != null && outcome.result().success() ? AuditOutcome.SUCCESS : AuditOutcome.ERROR,
                outcome.result() != null && outcome.result().error() != null ? outcome.result().error().message() : null
        ));

        SESSIONS.clearProposal(snapshot.metadata().sessionId());

        if (outcome.result() != null) {
            applyToolResult(player, proposal.toolCall(), outcome.result(), packet.decision().name(), duration, snapshot.metadata().sessionId());
        } else {
            SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.DONE);
        }

        sendSessionSnapshot(player);
    }

}
