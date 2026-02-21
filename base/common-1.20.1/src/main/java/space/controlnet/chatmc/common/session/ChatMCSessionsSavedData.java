package space.controlnet.chatmc.common.session;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.chatmc.core.proposal.ApprovalDecision;
import space.controlnet.chatmc.core.proposal.Proposal;
import space.controlnet.chatmc.core.proposal.ProposalDetails;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.ChatRole;
import space.controlnet.chatmc.core.session.DecisionLogEntry;
import space.controlnet.chatmc.core.session.PersistedSessions;
import space.controlnet.chatmc.core.session.SessionMetadata;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.SessionState;
import space.controlnet.chatmc.core.session.SessionVisibility;
import space.controlnet.chatmc.core.session.TerminalBinding;
import space.controlnet.chatmc.core.tools.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ChatMCSessionsSavedData extends SavedData {
    private static final String STORAGE_ID = "chatmc_sessions";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_TOOL_ARGS_JSON_LENGTH = 65_536;
    private static final String PERSIST_BOUNDARY_SIGNAL = "PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE";

    private PersistedSessions persisted = new PersistedSessions(CURRENT_VERSION, List.of(), Map.of());

    public ChatMCSessionsSavedData() {
    }

    public PersistedSessions data() {
        return persisted;
    }

    public void setData(PersistedSessions persisted) {
        if (persisted == null) {
            this.persisted = new PersistedSessions(CURRENT_VERSION, List.of(), Map.of());
        } else {
            this.persisted = persisted;
        }
        setDirty();
    }

    public static ChatMCSessionsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ChatMCSessionsSavedData::load, ChatMCSessionsSavedData::new, STORAGE_ID);
    }

    public static ChatMCSessionsSavedData load(CompoundTag root) {
        ChatMCSessionsSavedData data = new ChatMCSessionsSavedData();
        data.persisted = readPersisted(root);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putInt("version", CURRENT_VERSION);
        writePersisted(root, persisted);
        return root;
    }

    private static void writePersisted(CompoundTag root, PersistedSessions persisted) {
        root.putInt("persistedVersion", persisted.version());

        ListTag sessionsTag = new ListTag();
        for (SessionSnapshot session : persisted.sessions()) {
            sessionsTag.add(writeSession(session));
        }
        root.put("sessions", sessionsTag);

        ListTag activeTag = new ListTag();
        for (Map.Entry<UUID, UUID> e : persisted.activeSessionByPlayer().entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("playerId", e.getKey());
            t.putUUID("sessionId", e.getValue());
            activeTag.add(t);
        }
        root.put("activeByPlayer", activeTag);
    }

    private static PersistedSessions readPersisted(CompoundTag root) {
        int version = root.getInt("persistedVersion");

        List<SessionSnapshot> sessions = new ArrayList<>();
        ListTag sessionsTag = root.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < sessionsTag.size(); i++) {
            sessions.add(readSession(sessionsTag.getCompound(i)));
        }

        Map<UUID, UUID> activeByPlayer = new HashMap<>();
        ListTag activeTag = root.getList("activeByPlayer", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeTag.size(); i++) {
            CompoundTag t = activeTag.getCompound(i);
            if (t.hasUUID("playerId") && t.hasUUID("sessionId")) {
                activeByPlayer.put(t.getUUID("playerId"), t.getUUID("sessionId"));
            }
        }

        return new PersistedSessions(version, sessions, activeByPlayer);
    }

    private static CompoundTag writeSession(SessionSnapshot session) {
        CompoundTag root = new CompoundTag();

        SessionMetadata meta = session.metadata();
        root.putUUID("sessionId", meta.sessionId());
        root.putUUID("ownerId", meta.ownerId());
        root.putString("ownerName", meta.ownerName());
        root.putInt("visibility", meta.visibility().ordinal());
        root.putBoolean("hasTeamId", meta.teamId().isPresent());
        meta.teamId().ifPresent(team -> root.putString("teamId", team));
        root.putString("title", meta.title());
        root.putLong("createdAt", meta.createdAtMillis());
        root.putLong("lastActive", meta.lastActiveMillis());

        root.putInt("state", session.state().ordinal());

        ListTag messagesTag = new ListTag();
        for (ChatMessage msg : session.messages()) {
            CompoundTag t = new CompoundTag();
            t.putInt("role", msg.role().ordinal());
            t.putString("text", msg.text());
            t.putLong("ts", msg.timestampMillis());
            messagesTag.add(t);
        }
        root.put("messages", messagesTag);

        root.putBoolean("hasError", session.lastError().isPresent());
        session.lastError().ifPresent(err -> root.putString("error", err));

        root.putBoolean("hasProposal", session.pendingProposal().isPresent());
        if (session.pendingProposal().isPresent()) {
            root.put("proposal", writeProposal(session.pendingProposal().orElseThrow()));
        }

        root.putBoolean("hasBinding", session.proposalBinding().isPresent());
        if (session.proposalBinding().isPresent()) {
            root.put("binding", writeBinding(session.proposalBinding().orElseThrow()));
        }

        ListTag decisionsTag = new ListTag();
        for (DecisionLogEntry entry : session.decisions()) {
            decisionsTag.add(writeDecision(entry));
        }
        root.put("decisions", decisionsTag);

        return root;
    }

    private static SessionSnapshot readSession(CompoundTag root) {
        UUID sessionId = root.getUUID("sessionId");
        UUID ownerId = root.getUUID("ownerId");
        String ownerName = root.getString("ownerName");
        SessionVisibility visibility = SessionVisibility.values()[root.getInt("visibility")];
        Optional<String> teamId = root.getBoolean("hasTeamId") ? Optional.of(root.getString("teamId")) : Optional.empty();
        String title = root.getString("title");
        long createdAt = root.getLong("createdAt");
        long lastActive = root.getLong("lastActive");
        SessionMetadata meta = new SessionMetadata(sessionId, ownerId, ownerName, visibility, teamId, title, createdAt, lastActive);

        SessionState state = SessionState.values()[root.getInt("state")];

        List<ChatMessage> messages = new ArrayList<>();
        ListTag messagesTag = root.getList("messages", Tag.TAG_COMPOUND);
        for (int i = 0; i < messagesTag.size(); i++) {
            CompoundTag t = messagesTag.getCompound(i);
            ChatRole role = ChatRole.values()[t.getInt("role")];
            String text = t.getString("text");
            long ts = t.getLong("ts");
            messages.add(new ChatMessage(role, text, ts));
        }

        Optional<String> error = root.getBoolean("hasError") ? Optional.of(root.getString("error")) : Optional.empty();

        Optional<Proposal> proposal = Optional.empty();
        if (root.getBoolean("hasProposal")) {
            proposal = Optional.of(readProposal(root.getCompound("proposal")));
        }

        Optional<TerminalBinding> binding = Optional.empty();
        if (root.getBoolean("hasBinding")) {
            binding = Optional.of(readBinding(root.getCompound("binding")));
        }

        List<DecisionLogEntry> decisions = new ArrayList<>();
        ListTag decisionsTag = root.getList("decisions", Tag.TAG_COMPOUND);
        for (int i = 0; i < decisionsTag.size(); i++) {
            decisions.add(readDecision(decisionsTag.getCompound(i)));
        }

        return new SessionSnapshot(meta, messages, state, proposal, binding, decisions, error);
    }

    private static CompoundTag writeProposal(Proposal proposal) {
        CompoundTag t = new CompoundTag();
        t.putString("id", proposal.id());
        t.putInt("risk", proposal.riskLevel().ordinal());
        t.putString("summary", proposal.summary());
        t.putLong("createdAt", proposal.createdAtMillis());

        ToolCall call = proposal.toolCall();
        t.putString("toolName", call.toolName());
        validateToolArgsBoundary(call.toolName(), call.argsJson(), "write");
        t.putString("argsJson", call.argsJson());

        ProposalDetails details = proposal.details();
        t.putString("action", details.action());
        t.putString("itemId", details.itemId());
        t.putLong("count", details.count());
        t.putString("note", details.note());
        ListTag missingTag = new ListTag();
        for (String miss : details.missingItems()) {
            CompoundTag m = new CompoundTag();
            m.putString("id", miss);
            missingTag.add(m);
        }
        t.put("missing", missingTag);

        return t;
    }

    private static Proposal readProposal(CompoundTag t) {
        String id = t.getString("id");
        space.controlnet.chatmc.core.policy.RiskLevel risk = space.controlnet.chatmc.core.policy.RiskLevel.values()[t.getInt("risk")];
        String summary = t.getString("summary");
        long createdAt = t.getLong("createdAt");

        String toolName = t.getString("toolName");
        String argsJson = t.getString("argsJson");
        validateToolArgsBoundary(toolName, argsJson, "read");
        ToolCall call = new ToolCall(toolName, argsJson);

        String action = t.getString("action");
        String itemId = t.getString("itemId");
        long count = t.getLong("count");
        String note = t.getString("note");
        List<String> missingItems = new ArrayList<>();
        ListTag missingTag = t.getList("missing", Tag.TAG_COMPOUND);
        for (int i = 0; i < missingTag.size(); i++) {
            missingItems.add(missingTag.getCompound(i).getString("id"));
        }
        ProposalDetails details = new ProposalDetails(action, itemId, count, missingItems, note);

        return new Proposal(id, risk, summary, call, createdAt, details);
    }

    private static CompoundTag writeBinding(TerminalBinding binding) {
        CompoundTag t = new CompoundTag();
        t.putString("dimensionId", binding.dimensionId());
        t.putInt("x", binding.x());
        t.putInt("y", binding.y());
        t.putInt("z", binding.z());
        t.putBoolean("hasSide", binding.side().isPresent());
        binding.side().ifPresent(side -> t.putString("side", side));
        return t;
    }

    private static TerminalBinding readBinding(CompoundTag t) {
        String dimensionId = t.getString("dimensionId");
        int x = t.getInt("x");
        int y = t.getInt("y");
        int z = t.getInt("z");
        Optional<String> side = t.getBoolean("hasSide") ? Optional.of(t.getString("side")) : Optional.empty();
        return new TerminalBinding(dimensionId, x, y, z, side);
    }

    private static CompoundTag writeDecision(DecisionLogEntry entry) {
        CompoundTag t = new CompoundTag();
        t.putLong("ts", entry.timestampMillis());
        t.putBoolean("hasPlayerId", entry.playerId().isPresent());
        entry.playerId().ifPresent(id -> t.putUUID("playerId", id));
        t.putBoolean("hasPlayerName", entry.playerName().isPresent());
        entry.playerName().ifPresent(name -> t.putString("playerName", name));
        t.putString("proposalId", entry.proposalId());
        t.putBoolean("hasToolName", entry.toolName().isPresent());
        entry.toolName().ifPresent(name -> t.putString("toolName", name));
        t.putInt("decision", entry.decision().ordinal());
        return t;
    }

    private static DecisionLogEntry readDecision(CompoundTag t) {
        long ts = t.getLong("ts");
        Optional<UUID> playerId = t.getBoolean("hasPlayerId") ? Optional.of(t.getUUID("playerId")) : Optional.empty();
        Optional<String> playerName = t.getBoolean("hasPlayerName") ? Optional.of(t.getString("playerName")) : Optional.empty();
        String proposalId = t.getString("proposalId");
        Optional<String> toolName = t.getBoolean("hasToolName") ? Optional.of(t.getString("toolName")) : Optional.empty();
        ApprovalDecision decision = ApprovalDecision.values()[t.getInt("decision")];
        return new DecisionLogEntry(ts, playerId, playerName, proposalId, toolName, decision);
    }

    private static void validateToolArgsBoundary(String toolName, String argsJson, String phase) {
        if (argsJson == null) {
            return;
        }
        int length = argsJson.length();
        if (length > MAX_TOOL_ARGS_JSON_LENGTH) {
            throw new IllegalArgumentException(
                    PERSIST_BOUNDARY_SIGNAL
                            + ": phase='" + phase + "', tool='" + toolName + "', argsJson.length=" + length
                            + ", max=" + MAX_TOOL_ARGS_JSON_LENGTH
            );
        }
    }
}
