package space.controlnet.chatmc.matrix.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ChatMCMatrix {
    public static final String MOD_ID = "chatmcmatrix";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private ChatMCMatrix() {
    }

    public static void init() {
        LOGGER.info("ChatMCMatrix initialized");
    }
}
