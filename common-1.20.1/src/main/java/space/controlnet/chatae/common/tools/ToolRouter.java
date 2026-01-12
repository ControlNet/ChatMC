package space.controlnet.chatae.common.tools;

import com.google.gson.Gson;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.core.policy.PolicyDecision;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalFactory;
import space.controlnet.chatae.core.recipes.RecipeSearchFilters;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.core.tools.ToolArgs;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;
import space.controlnet.chatae.core.tools.ToolPolicy;
import space.controlnet.chatae.core.tools.ToolResult;

import java.util.Optional;

public final class ToolRouter {
    private static final Gson GSON = new Gson();

    private ToolRouter() {
    }

    public static space.controlnet.chatae.core.tools.ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        RiskLevel risk = ToolPolicy.classifyRisk(call.toolName());
        if (!approved) {
            PolicyDecision decision = ToolPolicy.policyFor(risk);
            if (decision == PolicyDecision.REQUIRE_APPROVAL) {
                Proposal proposal = ProposalFactory.build(risk, call);
                return space.controlnet.chatae.core.tools.ToolOutcome.proposal(proposal);
            }
            if (decision == PolicyDecision.DENY) {
                return space.controlnet.chatae.core.tools.ToolOutcome.result(ToolResult.error("denied", "Policy denied tool"));
            }
        }

        try {
            ToolResult result = switch (call.toolName()) {
                case "recipes.search" -> handleRecipeSearch(call);
                case "recipes.get" -> handleRecipeGet(call);
                case "ae2.list_items" -> handleAe2ListItems(terminal, call);
                case "ae2.list_craftables" -> handleAe2ListCraftables(terminal, call);
                case "ae2.simulate_craft" -> handleAe2Simulate(terminal, call);
                case "ae2.request_craft" -> handleAe2Request(terminal, call);
                case "ae2.job_status" -> handleAe2JobStatus(terminal, call);
                case "ae2.job_cancel" -> handleAe2JobCancel(terminal, call);
                default -> ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName());
            };
            return space.controlnet.chatae.core.tools.ToolOutcome.result(result);
        } catch (Exception e) {
            ChatAE.LOGGER.error("Tool execution failed", e);
            return space.controlnet.chatae.core.tools.ToolOutcome.result(ToolResult.error("exception", e.getMessage()));
        }
    }


    private static ToolResult handleRecipeSearch(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.RecipeSearchArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ChatAE.RECIPE_INDEX.isReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        RecipeSearchFilters filters = new RecipeSearchFilters(
                ToolPolicy.normalize(args.modId()),
                ToolPolicy.normalize(args.recipeType()),
                ToolPolicy.normalize(args.outputItemId()),
                ToolPolicy.normalize(args.ingredientItemId()),
                ToolPolicy.normalize(args.tagId())
        );
        var result = ChatAE.RECIPE_INDEX.search(args.query(), filters, Optional.ofNullable(args.pageToken()), args.limit());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleRecipeGet(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.RecipeGetArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ChatAE.RECIPE_INDEX.isReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        var result = ChatAE.RECIPE_INDEX.get(args.recipeId());
        return ToolResult.ok(GSON.toJson(result.orElse(null)));
    }

    private static ToolResult handleAe2ListItems(Optional<TerminalContext> terminal, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2ListArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext context = terminal.orElse(null);
        if (context == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = context.listItems(args.query(), args.craftableOnly(), args.limit(), args.pageToken());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2ListCraftables(Optional<TerminalContext> terminal, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2ListArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext context = terminal.orElse(null);
        if (context == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = context.listCraftables(args.query(), args.limit(), args.pageToken());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Simulate(Optional<TerminalContext> terminal, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2CraftArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ToolPolicy.isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + ToolPolicy.getMaxCraftCount());
        }
        TerminalContext context = terminal.orElse(null);
        if (context == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = context.simulateCraft(args.itemId(), args.count());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Request(Optional<TerminalContext> terminal, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2CraftArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ToolPolicy.isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + ToolPolicy.getMaxCraftCount());
        }
        TerminalContext context = terminal.orElse(null);
        if (context == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = context.requestCraft(args.itemId(), args.count(), args.cpuName());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobStatus(Optional<TerminalContext> terminal, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2JobArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext context = terminal.orElse(null);
        if (context == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = context.jobStatus(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobCancel(Optional<TerminalContext> terminal, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2JobArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext context = terminal.orElse(null);
        if (context == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = context.cancelJob(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }


}
