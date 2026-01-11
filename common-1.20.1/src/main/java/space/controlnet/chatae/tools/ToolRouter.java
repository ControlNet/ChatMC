package space.controlnet.chatae.tools;

import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.blockentity.AiTerminalBlockEntity;
import space.controlnet.chatae.core.policy.PolicyDecision;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolResult;
import space.controlnet.chatae.menu.AiTerminalMenu;
import space.controlnet.chatae.recipes.RecipeSearchFilters;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ToolRouter {
    private static final Gson GSON = new Gson();

    private ToolRouter() {
    }

    public static ToolOutcome execute(ServerPlayer player, ToolCall call, boolean approved) {
        RiskLevel risk = riskFor(call.toolName());
        if (!approved) {
            PolicyDecision decision = policyFor(risk);
            if (decision == PolicyDecision.REQUIRE_APPROVAL) {
                Proposal proposal = new Proposal(UUID.randomUUID().toString(), risk, summarize(call), call, System.currentTimeMillis());
                return ToolOutcome.proposal(proposal);
            }
            if (decision == PolicyDecision.DENY) {
                return ToolOutcome.result(ToolResult.error("denied", "Policy denied tool"));
            }
        }

        try {
            ToolResult result = switch (call.toolName()) {
                case "recipes.search" -> handleRecipeSearch(call);
                case "recipes.get" -> handleRecipeGet(call);
                case "ae2.list_items" -> handleAe2ListItems(player, call);
                case "ae2.list_craftables" -> handleAe2ListCraftables(player, call);
                case "ae2.simulate_craft" -> handleAe2Simulate(player, call);
                case "ae2.request_craft" -> handleAe2Request(player, call);
                case "ae2.job_status" -> handleAe2JobStatus(player, call);
                case "ae2.job_cancel" -> handleAe2JobCancel(player, call);
                default -> ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName());
            };
            return ToolOutcome.result(result);
        } catch (Exception e) {
            ChatAE.LOGGER.error("Tool execution failed", e);
            return ToolOutcome.result(ToolResult.error("exception", e.getMessage()));
        }
    }

    private static RiskLevel riskFor(String name) {
        return switch (name) {
            case "ae2.request_craft", "ae2.job_cancel" -> RiskLevel.SAFE_MUTATION;
            default -> RiskLevel.READ_ONLY;
        };
    }

    private static PolicyDecision policyFor(RiskLevel risk) {
        return switch (risk) {
            case READ_ONLY -> PolicyDecision.AUTO_APPROVE;
            case SAFE_MUTATION, DANGEROUS_MUTATION -> PolicyDecision.REQUIRE_APPROVAL;
        };
    }

    private static String summarize(ToolCall call) {
        return call.toolName() + " " + call.argsJson();
    }

    private static ToolResult handleRecipeSearch(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), RecipeSearchArgs.class);
        var result = ChatAE.RECIPE_INDEX.search(args.query(), RecipeSearchFilters.empty(), Optional.ofNullable(args.pageToken()), args.limit());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleRecipeGet(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), RecipeGetArgs.class);
        var result = ChatAE.RECIPE_INDEX.get(args.recipeId());
        return ToolResult.ok(GSON.toJson(result.orElse(null)));
    }

    private static ToolResult handleAe2ListItems(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2ListArgs.class);
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().listItems(args.query(), args.craftableOnly(), args.limit(), Optional.ofNullable(args.pageToken()));
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2ListCraftables(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2ListArgs.class);
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().listCraftables(args.query(), args.limit(), Optional.ofNullable(args.pageToken()));
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Simulate(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2CraftArgs.class);
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().simulateCraft(player, args.itemId(), args.count());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Request(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2CraftArgs.class);
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().requestCraft(player, args.itemId(), args.count(), Optional.ofNullable(args.cpuName()));
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobStatus(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2JobArgs.class);
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().jobStatus(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobCancel(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2JobArgs.class);
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().cancelJob(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static Optional<AiTerminalBlockEntity> resolveOpenTerminal(ServerPlayer player) {
        if (!(player.containerMenu instanceof AiTerminalMenu menu)) {
            return Optional.empty();
        }
        var pos = menu.getPos();
        var be = player.level().getBlockEntity(pos);
        if (be instanceof AiTerminalBlockEntity terminal) {
            return Optional.of(terminal);
        }
        return Optional.empty();
    }

    public record ToolOutcome(ToolResult result, Proposal proposal) {
        public static ToolOutcome result(ToolResult result) {
            return new ToolOutcome(result, null);
        }

        public static ToolOutcome proposal(Proposal proposal) {
            return new ToolOutcome(null, proposal);
        }

        public boolean hasProposal() {
            return proposal != null;
        }
    }

    public record RecipeSearchArgs(String query, String pageToken, int limit) {
    }

    public record RecipeGetArgs(String recipeId) {
    }

    public record Ae2ListArgs(String query, boolean craftableOnly, int limit, String pageToken) {
    }

    public record Ae2CraftArgs(String itemId, long count, String cpuName) {
    }

    public record Ae2JobArgs(String jobId) {
    }
}
