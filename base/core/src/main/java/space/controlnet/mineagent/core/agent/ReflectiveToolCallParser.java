package space.controlnet.mineagent.core.agent;

import space.controlnet.mineagent.core.tools.ToolCall;

import java.lang.reflect.Method;
import java.util.Optional;

public final class ReflectiveToolCallParser {
    private final Logger logger;
    private ToolCallParser parser;
    private boolean initAttempted;

    public ReflectiveToolCallParser(Logger logger) {
        this.logger = logger;
    }

    public boolean isAvailable() {
        ensureParser();
        return parser != null;
    }

    public Optional<ToolCall> parse(String prompt) {
        ToolCallParser instance = ensureParser();
        if (instance == null) {
            return Optional.empty();
        }
        return instance.parse(prompt);
    }

    private ToolCallParser ensureParser() {
        if (parser != null || initAttempted) {
            return parser;
        }
        initAttempted = true;
        try {
            Class<?> clazz = Class.forName("space.controlnet.mineagent.core.agent.LangChainToolCallParser");
            Method createMethod = clazz.getMethod("create", Logger.class);
            Object result = createMethod.invoke(null, logger);
            if (result instanceof Optional<?> optional) {
                Object value = optional.orElse(null);
                if (value instanceof ToolCallParser parsed) {
                    parser = parsed;
                }
            }
        } catch (Throwable t) {
            logger.warn("LLM runtime unavailable, continuing without LLM support", t);
            parser = null;
        }
        return parser;
    }
}
