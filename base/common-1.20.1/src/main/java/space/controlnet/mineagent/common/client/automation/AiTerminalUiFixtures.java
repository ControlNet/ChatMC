package space.controlnet.mineagent.common.client.automation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import space.controlnet.mineagent.common.client.screen.components.ItemSuggestion;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.session.ChatMessage;
import space.controlnet.mineagent.core.session.ChatRole;
import space.controlnet.mineagent.core.session.SessionMetadata;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.SessionState;
import space.controlnet.mineagent.core.session.SessionSummary;
import space.controlnet.mineagent.core.session.SessionVisibility;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolMessagePayload;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AiTerminalUiFixtures {
    private static final long BASE_TIME = 1_700_000_000_000L;

    private AiTerminalUiFixtures() {
    }

    public static AiTerminalUiPreviewState create(AiTerminalUiScenarioId scenarioId, UUID ownerId, String ownerName) {
        return switch (scenarioId) {
            case EMPTY -> empty(ownerId, ownerName);
            case CHAT_SHORT -> chatShort(ownerId, ownerName);
            case SUGGESTIONS_VISIBLE -> suggestionsVisible(ownerId, ownerName);
            case PROPOSAL_PENDING -> proposalPending(ownerId, ownerName);
            case EXECUTING -> executing(ownerId, ownerName);
            case ERROR_STATE -> errorState(ownerId, ownerName);
            case HTTP_RESULT -> httpResult(ownerId, ownerName);
            case SESSION_LIST_DENSE -> sessionListDense(ownerId, ownerName);
            case STATUS_BUTTON -> statusButton(ownerId, ownerName);
            case STATUS_PANEL -> statusPanel(ownerId, ownerName);
        };
    }

    private static AiTerminalUiPreviewState empty(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(1);
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Empty",
                List.of(),
                SessionState.IDLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "", false, "", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState chatShort(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(2);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.USER, "Show me the current crafting plan.", BASE_TIME),
                new ChatMessage(ChatRole.ASSISTANT, "I found two feasible paths. The faster one uses stored quartz.", BASE_TIME + 1_000L),
                new ChatMessage(ChatRole.ASSISTANT, "If you want, I can queue the safer option instead.", BASE_TIME + 2_000L)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Chat",
                messages,
                SessionState.DONE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "en_us", false, "", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState suggestionsVisible(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(3);
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Suggestions",
                List.of(new ChatMessage(ChatRole.ASSISTANT, "Type an item token to narrow the request.", BASE_TIME)),
                SessionState.IDLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        List<ItemSuggestion> suggestions = List.of(
                suggestion("minecraft:diamond", "Diamond", 0x5FE1FF, 90),
                suggestion("minecraft:diamond_block", "Block of Diamond", 0x56C6E8, 84),
                suggestion("minecraft:diamond_sword", "Diamond Sword", 0x78F0FF, 78)
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "", false, "@dia", suggestions, 1, false);
    }

    private static AiTerminalUiPreviewState proposalPending(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(4);
        Proposal proposal = new Proposal(
                "preview-proposal-1",
                RiskLevel.SAFE_MUTATION,
                "Queue 16 glass panes from the AE network",
                new ToolCall("ae.request_craft", "{\"itemId\":\"minecraft:glass_pane\",\"count\":16}"),
                BASE_TIME + 5_000L,
                new ProposalDetails("Queue craft", "minecraft:glass_pane", 16L, List.of("minecraft:sand x8"), "Will reserve one CPU for about 12 seconds.")
        );
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.USER, "Craft 16 glass panes.", BASE_TIME),
                new ChatMessage(ChatRole.ASSISTANT, "I can queue that craft, but it will consume one free crafting CPU.", BASE_TIME + 1_000L)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Proposal",
                messages,
                SessionState.WAIT_APPROVAL,
                Optional.of(proposal),
                Optional.of(new TerminalBinding("minecraft:overworld", 0, 64, 0, Optional.empty())),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "zh_cn", false, "", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState executing(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(5);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.USER, "Scan the network and summarize the missing ingredients.", BASE_TIME),
                new ChatMessage(ChatRole.ASSISTANT, "Working through the craft graph now.", BASE_TIME + 1_000L)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Executing",
                messages,
                SessionState.EXECUTING,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "en_us", false, "Queue another task", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState errorState(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(6);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.USER, "Fetch the endpoint status.", BASE_TIME),
                new ChatMessage(ChatRole.ASSISTANT, "The last request failed before a valid response arrived.", BASE_TIME + 1_000L)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Error",
                messages,
                SessionState.FAILED,
                Optional.empty(),
                Optional.empty(),
                Optional.of("connect timeout while waiting for upstream response")
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "", false, "Retry with a 5 second timeout", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState httpResult(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(7);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.USER, "Call the profile endpoint and summarize the result.", BASE_TIME),
                new ChatMessage(ChatRole.TOOL, httpToolPayload(), BASE_TIME + 1_000L)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview HTTP",
                messages,
                SessionState.DONE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "en_us", false, "", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState sessionListDense(UUID ownerId, String ownerName) {
        UUID activeSessionId = fixedSessionId(8);
        SessionSnapshot snapshot = snapshot(
                activeSessionId,
                ownerId,
                ownerName,
                "Dense Session Index",
                List.of(new ChatMessage(ChatRole.ASSISTANT, "Use the left sidebar to switch between saved terminal sessions.", BASE_TIME)),
                SessionState.IDLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        List<SessionSummary> sessions = new ArrayList<>();
        sessions.add(summary(snapshot));
        for (int i = 1; i <= 8; i++) {
            UUID sessionId = fixedSessionId(80 + i);
            sessions.add(new SessionSummary(
                    sessionId,
                    ownerId,
                    ownerName,
                    i % 3 == 0 ? SessionVisibility.PUBLIC : SessionVisibility.PRIVATE,
                    Optional.empty(),
                    "Archived Task " + i,
                    BASE_TIME - (i * 12_000L),
                    BASE_TIME - (i * 6_000L)
            ));
        }
        return new AiTerminalUiPreviewState(snapshot, sessions, "", true, "", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState statusButton(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(9);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.ASSISTANT, "Use Status to inspect the currently loaded tool set.", BASE_TIME)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Status Button",
                messages,
                SessionState.IDLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "en_us", false, "", List.of(), -1, false);
    }

    private static AiTerminalUiPreviewState statusPanel(UUID ownerId, String ownerName) {
        UUID sessionId = fixedSessionId(10);
        List<ChatMessage> messages = List.of(
                new ChatMessage(ChatRole.ASSISTANT, "Status groups the active built-in, extension, and MCP tools.", BASE_TIME)
        );
        SessionSnapshot snapshot = snapshot(
                sessionId,
                ownerId,
                ownerName,
                "Preview Status Panel",
                messages,
                SessionState.IDLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        return new AiTerminalUiPreviewState(snapshot, List.of(summary(snapshot)), "en_us", false, "", List.of(), -1, true);
    }

    private static SessionSnapshot snapshot(
            UUID sessionId,
            UUID ownerId,
            String ownerName,
            String title,
            List<ChatMessage> messages,
            SessionState state,
            Optional<Proposal> pendingProposal,
            Optional<TerminalBinding> proposalBinding,
            Optional<String> lastError
    ) {
        long createdAt = BASE_TIME - 60_000L;
        SessionMetadata metadata = new SessionMetadata(
                sessionId,
                ownerId,
                ownerName,
                SessionVisibility.PRIVATE,
                Optional.empty(),
                title,
                createdAt,
                BASE_TIME + 9_000L
        );
        return new SessionSnapshot(metadata, messages, state, pendingProposal, proposalBinding, List.of(), lastError);
    }

    private static SessionSummary summary(SessionSnapshot snapshot) {
        return new SessionSummary(
                snapshot.metadata().sessionId(),
                snapshot.metadata().ownerId(),
                snapshot.metadata().ownerName(),
                snapshot.metadata().visibility(),
                snapshot.metadata().teamId(),
                snapshot.metadata().title(),
                snapshot.metadata().createdAtMillis(),
                snapshot.metadata().lastActiveMillis()
        );
    }

    private static UUID fixedSessionId(long suffix) {
        return new UUID(0L, suffix);
    }

    private static ItemSuggestion suggestion(String itemId, String displayName, int color, int score) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        Item item = resourceLocation == null ? Items.AIR : BuiltInRegistries.ITEM.get(resourceLocation);
        if (item == null || item == Items.AIR) {
            item = Items.PAPER;
        }
        return new ItemSuggestion(item, displayName, itemId, score, color);
    }

    private static String httpToolPayload() {
        String argsJson = "{\"url\":\"https://example.invalid/api/profile\",\"method\":\"GET\"}";
        String outputJson = """
                {
                  \"status\": 200,
                  \"statusText\": \"OK\",
                  \"contentType\": \"application/json\",
                  \"entries\": [
                    {\"kind\":\"text\",\"text\":\"{\\\"ok\\\":true,\\\"profile\\\":{\\\"name\\\":\\\"MineAgent\\\",\\\"locale\\\":\\\"en_us\\\"}}\"}
                  ]
                }
                """;
        return ToolMessagePayload.wrap(
                new ToolCall("http.fetch", argsJson),
                ToolResult.ok(outputJson),
                "Fetching profile metadata from the configured endpoint."
        );
    }
}
