package space.controlnet.mineagent.common.client.screen.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.*;

public final class FlatButton extends Button {
    private final UiButtonStyle style;

    public FlatButton(int x, int y, int width, int height, Component message, OnPress onPress, UiButtonStyle style) {
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
        Minecraft minecraft = Minecraft.getInstance();

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
            guiGraphics.drawString(minecraft.font, this.getMessage(), scaledX, scaledY, text, true);
            guiGraphics.pose().popPose();
        }
    }
}
