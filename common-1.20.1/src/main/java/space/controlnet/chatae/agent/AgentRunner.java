package space.controlnet.chatae.agent;

import net.minecraft.server.level.ServerPlayer;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;
import space.controlnet.chatae.tools.ToolRouter;

import java.util.Map;
import java.util.Optional;

public final class AgentRunner {
    private static final String KEY_PLAYER = "player";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_OUTCOME = "outcome";

    private final CompiledGraph<SimpleState> graph;

    public AgentRunner() {
        try {
            StateGraph<SimpleState> graphDef = new StateGraph<>(SimpleState::new);
            graphDef.addNode("route", AsyncNodeAction.node_async((NodeAction<SimpleState>) AgentRunner::route));
            graphDef.addEdge(GraphDefinition.START, "route");
            graphDef.addEdge("route", GraphDefinition.END);

            this.graph = graphDef.compile(CompileConfig.builder().recursionLimit(5).build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build agent graph", e);
        }
    }

    public ToolOutcome run(ServerPlayer player, ToolCall call) {
        Map<String, Object> init = Map.of(
                KEY_PLAYER, player,
                KEY_MESSAGE, call,
                KEY_OUTCOME, AgentState.MARK_FOR_REMOVAL
        );

        Optional<SimpleState> state = graph.invoke(init);
        if (state.isEmpty()) {
            return ToolOutcome.result(space.controlnet.chatae.core.tools.ToolResult.error("empty_state", "Agent state is empty"));
        }

        return state.get().value(KEY_OUTCOME).map(ToolOutcome.class::cast)
                .orElseGet(() -> ToolOutcome.result(space.controlnet.chatae.core.tools.ToolResult.error("no_outcome", "No outcome from agent")));
    }

    private static Map<String, Object> route(SimpleState state) {
        ServerPlayer player = state.value(KEY_PLAYER).map(ServerPlayer.class::cast).orElse(null);
        ToolCall call = state.value(KEY_MESSAGE).map(ToolCall.class::cast).orElse(null);
        if (player == null || call == null) {
            return Map.of(KEY_OUTCOME, ToolOutcome.result(space.controlnet.chatae.core.tools.ToolResult.error("invalid_input", "Player or call is null")));
        }

        ToolOutcome outcome = ToolRouter.execute(player, call, false);
        if (outcome.hasProposal()) {
            ChatAE.LOGGER.debug("Agent produced proposal {}", outcome.proposal().id());
        }

        return Map.of(KEY_OUTCOME, outcome);
    }

    private static final class SimpleState extends AgentState {
        public SimpleState(Map<String, Object> data) {
            super(data);
        }
    }
}
