package space.controlnet.chatae.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import space.controlnet.chatae.ChatAENetwork;
import space.controlnet.chatae.core.client.ClientSessionStore;
import space.controlnet.chatae.core.proposal.ApprovalDecision;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalDetails;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.ChatRole;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.menu.AiTerminalMenu;

import java.util.ArrayList;
import java.util.List;

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

    private final List<Component> messages = new ArrayList<>();
    private final List<FormattedCharSequence> wrappedLines = new ArrayList<>();

    private EditBox inputBox;
    private Button sendButton;
    private Button approveButton;
    private Button denyButton;

    private String statusText = "Idle";

    private int chatX;
    private int chatY;
    private int chatW;
    private int chatH;
    private int proposalX;
    private int proposalY;
    private int proposalW;
    private int proposalH;

    private Proposal pendingProposal;

    private int scrollOffsetLines;
    private long lastSnapshotVersion = -1;

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
        this.addRenderableWidget(this.inputBox);

        this.sendButton = Button.builder(Component.literal("Send"), b -> submitInput()).bounds(
                inputX + inputW + 6, inputY, SEND_BUTTON_WIDTH, INPUT_HEIGHT).build();
        this.addRenderableWidget(this.sendButton);

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

        this.setInitialFocus(this.inputBox);
        rebuildWrappedLines();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, PADDING, PADDING, 0x404040, false);

        Component status = Component.literal("Status: ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(this.statusText).withStyle(ChatFormatting.GRAY));

        int statusY = this.imageHeight - PADDING - INPUT_HEIGHT - this.font.lineHeight - 4;
        guiGraphics.drawString(this.font, status, PADDING, statusY, 0x404040, false);
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
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromSessionStore();
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderChatLog(guiGraphics);
        renderProposalCard(guiGraphics);
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

    private void renderProposalCard(GuiGraphics guiGraphics) {
        if (this.pendingProposal == null) {
            return;
        }

        String summary = this.pendingProposal.summary();
        String risk = this.pendingProposal.riskLevel().name();
        String label = summary == null || summary.isBlank()
                ? "Proposal [" + risk + "] pending"
                : "Proposal [" + risk + "]: " + summary;
        String detail = buildProposalDetailLine(this.pendingProposal);

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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isWithinChat(mouseX, mouseY) && !this.wrappedLines.isEmpty()) {
            int direction = delta > 0 ? -1 : 1;
            this.scrollOffsetLines = this.scrollOffsetLines + direction;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.inputBox != null && this.inputBox.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                submitInput();
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
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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

        ChatAENetwork.sendChatToServer(message);

        this.inputBox.setValue("");
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
        updateProposalUi();

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
        MutableComponent prefix = switch (message.role()) {
            case USER -> Component.literal("You: ").withStyle(ChatFormatting.YELLOW);
            case ASSISTANT -> Component.literal("AI: ").withStyle(ChatFormatting.AQUA);
            case TOOL -> Component.literal("Tool: ").withStyle(ChatFormatting.GRAY);
            case SYSTEM -> Component.literal("System: ").withStyle(ChatFormatting.DARK_GRAY);
        };

        return prefix.append(Component.literal(message.text()).withStyle(ChatFormatting.WHITE));
    }

    private static String buildProposalDetailLine(Proposal proposal) {
        ProposalDetails details = proposal.details();
        if (details == null) {
            return "";
        }
        if (!details.missingItems().isEmpty()) {
            return "Missing: " + String.join(", ", details.missingItems());
        }
        if (!details.note().isBlank()) {
            return details.note();
        }
        return "";
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
