package space.controlnet.chatae.tools;

import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.blockentity.AiTerminalBlockEntity;
import space.controlnet.chatae.core.policy.PolicyDecision;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.proposal.ProposalDetails;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.SessionState;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolResult;
import space.controlnet.chatae.menu.AiTerminalMenu;
import space.controlnet.chatae.recipes.RecipeSearchFilters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ToolRouter {
    private static final Gson GSON = new Gson();
    private static final long MAX_CRAFT_COUNT = 10000L;

    private ToolRouter() {
    }

    public static ToolOutcome execute(ServerPlayer player, ToolCall call, boolean approved) {
        RiskLevel risk = classifyRisk(call.toolName());
        if (!approved) {
            PolicyDecision decision = policyFor(risk);
            if (decision == PolicyDecision.REQUIRE_APPROVAL) {
                Proposal proposal = buildProposal(risk, call);
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

    public static RiskLevel classifyRisk(String name) {
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

    private static Proposal buildProposal(RiskLevel risk, ToolCall call) {
        ProposalDetails details = ProposalDetails.empty();
        String summary = call.toolName();

        if ("ae2.request_craft".equals(call.toolName())) {
            var args = GSON.fromJson(call.argsJson(), Ae2CraftArgs.class);
            String itemId = args == null ? "" : args.itemId();
            long count = args == null ? 0 : args.count();
            summary = "Craft " + count + " " + itemId;
            String note = args != null && args.cpuName() != null && !args.cpuName().isBlank()
                    ? "CPU hint: " + args.cpuName() + ". Feasibility: unknown (run ae2.simulate_craft). Cancel after submit with ae2.job_cancel <jobId>."
                    : "Feasibility: unknown (run ae2.simulate_craft). Cancel after submit with ae2.job_cancel <jobId>.";
            details = new ProposalDetails("Craft", itemId, count, List.of(), note);
        } else if ("ae2.job_cancel".equals(call.toolName())) {
            var args = GSON.fromJson(call.argsJson(), Ae2JobArgs.class);
            String jobId = args == null ? "" : args.jobId();
            summary = "Cancel job " + jobId;
            details = new ProposalDetails("Cancel job", jobId, 0L, List.of(), "Stops an active crafting job.");
        }

        return new Proposal(UUID.randomUUID().toString(), risk, summary, call, System.currentTimeMillis(), details);
    }

    private static ToolResult handleRecipeSearch(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), RecipeSearchArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ChatAE.RECIPE_INDEX.isReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        RecipeSearchFilters filters = new RecipeSearchFilters(
                normalize(args.modId()),
                normalize(args.recipeType()),
                normalize(args.outputItemId()),
                normalize(args.ingredientItemId()),
                normalize(args.tagId())
        );
        var result = ChatAE.RECIPE_INDEX.search(args.query(), filters, Optional.ofNullable(args.pageToken()), args.limit());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleRecipeGet(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), RecipeGetArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!ChatAE.RECIPE_INDEX.isReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        var result = ChatAE.RECIPE_INDEX.get(args.recipeId());
        return ToolResult.ok(GSON.toJson(result.orElse(null)));
    }

    private static ToolResult handleAe2ListItems(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2ListArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().listItems(args.query(), args.craftableOnly(), args.limit(), Optional.ofNullable(args.pageToken()));
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2ListCraftables(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2ListArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().listCraftables(args.query(), args.limit(), Optional.ofNullable(args.pageToken()));
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Simulate(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2CraftArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + MAX_CRAFT_COUNT);
        }
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().simulateCraft(player, args.itemId(), args.count());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2Request(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2CraftArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        if (!isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + MAX_CRAFT_COUNT);
        }
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().requestCraft(player, args.itemId(), args.count(), Optional.ofNullable(args.cpuName()));
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobStatus(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2JobArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
        Optional<AiTerminalBlockEntity> terminal = resolveOpenTerminal(player);
        if (terminal.isEmpty()) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.get().jobStatus(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAe2JobCancel(ServerPlayer player, ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), Ae2JobArgs.class);
        if (args == null) {
            return ToolResult.error("invalid_args", "Missing arguments");
        }
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

    private static Optional<String> normalize(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static boolean isValidCraftCount(long count) {
        return count > 0 && count <= MAX_CRAFT_COUNT;
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

    public record RecipeSearchArgs(
            String query,
            String pageToken,
            int limit,
            String modId,
            String recipeType,
            String outputItemId,
            String ingredientItemId,
            String tagId
    ) {
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
