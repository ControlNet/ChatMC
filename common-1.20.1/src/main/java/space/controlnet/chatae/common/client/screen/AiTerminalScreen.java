package space.controlnet.chatae.common.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import space.controlnet.chatae.common.ChatAENetwork;
import space.controlnet.chatae.common.menu.AiTerminalMenu;
import space.controlnet.chatae.common.team.TeamAccess;
import space.controlnet.chatae.core.client.ClientSessionIndex;
import space.controlnet.chatae.core.client.ClientSessionStore;
import space.controlnet.chatae.core.proposal.ApprovalDecision;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalDetails;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.SessionListScope;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.session.SessionSummary;
import space.controlnet.chatae.core.session.SessionVisibility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AiTerminalScreen extends AbstractContainerScreen<AiTerminalMenu> {
    private static final int PADDING = 8;
    private static final int INPUT_HEIGHT = 20;
    private static final int SEND_BUTTON_WIDTH = 52;
    private static final int MAX_MESSAGES = 200;
    private static final int PROPOSAL_CARD_HEIGHT = 46;
    private static final int PROPOSAL_CARD_GAP = 6;
    private static final int PROPOSAL_BUTTON_WIDTH = 64;
    private static final int PROPOSAL_BUTTON_HEIGHT = 18;
    private static final int PROPOSAL_BUTTON_GAP = 4;
    private static final int SESSION_TOGGLE_WIDTH = 72;
    private static final int SESSION_TOGGLE_HEIGHT = 18;
    private static final int SESSION_PANEL_WIDTH = 124;
    private static final int SESSION_PANEL_PADDING = 6;
    private static final int SESSION_HEADER_HEIGHT = 14;
    private static final int SESSION_ROW_HEIGHT = 32;
    private static final int STATUS_DOT_SIZE = 6;
    private static final int SESSION_ROW_GAP = 4;
    private static final int SESSION_OPEN_BUTTON_WIDTH = 36;
    private static final int SESSION_DELETE_BUTTON_WIDTH = 22;
    private static final int SESSION_VIS_BUTTON_WIDTH = 28;
    private static final int SESSION_BUTTON_HEIGHT = 18;
    private static final int SESSION_BUTTON_GAP = 4;
    private static final int SESSION_NEW_BUTTON_HEIGHT = 18;
    private static final int TOKEN_ICON_SIZE = 12;
    private static final int TOKEN_TEXT_GAP = 4;
    private static final int TOKEN_PADDING_X = 4;
    private static final int TOKEN_PANEL_PADDING = 6;
    private static final int TOKEN_PANEL_WIDTH = 176;
    private static final int TOKEN_PANEL_MAX_ROWS = 6;
    private static final int TOKEN_PANEL_ROW_HEIGHT = 18;

    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final List<Component> messages = new ArrayList<>();
    private final List<FormattedCharSequence> wrappedLines = new ArrayList<>();
    private final List<ItemToken> inputTokens = new ArrayList<>();
    private final List<ItemSuggestion> itemSuggestions = new ArrayList<>();

    private EditBox inputBox;
    private Button sendButton;
    private Button approveButton;
    private Button denyButton;
    private Button sessionsToggleButton;
    private Button newSessionButton;
    private Button aiLocaleButton;

    private String statusText = "Idle";
    private int lastCursorPosition = -1;
    private int suggestionAnchorIndex = -1;
    private String suggestionQuery = "";
    private boolean suggestionsVisible;
    private int hoveredSuggestionIndex = -1;

    private int chatX;
    private int chatY;
    private int chatW;
    private int chatH;
    private int proposalX;
    private int proposalY;
    private int proposalW;
    private int proposalH;
    private int sessionPanelX;
    private int sessionPanelY;
    private int sessionPanelW;
    private int sessionPanelH;
    private int sessionInnerX;
    private int sessionInnerW;
    private int sessionListStartY;
    private int sessionMaxRows;

    private Proposal pendingProposal;
    private space.controlnet.chatae.core.session.TerminalBinding proposalBinding;

    private int scrollOffsetLines;
    private long lastSnapshotVersion = -1;
    private long lastSessionIndexVersion = -1;
    private boolean sessionsOpen;
    private UUID activeSessionId;

    private List<SessionSummary> sessionSummaries = List.of();
    private final List<SessionRow> sessionRows = new ArrayList<>();

    public AiTerminalScreen(AiTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("item.chatae.ai_terminal"));
        this.imageWidth = 248;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();

        this.chatX = this.leftPos + PADDING;
        this.chatY = this.topPos + PADDING + this.font.lineHeight + 6;
        this.chatW = this.imageWidth - (PADDING * 2);
        this.chatH = this.imageHeight - (PADDING * 3) - INPUT_HEIGHT - this.font.lineHeight - 6
                - (PROPOSAL_CARD_HEIGHT + PROPOSAL_CARD_GAP);

        this.proposalX = this.chatX;
        this.proposalY = this.chatY + this.chatH + PROPOSAL_CARD_GAP;
        this.proposalW = this.chatW;
        this.proposalH = PROPOSAL_CARD_HEIGHT;

        int inputY = this.topPos + this.imageHeight - PADDING - INPUT_HEIGHT;
        int inputX = this.leftPos + PADDING;
        int inputW = this.imageWidth - (PADDING * 2) - SEND_BUTTON_WIDTH - 6;

        this.inputBox = new EditBox(this.font, inputX, inputY, inputW, INPUT_HEIGHT, Component.empty());
        this.inputBox.setMaxLength(256);
        this.inputBox.setBordered(true);
        this.inputBox.setCanLoseFocus(true);
        this.inputBox.setResponder(this::onInputChanged);
        this.addRenderableWidget(this.inputBox);

        this.sendButton = Button.builder(Component.literal("Send"), b -> submitInput()).bounds(
                inputX + inputW + 6, inputY, SEND_BUTTON_WIDTH, INPUT_HEIGHT).build();
        this.addRenderableWidget(this.sendButton);

        this.aiLocaleButton = Button.builder(Component.literal(buildAiLocaleLabel()), b -> cycleAiLocale())
                .bounds(this.leftPos + PADDING, this.topPos + PADDING - 1, 90, 18)
                .build();
        this.addRenderableWidget(this.aiLocaleButton);

        int proposalButtonY = this.proposalY + (this.proposalH - PROPOSAL_BUTTON_HEIGHT) / 2;
        int denyX = this.proposalX + this.proposalW - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;
        int approveX = denyX - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;

        this.approveButton = Button.builder(Component.literal("Approve"), b -> sendApprovalDecision(ApprovalDecision.APPROVE))
                .bounds(approveX, proposalButtonY, PROPOSAL_BUTTON_WIDTH, PROPOSAL_BUTTON_HEIGHT).build();
        this.denyButton = Button.builder(Component.literal("Deny"), b -> sendApprovalDecision(ApprovalDecision.DENY))
                .bounds(denyX, proposalButtonY, PROPOSAL_BUTTON_WIDTH, PROPOSAL_BUTTON_HEIGHT).build();

        this.addRenderableWidget(this.approveButton);
        this.addRenderableWidget(this.denyButton);
        updateProposalUi();

        layoutSessionPanel();
        initSessionsToggle();
        initSessionPanelWidgets();
        setSessionsPanelVisible(this.sessionsOpen);
        ChatAENetwork.requestSessionList(SessionListScope.ALL);

        updateAiLocaleButtonLabel();
        this.setInitialFocus(this.inputBox);
        rebuildWrappedLines();
    }

    private String buildAiLocaleLabel() {
        String override = space.controlnet.chatae.core.client.ClientAiSettings.getAiLocaleOverride();
        if (override == null || override.isBlank()) {
            return "AI Language: Auto";
        }
        return "AI Language: " + override;
    }

    private void updateAiLocaleButtonLabel() {
        if (this.aiLocaleButton != null) {
            this.aiLocaleButton.setMessage(Component.literal(buildAiLocaleLabel()));
        }
    }

    private void cycleAiLocale() {
        String current = space.controlnet.chatae.core.client.ClientAiSettings.getAiLocaleOverride();
        String next;
        if (current == null || current.isBlank()) {
            next = "en_us";
        } else if ("en_us".equalsIgnoreCase(current)) {
            next = "zh_cn";
        } else if ("zh_cn".equalsIgnoreCase(current)) {
            next = "";
        } else {
            next = "";
        }
        space.controlnet.chatae.core.client.ClientAiSettings.setAiLocaleOverride(next);
        updateAiLocaleButtonLabel();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, PADDING + 96, PADDING, 0x404040, false);

        SessionState state = sessionStateFromStatus();
        int statusY = this.imageHeight - PADDING - INPUT_HEIGHT - this.font.lineHeight - 4;
        int dotX = PADDING;
        int dotY = statusY + (this.font.lineHeight - STATUS_DOT_SIZE) / 2;
        int dotColor = statusDotColor(state);
        guiGraphics.fill(dotX, dotY, dotX + STATUS_DOT_SIZE, dotY + STATUS_DOT_SIZE, 0xFF000000 | dotColor);

        Component status = Component.literal(stateLabel(state))
                .withStyle(ChatFormatting.GRAY);
        guiGraphics.drawString(this.font, status, dotX + STATUS_DOT_SIZE + 6, statusY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xC0101010);
        guiGraphics.renderOutline(left, top, this.imageWidth, this.imageHeight, 0xFF9A9A9A);

        guiGraphics.fill(this.chatX, this.chatY, this.chatX + this.chatW, this.chatY + this.chatH, 0x80101010);
        guiGraphics.renderOutline(this.chatX, this.chatY, this.chatW, this.chatH, 0xFF6A6A6A);

        if (this.pendingProposal != null) {
            guiGraphics.fill(this.proposalX, this.proposalY, this.proposalX + this.proposalW, this.proposalY + this.proposalH, 0x80202020);
            guiGraphics.renderOutline(this.proposalX, this.proposalY, this.proposalW, this.proposalH, 0xFF6A6A6A);
        }

        if (this.sessionsOpen) {
            guiGraphics.fill(this.sessionPanelX, this.sessionPanelY, this.sessionPanelX + this.sessionPanelW, this.sessionPanelY + this.sessionPanelH, 0xCC111111);
            guiGraphics.renderOutline(this.sessionPanelX, this.sessionPanelY, this.sessionPanelW, this.sessionPanelH, 0xFF5A5A5A);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromSessionStore();
        syncFromSessionIndex();
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderChatLog(guiGraphics);
        renderProposalCard(guiGraphics);
        renderSessionsPanel(guiGraphics);
        renderInputTokens(guiGraphics);
        renderItemSuggestions(guiGraphics, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderChatLog(GuiGraphics guiGraphics) {
        int lineHeight = this.font.lineHeight;
        int visibleLines = Math.max(1, (this.chatH - 4) / lineHeight);
        int totalLines = this.wrappedLines.size();

        int baseStart = Math.max(0, totalLines - visibleLines);
        int maxScroll = baseStart;
        this.scrollOffsetLines = clamp(this.scrollOffsetLines, 0, maxScroll);

        int startIndex = Math.max(0, baseStart - this.scrollOffsetLines);
        int endIndex = Math.min(totalLines, startIndex + visibleLines);

        guiGraphics.enableScissor(this.chatX + 1, this.chatY + 1, this.chatX + this.chatW - 1, this.chatY + this.chatH - 1);

        int y = this.chatY + 2;
        for (int i = startIndex; i < endIndex; i++) {
            guiGraphics.drawString(this.font, this.wrappedLines.get(i), this.chatX + 4, y, 0xE0E0E0, false);
            y += lineHeight;
        }

        guiGraphics.disableScissor();
    }

    private void renderInputTokens(GuiGraphics guiGraphics) {
        if (this.inputBox == null || this.inputTokens.isEmpty()) {
            return;
        }

        String value = this.inputBox.getValue();
        if (value.isBlank()) {
            return;
        }

        int inputX = this.inputBox.getX();
        int inputY = this.inputBox.getY();
        int inputW = this.inputBox.getWidth();
        int inputH = this.inputBox.getHeight();
        int rowHeight = Math.max(12, inputH - 4);
        int baseX = inputX + 4;
        int baseY = inputY + (inputH - rowHeight) / 2;
        int maxX = inputX + inputW - 4;

        guiGraphics.enableScissor(inputX + 1, inputY + 1, inputX + inputW - 1, inputY + inputH - 1);

        int searchStart = 0;
        for (ItemToken token : this.inputTokens) {
            String label = "@" + token.displayName();
            int idx = value.indexOf(label, searchStart);
            if (idx < 0) {
                continue;
            }
            int textWidth = this.font.width(value.substring(0, idx));
            int x = baseX + textWidth;
            TokenMetrics metrics = measureToken(token);
            int tokenW = metrics.width();
            if (x < maxX && x + tokenW > baseX) {
                renderTokenPill(guiGraphics, token, x, baseY, tokenW, rowHeight);
            }
            searchStart = idx + label.length();
        }

        guiGraphics.disableScissor();
    }

    private void renderTokenPill(GuiGraphics guiGraphics, ItemToken token, int x, int y, int width, int height) {
        int bg = 0xCC1E1A12;
        int outline = 0xFF6A5D4B;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        guiGraphics.renderOutline(x, y, width, height, outline);

        ItemStack stack = new ItemStack(token.item());
        int iconX = x + TOKEN_PADDING_X;
        int iconY = y + (height - TOKEN_ICON_SIZE) / 2;
        guiGraphics.renderItem(stack, iconX, iconY);

        int textX = iconX + TOKEN_ICON_SIZE + TOKEN_TEXT_GAP;
        int nameMaxWidth = Math.max(1, width - TOKEN_PADDING_X - TOKEN_ICON_SIZE - TOKEN_TEXT_GAP - TOKEN_PADDING_X);
        String label = this.font.plainSubstrByWidth(token.displayName(), nameMaxWidth);
        int textY = y + (height - this.font.lineHeight) / 2 + 1;
        guiGraphics.drawString(this.font, label, textX, textY, token.color(), false);
    }

    private void renderItemSuggestions(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.suggestionsVisible || this.inputBox == null || this.itemSuggestions.isEmpty()) {
            this.hoveredSuggestionIndex = -1;
            return;
        }

        int panelW = Math.min(TOKEN_PANEL_WIDTH, this.inputBox.getWidth());
        int visibleRows = Math.min(TOKEN_PANEL_MAX_ROWS, this.itemSuggestions.size());
        int panelH = visibleRows * TOKEN_PANEL_ROW_HEIGHT + TOKEN_PANEL_PADDING * 2;
        int panelX = getSuggestionsPanelX();
        int panelY = getSuggestionsPanelY(panelH);

        int bg = 0xE61A1712;
        int outline = 0xFF6A5D4B;
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, bg);
        guiGraphics.renderOutline(panelX, panelY, panelW, panelH, outline);

        this.hoveredSuggestionIndex = -1;
        for (int i = 0; i < visibleRows; i++) {
            int rowY = panelY + TOKEN_PANEL_PADDING + i * TOKEN_PANEL_ROW_HEIGHT;
            if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= rowY && mouseY < rowY + TOKEN_PANEL_ROW_HEIGHT) {
                this.hoveredSuggestionIndex = i;
                guiGraphics.fill(panelX + 2, rowY + 1, panelX + panelW - 2, rowY + TOKEN_PANEL_ROW_HEIGHT - 1, 0x553A3328);
            }
            renderSuggestionRow(guiGraphics, this.itemSuggestions.get(i), panelX + TOKEN_PANEL_PADDING, rowY, panelW - TOKEN_PANEL_PADDING * 2);
        }
    }

    private int getSuggestionsPanelX() {
        return this.inputBox == null ? 0 : this.inputBox.getX();
    }

    private int getSuggestionsPanelY(int panelHeight) {
        if (this.inputBox == null) {
            return 0;
        }
        int inputY = this.inputBox.getY();
        int panelY = inputY - panelHeight - 4;
        if (panelY < this.topPos + 4) {
            panelY = inputY + this.inputBox.getHeight() + 4;
        }
        return panelY;
    }

    private void renderSuggestionRow(GuiGraphics guiGraphics, ItemSuggestion suggestion, int x, int y, int width) {
        ItemStack stack = new ItemStack(suggestion.item());
        int iconY = y + (TOKEN_PANEL_ROW_HEIGHT - TOKEN_ICON_SIZE) / 2;
        guiGraphics.renderItem(stack, x, iconY);

        int textX = x + TOKEN_ICON_SIZE + TOKEN_TEXT_GAP;
        int nameMaxWidth = Math.max(1, width - TOKEN_ICON_SIZE - TOKEN_TEXT_GAP);
        String name = this.font.plainSubstrByWidth(suggestion.displayName(), nameMaxWidth);
        guiGraphics.drawString(this.font, name, textX, y + 4, suggestion.color(), false);
    }

    private void renderProposalCard(GuiGraphics guiGraphics) {
        if (this.pendingProposal == null) {
            return;
        }

        String summary = this.pendingProposal.summary();
        String risk = this.pendingProposal.riskLevel().name();
        String label = summary == null || summary.isBlank()
                ? "Proposal [" + risk + "] pending"
                : "Proposal [" + risk + "]: " + summary;
        String detail = buildProposalDetailLine(this.pendingProposal, this.proposalBinding);

        int textMaxWidth = this.proposalW - (PROPOSAL_BUTTON_WIDTH * 2) - (PROPOSAL_BUTTON_GAP * 3) - 8;
        int textX = this.proposalX + 6;
        int lineHeight = this.font.lineHeight;
        int lineCount = detail.isBlank() ? 1 : 2;
        int totalHeight = lineCount * lineHeight;
        int textY = this.proposalY + (this.proposalH - totalHeight) / 2;

        String trimmed = this.font.plainSubstrByWidth(label, Math.max(1, textMaxWidth));
        guiGraphics.drawString(this.font, trimmed, textX, textY, 0xE0E0E0, false);
        if (!detail.isBlank()) {
            String detailTrimmed = this.font.plainSubstrByWidth(detail, Math.max(1, textMaxWidth));
            guiGraphics.drawString(this.font, detailTrimmed, textX, textY + lineHeight, 0xB0B0B0, false);
        }
    }

    private void renderSessionsPanel(GuiGraphics guiGraphics) {
        if (!this.sessionsOpen) {
            return;
        }

        int headerX = this.sessionInnerX;
        int headerY = this.sessionPanelY + SESSION_PANEL_PADDING;
        guiGraphics.drawString(this.font, Component.literal("Sessions"), headerX, headerY, 0xE0E0E0, false);

        int visibleCount = Math.min(this.sessionSummaries.size(), this.sessionMaxRows);
        for (int i = 0; i < visibleCount; i++) {
            SessionSummary summary = this.sessionSummaries.get(i);
            int rowY = this.sessionListStartY + (i * (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
            String title = summary.title();
            if (title == null || title.isBlank()) {
                title = "Untitled";
            }
            boolean isActive = this.activeSessionId != null && this.activeSessionId.equals(summary.sessionId());
            String label = isActive ? "* " + title : title;
            int color = isActive ? 0xF0D27C : 0xE0E0E0;
            int titleMaxWidth = Math.max(1, this.sessionInnerW);
            String trimmed = this.font.plainSubstrByWidth(label, titleMaxWidth);
            guiGraphics.drawString(this.font, trimmed, this.sessionInnerX, rowY + 2, color, false);

            String lastActive = formatRelativeTime(summary.lastActiveMillis());
            if (!lastActive.isBlank()) {
                guiGraphics.drawString(this.font, lastActive, this.sessionInnerX, rowY + 2 + this.font.lineHeight, 0x909090, false);
            }
        }

        if (this.sessionSummaries.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("No sessions"), this.sessionInnerX, this.sessionListStartY + 2, 0x909090, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isWithinChat(mouseX, mouseY) && !this.wrappedLines.isEmpty()) {
            int direction = delta > 0 ? -1 : 1;
            this.scrollOffsetLines = this.scrollOffsetLines + direction;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void layoutSessionPanel() {
        this.sessionPanelW = SESSION_PANEL_WIDTH;
        this.sessionPanelH = this.chatH + PROPOSAL_CARD_HEIGHT + PROPOSAL_CARD_GAP;
        this.sessionPanelX = this.leftPos + this.imageWidth - PADDING - this.sessionPanelW;
        this.sessionPanelY = this.chatY;

        this.sessionInnerX = this.sessionPanelX + SESSION_PANEL_PADDING;
        this.sessionInnerW = this.sessionPanelW - (SESSION_PANEL_PADDING * 2);
        this.sessionListStartY = this.sessionPanelY + SESSION_PANEL_PADDING + SESSION_HEADER_HEIGHT + 4;
        int sessionListEndY = this.sessionPanelY + this.sessionPanelH - SESSION_PANEL_PADDING - SESSION_NEW_BUTTON_HEIGHT - 4;

        int availableHeight = Math.max(0, sessionListEndY - this.sessionListStartY);
        this.sessionMaxRows = Math.max(1, availableHeight / (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
    }

    private void initSessionsToggle() {
        int toggleX = this.leftPos + this.imageWidth - PADDING - SESSION_TOGGLE_WIDTH;
        int toggleY = this.topPos + PADDING - 1;
        this.sessionsToggleButton = Button.builder(Component.literal("Sessions"), b -> toggleSessionsPanel())
                .bounds(toggleX, toggleY, SESSION_TOGGLE_WIDTH, SESSION_TOGGLE_HEIGHT)
                .build();
        this.addRenderableWidget(this.sessionsToggleButton);
        updateSessionsToggleLabel();
    }

    private void initSessionPanelWidgets() {
        this.sessionRows.clear();
        int newButtonY = this.sessionPanelY + this.sessionPanelH - SESSION_PANEL_PADDING - SESSION_NEW_BUTTON_HEIGHT;
        this.newSessionButton = Button.builder(Component.literal("New"), b -> ChatAENetwork.createSession())
                .bounds(this.sessionInnerX, newButtonY, this.sessionInnerW, SESSION_NEW_BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.newSessionButton);

        int listStartY = this.sessionListStartY;
        for (int i = 0; i < this.sessionMaxRows; i++) {
            int rowY = listStartY + (i * (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
            int buttonY = rowY + SESSION_ROW_HEIGHT - SESSION_BUTTON_HEIGHT - 2;
            int openX = this.sessionInnerX;
            int deleteX = openX + SESSION_OPEN_BUTTON_WIDTH + SESSION_BUTTON_GAP;
            int visX = deleteX + SESSION_DELETE_BUTTON_WIDTH + SESSION_BUTTON_GAP;

            SessionRow row = new SessionRow();
            row.openButton = Button.builder(Component.literal("Open"), b -> openSession(row))
                    .bounds(openX, buttonY, SESSION_OPEN_BUTTON_WIDTH, SESSION_BUTTON_HEIGHT)
                    .build();
            row.deleteButton = Button.builder(Component.literal("Del"), b -> deleteSession(row))
                    .bounds(deleteX, buttonY, SESSION_DELETE_BUTTON_WIDTH, SESSION_BUTTON_HEIGHT)
                    .build();
            row.visibilityButton = Button.builder(Component.literal("Priv"), b -> cycleSessionVisibility(row))
                    .bounds(visX, buttonY, SESSION_VIS_BUTTON_WIDTH, SESSION_BUTTON_HEIGHT)
                    .build();

            this.sessionRows.add(row);
            this.addRenderableWidget(row.openButton);
            this.addRenderableWidget(row.deleteButton);
            this.addRenderableWidget(row.visibilityButton);
        }
    }

    private void toggleSessionsPanel() {
        setSessionsPanelVisible(!this.sessionsOpen);
        if (this.sessionsOpen) {
        ChatAENetwork.requestSessionList(SessionListScope.ALL);

        }
    }

    private void setSessionsPanelVisible(boolean visible) {
        this.sessionsOpen = visible;
        updateSessionsToggleLabel();
        if (this.newSessionButton != null) {
            this.newSessionButton.visible = visible;
            this.newSessionButton.active = visible;
        }
        for (SessionRow row : this.sessionRows) {
            row.openButton.visible = visible;
            row.deleteButton.visible = visible;
            row.visibilityButton.visible = visible;
        }
        if (visible) {
            rebuildSessionRows();
        }
    }

    private void updateSessionsToggleLabel() {
        if (this.sessionsToggleButton == null) {
            return;
        }
        String label = this.sessionsOpen ? "Close" : "Sessions";
        this.sessionsToggleButton.setMessage(Component.literal(label));
    }

    private void syncFromSessionIndex() {
        long version = ClientSessionIndex.version();
        if (version == this.lastSessionIndexVersion) {
            return;
        }
        this.lastSessionIndexVersion = version;
        this.sessionSummaries = ClientSessionIndex.get();
        if (this.sessionsOpen) {
            rebuildSessionRows();
        }
    }

    private void rebuildSessionRows() {
        int visibleCount = Math.min(this.sessionSummaries.size(), this.sessionMaxRows);
        for (int i = 0; i < this.sessionRows.size(); i++) {
            SessionRow row = this.sessionRows.get(i);
            if (i < visibleCount) {
                SessionSummary summary = this.sessionSummaries.get(i);
                row.summary = summary;
                row.openButton.visible = this.sessionsOpen;
                row.deleteButton.visible = this.sessionsOpen;
                row.visibilityButton.visible = this.sessionsOpen;
                row.openButton.active = !isActiveSession(summary.sessionId());
                row.deleteButton.active = isOwner(summary);
                row.visibilityButton.active = isOwner(summary);
                row.visibilityButton.setMessage(Component.literal(visibilityLabel(summary.visibility())));
            } else {
                row.summary = null;
                row.openButton.visible = false;
                row.deleteButton.visible = false;
                row.visibilityButton.visible = false;
            }
        }
        if (this.newSessionButton != null) {
            this.newSessionButton.visible = this.sessionsOpen;
            this.newSessionButton.active = this.sessionsOpen;
        }
    }

    private void openSession(SessionRow row) {
        if (row.summary == null) {
            return;
        }
        ChatAENetwork.openSession(row.summary.sessionId());
    }

    private void deleteSession(SessionRow row) {
        if (row.summary == null || !isOwner(row.summary)) {
            return;
        }
        ChatAENetwork.deleteSession(row.summary.sessionId());
    }

    private void cycleSessionVisibility(SessionRow row) {
        if (row.summary == null || !isOwner(row.summary)) {
            return;
        }
        boolean teamsAvailable = TeamAccess.isTeamFeatureAvailable();
        SessionVisibility next = nextVisibility(row.summary.visibility(), teamsAvailable);
        ChatAENetwork.updateSession(row.summary.sessionId(), Optional.empty(), Optional.of(next));
    }

    private boolean isOwner(SessionSummary summary) {
        UUID playerId = getPlayerId();
        return playerId != null && playerId.equals(summary.ownerId());
    }

    private boolean isActiveSession(UUID sessionId) {
        return this.activeSessionId != null && this.activeSessionId.equals(sessionId);
    }

    private UUID getPlayerId() {
        return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
    }

    private static SessionVisibility nextVisibility(SessionVisibility current, boolean teamsAvailable) {
        if (!teamsAvailable) {
            return current == SessionVisibility.PUBLIC ? SessionVisibility.PRIVATE : SessionVisibility.PUBLIC;
        }
        return switch (current) {
            case PRIVATE -> SessionVisibility.TEAM;
            case TEAM -> SessionVisibility.PUBLIC;
            case PUBLIC -> SessionVisibility.PRIVATE;
        };
    }

    private static String visibilityLabel(SessionVisibility visibility) {
        if (!TeamAccess.isTeamFeatureAvailable() && visibility == SessionVisibility.TEAM) {
            return "Priv";
        }
        return switch (visibility) {
            case PRIVATE -> "Priv";
            case TEAM -> "Team";
            case PUBLIC -> "Pub";
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.inputBox != null && this.inputBox.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                if (this.suggestionsVisible && !this.itemSuggestions.isEmpty()) {
                    int index = this.hoveredSuggestionIndex >= 0 ? this.hoveredSuggestionIndex : 0;
                    if (index >= 0 && index < this.itemSuggestions.size()) {
                        applySuggestion(this.itemSuggestions.get(index));
                        return true;
                    }
                }
                submitInput();
                return true;
            }
            if (keyCode == InputConstants.KEY_TAB && this.suggestionsVisible && !this.itemSuggestions.isEmpty()) {
                int index = this.hoveredSuggestionIndex >= 0 ? this.hoveredSuggestionIndex : 0;
                applySuggestion(this.itemSuggestions.get(index));
                return true;
            }
            if (keyCode != InputConstants.KEY_ESCAPE) {
                if (this.inputBox.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
                // Swallow other keybinds (like inventory) while typing.
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.inputBox != null && this.inputBox.isFocused() && this.inputBox.charTyped(codePoint, modifiers)) {
            int cursor = this.inputBox.getCursorPosition();
            if (cursor != this.lastCursorPosition) {
                updateSuggestionQuery();
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.suggestionsVisible && this.inputBox != null) {
            int panelW = Math.min(TOKEN_PANEL_WIDTH, this.inputBox.getWidth());
            int visibleRows = Math.min(TOKEN_PANEL_MAX_ROWS, this.itemSuggestions.size());
            int panelH = visibleRows * TOKEN_PANEL_ROW_HEIGHT + TOKEN_PANEL_PADDING * 2;
            int panelX = getSuggestionsPanelX();
            int panelY = getSuggestionsPanelY(panelH);

            if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= panelY && mouseY < panelY + panelH) {
                int rowY = (int) mouseY - panelY - TOKEN_PANEL_PADDING;
                int rowIndex = rowY / TOKEN_PANEL_ROW_HEIGHT;
                if (rowIndex >= 0 && rowIndex < visibleRows) {
                    applySuggestion(this.itemSuggestions.get(rowIndex));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void submitInput() {
        if (this.inputBox == null) {
            return;
        }

        String raw = this.inputBox.getValue();
        String message = raw == null ? "" : raw.trim();
        if (message.isEmpty()) {
            return;
        }

        SessionSnapshot snapshot = ClientSessionStore.get();
        if (!isIdleLike(snapshot.state())) {
            return;
        }

        ChatAENetwork.sendChatToServer(
                serializeInputForSend(message),
                ChatAENetwork.getClientLocale(),
                space.controlnet.chatae.core.client.ClientAiSettings.getAiLocaleOverride()
        );

        this.inputBox.setValue("");
        this.inputTokens.clear();
        this.itemSuggestions.clear();
        this.suggestionsVisible = false;
        this.scrollOffsetLines = 0;
    }

    private void sendApprovalDecision(ApprovalDecision decision) {
        if (this.pendingProposal == null) {
            return;
        }

        ChatAENetwork.sendApprovalDecision(this.pendingProposal.id(), decision);
    }

    private void syncFromSessionStore() {
        long version = ClientSessionStore.version();
        if (version == this.lastSnapshotVersion) {
            return;
        }

        this.lastSnapshotVersion = version;

        SessionSnapshot snapshot = ClientSessionStore.get();
        this.statusText = snapshot.state().name();
        this.pendingProposal = snapshot.pendingProposal().orElse(null);
        this.proposalBinding = snapshot.proposalBinding().orElse(null);
        this.activeSessionId = snapshot.metadata().sessionId();

        boolean canSend = isIdleLike(snapshot.state());
        if (this.sendButton != null) {
            this.sendButton.active = canSend;
        }
        if (this.inputBox != null) {
            this.inputBox.setEditable(canSend);
        }
        updateAiLocaleButtonLabel();
        updateProposalUi();
        if (this.sessionsOpen) {
            rebuildSessionRows();
        }

        this.messages.clear();
        for (ChatMessage msg : snapshot.messages()) {
            this.messages.add(formatMessage(msg));
        }

        if (this.messages.size() > MAX_MESSAGES) {
            this.messages.subList(0, this.messages.size() - MAX_MESSAGES).clear();
        }

        rebuildWrappedLines();
    }

    private void updateProposalUi() {
        boolean hasProposal = this.pendingProposal != null;
        if (this.approveButton != null) {
            this.approveButton.active = hasProposal;
            this.approveButton.visible = hasProposal;
        }
        if (this.denyButton != null) {
            this.denyButton.active = hasProposal;
            this.denyButton.visible = hasProposal;
        }
    }

    private static Component formatMessage(ChatMessage message) {
        String timestamp = formatMessageTimestamp(message.timestampMillis());
        MutableComponent prefix = Component.literal("[" + timestamp + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(switch (message.role()) {
                    case USER -> Component.literal("You: ").withStyle(ChatFormatting.YELLOW);
                    case ASSISTANT -> Component.literal("AI: ").withStyle(ChatFormatting.AQUA);
                    case TOOL -> Component.literal("Tool: ").withStyle(ChatFormatting.GRAY);
                    case SYSTEM -> Component.literal("System: ").withStyle(ChatFormatting.DARK_GRAY);
                });

        return prefix.append(renderItemTags(message.text()).withStyle(ChatFormatting.WHITE));
    }

    private void onInputChanged(String value) {
        String safe = escapeItemTags(value == null ? "" : value);
        if (!safe.equals(value)) {
            this.inputBox.setValue(safe);
            return;
        }

        updateTokensFromInput();
        updateSuggestionQuery();
    }

    private static MutableComponent renderItemTags(String text) {
        if (text == null || text.isBlank() || !text.contains("<item")) {
            return Component.literal(text == null ? "" : text);
        }

        MutableComponent result = Component.empty();
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf("<item", index);
            if (start < 0) {
                result.append(Component.literal(text.substring(index)));
                break;
            }
            if (start > index) {
                result.append(Component.literal(text.substring(index, start)));
            }
            int end = text.indexOf('>', start);
            if (end < 0) {
                result.append(Component.literal(text.substring(start)));
                break;
            }

            String tag = text.substring(start, end + 1);
            String itemId = extractAttribute(tag, "id");
            String displayName = extractAttribute(tag, "display_name");
            if (itemId != null) {
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
                if (id != null) {
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        String name = displayName == null || displayName.isBlank()
                                ? new net.minecraft.world.item.ItemStack(item).getHoverName().getString()
                                : displayName;
                        net.minecraft.network.chat.MutableComponent itemComponent = Component.literal(name).withStyle(ChatFormatting.GOLD);
                        result.append(itemComponent);
                        index = end + 1;
                        continue;
                    }
                }
            }
            result.append(Component.literal(tag));
            index = end + 1;
        }

        return result;
    }

    private static String extractAttribute(String tag, String attr) {
        String needle = attr + "=\"";
        int idx = tag.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int start = idx + needle.length();
        int end = tag.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return tag.substring(start, end);
    }

    private void updateTokensFromInput() {
        String value = this.inputBox == null ? "" : this.inputBox.getValue();
        if (value.isBlank()) {
            this.inputTokens.clear();
            return;
        }

        this.inputTokens.removeIf(token -> !value.contains("@" + token.displayName()));
    }

    private void updateSuggestionQuery() {
        if (this.inputBox == null) {
            return;
        }

        String value = this.inputBox.getValue();
        int cursor = this.inputBox.getCursorPosition();
        this.lastCursorPosition = cursor;
        String slice = value.substring(0, Math.min(cursor, value.length()));
        int atIndex = slice.lastIndexOf('@');
        if (atIndex < 0) {
            clearSuggestions();
            return;
        }


        int endIndex = atIndex + 1;
        while (endIndex < value.length()) {
            char ch = value.charAt(endIndex);
            if (Character.isWhitespace(ch)) {
                break;
            }
            endIndex++;
        }

        this.suggestionAnchorIndex = atIndex;
        this.suggestionQuery = value.substring(atIndex + 1, endIndex).trim();
        rebuildSuggestions();
    }

    private void rebuildSuggestions() {
        this.itemSuggestions.clear();
        this.suggestionsVisible = this.inputBox != null && this.inputBox.isFocused();
        if (!this.suggestionsVisible) {
            return;
        }

        String query = this.suggestionQuery.toLowerCase();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            String displayName = stack.getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!query.isBlank() && !matchesQuery(displayName, id, query)) {
                continue;
            }
            int score = scoreCandidate(displayName, id, query);
            this.itemSuggestions.add(new ItemSuggestion(item, displayName, id, score, 0xF0D27C));
        }

        this.itemSuggestions.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.score(), a.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return a.displayName().compareToIgnoreCase(b.displayName());
        });

        int limit = Math.min(TOKEN_PANEL_MAX_ROWS, this.itemSuggestions.size());
        if (limit < this.itemSuggestions.size()) {
            this.itemSuggestions.subList(limit, this.itemSuggestions.size()).clear();
        }
    }

    private void clearSuggestions() {
        this.suggestionsVisible = false;
        this.itemSuggestions.clear();
        this.hoveredSuggestionIndex = -1;
    }

    private boolean matchesQuery(String displayName, String id, String query) {
        if (query.isBlank()) {
            return true;
        }
        String lowerName = displayName.toLowerCase();
        String lowerId = id.toLowerCase();
        return lowerName.contains(query) || lowerId.contains(query);
    }

    private int scoreCandidate(String displayName, String id, String query) {
        if (query.isBlank()) {
            return 0;
        }
        String lowerName = displayName.toLowerCase();
        String lowerId = id.toLowerCase();
        if (lowerName.startsWith(query)) {
            return 3;
        }
        if (lowerId.startsWith(query)) {
            return 2;
        }
        if (lowerName.contains(query) || lowerId.contains(query)) {
            return 1;
        }
        return 0;
    }

    private void applySuggestion(ItemSuggestion suggestion) {
        if (this.inputBox == null) {
            return;
        }

        String value = this.inputBox.getValue();
        int cursor = this.inputBox.getCursorPosition();
        int start = Math.max(0, Math.min(this.suggestionAnchorIndex, value.length()));
        int end = start;
        while (end < value.length()) {
            char ch = value.charAt(end);
            if (Character.isWhitespace(ch)) {
                break;
            }
            end++;
        }

        String before = value.substring(0, start);
        String after = value.substring(end);
        String insert = "@" + suggestion.displayName();
        if (!after.isBlank()) {
            insert = insert + " ";
        }

        this.inputBox.setValue(before + insert + after);
        this.inputBox.setCursorPosition(before.length() + insert.length());

        int tokenIndex = this.inputTokens.size();
        this.inputTokens.add(new ItemToken(suggestion.item(), suggestion.displayName(), suggestion.itemId(), 0xF0D27C, tokenIndex));
        clearSuggestions();
    }


    private String serializeInputForSend(String input) {
        String sanitized = escapeItemTags(input);
        String output = sanitized;
        List<ItemToken> tokens = new ArrayList<>(this.inputTokens);
        tokens.sort((a, b) -> Integer.compare(b.displayName().length(), a.displayName().length()));
        for (ItemToken token : tokens) {
            String tokenLabel = "@" + token.displayName();
            if (output.contains(tokenLabel)) {
                String tag = "<item id=\"" + token.itemId() + "\" display_name=\"" + escapeAttribute(token.displayName()) + "\">";
                output = replaceFirstLiteral(output, tokenLabel, tag);
            }
        }
        return output;
    }

    private String replaceFirstLiteral(String source, String target, String replacement) {
        int index = source.indexOf(target);
        if (index < 0) {
            return source;
        }
        return source.substring(0, index) + replacement + source.substring(index + target.length());
    }

    private String escapeAttribute(String value) {
        return value.replace("\"", "&quot;");
    }


    private String escapeItemTags(String input) {
        if (input.contains("<item")) {
            return input.replace("<", "&lt;").replace(">", "&gt;");
        }
        return input;
    }

    @Override
    public void onClose() {
        clearSuggestions();
        this.inputTokens.clear();
        super.onClose();
    }

    private TokenMetrics measureToken(ItemToken token) {
        int textWidth = this.font.width(token.displayName());
        int width = TOKEN_PADDING_X + TOKEN_ICON_SIZE + TOKEN_TEXT_GAP + textWidth + TOKEN_PADDING_X;
        return new TokenMetrics(width);
    }

    private static String buildProposalDetailLine(Proposal proposal, space.controlnet.chatae.core.session.TerminalBinding binding) {
        String detail = "";
        ProposalDetails details = proposal.details();
        if (details != null) {
            if (!details.missingItems().isEmpty()) {
                detail = "Missing: " + String.join(", ", details.missingItems());
            } else if (!details.note().isBlank()) {
                detail = details.note();
            }
        }

        if (binding != null) {
            String side = binding.side().orElse("BLOCK");
            String bound = "Bound: " + binding.dimensionId() + " " + binding.x() + "," + binding.y() + "," + binding.z() + " " + side;
            if (detail.isBlank()) {
                return bound;
            }
            return detail + " | " + bound;
        }

        return detail;
    }

    private void appendMessage(Component message) {
        this.messages.add(message);
        if (this.messages.size() > MAX_MESSAGES) {
            this.messages.subList(0, this.messages.size() - MAX_MESSAGES).clear();
        }
        rebuildWrappedLines();
    }

    private void rebuildWrappedLines() {
        this.wrappedLines.clear();
        int maxWidth = Math.max(1, this.chatW - 8);
        for (Component message : this.messages) {
            this.wrappedLines.addAll(this.font.split(message, maxWidth));
        }
    }

    private boolean isWithinChat(double mouseX, double mouseY) {
        return mouseX >= this.chatX && mouseX < (this.chatX + this.chatW)
                && mouseY >= this.chatY && mouseY < (this.chatY + this.chatH);
    }

    private static boolean isIdleLike(SessionState state) {
        return state == SessionState.IDLE
                || state == SessionState.DONE
                || state == SessionState.FAILED;
    }

    private SessionState sessionStateFromStatus() {
        try {
            return SessionState.valueOf(this.statusText);
        } catch (IllegalArgumentException ex) {
            return SessionState.IDLE;
        }
    }

    private static String stateLabel(SessionState state) {
        return switch (state) {
            case IDLE -> "Idle";
            case INDEXING -> "Indexing";
            case THINKING -> "Thinking";
            case WAIT_APPROVAL -> "Awaiting Approval";
            case EXECUTING -> "Executing";
            case DONE -> "Done";
            case FAILED -> "Failed";
            case CANCELED -> "Canceled";
        };
    }

    private static int statusDotColor(SessionState state) {
        return switch (state) {
            case IDLE -> 0x7A8B6C;
            case INDEXING -> 0xC59A3C;
            case THINKING -> 0x62A5E4;
            case WAIT_APPROVAL -> 0xD77A3D;
            case EXECUTING -> 0x5BBE9A;
            case DONE -> 0x7AC2A5;
            case FAILED -> 0xC45B5B;
            case CANCELED -> 0x8B7A67;
        };
    }

    private static String formatMessageTimestamp(long timestampMillis) {
        if (timestampMillis <= 0) {
            return "--:--";
        }
        return MESSAGE_TIME_FORMAT.format(Instant.ofEpochMilli(timestampMillis));
    }

    private static String formatRelativeTime(long timestampMillis) {
        if (timestampMillis <= 0) {
            return "";
        }
        long now = System.currentTimeMillis();
        long deltaMillis = Math.max(0, now - timestampMillis);
        long minutes = deltaMillis / 60000L;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        return days + "d ago";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TokenMetrics(int width) {
    }

    private record ItemSuggestion(Item item, String displayName, String itemId, int score, int color) {
    }

    private record ItemToken(Item item, String displayName, String itemId, int color, int index) {
    }

    private static final class SessionRow {
        private SessionSummary summary;
        private Button openButton;
        private Button deleteButton;
        private Button visibilityButton;
    }
}
