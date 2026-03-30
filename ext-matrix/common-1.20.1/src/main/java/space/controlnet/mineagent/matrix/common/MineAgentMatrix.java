package space.controlnet.mineagent.matrix.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MineAgentMatrix {
    public static final String MOD_ID = "mineagentmatrix";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private MineAgentMatrix() {
    }

    public static void init() {
        LOGGER.info("MineAgentMatrix initialized");
    }
}
