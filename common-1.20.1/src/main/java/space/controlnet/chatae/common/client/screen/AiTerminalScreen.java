package space.controlnet.chatae.common.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import space.controlnet.chatae.core.session.ChatRole;
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
    private static final int PADDING = 10;
    private static final int HEADER_HEIGHT = 28;
    private static final int INPUT_HEIGHT = 20;
    private static final int INPUT_PAD_Y = 7;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int SIDEBAR_HEADER_HEIGHT = 22;
    private static final int SIDEBAR_FOOTER_HEIGHT = 22;
    private static final int SEND_BUTTON_WIDTH = 46;
    private static final int MAX_MESSAGES = 200;
    private static final int PROPOSAL_CARD_HEIGHT = 56;
    private static final int PROPOSAL_CARD_GAP = 6;
    private static final int PROPOSAL_BUTTON_WIDTH = 62;
    private static final int PROPOSAL_BUTTON_HEIGHT = 18;
    private static final int PROPOSAL_BUTTON_GAP = 4;
    private static final int LOCALE_BUTTON_WIDTH = 24;
    private static final int LOCALE_BUTTON_HEIGHT = 18;
    private static final int SESSION_TOGGLE_WIDTH = 24;
    private static final int SESSION_TOGGLE_HEIGHT = 18;
    private static final int SESSION_PANEL_PADDING = 8;
    private static final int SESSION_ROW_HEIGHT = 28;
    private static final int STATUS_DOT_SIZE = 6;
    private static final int SESSION_ROW_GAP = 6;
    private static final int SESSION_DELETE_BUTTON_WIDTH = 20;
    private static final int SESSION_VIS_BUTTON_WIDTH = 32;
    private static final int SESSION_BUTTON_HEIGHT = 16;
    private static final int SESSION_BUTTON_GAP = 4;
    private static final int SESSION_NEW_BUTTON_WIDTH = 24;
    private static final int SESSION_NEW_BUTTON_HEIGHT = 18;
    private static final int TOKEN_ICON_SIZE = 12;
    private static final int TOKEN_TEXT_GAP = 4;
    private static final int TOKEN_PADDING_X = 4;
    private static final int TOKEN_PANEL_PADDING = 6;
    private static final int TOKEN_PANEL_WIDTH = 200;
    private static final int TOKEN_PANEL_MAX_ROWS = 6;
    private static final int TOKEN_PANEL_ROW_HEIGHT = 18;
    private static final int MESSAGE_PAD_X = 8;
    private static final int MESSAGE_PAD_Y = 4;
    private static final int MESSAGE_MAX_WIDTH_PAD = 36;
    private static final int TOOL_RESULT_MAX = 8;
    private static final int MAX_CHAT_MESSAGE_LENGTH = 65536;
    private static final float TITLE_FONT_SCALE = 0.9f;
    private static final float FONT_SCALE = 0.5f;
    private static final float TOOLTIP_FONT_SCALE = 0.4f;

    private static final int COLOR_BG_DARK = 0xFF0B0B0D;
    private static final int COLOR_BG_PANEL = 0xFF151518;
    private static final int COLOR_BG_PANEL_TRANSPARENT = 0xD9151518;
    private static final int COLOR_BORDER = 0xFF3A3A40;
    private static final int COLOR_PRIMARY_FLUIX = 0xFF6B2FB5;
    private static final int COLOR_PRIMARY_FLUIX_DIM = 0x806B2FB5;
    private static final int COLOR_ACCENT_CYAN = 0xFF00FFFF;
    private static final int COLOR_ACCENT_CYAN_DIM = 0x3300FFFF;
    private static final int COLOR_TEXT_MAIN = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DIM = 0xFF808080;
    private static final int COLOR_TEXT_HIGHLIGHT = 0xFFFFFFFF;

    private static final int COLOR_STATUS_ONLINE = 0xFF00FF9D;
    private static final int COLOR_STATUS_INDEXING = 0xFF33CCFF;
    private static final int COLOR_STATUS_THINKING = 0xFFD400FF;
    private static final int COLOR_STATUS_EXECUTING = 0xFFFFCC00;
    private static final int COLOR_STATUS_BUSY = 0xFFFFB700;
    private static final int COLOR_STATUS_ERROR = 0xFFFF3333;

    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<ChatLine> wrappedLines = new ArrayList<>();
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

    private int sidebarX;
    private int sidebarY;
    private int sidebarW;
    private int sidebarH;
    private int headerX;
    private int headerY;
    private int headerW;
    private int headerH;
    private int chatX;
    private int chatY;
    private int chatW;
    private int chatH;
    private int proposalX;
    private int proposalY;
    private int proposalW;
    private int proposalH;
    private int inputX;
    private int inputFieldX;
    private int inputY;
    private int inputW;
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
    private int maxScrollLines;
    private long lastSnapshotVersion = -1;
    private long lastSessionIndexVersion = -1;
    private boolean sessionsOpen;
    private UUID activeSessionId;
    private ToolPayload hoveredToolPayload;

    private List<SessionSummary> sessionSummaries = List.of();
    private final List<SessionRow> sessionRows = new ArrayList<>();

    public AiTerminalScreen(AiTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("item.chatae.ai_terminal"));
        this.imageWidth = 420;
        this.imageHeight = 250;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();

        this.inputBox = new EditBox(this.font, this.inputFieldX, this.inputY, this.inputW, INPUT_HEIGHT, Component.empty());
        this.inputBox.setMaxLength(MAX_CHAT_MESSAGE_LENGTH);
        this.inputBox.setBordered(false);
        this.inputBox.setCanLoseFocus(true);
        this.inputBox.setTextColor(COLOR_TEXT_MAIN);
        this.inputBox.setTextColorUneditable(COLOR_TEXT_DIM);
        this.inputBox.setResponder(this::onInputChanged);
        this.addRenderableWidget(this.inputBox);

        this.sendButton = new FlatButton(
                this.inputFieldX + this.inputW + 4,
                this.inputY,
                SEND_BUTTON_WIDTH,
                INPUT_HEIGHT,
                Component.literal("SEND"),
                b -> submitInput(),
                UiButtonStyle.ACCENT
        );
        this.addRenderableWidget(this.sendButton);

        this.aiLocaleButton = new FlatButton(
                0,
                0,
                LOCALE_BUTTON_WIDTH,
                LOCALE_BUTTON_HEIGHT,
                Component.literal(buildAiLocaleLabel()),
                b -> cycleAiLocale(),
                UiButtonStyle.GHOST
        );
        this.addRenderableWidget(this.aiLocaleButton);

        int proposalButtonY = this.proposalY + (this.proposalH - PROPOSAL_BUTTON_HEIGHT) / 2;
        int denyX = this.proposalX + this.proposalW - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;
        int approveX = denyX - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;

        this.approveButton = new FlatButton(
                approveX,
                proposalButtonY,
                PROPOSAL_BUTTON_WIDTH,
                PROPOSAL_BUTTON_HEIGHT,
                Component.literal("APPROVE"),
                b -> sendApprovalDecision(ApprovalDecision.APPROVE),
                UiButtonStyle.PRIMARY
        );
        this.denyButton = new FlatButton(
                denyX,
                proposalButtonY,
                PROPOSAL_BUTTON_WIDTH,
                PROPOSAL_BUTTON_HEIGHT,
                Component.literal("DENY"),
                b -> sendApprovalDecision(ApprovalDecision.DENY),
                UiButtonStyle.DANGER
        );

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

    private void computeLayout() {
        this.sidebarX = this.leftPos;
        this.sidebarY = this.topPos;
        this.sidebarW = this.sessionsOpen ? SIDEBAR_WIDTH : 0;
        this.sidebarH = this.imageHeight;

        this.headerX = this.leftPos + this.sidebarW;
        this.headerY = this.topPos;
        this.headerW = this.imageWidth - this.sidebarW;
        this.headerH = HEADER_HEIGHT;

        this.inputX = this.headerX + PADDING;
        this.inputFieldX = this.inputX + 10;
        this.inputY = this.topPos + this.imageHeight - PADDING - INPUT_HEIGHT;
        this.inputW = this.headerW - (PADDING * 2) - SEND_BUTTON_WIDTH - 4 - 10;

        this.chatX = this.headerX + PADDING;
        this.chatY = this.headerY + this.headerH + PADDING;
        this.chatW = this.headerW - (PADDING * 2);
        this.chatH = this.inputY - this.chatY - PADDING;

        this.proposalX = this.chatX;
        this.proposalY = this.chatY + this.chatH - PROPOSAL_CARD_HEIGHT;
        this.proposalW = this.chatW;
        this.proposalH = PROPOSAL_CARD_HEIGHT;

        this.sessionPanelX = this.sidebarX;
        this.sessionPanelY = this.topPos;
        this.sessionPanelW = this.sidebarW;
        this.sessionPanelH = this.imageHeight;

        this.sessionInnerX = this.sessionPanelX + SESSION_PANEL_PADDING;
        this.sessionInnerW = Math.max(1, this.sessionPanelW - (SESSION_PANEL_PADDING * 2));
        int headerBottom = this.sessionPanelY + SESSION_PANEL_PADDING + SIDEBAR_HEADER_HEIGHT + 4;
        this.sessionListStartY = headerBottom;
        int listEndY = this.sessionPanelY + this.sessionPanelH - SESSION_PANEL_PADDING - SIDEBAR_FOOTER_HEIGHT;
        int availableHeight = Math.max(0, listEndY - this.sessionListStartY);
        this.sessionMaxRows = Math.max(1, availableHeight / (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
    }

    private String buildAiLocaleLabel() {
        String override = space.controlnet.chatae.core.client.ClientAiSettings.getAiLocaleOverride();
        if (override == null || override.isBlank()) {
            return "AUTO";
        }
        return override.toUpperCase();
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
        String brand = "AI TERMINAL";
        int titleX = this.headerX + PADDING + SESSION_TOGGLE_WIDTH + 6;
        int titleY = this.headerY + (this.headerH - this.font.lineHeight) / 2;
        drawScaledString(guiGraphics, brand, titleX, titleY, COLOR_TEXT_MAIN, true);

        SessionState state = sessionStateFromStatus();
        String statusLabel = stateLabel(state).toUpperCase();
        int statusTextWidth = scaledWidth(statusLabel);
        int localeWidth = this.aiLocaleButton == null ? 0 : this.aiLocaleButton.getWidth();
        int statusRight = this.headerX + this.headerW - PADDING - localeWidth - 48;
        int statusX = statusRight - statusTextWidth;
        int statusY = this.headerY + (this.headerH - Math.round(this.font.lineHeight * FONT_SCALE)) / 2;
        int dotX = statusX - STATUS_DOT_SIZE - 4;
        int dotY = statusY + (Math.round(this.font.lineHeight * FONT_SCALE) - STATUS_DOT_SIZE - 2) / 2;
        int dotColor = statusDotColor(state);
        guiGraphics.fill(dotX, dotY, dotX + STATUS_DOT_SIZE, dotY + STATUS_DOT_SIZE, 0xFF000000 | dotColor);
        drawScaledString(guiGraphics, statusLabel, statusX, statusY, dotColor, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, COLOR_BG_DARK);
        guiGraphics.renderOutline(left, top, this.imageWidth, this.imageHeight, COLOR_BORDER);
        guiGraphics.renderOutline(left + 1, top + 1, this.imageWidth - 2, this.imageHeight - 2, COLOR_PRIMARY_FLUIX_DIM);

        if (this.sessionsOpen) {
            guiGraphics.fill(this.sessionPanelX, this.sessionPanelY, this.sessionPanelX + this.sessionPanelW, this.sessionPanelY + this.sessionPanelH, COLOR_BG_PANEL);
            guiGraphics.fill(this.sessionPanelX + this.sessionPanelW - 1, this.sessionPanelY,
                    this.sessionPanelX + this.sessionPanelW, this.sessionPanelY + this.sessionPanelH, COLOR_BORDER);
            int headerBottom = this.sessionPanelY + SESSION_PANEL_PADDING + SIDEBAR_HEADER_HEIGHT;
            guiGraphics.fill(this.sessionPanelX, headerBottom, this.sessionPanelX + this.sessionPanelW, headerBottom + 1, COLOR_BORDER);
            int footerTop = this.sessionPanelY + this.sessionPanelH - SIDEBAR_FOOTER_HEIGHT;
            guiGraphics.fill(this.sessionPanelX, footerTop, this.sessionPanelX + this.sessionPanelW, footerTop + 1, COLOR_BORDER);
        }

        guiGraphics.fill(this.headerX, this.headerY, this.headerX + this.headerW, this.headerY + this.headerH, COLOR_BG_PANEL_TRANSPARENT);
        guiGraphics.fill(this.headerX, this.headerY + this.headerH - 1, this.headerX + this.headerW, this.headerY + this.headerH, COLOR_BORDER);

        guiGraphics.fill(this.chatX, this.chatY, this.chatX + this.chatW, this.chatY + this.chatH, 0xD90B0B0D);
        guiGraphics.renderOutline(this.chatX, this.chatY, this.chatW, this.chatH, COLOR_BORDER);

        if (this.pendingProposal != null) {
            // Proposal card renders within chat area in renderProposalCard.
        }

        int inputAreaY = this.inputY - INPUT_PAD_Y;
        int inputAreaH = INPUT_HEIGHT + INPUT_PAD_Y * 2;
        guiGraphics.fill(this.headerX, inputAreaY, this.headerX + this.headerW, inputAreaY + inputAreaH, COLOR_BG_PANEL);
        guiGraphics.fill(this.headerX, inputAreaY, this.headerX + this.headerW, inputAreaY + 1, COLOR_BORDER);

        int inputBoxX = this.inputFieldX;
        int inputBoxY = this.inputY;
        guiGraphics.fill(inputBoxX, inputBoxY, inputBoxX + this.inputW, inputBoxY + INPUT_HEIGHT, 0x66000000);
        int inputBorder = this.inputBox != null && this.inputBox.isFocused() ? COLOR_ACCENT_CYAN : COLOR_BORDER;
        guiGraphics.renderOutline(inputBoxX, inputBoxY, this.inputW, INPUT_HEIGHT, inputBorder);
        int promptY = inputBoxY + (INPUT_HEIGHT - this.font.lineHeight) / 2 + 1;
        drawScaledString(guiGraphics, ">", this.inputX + 4, promptY, COLOR_ACCENT_CYAN, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromSessionStore();
        syncFromSessionIndex();
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderChatLog(guiGraphics, mouseX, mouseY);
        renderProposalCard(guiGraphics);
        renderSessionsPanel(guiGraphics);
        renderInputTokens(guiGraphics);
        renderInputHint(guiGraphics);
        renderItemSuggestions(guiGraphics, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        renderToolTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderInputHint(GuiGraphics guiGraphics) {
        if (this.inputBox == null) {
            return;
        }
        if (!this.inputBox.getValue().isBlank()) {
            return;
        }
        String hint = "Type a command or request (@ for items)...";
        int hintX = this.inputFieldX + 4;
        int hintY = this.inputY + (INPUT_HEIGHT - this.font.lineHeight) / 2 + 1;
        drawScaledString(guiGraphics, this.font.plainSubstrByWidth(hint, Math.max(1, this.inputW - 8)), hintX, hintY, COLOR_TEXT_DIM, false);
    }

    private void renderChatLog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int lineHeight = scaledLineHeight();
        int spacing = 2;
        int lineStep = Math.max(1, lineHeight + spacing);
        this.hoveredToolPayload = null;

        // Compute total content height for scroll limits.
        int totalHeight = 0;
        int maxWidth = Math.max(1, this.chatW - MESSAGE_MAX_WIDTH_PAD);
        for (ChatMessage message : this.messages) {
            List<ChatLine> messageLines = buildMessageLines(message, maxWidth);
            if (messageLines.isEmpty()) {
                continue;
            }
            int totalTextHeight = messageLines.size() * lineHeight + (messageLines.size() - 1) * spacing;
            int bubbleH = totalTextHeight + (MESSAGE_PAD_Y * 2);
            totalHeight += bubbleH + 12;
        }
        int visibleHeight = this.chatH - 8;
        int maxScrollPx = Math.max(0, totalHeight - visibleHeight);
        this.maxScrollLines = maxScrollPx == 0 ? 0 : (int) Math.ceil(maxScrollPx / (double) lineStep);
        this.scrollOffsetLines = clamp(this.scrollOffsetLines, 0, this.maxScrollLines);
        int scrollOffsetPx = this.scrollOffsetLines * lineStep;

        guiGraphics.enableScissor(this.chatX + 1, this.chatY + 1, this.chatX + this.chatW - 1, this.chatY + this.chatH - 1);

        int currentY = this.chatY + 4 - scrollOffsetPx;

        for (ChatMessage message : this.messages) {
            List<ChatLine> messageLines = buildMessageLines(message, maxWidth);

            if (messageLines.isEmpty()) continue;
            int totalTextHeight = messageLines.size() * lineHeight + (messageLines.size() - 1) * spacing;
            int bubbleH = totalTextHeight + (MESSAGE_PAD_Y * 2);

            int maxLineWidth = 0;
            for (ChatLine line : messageLines) {
                maxLineWidth = Math.max(maxLineWidth, line.width());
            }
            int bubbleW = maxLineWidth + (MESSAGE_PAD_X * 2);

            int bubbleX;
            if (message.role() == ChatRole.USER) {
                bubbleX = this.chatX + this.chatW - bubbleW - 10;
            } else if (message.role() == ChatRole.SYSTEM) {
                bubbleX = this.chatX + (this.chatW - bubbleW) / 2;
            } else {
                bubbleX = this.chatX + 10;
            }

            if (message.role() != ChatRole.SYSTEM) {
                int bgColor = message.role() == ChatRole.USER ? 0x336B2FB5 : 0x662A2A2A;
                int borderColor = message.role() == ChatRole.USER ? COLOR_PRIMARY_FLUIX : COLOR_ACCENT_CYAN;
                guiGraphics.fill(bubbleX, currentY, bubbleX + bubbleW, currentY + bubbleH, bgColor);
                guiGraphics.renderOutline(bubbleX, currentY, bubbleW, bubbleH, borderColor);
                if (message.role() == ChatRole.ASSISTANT) {
                    guiGraphics.fill(bubbleX, currentY, bubbleX + 2, currentY + bubbleH, COLOR_ACCENT_CYAN);
                }
            }
            if (message.role() == ChatRole.TOOL
                    && mouseX >= bubbleX && mouseX < bubbleX + bubbleW
                    && mouseY >= currentY && mouseY < currentY + bubbleH) {
                this.hoveredToolPayload = parseToolPayload(message.text());
            }

            int textY = currentY + MESSAGE_PAD_Y;
            for (ChatLine line : messageLines) {
                int drawX = bubbleX + MESSAGE_PAD_X;
                for (ChatSpan span : line.spans()) {
                    if (span.token() != null) {
                        renderTokenPill(guiGraphics, span.token(), drawX, textY - 2, span.width(), lineHeight + 4);
                    } else {
                        drawScaledString(guiGraphics, span.text(), drawX, textY, COLOR_TEXT_MAIN, false);
                    }
                    drawX += span.width();
                }
                textY += lineHeight + spacing;
            }

            String timestamp = formatMessageTimestamp(message.timestampMillis());
            int timeX = (message.role() == ChatRole.USER) ? bubbleX + bubbleW - scaledWidth(timestamp) : bubbleX;
            drawScaledString(guiGraphics, timestamp, timeX, currentY + bubbleH + 2, COLOR_TEXT_DIM, false);

            currentY += bubbleH + 12;
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
        int rowHeight = Math.max(12, scaledLineHeight() + 4);
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
        int bg = 0xFF3C3C41;
        int outline = 0xFF505055;
        guiGraphics.fill(x, y, x + width, y + height, bg);
        guiGraphics.renderOutline(x, y, width, height, outline);

        ItemStack stack = new ItemStack(token.item());

        float iconScale = FONT_SCALE;
        int iconSize = Math.round(16 * iconScale);
        int iconX = x + 2;
        int iconY = y + (height - iconSize) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX, iconY, 0);
        guiGraphics.pose().scale(iconScale, iconScale, 1.0f);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();

        int textX = iconX + iconSize + 2;
        int textY = y + (height - Math.round(this.font.lineHeight * FONT_SCALE)) / 2;
        String label = token.displayName();
        drawScaledString(guiGraphics, label, textX, textY, token.color(), false);
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

        int bg = 0xE6151518;
        int outline = COLOR_BORDER;
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, bg);
        guiGraphics.renderOutline(panelX, panelY, panelW, panelH, outline);

        this.hoveredSuggestionIndex = -1;
        for (int i = 0; i < visibleRows; i++) {
            int rowY = panelY + TOKEN_PANEL_PADDING + i * TOKEN_PANEL_ROW_HEIGHT;
            if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= rowY && mouseY < rowY + TOKEN_PANEL_ROW_HEIGHT) {
                this.hoveredSuggestionIndex = i;
                guiGraphics.fill(panelX + 2, rowY + 1, panelX + panelW - 2, rowY + TOKEN_PANEL_ROW_HEIGHT - 1, 0x33404040);
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
        drawScaledString(guiGraphics, name, textX, y + 4, suggestion.color(), false);
    }

    private void renderProposalCard(GuiGraphics guiGraphics) {
        if (this.pendingProposal == null) {
            return;
        }

        String label = proposalHeaderLabel(this.pendingProposal);
        String id = formatProposalId(this.pendingProposal.id());
        List<String> detailLines = buildProposalDetailLines(this.pendingProposal, this.proposalBinding);

        int lineHeight = scaledLineHeight();
        int detailCount = detailLines.size();
        int bubbleHeight = (lineHeight * (1 + detailCount)) + 12;
        int bubbleWidth = this.chatW - 12;
        int bubbleX = this.chatX + 6;
        int bubbleY = this.chatY + this.chatH - bubbleHeight - 6;

        this.proposalX = bubbleX;
        this.proposalY = bubbleY;
        this.proposalW = bubbleWidth;
        this.proposalH = bubbleHeight;

        guiGraphics.fill(bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleY + bubbleHeight, 0xCC151515);
        guiGraphics.renderOutline(bubbleX, bubbleY, bubbleWidth, bubbleHeight, COLOR_STATUS_BUSY);
        guiGraphics.fill(bubbleX, bubbleY, bubbleX + 3, bubbleY + bubbleHeight, COLOR_STATUS_BUSY);

        int textMaxWidth = bubbleWidth - (PROPOSAL_BUTTON_WIDTH * 2) - (PROPOSAL_BUTTON_GAP * 3) - 12;
        int headerY = bubbleY + 6;
        int headerX = bubbleX + 8;
        int idWidth = scaledWidth(id);
        int idX = bubbleX + bubbleWidth - idWidth - (PROPOSAL_BUTTON_WIDTH * 2) - (PROPOSAL_BUTTON_GAP * 3) - 8;
        drawScaledString(guiGraphics, this.font.plainSubstrByWidth(label, Math.max(1, textMaxWidth)), headerX, headerY, COLOR_STATUS_BUSY, false);
        drawScaledString(guiGraphics, id, idX, headerY, COLOR_TEXT_DIM, false);

        int detailY = headerY + lineHeight + 2;
        for (int i = 0; i < detailLines.size(); i++) {
            String line = this.font.plainSubstrByWidth(detailLines.get(i), Math.max(1, textMaxWidth));
            int color = COLOR_TEXT_DIM;
            if (line.startsWith("Missing:")) {
                color = COLOR_STATUS_ERROR;
            } else if (line.startsWith("Context:")) {
                color = COLOR_STATUS_EXECUTING;
            } else if (i == 0) {
                color = COLOR_TEXT_MAIN;
            }
            drawScaledString(guiGraphics, line, headerX, detailY + (i * lineHeight), color, false);
        }

        int buttonY = bubbleY + bubbleHeight - PROPOSAL_BUTTON_HEIGHT - 6;
        int denyX = bubbleX + bubbleWidth - PROPOSAL_BUTTON_WIDTH - 6;
        int approveX = denyX - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;
        if (this.approveButton != null && this.denyButton != null) {
            this.approveButton.setPosition(approveX, buttonY);
            this.denyButton.setPosition(denyX, buttonY);
        }
    }

    private void renderSessionsPanel(GuiGraphics guiGraphics) {
        if (!this.sessionsOpen) {
            return;
        }

        int headerX = this.sessionInnerX;
        int headerY = this.sessionPanelY + SESSION_PANEL_PADDING + 2;
        drawScaledString(guiGraphics, "SESSIONS", headerX, headerY, COLOR_ACCENT_CYAN, true);

        int visibleCount = Math.min(this.sessionSummaries.size(), this.sessionMaxRows);
        for (int i = 0; i < visibleCount; i++) {
            SessionSummary summary = this.sessionSummaries.get(i);
            int rowY = this.sessionListStartY + (i * (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
            int rowX = this.sessionInnerX;
            int rowW = this.sessionInnerW;
            String title = summary.title();
            if (title == null || title.isBlank()) {
                title = "Untitled";
            }
            boolean isActive = this.activeSessionId != null && this.activeSessionId.equals(summary.sessionId());
            if (isActive) {
                guiGraphics.fill(rowX - 2, rowY - 1, rowX + rowW + 2, rowY + SESSION_ROW_HEIGHT + 1, 0x331D1A28);
            }
            boolean showActions = isActive && isOwner(summary);

            String visibility = visibilityBadgeLabel(summary.visibility());
            int visColor = switch (summary.visibility()) {
                case PUBLIC -> COLOR_STATUS_ONLINE;
                case TEAM -> COLOR_ACCENT_CYAN;
                case PRIVATE -> COLOR_TEXT_DIM;
            };
            int visWidth = scaledWidth(visibility) + 6;
            int visX = rowX;
            int visY = rowY + 2;
            guiGraphics.fill(visX, visY, visX + visWidth, visY + this.font.lineHeight + 2, 0x1AFFFFFF);
            guiGraphics.renderOutline(visX, visY, visWidth, this.font.lineHeight + 2, visColor);
            drawScaledString(guiGraphics, visibility, visX + 3, visY + 1, visColor, false);

            int titleX = visX + visWidth + 4;
            int reservedRight = showActions ? (SESSION_DELETE_BUTTON_WIDTH + SESSION_BUTTON_GAP + 6) : 0;
            int timeWidth = 0;
            String lastActive = "";
            if (!isActive) {
                lastActive = formatRelativeTime(summary.lastActiveMillis());
                if (!lastActive.isBlank()) {
                    timeWidth = scaledWidth(lastActive) + 6;
                }
            }
            int titleMaxWidth = Math.max(1, rowW - visWidth - 4 - timeWidth - reservedRight);
            String trimmed = this.font.plainSubstrByWidth(title, titleMaxWidth);
            drawScaledString(guiGraphics, trimmed, titleX, rowY + 3, COLOR_TEXT_MAIN, false);

            if (!isActive && !lastActive.isBlank()) {
                int timeX = rowX + rowW - scaledWidth(lastActive) - reservedRight;
                drawScaledString(guiGraphics, lastActive, timeX, rowY + 3, COLOR_TEXT_DIM, false);
            }
        }

        if (this.sessionSummaries.isEmpty()) {
            drawScaledString(guiGraphics, "No sessions", this.sessionInnerX, this.sessionListStartY + 2, COLOR_TEXT_DIM, true);
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            String name = this.minecraft.player.getName().getString();
            int footerY = this.sessionPanelY + this.sessionPanelH - SESSION_PANEL_PADDING - this.font.lineHeight;
            drawScaledString(guiGraphics, name, this.sessionInnerX, footerY, COLOR_TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isWithinChat(mouseX, mouseY) && !this.messages.isEmpty()) {
            int direction = delta > 0 ? -1 : 1;
            this.scrollOffsetLines = clamp(this.scrollOffsetLines + direction, 0, this.maxScrollLines);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void layoutSessionPanel() {
        this.sessionPanelX = this.sidebarX;
        this.sessionPanelY = this.sidebarY;
        this.sessionPanelW = this.sidebarW;
        this.sessionPanelH = this.sidebarH;

        this.sessionInnerX = this.sessionPanelX + SESSION_PANEL_PADDING;
        this.sessionInnerW = Math.max(1, this.sessionPanelW - (SESSION_PANEL_PADDING * 2));
        this.sessionListStartY = this.sessionPanelY + SESSION_PANEL_PADDING + SIDEBAR_HEADER_HEIGHT + 4;
        int sessionListEndY = this.sessionPanelY + this.sessionPanelH - SESSION_PANEL_PADDING - SIDEBAR_FOOTER_HEIGHT;

        int availableHeight = Math.max(0, sessionListEndY - this.sessionListStartY);
        this.sessionMaxRows = Math.max(1, availableHeight / (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
    }

    private void initSessionsToggle() {
        int toggleX = this.headerX + PADDING;
        int toggleY = this.headerY + (this.headerH - SESSION_TOGGLE_HEIGHT) / 2;
        this.sessionsToggleButton = new FlatButton(
                toggleX,
                toggleY,
                SESSION_TOGGLE_WIDTH,
                SESSION_TOGGLE_HEIGHT,
                Component.literal("|||"),
                b -> toggleSessionsPanel(),
                UiButtonStyle.GHOST
        );
        this.addRenderableWidget(this.sessionsToggleButton);
    }

    private void initSessionPanelWidgets() {
        this.sessionRows.clear();
        int newButtonX = this.sessionPanelX + this.sessionPanelW - SESSION_PANEL_PADDING - SESSION_NEW_BUTTON_HEIGHT;
        int newButtonY = this.sessionPanelY + SESSION_PANEL_PADDING;
        this.newSessionButton = new FlatButton(
                newButtonX,
                newButtonY,
                SESSION_NEW_BUTTON_WIDTH,
                SESSION_NEW_BUTTON_HEIGHT,
                Component.literal("+"),
                b -> ChatAENetwork.createSession(),
                UiButtonStyle.GHOST
        );
        this.addRenderableWidget(this.newSessionButton);

        int listStartY = this.sessionListStartY;
        for (int i = 0; i < this.sessionMaxRows; i++) {
            int rowY = listStartY + (i * (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
            int buttonY = rowY + (SESSION_ROW_HEIGHT - SESSION_BUTTON_HEIGHT) / 2;
            int deleteX = this.sessionPanelX + this.sessionPanelW - SESSION_PANEL_PADDING - SESSION_DELETE_BUTTON_WIDTH;
            int visX = deleteX - SESSION_BUTTON_GAP - SESSION_VIS_BUTTON_WIDTH;

            SessionRow row = new SessionRow();
            row.deleteButton = new FlatButton(
                    deleteX,
                    buttonY,
                    SESSION_DELETE_BUTTON_WIDTH,
                    SESSION_BUTTON_HEIGHT,
                    Component.literal("DEL"),
                    b -> deleteSession(row),
                    UiButtonStyle.DANGER
            );
            row.visibilityButton = new FlatButton(
                    visX,
                    buttonY,
                    SESSION_VIS_BUTTON_WIDTH,
                    SESSION_BUTTON_HEIGHT,
                    Component.literal("PRIV"),
                    b -> cycleSessionVisibility(row),
                    UiButtonStyle.GHOST
            );

            this.sessionRows.add(row);
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
        computeLayout();
        layoutSessionPanel();
        applyLayoutToWidgets();
        updateSessionsToggleLabel();
        if (this.newSessionButton != null) {
            this.newSessionButton.visible = visible;
            this.newSessionButton.active = visible;
        }
        for (SessionRow row : this.sessionRows) {
            row.deleteButton.visible = visible;
            row.visibilityButton.visible = false;
        }
        if (visible) {
            rebuildSessionRows();
        }
        rebuildWrappedLines();
    }

    private void applyLayoutToWidgets() {
        if (this.inputBox != null) {
            this.inputBox.setX(this.inputFieldX);
            this.inputBox.setY(this.inputY);
            this.inputBox.setWidth(this.inputW);
        }
        if (this.sendButton != null) {
            this.sendButton.setPosition(this.inputFieldX + this.inputW + 4, this.inputY);
        }
        if (this.aiLocaleButton != null) {
            int localeX = this.headerX + this.headerW - PADDING - this.aiLocaleButton.getWidth();
            int localeY = this.headerY + (this.headerH - this.aiLocaleButton.getHeight()) / 2;
            this.aiLocaleButton.setPosition(localeX, localeY);
        }
        if (this.sessionsToggleButton != null) {
            int toggleX = this.headerX + PADDING;
            int toggleY = this.headerY + (this.headerH - SESSION_TOGGLE_HEIGHT) / 2;
            this.sessionsToggleButton.setPosition(toggleX, toggleY);
        }
        if (this.approveButton != null && this.denyButton != null) {
            int proposalButtonY = this.proposalY + (this.proposalH - PROPOSAL_BUTTON_HEIGHT) / 2;
            int denyX = this.proposalX + this.proposalW - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;
            int approveX = denyX - PROPOSAL_BUTTON_WIDTH - PROPOSAL_BUTTON_GAP;
            this.approveButton.setPosition(approveX, proposalButtonY);
            this.denyButton.setPosition(denyX, proposalButtonY);
        }
        if (this.newSessionButton != null) {
            int newButtonX = this.sessionPanelX + this.sessionPanelW - SESSION_PANEL_PADDING - SESSION_NEW_BUTTON_HEIGHT;
            int newButtonY = this.sessionPanelY + SESSION_PANEL_PADDING;
            this.newSessionButton.setPosition(newButtonX, newButtonY);
        }
        int listStartY = this.sessionListStartY;
        for (int i = 0; i < this.sessionRows.size(); i++) {
            SessionRow row = this.sessionRows.get(i);
            int rowY = listStartY + (i * (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
            int buttonY = rowY + (SESSION_ROW_HEIGHT - SESSION_BUTTON_HEIGHT) / 2;
            int deleteX = this.sessionPanelX + this.sessionPanelW - SESSION_PANEL_PADDING - SESSION_DELETE_BUTTON_WIDTH;
            int visX = deleteX - SESSION_BUTTON_GAP - SESSION_VIS_BUTTON_WIDTH;
            row.deleteButton.setPosition(deleteX, buttonY);
            row.visibilityButton.setPosition(visX, buttonY);
        }
    }

    private void updateSessionsToggleLabel() {
        if (this.sessionsToggleButton == null) {
            return;
        }
        String label = this.sessionsOpen ? "X" : "|||";
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
                row.deleteButton.visible = this.sessionsOpen && isActiveSession(summary.sessionId()) && isOwner(summary);
                row.visibilityButton.visible = false;
                row.deleteButton.active = isOwner(summary);
                row.visibilityButton.active = false;
                row.visibilityButton.setMessage(Component.literal(visibilityLabel(summary.visibility()).toUpperCase()));
            } else {
                row.summary = null;
                row.deleteButton.visible = false;
                row.visibilityButton.visible = false;
            }
        }
        if (this.newSessionButton != null) {
            this.newSessionButton.visible = this.sessionsOpen;
            this.newSessionButton.active = this.sessionsOpen;
        }
    }

    private void deleteSession(SessionRow row) {
        if (row.summary == null || !isOwner(row.summary)) {
            return;
        }
        ChatAENetwork.deleteSession(row.summary.sessionId());
        ChatAENetwork.requestSessionList(SessionListScope.ALL);
    }

    private void cycleSessionVisibility(SessionRow row) {
        if (row.summary == null || !isOwner(row.summary)) {
            return;
        }
        boolean teamsAvailable = TeamAccess.isTeamFeatureAvailable();
        SessionVisibility next = nextVisibility(row.summary.visibility(), teamsAvailable);
        ChatAENetwork.updateSession(row.summary.sessionId(), Optional.empty(), Optional.of(next));
    }

    private void cycleSessionVisibility(SessionSummary summary) {
        if (summary == null || !isOwner(summary)) {
            return;
        }
        boolean teamsAvailable = TeamAccess.isTeamFeatureAvailable();
        SessionVisibility next = nextVisibility(summary.visibility(), teamsAvailable);
        ChatAENetwork.updateSession(summary.sessionId(), Optional.empty(), Optional.of(next));
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

    private static String visibilityBadgeLabel(SessionVisibility visibility) {
        return switch (visibility) {
            case PRIVATE -> "PRIVATE";
            case TEAM -> "TEAM";
            case PUBLIC -> "PUBLIC";
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
            if (keyCode == InputConstants.KEY_BACKSPACE) {
                if (handleTokenBackspace()) {
                    return true;
                }
            }
            if (keyCode == InputConstants.KEY_TAB && this.suggestionsVisible && !this.itemSuggestions.isEmpty()) {
                int index = Math.max(this.hoveredSuggestionIndex, 0);
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
        if (handleSessionRowClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSessionRowClick(double mouseX, double mouseY) {
        if (!this.sessionsOpen || this.sessionSummaries.isEmpty()) {
            return false;
        }
        SessionRowHit hit = getSessionRowHit(mouseX, mouseY);
        if (hit == null || hit.summary() == null) {
            return false;
        }
        if (hit.onBadge()) {
            if (isOwner(hit.summary())) {
                cycleSessionVisibility(hit.summary());
            }
            return true;
        }
        ChatAENetwork.openSession(hit.summary().sessionId());
        return true;
    }

    private SessionRowHit getSessionRowHit(double mouseX, double mouseY) {
        int visibleCount = Math.min(this.sessionSummaries.size(), this.sessionMaxRows);
        for (int i = 0; i < visibleCount; i++) {
            int rowX = this.sessionInnerX;
            int rowY = this.sessionListStartY + (i * (SESSION_ROW_HEIGHT + SESSION_ROW_GAP));
            int rowW = this.sessionInnerW;
            int rowH = SESSION_ROW_HEIGHT;
            if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + rowH) {
                SessionSummary summary = this.sessionSummaries.get(i);
                if (isOverSessionActionButtons(mouseX, mouseY, rowX, rowY, rowW)) {
                    return null;
                }
                String badge = visibilityBadgeLabel(summary.visibility());
                int badgeWidth = scaledWidth(badge) + 6;
                int badgeX = rowX;
                int badgeY = rowY + 2;
                boolean onBadge = mouseX >= badgeX && mouseX < badgeX + badgeWidth
                        && mouseY >= badgeY && mouseY < badgeY + this.font.lineHeight + 2;
                return new SessionRowHit(summary, onBadge);
            }
        }
        return null;
    }

    private boolean isOverSessionActionButtons(double mouseX, double mouseY, int rowX, int rowY, int rowW) {
        for (SessionRow row : this.sessionRows) {
            if (row.deleteButton != null && row.deleteButton.visible && isWithinButton(row.deleteButton, mouseX, mouseY)) {
                return true;
            }
        }
        if (this.newSessionButton != null && this.newSessionButton.visible && isWithinButton(this.newSessionButton, mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    private boolean isWithinButton(Button button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
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
            this.messages.add(msg);
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

    private void onInputChanged(String value) {
        String safe = escapeItemTags(value == null ? "" : value);
        if (!safe.equals(value)) {
            this.inputBox.setValue(safe);
            return;
        }

        updateTokensFromInput();
        updateSuggestionQuery();
    }

    private List<ChatSpan> parseMessageSpans(String text) {
        List<ChatSpan> spans = new ArrayList<>();
        if (text == null || text.isBlank() || !text.contains("<item")) {
            if (text != null && !text.isEmpty()) {
                spans.add(new ChatSpan(text, null, scaledWidth(text)));
            }
            return spans;
        }

        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf("<item", index);
            if (start < 0) {
                String tail = text.substring(index);
                spans.add(new ChatSpan(tail, null, scaledWidth(tail)));
                break;
            }
            if (start > index) {
                String head = text.substring(index, start);
                spans.add(new ChatSpan(head, null, scaledWidth(head)));
            }
            int end = text.indexOf('>', start);
            if (end < 0) {
                String tail = text.substring(start);
                spans.add(new ChatSpan(tail, null, scaledWidth(tail)));
                break;
            }

            String tag = text.substring(start, end + 1);
            String itemId = extractAttribute(tag, "id");
            String displayName = extractAttribute(tag, "display_name");
            ItemToken token = buildItemToken(itemId, displayName);
            if (token != null) {
                spans.add(new ChatSpan(token.displayName(), token, measureToken(token).width()));
            } else {
                spans.add(new ChatSpan(tag, null, scaledWidth(tag)));
            }
            index = end + 1;
        }

        return spans;
    }

    private List<ChatLine> buildMessageLines(ChatMessage message, int maxWidth) {
        if (message == null) {
            return List.of();
        }
        String text = message.text();
        if (message.role() == ChatRole.TOOL) {
            text = formatToolText(text);
        }
        return wrapMessageText(text, maxWidth, message.role());
    }

    private List<ChatLine> wrapMessageText(String text, int maxWidth, ChatRole role) {
        List<ChatLine> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        String[] segments = text.split("\\R", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                lines.add(new ChatLine(List.of(), role, false, 0, ""));
                continue;
            }
            List<ChatSpan> spans = parseMessageSpans(segment);
            List<ChatLine> wrapped = wrapSpans(spans, maxWidth, role);
            if (wrapped.isEmpty()) {
                lines.add(new ChatLine(List.of(), role, false, 0, ""));
            } else {
                lines.addAll(wrapped);
            }
        }
        return lines;
    }

    private String formatToolText(String raw) {
        ToolPayload payload = parseToolPayload(raw);
        if (payload != null && payload.tool() != null) {
            List<String> lines = new ArrayList<>();
            String summary = formatToolSummary(payload);
            if (summary != null && !summary.isBlank()) {
                lines.add(summary);
            }
            if (payload.error() != null && !payload.error().isBlank()) {
                lines.add(Component.translatable("ui.chatae.tool.error", payload.error()).getString());
            }
            if (payload.outputJson() != null && !payload.outputJson().isBlank()) {
                lines.addAll(formatToolLines(payload.outputJson()));
            }
            if (!lines.isEmpty()) {
                return String.join("\n", lines);
            }
        }
        List<String> lines = formatToolLines(raw);
        if (lines.isEmpty()) {
            return raw == null ? "" : raw;
        }
        return String.join("\n", lines);
    }

    private String formatToolSummary(ToolPayload payload) {
        String tool = payload.tool();
        if (tool == null || tool.isBlank()) {
            return null;
        }
        JsonObject args = parseJsonObject(payload.argsJson());
        String fallback = Component.translatable("ui.chatae.tool.unknown", tool).getString();
        return switch (tool) {
            case "mc.find_recipes" -> Component.translatable(
                    "ui.chatae.tool.mc.find_recipes",
                    formatSearchTarget(args, "itemId", "itemId")).getString();
            case "mc.find_usage" -> Component.translatable(
                    "ui.chatae.tool.mc.find_usage",
                    formatSearchTarget(args, "itemId", "itemId")).getString();
            case "ae2.list_items" -> Component.translatable(
                    "ui.chatae.tool.ae2.list_items",
                    formatQuery(args)).getString();
            case "ae2.list_craftables" -> Component.translatable(
                    "ui.chatae.tool.ae2.list_craftables",
                    formatQuerySuffix(args)).getString();
            case "ae2.simulate_craft" -> Component.translatable(
                    "ui.chatae.tool.ae2.simulate_craft",
                    formatCraftTarget(args)).getString();
            case "ae2.request_craft" -> Component.translatable(
                    "ui.chatae.tool.ae2.request_craft",
                    formatCraftTarget(args)).getString();
            case "ae2.job_status" -> Component.translatable(
                    "ui.chatae.tool.ae2.job_status",
                    formatArg(args, "jobId")).getString();
            case "ae2.job_cancel" -> Component.translatable(
                    "ui.chatae.tool.ae2.job_cancel",
                    formatArg(args, "jobId")).getString();
            default -> fallback;
        };
    }

    private String formatSearchTarget(JsonObject args, String itemKey, String fallbackKey) {
        String itemId = getString(args, itemKey);
        if (itemId != null && !itemId.isBlank()) {
            return formatItemTag(itemId);
        }
        String fallback = getString(args, fallbackKey);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return Component.translatable("ui.chatae.tool.query.empty").getString();
    }

    private String formatCraftTarget(JsonObject args) {
        String itemId = getString(args, "itemId");
        long count = getLong(args, "count", 1);
        String prefix = count > 1 ? count + "x " : "";
        if (itemId == null || itemId.isBlank()) {
            return prefix + Component.translatable("ui.chatae.tool.query.empty").getString();
        }
        return prefix + formatItemTag(itemId);
    }

    private String formatArg(JsonObject args, String key) {
        String value = getString(args, key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return Component.translatable("ui.chatae.tool.query.empty").getString();
    }

    private String formatQuery(JsonObject args) {
        String query = getString(args, "query");
        if (query == null || query.isBlank()) {
            return Component.translatable("ui.chatae.tool.query.empty").getString();
        }
        return query;
    }

    private String formatQuerySuffix(JsonObject args) {
        String query = getString(args, "query");
        if (query == null || query.isBlank()) {
            return "";
        }
        return " (" + query + ")";
    }

    private JsonObject parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(raw);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> formatToolLines(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if ("null".equals(text)) {
            return List.of("No result.");
        }
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return List.of(raw);
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                return List.of(raw);
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("results") && obj.get("results").isJsonArray()) {
                JsonArray results = obj.getAsJsonArray("results");
                if (isRecipeResults(results)) {
                    return formatRecipeSearch(results, getString(obj, "nextPageToken"));
                }
                if (isAe2ListResults(results)) {
                    return formatAe2List(results, getString(obj, "nextPageToken"), getString(obj, "error"));
                }
            }
            if (obj.has("recipeId") || obj.has("outputItemId")) {
                return formatRecipeSummary(obj);
            }
            if (obj.has("jobId")) {
                return formatJobStatus(obj);
            }
            if (obj.has("status") || obj.has("error")) {
                List<String> lines = new ArrayList<>();
                String status = getString(obj, "status");
                if (status != null && !status.isBlank()) {
                    lines.add("Status: " + status);
                }
                String error = getString(obj, "error");
                if (error != null && !error.isBlank()) {
                    lines.add("Error: " + error);
                }
                if (!lines.isEmpty()) {
                    return lines;
                }
            }
        } catch (Exception ignored) {
            return List.of(raw);
        }
        return List.of(raw);
    }

    private List<String> formatRecipeSearch(JsonArray results, String nextPageToken) {
        List<String> lines = new ArrayList<>();
        int total = results.size();
        if (total == 0) {
            lines.add("No recipes found.");
        } else {
            lines.add("Recipes (" + total + "):");
            int shown = Math.min(total, TOOL_RESULT_MAX);
            for (int i = 0; i < shown; i++) {
                JsonObject obj = safeObject(results.get(i));
                if (obj == null) {
                    continue;
                }
                String itemId = getString(obj, "outputItemId");
                int count = getInt(obj, "outputCount", 1);
                String type = getString(obj, "recipeType");
                String label = (count > 1 ? count + "x " : "") + formatItemTag(itemId);
                if (type != null && !type.isBlank()) {
                    label += " [" + type + "]";
                }
                lines.add("• " + label);
            }
            if (total > shown) {
                lines.add("… +" + (total - shown) + " more");
            }
        }
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            lines.add("Next page: " + nextPageToken);
        }
        return lines;
    }

    private List<String> formatRecipeSummary(JsonObject obj) {
        List<String> lines = new ArrayList<>();
        String outputItem = getString(obj, "outputItemId");
        int count = getInt(obj, "outputCount", 1);
        String type = getString(obj, "recipeType");
        String recipeId = getString(obj, "recipeId");
        String header = "Recipe: " + (count > 1 ? count + "x " : "") + formatItemTag(outputItem);
        lines.add(header);
        if (type != null && !type.isBlank()) {
            lines.add("Type: " + type);
        }
        if (recipeId != null && !recipeId.isBlank()) {
            lines.add("Id: " + recipeId);
        }
        JsonArray ingredients = obj.getAsJsonArray("ingredientItemIds");
        if (ingredients != null) {
            lines.add("Ingredients: " + formatItemList(ingredients, 6));
        }
        return lines;
    }

    private List<String> formatAe2List(JsonArray results, String nextPageToken, String error) {
        List<String> lines = new ArrayList<>();
        int total = results.size();
        if (total == 0) {
            lines.add("No items found.");
        } else {
            lines.add("Items (" + total + "):");
            int shown = Math.min(total, TOOL_RESULT_MAX);
            for (int i = 0; i < shown; i++) {
                JsonObject obj = safeObject(results.get(i));
                if (obj == null) {
                    continue;
                }
                String itemId = getString(obj, "itemId");
                long amount = getLong(obj, "amount", 0);
                boolean craftable = getBoolean(obj, "craftable");
                String label = formatItemTag(itemId) + " — " + amount;
                if (craftable) {
                    label += " (craftable)";
                }
                lines.add("• " + label);
            }
            if (total > shown) {
                lines.add("… +" + (total - shown) + " more");
            }
        }
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            lines.add("Next page: " + nextPageToken);
        }
        if (error != null && !error.isBlank()) {
            lines.add("Error: " + error);
        }
        return lines;
    }

    private List<String> formatJobStatus(JsonObject obj) {
        List<String> lines = new ArrayList<>();
        String jobId = getString(obj, "jobId");
        String status = getString(obj, "status");
        String header = "Job " + (jobId == null ? "" : jobId);
        if (status != null && !status.isBlank()) {
            header += " — " + status;
        }
        lines.add(header.trim());

        JsonArray missing = obj.getAsJsonArray("missingItems");
        if (missing != null && missing.size() > 0) {
            lines.add("Missing:");
            int shown = Math.min(missing.size(), TOOL_RESULT_MAX);
            for (int i = 0; i < shown; i++) {
                JsonObject item = safeObject(missing.get(i));
                if (item == null) {
                    continue;
                }
                String itemId = getString(item, "itemId");
                long amount = getLong(item, "amount", 0);
                lines.add("• " + amount + "x " + formatItemTag(itemId));
            }
            if (missing.size() > shown) {
                lines.add("… +" + (missing.size() - shown) + " more");
            }
        }

        String error = getString(obj, "error");
        if (error != null && !error.isBlank()) {
            lines.add("Error: " + error);
        }
        return lines;
    }

    private boolean isRecipeResults(JsonArray results) {
        JsonObject first = firstObject(results);
        return first != null && (first.has("recipeId") || first.has("outputItemId"));
    }

    private boolean isAe2ListResults(JsonArray results) {
        JsonObject first = firstObject(results);
        return first != null && first.has("itemId") && first.has("amount");
    }

    private JsonObject firstObject(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return safeObject(array.get(0));
    }

    private JsonObject safeObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private String formatItemTag(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "unknown";
        }
        ItemToken token = buildItemToken(itemId, null);
        if (token == null) {
            return itemId;
        }
        String name = token.displayName().replace("\"", "'");
        return "<item id=\"" + token.itemId() + "\" display_name=\"" + name + "\">";
    }

    private String formatItemList(JsonArray items, int limit) {
        if (items == null || items.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        int shown = Math.min(items.size(), Math.max(1, limit));
        for (int i = 0; i < shown; i++) {
            JsonElement el = items.get(i);
            if (el != null && el.isJsonPrimitive()) {
                parts.add(formatItemTag(el.getAsString()));
            }
        }
        if (items.size() > shown) {
            parts.add("+" + (items.size() - shown) + " more");
        }
        return String.join(", ", parts);
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return false;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private ToolPayload parseToolPayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(raw);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("tool") && !obj.has("args") && !obj.has("output") && !obj.has("error")) {
                return null;
            }
            String tool = getString(obj, "tool");
            String args = stringifyElement(obj.get("args"));
            String output = stringifyElement(obj.get("output"));
            String error = getString(obj, "error");
            return new ToolPayload(tool, args, output, error);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringifyElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (Exception ignored) {
                return element.toString();
            }
        }
        return element.toString();
    }

    private void renderToolTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.hoveredToolPayload == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        String tool = this.hoveredToolPayload.tool();
        if (tool != null && !tool.isBlank()) {
            lines.add(Component.literal("Tool: " + tool));
        }
        lines.add(Component.literal("Input:"));
        String args = this.hoveredToolPayload.argsJson();
        lines.add(Component.literal(args == null || args.isBlank() ? "(none)" : args));
        lines.add(Component.literal("Output:"));
        String output = this.hoveredToolPayload.outputJson();
        lines.add(Component.literal(output == null || output.isBlank() ? "(none)" : output));
        String error = this.hoveredToolPayload.error();
        if (error != null && !error.isBlank()) {
            lines.add(Component.literal("Error: " + error));
        }
        renderScaledTooltip(guiGraphics, lines, mouseX, mouseY);
    }

    private void renderScaledTooltip(GuiGraphics guiGraphics, List<Component> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int padding = 4;
        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }
        int lineHeight = this.font.lineHeight;
        int boxW = maxWidth + padding * 2;
        int boxH = lineHeight * lines.size() + padding * 2;

        float scale = TOOLTIP_FONT_SCALE;
        int x = mouseX + 8;
        int y = mouseY + 8;

        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        int maxX = Math.round(this.width / scale);
        int maxY = Math.round(this.height / scale);
        if (scaledX + boxW > maxX) {
            scaledX = Math.max(2, maxX - boxW - 2);
        }
        if (scaledY + boxH > maxY) {
            scaledY = Math.max(2, maxY - boxH - 2);
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.fill(scaledX, scaledY, scaledX + boxW, scaledY + boxH, 0xF0100010);
        guiGraphics.renderOutline(scaledX, scaledY, boxW, boxH, COLOR_BORDER);
        int textX = scaledX + padding;
        int textY = scaledY + padding;
        for (Component line : lines) {
            guiGraphics.drawString(this.font, line, textX, textY, COLOR_TEXT_MAIN, false);
            textY += lineHeight;
        }
        guiGraphics.pose().popPose();
    }

    private ItemToken buildItemToken(String itemId, String displayName) {
        if (itemId == null) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        String name = displayName == null || displayName.isBlank()
                ? new ItemStack(item).getHoverName().getString()
                : displayName;
        return new ItemToken(item, name, itemId, 0xF0D27C, 0);
    }

    private List<ChatLine> wrapSpans(List<ChatSpan> spans, int maxWidth, ChatRole role) {
        List<ChatLine> lines = new ArrayList<>();
        List<ChatSpan> current = new ArrayList<>();
        int width = 0;

        for (ChatSpan span : spans) {
            if (span.token() == null) {
                for (ChatSpan piece : splitTextSpan(span.text())) {
                    width = appendSpan(lines, current, width, piece, maxWidth, role);
                }
            } else {
                width = appendSpan(lines, current, width, span, maxWidth, role);
            }
        }
        if (!current.isEmpty()) {
            lines.add(new ChatLine(new ArrayList<>(current), role, false, width, plainTextFromSpans(current)));
        }
        return lines;
    }

    private int appendSpan(List<ChatLine> lines, List<ChatSpan> current, int currentWidth, ChatSpan span, int maxWidth, ChatRole role) {
        if (span.text().isBlank() && current.isEmpty()) {
            return currentWidth;
        }
        if (currentWidth + span.width() > maxWidth && !current.isEmpty()) {
            lines.add(new ChatLine(new ArrayList<>(current), role, false, currentWidth, plainTextFromSpans(current)));
            current.clear();
            currentWidth = 0;
        }
        if (span.text().isBlank() && current.isEmpty()) {
            return currentWidth;
        }
        current.add(span);
        return currentWidth + span.width();
    }

    private List<ChatSpan> splitTextSpan(String text) {
        List<ChatSpan> pieces = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int nextSpace = text.indexOf(' ', idx);
            if (nextSpace < 0) {
                String part = text.substring(idx);
                pieces.add(new ChatSpan(part, null, scaledWidth(part)));
                break;
            }
            String part = text.substring(idx, nextSpace + 1);
            pieces.add(new ChatSpan(part, null, scaledWidth(part)));
            idx = nextSpace + 1;
        }
        return pieces;
    }

    private String plainTextFromSpans(List<ChatSpan> spans) {
        StringBuilder builder = new StringBuilder();
        for (ChatSpan span : spans) {
            if (span.token() != null) {
                builder.append(span.token().displayName());
            } else {
                builder.append(span.text());
            }
        }
        return builder.toString();
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
        int atIndex = findSuggestionAnchor(value, cursor);
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
        if (this.suggestionQuery.isBlank()) {
            clearSuggestions();
            return;
        }
        rebuildSuggestions();
    }

    private int findSuggestionAnchor(String value, int cursor) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        int scan = Math.min(cursor, value.length()) - 1;
        List<TokenRange> ranges = findTokenRanges(value);
        while (scan >= 0) {
            if (value.charAt(scan) == '@' && !isWithinTokenRange(ranges, scan)) {
                return scan;
            }
            scan--;
        }
        return -1;
    }

    private List<TokenRange> findTokenRanges(String value) {
        List<TokenRange> ranges = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return ranges;
        }
        for (ItemToken token : this.inputTokens) {
            String label = "@" + token.displayName();
            int start = 0;
            while (start >= 0) {
                int idx = value.indexOf(label, start);
                if (idx < 0) {
                    break;
                }
                ranges.add(new TokenRange(idx, idx + label.length(), token));
                start = idx + label.length();
            }
        }
        return ranges;
    }

    private boolean isWithinTokenRange(List<TokenRange> ranges, int index) {
        for (TokenRange range : ranges) {
            if (index >= range.start() && index < range.end()) {
                return true;
            }
        }
        return false;
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

    private boolean handleTokenBackspace() {
        if (this.inputBox == null) {
            return false;
        }
        String value = this.inputBox.getValue();
        int cursor = this.inputBox.getCursorPosition();
        if (cursor <= 0 || value.isBlank()) {
            return false;
        }
        List<TokenRange> ranges = findTokenRanges(value);
        int index = cursor - 1;
        TokenRange target = null;
        for (TokenRange range : ranges) {
            if (index >= range.start() && index < range.end()) {
                target = range;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        String before = value.substring(0, target.start());
        String after = value.substring(target.end());
        this.inputBox.setValue(before + after);
        this.inputBox.setCursorPosition(target.start());
        String tokenName = target.token().displayName();
        this.inputTokens.removeIf(token -> token.displayName().equals(tokenName));
        clearSuggestions();
        return true;
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
        if (output.length() <= MAX_CHAT_MESSAGE_LENGTH) {
            return output;
        }
        if (sanitized.length() <= MAX_CHAT_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_CHAT_MESSAGE_LENGTH);
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
        int textWidth = scaledWidth(token.displayName());
        int width = 2 + 10 + 2 + textWidth;
        return new TokenMetrics(width);
    }

    private static String proposalHeaderLabel(Proposal proposal) {
        String toolName = proposal.toolCall() == null ? "" : proposal.toolCall().toolName();
        String risk = proposal.riskLevel().name().replace("_", " ");
        if (toolName.contains("craft")) {
            return "CRAFTING REQUEST [" + risk + "]";
        }
        return "REQUEST [" + risk + "]";
    }

    private static String formatProposalId(String id) {
        if (id == null || id.isBlank()) {
            return "#REQ-????";
        }
        String trimmed = id.length() > 8 ? id.substring(0, 8) : id;
        return "#REQ-" + trimmed.toUpperCase();
    }

    private static List<String> buildProposalDetailLines(Proposal proposal, space.controlnet.chatae.core.session.TerminalBinding binding) {
        List<String> lines = new ArrayList<>();
        String summary = proposal.summary();
        if (summary != null && !summary.isBlank()) {
            lines.add(summary);
        }
        ProposalDetails details = proposal.details();
        if (details != null) {
            if (!details.missingItems().isEmpty()) {
                lines.add("Missing: " + String.join(", ", details.missingItems()));
            } else if (details.note() != null && !details.note().isBlank()) {
                lines.add(details.note());
            }
        }
        if (binding != null) {
            String side = binding.side().orElse("BLOCK");
            String bound = "Context: " + binding.dimensionId() + " [" + binding.x() + ", " + binding.y() + ", " + binding.z() + "] " + side;
            lines.add(bound);
        }
        if (lines.isEmpty()) {
            lines.add("Awaiting approval.");
        }
        if (lines.size() > 3) {
            return lines.subList(0, 3);
        }
        return lines;
    }

    private void appendMessage(ChatMessage message) {
        this.messages.add(message);
        if (this.messages.size() > MAX_MESSAGES) {
            this.messages.subList(0, this.messages.size() - MAX_MESSAGES).clear();
        }
        rebuildWrappedLines();
    }

    private void rebuildWrappedLines() {
        this.wrappedLines.clear();
        int maxWidth = Math.max(1, this.chatW - MESSAGE_MAX_WIDTH_PAD);
        for (ChatMessage message : this.messages) {
            List<ChatLine> lines = buildMessageLines(message, maxWidth);
            this.wrappedLines.addAll(lines);
            String timestamp = formatMessageTimestamp(message.timestampMillis());
            if (!timestamp.isBlank()) {
                ChatSpan span = new ChatSpan(timestamp, null, scaledWidth(timestamp));
                this.wrappedLines.add(new ChatLine(List.of(span), message.role(), true, span.width(), timestamp));
            }
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
            case IDLE -> "Online";
            case INDEXING -> "Indexing";
            case THINKING -> "Thinking";
            case WAIT_APPROVAL -> "Awaiting";
            case EXECUTING -> "Working";
            case DONE -> "Online";
            case FAILED -> "Failed";
            case CANCELED -> "Canceled";
        };
    }

    private static int statusDotColor(SessionState state) {
        return switch (state) {
            case IDLE -> COLOR_STATUS_ONLINE;
            case INDEXING -> COLOR_STATUS_INDEXING;
            case THINKING -> COLOR_STATUS_THINKING;
            case WAIT_APPROVAL -> COLOR_STATUS_BUSY;
            case EXECUTING -> COLOR_STATUS_EXECUTING;
            case DONE -> COLOR_STATUS_ONLINE;
            case FAILED -> COLOR_STATUS_ERROR;
            case CANCELED -> COLOR_TEXT_DIM;
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

    private int scaledWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.round(this.font.width(text) * FONT_SCALE);
    }

    private int scaledLineHeight() {
        return Math.max(1, Math.round(this.font.lineHeight * FONT_SCALE));
    }

    private void drawScaledString(GuiGraphics guiGraphics, String text, int x, int y, int color, boolean isTitle) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float scale = isTitle ? TITLE_FONT_SCALE : FONT_SCALE;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        guiGraphics.drawString(this.font, text, scaledX, scaledY, color, false);
        guiGraphics.pose().popPose();
    }

    private enum UiButtonStyle {
        PRIMARY,
        ACCENT,
        DANGER,
        GHOST
    }

    private static final class FlatButton extends Button {
        private final UiButtonStyle style;

        private FlatButton(int x, int y, int width, int height, Component message, OnPress onPress, UiButtonStyle style) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            boolean hovered = this.isHoveredOrFocused();
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

            int bg;
            int outline;
            int text;
            switch (this.style) {
                case PRIMARY -> {
                    bg = hovered ? 0xFFFFCC33 : COLOR_STATUS_BUSY;
                    outline = COLOR_STATUS_BUSY;
                    text = 0xFF000000;
                }
                case ACCENT -> {
                    bg = hovered ? COLOR_ACCENT_CYAN : 0x00151518;
                    outline = COLOR_ACCENT_CYAN;
                    text = hovered ? 0xFF000000 : COLOR_ACCENT_CYAN;
                }
                case DANGER -> {
                    bg = hovered ? 0x33FF3333 : 0x00151518;
                    outline = COLOR_STATUS_ERROR;
                    text = COLOR_STATUS_ERROR;
                }
                case GHOST -> {
                    bg = hovered ? 0x22151518 : 0x00151518;
                    outline = hovered ? COLOR_ACCENT_CYAN : COLOR_BORDER;
                    text = hovered ? COLOR_ACCENT_CYAN : COLOR_TEXT_MAIN;
                }
                default -> {
                    bg = COLOR_BG_PANEL;
                    outline = COLOR_BORDER;
                    text = COLOR_TEXT_MAIN;
                }
            }

            if (!this.active) {
                bg = 0x22101010;
                outline = COLOR_BORDER;
                text = COLOR_TEXT_DIM;
            }

            guiGraphics.fill(x, y, x + width, y + height, bg);
            guiGraphics.renderOutline(x, y, width, height, outline);

            if (minecraft != null) {
                int textWidth = Math.round(minecraft.font.width(this.getMessage()) * FONT_SCALE);
                int textX = x + (width - textWidth) / 2;
                int textY = y + (height - Math.round(minecraft.font.lineHeight * FONT_SCALE)) / 2;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0f);
                int scaledX = Math.round(textX / FONT_SCALE);
                int scaledY = Math.round(textY / FONT_SCALE);
                guiGraphics.drawString(minecraft.font, this.getMessage(), scaledX, scaledY, text, false);
                guiGraphics.pose().popPose();
            }
        }
    }

    private record ChatSpan(String text, ItemToken token, int width) {
    }

    private record ChatLine(List<ChatSpan> spans, ChatRole role, boolean isMeta, int width, String plainText) {
    }

    private record TokenMetrics(int width) {
    }

    private record TokenRange(int start, int end, ItemToken token) {
    }

    private record SessionRowHit(SessionSummary summary, boolean onBadge) {
    }

    private record ItemSuggestion(Item item, String displayName, String itemId, int score, int color) {
    }

    private record ItemToken(Item item, String displayName, String itemId, int color, int index) {
    }

    private record ToolPayload(String tool, String argsJson, String outputJson, String error) {
    }

    private static final class SessionRow {
        private SessionSummary summary;
        private Button deleteButton;
        private Button visibilityButton;
    }
}
