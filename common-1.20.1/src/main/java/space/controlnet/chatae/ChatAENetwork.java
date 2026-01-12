package space.controlnet.chatae;

import com.google.gson.Gson;
import dev.architectury.networking.NetworkChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.audit.AuditLogger;
import space.controlnet.chatae.core.audit.AuditEvent;
import space.controlnet.chatae.core.audit.AuditOutcome;
import space.controlnet.chatae.core.client.ClientSessionStore;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.ApprovalDecision;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalDetails;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;
import space.controlnet.chatae.core.session.ServerSessionManager;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.tools.ToolArgs;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;
import space.controlnet.chatae.core.tools.ToolResult;
import space.controlnet.chatae.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatae.net.c2s.C2SSendChatPacket;
import space.controlnet.chatae.net.s2c.S2CSessionSnapshotPacket;
import space.controlnet.chatae.tools.ToolRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatAENetwork {
    private static final NetworkChannel CHANNEL = NetworkChannel.create(ChatAERegistries.id("main"));
    private static final Gson GSON = new Gson();
    private static final int PROTOCOL_VERSION = 1;

    public static final ServerSessionManager SESSIONS = new ServerSessionManager();
    private static final AgentInvoker AGENT = new AgentInvoker();
    private static final LlmToolParser LLM_PARSER = new LlmToolParser();
    private static final ExecutorService LLM_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chatae-llm");
        t.setDaemon(true);
        return t;
    });
    private static final ConcurrentHashMap<UUID, AtomicLong> LAST_LLM_CALL = new ConcurrentHashMap<>();
    private static final long LLM_TIMEOUT_MS = 5000;
    private static final long LLM_COOLDOWN_MS = 1500;

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

    public static void sendSessionSnapshot(ServerPlayer player) {
        SessionSnapshot snapshot = SESSIONS.get(player.getUUID());
        if (!ChatAE.RECIPE_INDEX.isReady()
                && (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.DONE)) {
            snapshot = SESSIONS.setState(player.getUUID(), SessionState.INDEXING);
        }
        CHANNEL.sendToPlayer(player, new S2CSessionSnapshotPacket(PROTOCOL_VERSION, snapshot));
    }

    private static void handleChatPacket(ServerPlayer player, String text) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();

        SESSIONS.appendMessage(playerId, new ChatMessage(ChatRole.USER, text, now));
        SESSIONS.setState(playerId, SessionState.THINKING);
        sendSessionSnapshot(player);

        parseCommandAsync(playerId, text)
                .whenComplete((outcome, error) -> player.getServer().execute(() -> {
                    if (error != null) {
                        applyParseFailure(player, "LLM error: " + error.getMessage());
                        return;
                    }
                    handleParsedOutcome(player, outcome);
                }));
    }

    private static void writeSnapshot(FriendlyByteBuf buf, SessionSnapshot snapshot) {
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
            long createdAt = buf.readLong();
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
            proposal = new Proposal(id, risk, summary, new ToolCall(toolName, argsJson), createdAt, details);
        }

        return new SessionSnapshot(messages, state, Optional.ofNullable(proposal), error);
    }

    private static void handleParsedOutcome(ServerPlayer player, ParseOutcome outcome) {
        UUID playerId = player.getUUID();
        if (outcome == null || outcome.call() == null) {
            if (outcome != null && outcome.errorMessage() != null) {
                SESSIONS.appendMessage(playerId, new ChatMessage(ChatRole.ASSISTANT, "Error: " + outcome.errorMessage(), System.currentTimeMillis()));
                if ("llm_timeout".equals(outcome.errorCode()) || "llm_failed".equals(outcome.errorCode())) {
                    SESSIONS.setError(playerId, outcome.errorMessage());
                } else {
                    SESSIONS.setState(playerId, SessionState.IDLE);
                }
            } else {
                SESSIONS.setState(playerId, SessionState.IDLE);
            }
            sendSessionSnapshot(player);
            return;
        }

        SESSIONS.setState(playerId, SessionState.EXECUTING);
        long start = System.currentTimeMillis();
        ToolOutcome toolOutcome = AGENT.run(player, outcome.call());
        long duration = System.currentTimeMillis() - start;

        if (toolOutcome.hasProposal()) {
            SESSIONS.setProposal(playerId, toolOutcome.proposal());
        } else if (toolOutcome.result() != null) {
            applyToolResult(player, outcome.call(), toolOutcome.result(), "AUTO_APPROVE", duration);
        } else {
            SESSIONS.setState(playerId, SessionState.IDLE);
        }

        sendSessionSnapshot(player);
    }

    private static void applyParseFailure(ServerPlayer player, String message) {
        UUID playerId = player.getUUID();
        SESSIONS.appendMessage(playerId, new ChatMessage(ChatRole.ASSISTANT, "Error: " + message, System.currentTimeMillis()));
        SESSIONS.setError(playerId, message);
        sendSessionSnapshot(player);
    }

    private static void applyToolResult(ServerPlayer player, ToolCall call, ToolResult result, String decision, long durationMillis) {
        UUID playerId = player.getUUID();
        String payload = result.payloadJson();
        if (payload == null && result.error() != null) {
            payload = "Error: " + result.error().message();
        }
        if (payload != null && !payload.isBlank()) {
            SESSIONS.appendMessage(playerId, new ChatMessage(ChatRole.ASSISTANT, payload, System.currentTimeMillis()));
        }

        if (result.success()) {
            SESSIONS.setState(playerId, SessionState.DONE);
        } else if (result.error() != null && "index_not_ready".equals(result.error().code())) {
            SESSIONS.setState(playerId, SessionState.INDEXING);
        } else if (result.error() != null) {
            SESSIONS.setError(playerId, result.error().message());
        } else {
            SESSIONS.setState(playerId, SessionState.FAILED);
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
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return CompletableFuture.completedFuture(new ParseOutcome(null, "empty", "Empty command"));
        }

        ToolCall local = parseCommandLocal(text);
        if (local != null) {
            return CompletableFuture.completedFuture(new ParseOutcome(local, null, null));
        }

        if (!LLM_PARSER.isAvailable()) {
            return CompletableFuture.completedFuture(new ParseOutcome(null, "llm_unavailable", "LLM unavailable"));
        }

        if (!allowLlm(playerId)) {
            return CompletableFuture.completedFuture(new ParseOutcome(null, "llm_rate_limited", "LLM rate limit exceeded"));
        }

        return CompletableFuture.supplyAsync(() -> LLM_PARSER.parse(text).orElse(null), LLM_EXECUTOR)
                .orTimeout(LLM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .handle((toolCall, error) -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                        if (cause instanceof java.util.concurrent.TimeoutException) {
                            return new ParseOutcome(null, "llm_timeout", "LLM request timed out");
                        }
                        return new ParseOutcome(null, "llm_failed", "LLM request failed");
                    }
                    if (toolCall == null) {
                        return new ParseOutcome(null, "unknown_command", "Unknown command");
                    }
                    return new ParseOutcome(toolCall, null, null);
                });
    }

    private static boolean allowLlm(UUID playerId) {
        AtomicLong last = LAST_LLM_CALL.computeIfAbsent(playerId, id -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        long prev = last.get();
        if (now - prev < LLM_COOLDOWN_MS) {
            return false;
        }
        last.set(now);
        return true;
    }

    private static ToolCall parseCommandLocal(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("recipes.search ")) {
            String query = text.substring("recipes.search ".length()).trim();
            return new ToolCall("recipes.search", GSON.toJson(new ToolArgs.RecipeSearchArgs(
                    query, null, 10, null, null, null, null, null)));
        }

        if (lower.startsWith("recipes.get ")) {
            String id = text.substring("recipes.get ".length()).trim();
            return new ToolCall("recipes.get", GSON.toJson(new ToolArgs.RecipeGetArgs(id)));
        }

        if (lower.startsWith("ae2.list_items")) {
            String query = parseOptionalArg(text, "ae2.list_items");
            return new ToolCall("ae2.list_items", GSON.toJson(new ToolArgs.Ae2ListArgs(query, false, 50, null)));
        }

        if (lower.startsWith("ae2.list_craftables")) {
            String query = parseOptionalArg(text, "ae2.list_craftables");
            return new ToolCall("ae2.list_craftables", GSON.toJson(new ToolArgs.Ae2ListArgs(query, true, 50, null)));
        }

        if (lower.startsWith("ae2.simulate_craft ")) {
            String args = text.substring("ae2.simulate_craft ".length()).trim();
            String[] parts = args.split("\\s+", 2);
            String itemId = parts[0];
            long count = parts.length > 1 ? parseLong(parts[1], 1) : 1;
            return new ToolCall("ae2.simulate_craft", GSON.toJson(new ToolArgs.Ae2CraftArgs(itemId, count, null)));
        }

        if (lower.startsWith("ae2.request_craft ")) {
            String args = text.substring("ae2.request_craft ".length()).trim();
            String[] parts = args.split("\\s+", 2);
            String itemId = parts[0];
            long count = parts.length > 1 ? parseLong(parts[1], 1) : 1;
            return new ToolCall("ae2.request_craft", GSON.toJson(new ToolArgs.Ae2CraftArgs(itemId, count, null)));
        }

        if (lower.startsWith("ae2.job_status ")) {
            String jobId = text.substring("ae2.job_status ".length()).trim();
            return new ToolCall("ae2.job_status", GSON.toJson(new ToolArgs.Ae2JobArgs(jobId)));
        }

        if (lower.startsWith("ae2.job_cancel ")) {
            String jobId = text.substring("ae2.job_cancel ".length()).trim();
            return new ToolCall("ae2.job_cancel", GSON.toJson(new ToolArgs.Ae2JobArgs(jobId)));
        }

        return null;
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
                Class<?> clazz = Class.forName("space.controlnet.chatae.agent.AgentRunner");
                runner = clazz.getConstructor().newInstance();
                return runner;
            } catch (Throwable t) {
                ChatAE.LOGGER.warn("Agent runtime unavailable, continuing without LLM support", t);
                runner = null;
                return null;
            }
        }
    }

    private static final class LlmToolParser {
        private Object parser;
        private boolean initAttempted;

        boolean isAvailable() {
            ensureParser();
            return parser != null;
        }

        Optional<ToolCall> parse(String text) {
            Object instance = ensureParser();
            if (instance == null) {
                return Optional.empty();
            }
            try {
                java.lang.reflect.Method parseMethod = instance.getClass().getMethod("parse", String.class);
                Object result = parseMethod.invoke(instance, text);
                if (result instanceof Optional<?> optional) {
                    Object value = optional.orElse(null);
                    return value instanceof ToolCall call ? Optional.of(call) : Optional.empty();
                }
                return Optional.empty();
            } catch (Throwable t) {
                ChatAE.LOGGER.warn("LLM parse failed", t);
                return Optional.empty();
            }
        }

        private Object ensureParser() {
            if (parser != null || initAttempted) {
                return parser;
            }
            initAttempted = true;
            try {
                Class<?> clazz = Class.forName("space.controlnet.chatae.core.agent.LangChainToolCallParser");
                java.lang.reflect.Method createMethod = clazz.getMethod("create", space.controlnet.chatae.core.agent.Logger.class);
                Object result = createMethod.invoke(null, (space.controlnet.chatae.core.agent.Logger) (msg, ex) -> ChatAE.LOGGER.warn(msg, ex));
                if (result instanceof Optional<?> optional) {
                    parser = optional.orElse(null);
                }
            } catch (Throwable t) {
                ChatAE.LOGGER.warn("LLM runtime unavailable, continuing without LLM support", t);
                parser = null;
            }
            return parser;
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String parseOptionalArg(String text, String prefix) {
        String remainder = text.substring(prefix.length()).trim();
        return remainder.isEmpty() ? "" : remainder;
    }

    private static void handleApprovalDecision(ServerPlayer player, C2SApprovalDecisionPacket packet) {
        SessionSnapshot snapshot = SESSIONS.get(player.getUUID());
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

            SESSIONS.clearProposal(player.getUUID());
            SESSIONS.setState(player.getUUID(), SessionState.IDLE);
            sendSessionSnapshot(player);
            return;
        }

        SESSIONS.setState(player.getUUID(), SessionState.EXECUTING);
        long start = System.currentTimeMillis();
        ToolOutcome outcome = ToolRouter.execute(player, proposal.toolCall(), true);
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

        SESSIONS.clearProposal(player.getUUID());

        if (outcome.result() != null) {
            applyToolResult(player, proposal.toolCall(), outcome.result(), packet.decision().name(), duration);
        } else {
            SESSIONS.setState(player.getUUID(), SessionState.DONE);
        }

        sendSessionSnapshot(player);
    }

    private record ParseOutcome(ToolCall call, String errorCode, String errorMessage) {
    }
}
