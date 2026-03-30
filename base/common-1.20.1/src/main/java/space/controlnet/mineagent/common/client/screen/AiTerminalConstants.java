package space.controlnet.mineagent.common.client.screen;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * UI constants for the AI Terminal screen.
 * Contains dimensions, colors, and formatting constants.
 */
public final class AiTerminalConstants {
    private AiTerminalConstants() {}

    // ===== Layout Dimensions =====
    public static final int PADDING = 10;
    public static final int HEADER_HEIGHT = 28;
    public static final int INPUT_HEIGHT = 20;
    public static final int INPUT_PAD_Y = 7;
    public static final int SIDEBAR_WIDTH = 150;
    public static final int SIDEBAR_HEADER_HEIGHT = 22;
    public static final int SIDEBAR_FOOTER_HEIGHT = 22;
    public static final int SEND_BUTTON_WIDTH = 46;
    public static final int MAX_MESSAGES = 400;

    // ===== Proposal Card =====
    public static final int PROPOSAL_CARD_HEIGHT = 56;
    public static final int PROPOSAL_CARD_GAP = 6;
    public static final int PROPOSAL_BUTTON_WIDTH = 62;
    public static final int PROPOSAL_BUTTON_HEIGHT = 18;
    public static final int PROPOSAL_BUTTON_GAP = 4;

    // ===== Header Buttons =====
    public static final int LOCALE_BUTTON_WIDTH = 24;
    public static final int LOCALE_BUTTON_HEIGHT = 18;
    public static final int SESSION_TOGGLE_WIDTH = 24;
    public static final int SESSION_TOGGLE_HEIGHT = 18;

    // ===== Session Panel =====
    public static final int SESSION_PANEL_PADDING = 8;
    public static final int SESSION_ROW_HEIGHT = 28;
    public static final int STATUS_DOT_SIZE = 6;
    public static final int SESSION_ROW_GAP = 6;
    public static final int SESSION_DELETE_BUTTON_WIDTH = 20;
    public static final int SESSION_VIS_BUTTON_WIDTH = 32;
    public static final int SESSION_BUTTON_HEIGHT = 16;
    public static final int SESSION_BUTTON_GAP = 4;
    public static final int SESSION_NEW_BUTTON_WIDTH = 24;
    public static final int SESSION_NEW_BUTTON_HEIGHT = 18;

    // ===== Token/Item Input =====
    public static final int TOKEN_ICON_SIZE = 12;
    public static final int TOKEN_TEXT_GAP = 4;
    public static final int TOKEN_PADDING_X = 4;
    public static final int TOKEN_PANEL_PADDING = 6;
    public static final int TOKEN_PANEL_WIDTH = 200;
    public static final int TOKEN_PANEL_MAX_ROWS = 6;
    public static final int TOKEN_PANEL_ROW_HEIGHT = 18;

    // ===== Message Rendering =====
    public static final int MESSAGE_PAD_X = 8;
    public static final int MESSAGE_PAD_Y = 4;
    public static final int MESSAGE_MAX_WIDTH_PAD = 36;
    public static final int TOOL_RESULT_MAX = 100;
    public static final int MAX_CHAT_MESSAGE_LENGTH = 65536;

    // ===== Font Scales =====
    public static final float TITLE_FONT_SCALE = 0.9f;
    public static final float FONT_SCALE = 0.5f;
    public static final float TOOLTIP_FONT_SCALE = 0.4f;

    // ===== Background Colors =====
    public static final int COLOR_BG_DARK = 0xFF0B0B0D;
    public static final int COLOR_BG_PANEL = 0xFF151518;
    public static final int COLOR_BG_PANEL_TRANSPARENT = 0xD9151518;
    public static final int COLOR_BORDER = 0xFF3A3A40;

    // ===== Primary/Accent Colors =====
    public static final int COLOR_PRIMARY_FLUIX = 0xFF6B2FB5;
    public static final int COLOR_PRIMARY_FLUIX_DIM = 0x806B2FB5;
    public static final int COLOR_ACCENT_CYAN = 0xFF00FFFF;
    public static final int COLOR_ACCENT_CYAN_DIM = 0x3300FFFF;

    // ===== Text Colors =====
    public static final int COLOR_TEXT_MAIN = 0xFFE0E0E0;
    public static final int COLOR_TEXT_DIM = 0xFF808080;
    public static final int COLOR_TEXT_HIGHLIGHT = 0xFFFFFFFF;

    // ===== Status Indicator Colors =====
    public static final int COLOR_STATUS_ONLINE = 0xFF00FF9D;
    public static final int COLOR_STATUS_INDEXING = 0xFF33CCFF;
    public static final int COLOR_STATUS_THINKING = 0xFFD400FF;
    public static final int COLOR_STATUS_EXECUTING = 0xFFFFCC00;
    public static final int COLOR_STATUS_BUSY = 0xFFFFB700;
    public static final int COLOR_STATUS_ERROR = 0xFFFF3333;

    // ===== Time Formatting =====
    public static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
}
