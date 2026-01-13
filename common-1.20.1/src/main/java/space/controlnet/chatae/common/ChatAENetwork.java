package space.controlnet.chatae.common;

import dev.architectury.networking.NetworkChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.common.llm.PromptRuntime;
import space.controlnet.chatae.common.audit.AuditLogger;
import space.controlnet.chatae.common.team.TeamAccess;
import space.controlnet.chatae.common.terminal.TerminalContextFactory;
import space.controlnet.chatae.common.session.ChatAESessionsSavedData;
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
import space.controlnet.chatae.core.session.DecisionLogEntry;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.session.SessionVisibility;
import space.controlnet.chatae.core.session.TerminalBinding;
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
    private static final int PROTOCOL_VERSION = 3;

    public static final ServerSessionManager SESSIONS = new ServerSessionManager();
    private static final AgentInvoker AGENT = new AgentInvoker();
    private static final ReflectiveToolCallParser LLM_PARSER = new ReflectiveToolCallParser((msg, ex) -> ChatAE.LOGGER.warn(msg, ex));
    private static final ExecutorService LLM_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chatae-llm");
        t.setDaemon(true);
        return t;
    });
    private static final long LLM_TIMEOUT_MS = 5000;
    private static final java.util.concurrent.atomic.AtomicLong LLM_COOLDOWN_MS = new java.util.concurrent.atomic.AtomicLong(1500);
    private static final LlmRateLimiter LLM_RATE_LIMITER = new LlmRateLimiter(LLM_COOLDOWN_MS.get());

    private static final String TOOL_LIST = String.join(", ",
            "recipes.search",
            "recipes.get",
            "ae2.list_items",
            "ae2.list_craftables",
            "ae2.simulate_craft",
            "ae2.request_craft",
            "ae2.job_status",
            "ae2.job_cancel"
    );

    private static final String ARGS_SCHEMA = "- recipes.search: {query, pageToken?, limit, modId?, recipeType?, outputItemId?, ingredientItemId?, tagId?}\n"
            + "- recipes.get: {recipeId}\n"
            + "- ae2.list_items: {query, craftableOnly, limit, pageToken?}\n"
            + "- ae2.list_craftables: {query, craftableOnly, limit, pageToken?}\n"
            + "- ae2.simulate_craft: {itemId, count}\n"
            + "- ae2.request_craft: {itemId, count, cpuName?}\n"
            + "- ae2.job_status: {jobId}\n"
            + "- ae2.job_cancel: {jobId}.";

    private static final java.util.concurrent.atomic.AtomicReference<MinecraftServer> SERVER = new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<ChatAESessionsSavedData> SAVED_SESSIONS = new java.util.concurrent.atomic.AtomicReference<>();

    private static final java.util.concurrent.ConcurrentHashMap<UUID, java.util.Set<UUID>> VIEWERS_BY_SESSION = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, UUID> SESSION_BY_VIEWER = new java.util.concurrent.ConcurrentHashMap<>();

    private ChatAENetwork() {
    }

    public static void init() {
        CHANNEL.register(
                C2SSendChatPacket.class,
                (packet, buf) -> {
                    buf.writeVarInt(packet.protocolVersion());
                    buf.writeUtf(packet.text(), 256);
                    buf.writeUtf(packet.clientLocale(), 32);
                    buf.writeUtf(packet.aiLocaleOverride(), 32);
                },
                buf -> new C2SSendChatPacket(buf.readVarInt(), buf.readUtf(256), buf.readUtf(32), buf.readUtf(32)),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    if (packet.protocolVersion() != PROTOCOL_VERSION) {
                        return;
                    }
                    handleChatPacket(player, packet.text(), packet.clientLocale(), packet.aiLocaleOverride());
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

    public static void setServer(MinecraftServer server) {
        if (server == null) {
            ChatAESessionsSavedData saved = SAVED_SESSIONS.getAndSet(null);
            if (saved != null) {
                saved.setData(SESSIONS.exportForSave());
            }
            SERVER.set(null);
            VIEWERS_BY_SESSION.clear();
            SESSION_BY_VIEWER.clear();
            return;
        }

        SERVER.set(server);

        ChatAESessionsSavedData saved = ChatAESessionsSavedData.get(server);
        SAVED_SESSIONS.set(saved);
        SESSIONS.loadFromSave(saved.data());

        VIEWERS_BY_SESSION.clear();
        SESSION_BY_VIEWER.clear();
    }

    public static void updateLlmCooldown(long cooldownMillis) {
        if (cooldownMillis < 0) {
            return;
        }
        LLM_COOLDOWN_MS.set(cooldownMillis);
        LLM_RATE_LIMITER.setCooldownMillis(cooldownMillis);
    }

    public static void onTerminalOpened(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SessionSnapshot snapshot = SESSIONS.getActive(player.getUUID(), player.getGameProfile().getName());
        if (!canView(player, snapshot)) {
            snapshot = SESSIONS.create(player.getUUID(), player.getGameProfile().getName());
            SESSIONS.setActive(player.getUUID(), snapshot.metadata().sessionId());
        }
        subscribeViewer(player.getUUID(), snapshot.metadata().sessionId());
        persistSessions();
        broadcastSessionSnapshot(snapshot.metadata().sessionId());
    }

    public static void onTerminalClosed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        unsubscribeViewer(player.getUUID());
    }

    private static void subscribeViewer(UUID playerId, UUID sessionId) {
        UUID previous = SESSION_BY_VIEWER.put(playerId, sessionId);
        if (previous != null && !previous.equals(sessionId)) {
            java.util.Set<UUID> prevSet = VIEWERS_BY_SESSION.get(previous);
            if (prevSet != null) {
                prevSet.remove(playerId);
                if (prevSet.isEmpty()) {
                    VIEWERS_BY_SESSION.remove(previous, prevSet);
                }
            }
        }
        VIEWERS_BY_SESSION.computeIfAbsent(sessionId, ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(playerId);
    }

    private static void unsubscribeViewer(UUID playerId) {
        UUID sessionId = SESSION_BY_VIEWER.remove(playerId);
        if (sessionId == null) {
            return;
        }
        java.util.Set<UUID> set = VIEWERS_BY_SESSION.get(sessionId);
        if (set != null) {
            set.remove(playerId);
            if (set.isEmpty()) {
                VIEWERS_BY_SESSION.remove(sessionId, set);
            }
        }
    }

    private static void broadcastSessionSnapshot(UUID sessionId) {
        MinecraftServer server = SERVER.get();
        if (server == null) {
            return;
        }
        java.util.Set<UUID> viewers = VIEWERS_BY_SESSION.get(sessionId);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        SessionSnapshot base = SESSIONS.get(sessionId).orElse(null);
        if (base == null) {
            for (UUID viewerId : List.copyOf(viewers)) {
                unsubscribeViewer(viewerId);
            }
            return;
        }

        for (UUID viewerId : List.copyOf(viewers)) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) {
                unsubscribeViewer(viewerId);
                continue;
            }
            if (!canView(viewer, base)) {
                unsubscribeViewer(viewerId);
                continue;
            }
            SessionSnapshot snapshot = ensureIndexingStateIfNeeded(base);
            CHANNEL.sendToPlayer(viewer, new S2CSessionSnapshotPacket(PROTOCOL_VERSION, snapshot));
        }
    }

    private static SessionSnapshot ensureIndexingStateIfNeeded(SessionSnapshot snapshot) {
        if (!ChatAE.RECIPE_INDEX.isReady()
                && (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.DONE)) {
            SessionSnapshot updated = SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.INDEXING);
            persistSessions();
            return updated;
        }
        return snapshot;
    }

    private static String resolveEffectiveLocale(String clientLocale, String aiLocaleOverride) {
        String override = aiLocaleOverride == null ? "" : aiLocaleOverride.trim();
        if (!override.isBlank()) {
            return override;
        }
        String locale = clientLocale == null ? "" : clientLocale.trim();
        return locale.isBlank() ? "en_us" : locale;
    }

    private static void persistSessions() {
        ChatAESessionsSavedData saved = SAVED_SESSIONS.get();
        if (saved == null) {
            return;
        }
        saved.setData(SESSIONS.exportForSave());
    }

    public static void sendChatToServer(String text, String clientLocale, String aiLocaleOverride) {
        String locale = clientLocale == null || clientLocale.isBlank() ? "en_us" : clientLocale;
        String override = aiLocaleOverride == null ? "" : aiLocaleOverride;
        CHANNEL.sendToServer(new C2SSendChatPacket(PROTOCOL_VERSION, text, locale, override));
    }

    public static String getClientLocale() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            String selected = minecraft.getLanguageManager().getSelected();
            if (selected != null && !selected.isBlank()) {
                return selected;
            }
            return minecraft.options.languageCode;
        } catch (Exception e) {
            return "en_us";
        }
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

    private static void handleChatPacket(ServerPlayer player, String text, String clientLocale, String aiLocaleOverride) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();

        SessionSnapshot snapshot = SESSIONS.getActive(playerId, player.getGameProfile().getName());
        if (!canView(player, snapshot)) {
            return;
        }

        UUID sessionId = snapshot.metadata().sessionId();
        if (!SESSIONS.tryStartThinking(sessionId)) {
            broadcastSessionSnapshot(sessionId);
            return;
        }

        Optional<TerminalBinding> binding = captureTerminalBinding(player);
        SESSIONS.appendMessage(sessionId, new ChatMessage(ChatRole.USER, text, now));
        persistSessions();
        broadcastSessionSnapshot(sessionId);

        String effectiveLocale = resolveEffectiveLocale(clientLocale, aiLocaleOverride);
        String renderedPrompt = buildToolParserPrompt(text, effectiveLocale);
        parseCommandAsync(playerId, renderedPrompt, effectiveLocale)
                .whenComplete((outcome, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        applyParseFailure(sessionId, "LLM error: " + error.getMessage());
                        return;
                    }
                    handleParsedOutcome(player, outcome, sessionId, binding.orElse(null));
                }));
    }

    private static Optional<TerminalBinding> captureTerminalBinding(ServerPlayer player) {
        if (!(player.containerMenu instanceof space.controlnet.chatae.common.menu.AiTerminalMenu menu)) {
            return Optional.empty();
        }
        Optional<space.controlnet.chatae.common.terminal.AiTerminalHost> host = menu.getHost();
        if (host.isEmpty()) {
            return Optional.empty();
        }
        net.minecraft.world.level.Level level = host.get().getHostLevel();
        if (level == null) {
            return Optional.empty();
        }

        net.minecraft.core.BlockPos pos = menu.getPos();
        String dimensionId = level.dimension().location().toString();
        Optional<String> side = menu.getSide().map(net.minecraft.core.Direction::name);
        return Optional.of(new TerminalBinding(dimensionId, pos.getX(), pos.getY(), pos.getZ(), side));
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

            boolean hasBinding = snapshot.proposalBinding().isPresent();
            buf.writeBoolean(hasBinding);
            if (hasBinding) {
                TerminalBinding binding = snapshot.proposalBinding().orElseThrow();
                buf.writeUtf(binding.dimensionId(), 128);
                buf.writeInt(binding.x());
                buf.writeInt(binding.y());
                buf.writeInt(binding.z());
                buf.writeBoolean(binding.side().isPresent());
                binding.side().ifPresent(side -> buf.writeUtf(side, 16));
            }
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

            TerminalBinding binding = null;
            if (buf.readBoolean()) {
                String dimensionId = buf.readUtf(128);
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                Optional<String> side = buf.readBoolean() ? Optional.of(buf.readUtf(16)) : Optional.empty();
                binding = new TerminalBinding(dimensionId, x, y, z, side);
            }

            ProposalDetails details = new ProposalDetails(action, itemId, count, missingItems, note);
            proposal = new Proposal(id, risk, summary, new ToolCall(toolName, argsJson), proposalCreatedAt, details);
            return new SessionSnapshot(metadata, messages, state, Optional.of(proposal), Optional.ofNullable(binding), List.of(), error);
        }

        return new SessionSnapshot(metadata, messages, state, Optional.empty(), Optional.empty(), List.of(), error);
    }

    private static void handleParsedOutcome(ServerPlayer player, ParseOutcome outcome, UUID sessionId, TerminalBinding binding) {
        SessionSnapshot current = SESSIONS.get(sessionId).orElse(null);
        if (current == null) {
            return;
        }
        if (current.state() != SessionState.THINKING) {
            broadcastSessionSnapshot(sessionId);
            return;
        }

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
            persistSessions();
            broadcastSessionSnapshot(sessionId);
            return;
        }

        SESSIONS.setState(sessionId, SessionState.EXECUTING);
        long start = System.currentTimeMillis();
        ToolOutcome toolOutcome = AGENT.run(player, outcome.call());
        long duration = System.currentTimeMillis() - start;

        if (toolOutcome.hasProposal()) {
            if (!SESSIONS.trySetProposal(sessionId, toolOutcome.proposal(), binding)) {
                SESSIONS.setState(sessionId, SessionState.IDLE);
            }
        } else if (toolOutcome.result() != null) {
            applyToolResult(player, outcome.call(), toolOutcome.result(), "AUTO_APPROVE", duration, sessionId);
        } else {
            SESSIONS.setState(sessionId, SessionState.IDLE);
        }

        persistSessions();
        broadcastSessionSnapshot(sessionId);
    }

    private static void applyParseFailure(UUID sessionId, String message) {
        SESSIONS.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, "Error: " + message, System.currentTimeMillis()));
        SESSIONS.setError(sessionId, message);
        persistSessions();
        broadcastSessionSnapshot(sessionId);
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
        persistSessions();
    }

    private static CompletableFuture<ParseOutcome> parseCommandAsync(UUID playerId, String prompt, String effectiveLocale) {
        PromptRuntime.promptHash(prompt)
                .ifPresent(hash -> ChatAE.LOGGER.debug("LLM prompt tool_call_parser.main locale={} hash={}", effectiveLocale, hash));

        return ToolCallParsingService.parseAsync(playerId, prompt, LLM_PARSER, LLM_EXECUTOR, LLM_TIMEOUT_MS, LLM_RATE_LIMITER);
    }

    private static String buildToolParserPrompt(String userMessage, String effectiveLocale) {
        java.util.Map<String, String> variables = new java.util.HashMap<>();
        variables.put("tool_list", TOOL_LIST);
        variables.put("args_schema", ARGS_SCHEMA);
        variables.put("effectiveLocale", effectiveLocale);
        variables.put("user_message", userMessage == null ? "" : userMessage);

        return PromptRuntime.render(
                space.controlnet.chatae.core.agent.PromptId.TOOL_CALL_PARSER_MAIN,
                effectiveLocale,
                variables
        );
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
        subscribeViewer(player.getUUID(), sessionId);
        persistSessions();
        broadcastSessionSnapshot(sessionId);
    }

    private static void handleCreateSession(ServerPlayer player) {
        SessionSnapshot created = SESSIONS.create(player.getUUID(), player.getGameProfile().getName());
        SESSIONS.setActive(player.getUUID(), created.metadata().sessionId());
        subscribeViewer(player.getUUID(), created.metadata().sessionId());
        persistSessions();
        broadcastSessionSnapshot(created.metadata().sessionId());
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
        persistSessions();

        java.util.Set<UUID> viewers = VIEWERS_BY_SESSION.remove(sessionId);
        if (viewers != null) {
            for (UUID viewerId : List.copyOf(viewers)) {
                SESSION_BY_VIEWER.remove(viewerId, sessionId);
                ServerPlayer viewer = player.getServer().getPlayerList().getPlayer(viewerId);
                if (viewer != null) {
                    onTerminalOpened(viewer);
                }
            }
        }

        if (SESSIONS.getActiveSessionId(player.getUUID()).isEmpty()) {
            SessionSnapshot created = SESSIONS.create(player.getUUID(), player.getGameProfile().getName());
            SESSIONS.setActive(player.getUUID(), created.metadata().sessionId());
            subscribeViewer(player.getUUID(), created.metadata().sessionId());
            persistSessions();
            broadcastSessionSnapshot(created.metadata().sessionId());
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
        persistSessions();
        sendSessionList(player, SessionListScope.MY);
        broadcastSessionSnapshot(packet.sessionId());
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

        UUID sessionId = snapshot.metadata().sessionId();
        Optional<Proposal> pending = snapshot.pendingProposal();
        if (pending.isEmpty()) {
            return;
        }

        Proposal proposal = pending.get();
        if (!proposal.id().equals(packet.proposalId())) {
            return;
        }

        long now = System.currentTimeMillis();
        SESSIONS.appendDecision(sessionId, new DecisionLogEntry(
                now,
                Optional.of(player.getUUID()),
                Optional.of(player.getGameProfile().getName()),
                proposal.id(),
                Optional.of(proposal.toolCall().toolName()),
                packet.decision()
        ));
        persistSessions();

        if (packet.decision() == ApprovalDecision.DENY) {
            AuditLogger.instance().log(new AuditEvent(
                    player.getUUID().toString(),
                    now,
                    proposal.toolCall().toolName(),
                    proposal.toolCall().argsJson(),
                    proposal.riskLevel(),
                    packet.decision().name(),
                    0,
                    AuditOutcome.DENIED,
                    "User denied proposal"
            ));

            SESSIONS.clearProposal(sessionId);
            persistSessions();
            broadcastSessionSnapshot(sessionId);
            return;
        }

        if (!SESSIONS.tryStartExecuting(sessionId, proposal.id())) {
            broadcastSessionSnapshot(sessionId);
            return;
        }

        Optional<TerminalBinding> binding = snapshot.proposalBinding();
        Optional<space.controlnet.chatae.core.terminal.TerminalContext> context = binding.isPresent()
                ? TerminalContextFactory.fromPlayerAtBinding(player, binding.get())
                : Optional.empty();

        if (context.isEmpty()) {
            SESSIONS.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, "Error: bound terminal unavailable", System.currentTimeMillis()));
            SESSIONS.tryFailProposal(sessionId, proposal.id(), "bound terminal unavailable");
            persistSessions();
            broadcastSessionSnapshot(sessionId);
            return;
        }

        long start = System.currentTimeMillis();
        ToolOutcome outcome = ToolRouter.execute(context, proposal.toolCall(), true);
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

        SESSIONS.clearProposalPreserveState(sessionId);

        if (outcome.result() != null) {
            applyToolResult(player, proposal.toolCall(), outcome.result(), packet.decision().name(), duration, sessionId);
        } else {
            SESSIONS.setState(sessionId, SessionState.DONE);
            persistSessions();
        }

        persistSessions();
        broadcastSessionSnapshot(sessionId);
    }

}
