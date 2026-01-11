package space.controlnet.chatae;

import com.google.gson.Gson;
import dev.architectury.networking.NetworkChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.agent.AgentRunner;
import space.controlnet.chatae.agent.LangChainToolCallParser;
import space.controlnet.chatae.audit.AuditLogger;
import space.controlnet.chatae.client.ClientSessionStore;
import space.controlnet.chatae.core.audit.AuditEvent;
import space.controlnet.chatae.core.audit.AuditOutcome;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.ApprovalDecision;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolResult;
import space.controlnet.chatae.net.c2s.C2SApprovalDecisionPacket;
import space.controlnet.chatae.net.c2s.C2SSendChatPacket;
import space.controlnet.chatae.net.s2c.S2CSessionSnapshotPacket;
import space.controlnet.chatae.session.ServerSessionManager;
import space.controlnet.chatae.tools.ToolRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ChatAENetwork {
    private static final NetworkChannel CHANNEL = NetworkChannel.create(ChatAERegistries.id("main"));
    private static final Gson GSON = new Gson();

    public static final ServerSessionManager SESSIONS = new ServerSessionManager();
    private static final AgentRunner AGENT = new AgentRunner();
    private static final Optional<LangChainToolCallParser> LLM_PARSER = LangChainToolCallParser.create();

    private ChatAENetwork() {
    }

    public static void init() {
        CHANNEL.register(
                C2SSendChatPacket.class,
                (packet, buf) -> buf.writeUtf(packet.text(), 256),
                buf -> new C2SSendChatPacket(buf.readUtf(256)),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    long now = System.currentTimeMillis();

                    SESSIONS.appendMessage(player.getUUID(), new ChatMessage(ChatRole.USER, packet.text(), now));

                    ToolRouter.ToolOutcome outcome = handleChatCommand(player, packet.text());
                    if (outcome.hasProposal()) {
                        SESSIONS.setProposal(player.getUUID(), outcome.proposal());
                    } else if (outcome.result() != null) {
                        String payload = outcome.result().payloadJson();
                        if (payload == null && outcome.result().error() != null) {
                            payload = "Error: " + outcome.result().error().message();
                        }
                        SESSIONS.appendMessage(player.getUUID(), new ChatMessage(ChatRole.ASSISTANT, payload == null ? "" : payload, now));
                    }

                    sendSessionSnapshot(player);
                })
        );

        CHANNEL.register(
                C2SApprovalDecisionPacket.class,
                (packet, buf) -> {
                    buf.writeUtf(packet.proposalId(), 64);
                    buf.writeVarInt(packet.decision().ordinal());
                },
                buf -> new C2SApprovalDecisionPacket(buf.readUtf(64), ApprovalDecision.values()[buf.readVarInt()]),
                (packet, context) -> context.get().queue(() -> {
                    ServerPlayer player = (ServerPlayer) context.get().getPlayer();
                    handleApprovalDecision(player, packet);
                })
        );

        CHANNEL.register(
                S2CSessionSnapshotPacket.class,
                (packet, buf) -> writeSnapshot(buf, packet.snapshot()),
                buf -> new S2CSessionSnapshotPacket(readSnapshot(buf)),
                (packet, context) -> context.get().queue(() -> ClientSessionStore.set(packet.snapshot()))
        );
    }

    public static void sendChatToServer(String text) {
        CHANNEL.sendToServer(new C2SSendChatPacket(text));
    }

    public static void sendApprovalDecision(String proposalId, ApprovalDecision decision) {
        CHANNEL.sendToServer(new C2SApprovalDecisionPacket(proposalId, decision));
    }

    public static void sendSessionSnapshot(ServerPlayer player) {
        SessionSnapshot snapshot = SESSIONS.get(player.getUUID());
        CHANNEL.sendToPlayer(player, new S2CSessionSnapshotPacket(snapshot));
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
            proposal = new Proposal(id, risk, summary, new ToolCall(toolName, argsJson), createdAt);
        }

        return new SessionSnapshot(messages, state, Optional.ofNullable(proposal), error);
    }

    private static ToolRouter.ToolOutcome handleChatCommand(ServerPlayer player, String raw) {
        ToolCall call = parseCommand(raw);
        if (call == null) {
            return ToolRouter.ToolOutcome.result(ToolResult.ok(""));
        }
        return AGENT.run(player, call);
    }

    private static ToolCall parseCommand(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        ToolCall llmCall = LLM_PARSER.flatMap(parser -> parser.parse(text)).orElse(null);
        if (llmCall != null) {
            return llmCall;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("recipes.search ")) {
            String query = text.substring("recipes.search ".length()).trim();
            return new ToolCall("recipes.search", GSON.toJson(new ToolRouter.RecipeSearchArgs(query, null, 10)));
        }

        if (lower.startsWith("recipes.get ")) {
            String id = text.substring("recipes.get ".length()).trim();
            return new ToolCall("recipes.get", GSON.toJson(new ToolRouter.RecipeGetArgs(id)));
        }

        if (lower.startsWith("ae2.list_items")) {
            String query = parseOptionalArg(text, "ae2.list_items");
            return new ToolCall("ae2.list_items", GSON.toJson(new ToolRouter.Ae2ListArgs(query, false, 50, null)));
        }

        if (lower.startsWith("ae2.list_craftables")) {
            String query = parseOptionalArg(text, "ae2.list_craftables");
            return new ToolCall("ae2.list_craftables", GSON.toJson(new ToolRouter.Ae2ListArgs(query, true, 50, null)));
        }

        if (lower.startsWith("ae2.simulate_craft ")) {
            String args = text.substring("ae2.simulate_craft ".length()).trim();
            String[] parts = args.split("\\s+", 2);
            String itemId = parts[0];
            long count = parts.length > 1 ? parseLong(parts[1], 1) : 1;
            return new ToolCall("ae2.simulate_craft", GSON.toJson(new ToolRouter.Ae2CraftArgs(itemId, count, null)));
        }

        if (lower.startsWith("ae2.request_craft ")) {
            String args = text.substring("ae2.request_craft ".length()).trim();
            String[] parts = args.split("\\s+", 2);
            String itemId = parts[0];
            long count = parts.length > 1 ? parseLong(parts[1], 1) : 1;
            return new ToolCall("ae2.request_craft", GSON.toJson(new ToolRouter.Ae2CraftArgs(itemId, count, null)));
        }

        if (lower.startsWith("ae2.job_status ")) {
            String jobId = text.substring("ae2.job_status ".length()).trim();
            return new ToolCall("ae2.job_status", GSON.toJson(new ToolRouter.Ae2JobArgs(jobId)));
        }

        if (lower.startsWith("ae2.job_cancel ")) {
            String jobId = text.substring("ae2.job_cancel ".length()).trim();
            return new ToolCall("ae2.job_cancel", GSON.toJson(new ToolRouter.Ae2JobArgs(jobId)));
        }

        return null;
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
            SESSIONS.clearProposal(player.getUUID());
            sendSessionSnapshot(player);
            return;
        }

        long start = System.currentTimeMillis();
        ToolRouter.ToolOutcome outcome = ToolRouter.execute(player, proposal.toolCall(), true);
        long duration = System.currentTimeMillis() - start;

        AuditLogger.log(new AuditEvent(
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
            SESSIONS.appendMessage(player.getUUID(), new ChatMessage(ChatRole.ASSISTANT, outcome.result().payloadJson(), System.currentTimeMillis()));
        }

        sendSessionSnapshot(player);
    }
}
