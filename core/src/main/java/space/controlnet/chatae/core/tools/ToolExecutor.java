package space.controlnet.chatae.core.tools;

import space.controlnet.chatae.core.policy.PolicyDecision;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalFactory;
import space.controlnet.chatae.core.recipes.RecipeSearchFilters;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.core.util.JsonSupport;

import java.util.Optional;

/**
 * Executes tool calls using a provided execution context.
 * This class contains the pure tool dispatch logic without MC dependencies.
 */
public final class ToolExecutor {
    private static final com.google.gson.Gson GSON = JsonSupport.GSON;

    private ToolExecutor() {
    }

    /**
     * Executes a tool call.
     *
     * @param ctx      the execution context providing terminal, recipes, and logging
     * @param call     the tool call to execute
     * @param approved whether the call has been pre-approved (bypasses policy check)
     * @return the outcome of the tool execution
     */
    public static ToolOutcome execute(ToolExecutionContext ctx, ToolCall call, boolean approved) {
        RiskLevel risk = ToolPolicy.classifyRisk(call.toolName());
        if (!approved) {
            PolicyDecision decision = ToolPolicy.policyFor(risk);
            if (decision == PolicyDecision.REQUIRE_APPROVAL) {
                Proposal proposal = ProposalFactory.build(risk, call);
                return ToolOutcome.proposal(proposal);
            }
            if (decision == PolicyDecision.DENY) {
                return ToolOutcome.result(ToolResult.error("denied", "Policy denied tool"));
            }
        }

        try {
            ToolResult result = switch (call.toolName()) {
                case "recipes.search" -> handleRecipeSearch(ctx, call);
                case "recipes.get" -> handleRecipeGet(ctx, call);
                case "ae2.list_items" -> handleAe2ListItems(ctx, call);
                case "ae2.list_craftables" -> handleAe2ListCraftables(ctx, call);
                case "ae2.simulate_craft" -> handleAe2Simulate(ctx, call);
                case "ae2.request_craft" -> handleAe2Request(ctx, call);
                case "ae2.job_status" -> handleAe2JobStatus(ctx, call);
                case "ae2.job_cancel" -> handleAe2JobCancel(ctx, call);
                default -> ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName());
            };
            return ToolOutcome.result(result);
        } catch (Exception e) {
            ctx.logError("Tool execution failed", e);
            return ToolOutcome.result(ToolResult.error("exception", e.getMessage()));
        }
    }

    private static ToolResult handleRecipeSearch(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.RecipeSearchArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ctx.isRecipeIndexReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        RecipeSearchFilters filters = new RecipeSearchFilters(
                ToolPolicy.normalize(args.modId()),
                ToolPolicy.normalize(args.recipeType()),
                ToolPolicy.normalize(args.outputItemId()),
                ToolPolicy.normalize(args.ingredientItemId()),
                ToolPolicy.normalize(args.tagId())
        );
        var result = ctx.searchRecipes(args.query(), filters, Optional.ofNullable(args.pageToken()), args.limit());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleRecipeGet(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.RecipeGetArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ctx.isRecipeIndexReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        var result = ctx.getRecipe(args.recipeId());
        return ToolResult.ok(GSON.toJson(result.orElse(null)));
    }

    private static ToolResult handleAe2ListItems(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2ListArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext terminal = ctx.getTerminal().orElse(null);
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.listItems(args.query(), args.craftableOnly(), args.limit(), args.pageToken());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2ListCraftables(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2ListArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext terminal = ctx.getTerminal().orElse(null);
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.listCraftables(args.query(), args.limit(), args.pageToken());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Simulate(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2CraftArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ToolPolicy.isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + ToolPolicy.getMaxCraftCount());
        }
        TerminalContext terminal = ctx.getTerminal().orElse(null);
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.simulateCraft(args.itemId(), args.count());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Request(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2CraftArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ToolPolicy.isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + ToolPolicy.getMaxCraftCount());
        }
        TerminalContext terminal = ctx.getTerminal().orElse(null);
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.requestCraft(args.itemId(), args.count(), args.cpuName());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobStatus(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2JobArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext terminal = ctx.getTerminal().orElse(null);
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.jobStatus(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobCancel(ToolExecutionContext ctx, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2JobArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        TerminalContext terminal = ctx.getTerminal().orElse(null);
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.cancelJob(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }
}
