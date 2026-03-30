package space.controlnet.mineagent.ae.common.part;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import space.controlnet.mineagent.ae.core.terminal.AiTerminalData;
import space.controlnet.mineagent.ae.common.terminal.AeTerminalHost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class AiTerminalPartOperations {
    private final Map<String, CraftJob> jobs = new ConcurrentHashMap<>();

    public AiTerminalData.AeListResult listItems(IGrid grid, String query, boolean craftableOnly, int limit, @Nullable String pageToken) {
        var inv = grid.getStorageService().getCachedInventory();

        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int offset = parseOffsetNullable(pageToken).orElse(0);

        var craftables = craftableOnly ? grid.getCraftingService().getCraftables(AEItemKey.filter()) : null;

        List<AEItemKey> keys = inv.keySet().stream()
                .filter(AEItemKey::is)
                .map(k -> (AEItemKey) k)
                .filter(k -> q.isEmpty() || k.getId().toString().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(k -> k.getId().toString()))
                .toList();

        List<AiTerminalData.AeEntry> out = new ArrayList<>(Math.min(safeLimit, keys.size()));
        for (int i = offset; i < keys.size() && out.size() < safeLimit; i++) {
            AEItemKey key = keys.get(i);
            long amount = inv.get(key);
            boolean craftable = craftables != null && craftables.contains(key);
            if (craftableOnly && !craftable) {
                continue;
            }
            out.add(new AiTerminalData.AeEntry(key.getId().toString(), amount, craftable));
        }

        Optional<String> next = (offset + safeLimit) < keys.size() ? Optional.of(Integer.toString(offset + safeLimit)) : Optional.empty();
        return new AiTerminalData.AeListResult(out, next, Optional.empty());
    }

    public AiTerminalData.AeListResult listCraftables(IGrid grid, String query, int limit, @Nullable String pageToken) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int offset = parseOffsetNullable(pageToken).orElse(0);

        List<AEItemKey> keys = grid.getCraftingService().getCraftables(AEItemKey.filter()).stream()
                .filter(AEItemKey::is)
                .map(k -> (AEItemKey) k)
                .filter(k -> q.isEmpty() || k.getId().toString().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(k -> k.getId().toString()))
                .toList();

        List<AiTerminalData.AeEntry> out = new ArrayList<>(Math.min(safeLimit, keys.size()));
        for (int i = offset; i < keys.size() && out.size() < safeLimit; i++) {
            AEItemKey key = keys.get(i);
            out.add(new AiTerminalData.AeEntry(key.getId().toString(), 0, true));
        }

        Optional<String> next = (offset + safeLimit) < keys.size() ? Optional.of(Integer.toString(offset + safeLimit)) : Optional.empty();
        return new AiTerminalData.AeListResult(out, next, Optional.empty());
    }

    public AiTerminalData.AeCraftSimulation simulateCraft(Player player, IGrid grid, @Nullable Level level, AeTerminalHost host, String itemId, long count) {
        AEItemKey key = resolveKey(itemId);
        if (key == null) {
            return new AiTerminalData.AeCraftSimulation("", "error", List.of(), Optional.of("Unknown item: " + itemId));
        }

        String jobId = UUID.randomUUID().toString();
        CraftJob job = new CraftJob(jobId, CraftJobState.CALCULATING, null, List.of(), Optional.empty());
        jobs.put(jobId, job);

        ICraftingSimulationRequester requester = new SimulationRequester(player, host);
        CompletableFuture<ICraftingPlan> future = toCompletable(grid.getCraftingService().beginCraftingCalculation(level, requester, key, count, CalculationStrategy.REPORT_MISSING_ITEMS));
        future.whenComplete((plan, error) -> {
            if (error != null) {
                jobs.put(jobId, job.withError("Crafting simulation failed: " + error.getMessage()));
                return;
            }
            if (plan == null) {
                jobs.put(jobId, job.withError("Crafting simulation failed"));
                return;
            }
            List<AiTerminalData.AePlanItem> missing = toPlanItems(plan.missingItems());
            jobs.put(jobId, job.withPlan(plan, missing));
        });

        return new AiTerminalData.AeCraftSimulation(jobId, job.state().name().toLowerCase(Locale.ROOT), job.missingItems(), Optional.empty());
    }

    public AiTerminalData.AeCraftRequest requestCraft(Player player, IGrid grid, @Nullable Level level, AeTerminalHost host, String itemId, long count, @Nullable String cpuName) {
        AEItemKey key = resolveKey(itemId);
        if (key == null) {
            return new AiTerminalData.AeCraftRequest("", "error", Optional.of("Unknown item: " + itemId));
        }

        String jobId = UUID.randomUUID().toString();
        CraftJob job = new CraftJob(jobId, CraftJobState.CALCULATING, null, List.of(), Optional.empty());
        jobs.put(jobId, job);

        ICraftingSimulationRequester requester = new SimulationRequester(player, host);
        CompletableFuture<ICraftingPlan> future = toCompletable(grid.getCraftingService().beginCraftingCalculation(level, requester, key, count, CalculationStrategy.REPORT_MISSING_ITEMS));
        future.whenComplete((plan, error) -> {
            if (error != null) {
                jobs.put(jobId, job.withError("Crafting calculation failed: " + error.getMessage()));
                return;
            }
            if (plan == null) {
                jobs.put(jobId, job.withError("Crafting calculation failed"));
                return;
            }

            List<AiTerminalData.AePlanItem> missing = toPlanItems(plan.missingItems());
            if (plan.simulation()) {
                jobs.put(jobId, job.withPlan(plan, missing));
                return;
            }

            if (level == null || level.isClientSide()) {
                jobs.put(jobId, job.withError("No server level"));
                return;
            }

            jobs.put(jobId, job.withMissingItems(missing));

            level.getServer().execute(() -> {
                CraftJob current = jobs.getOrDefault(jobId, job);
                ICraftingCPU target = selectCpu(grid.getCraftingService().getCpus(), cpuName);
                if (cpuName != null && !cpuName.isBlank() && target == null) {
                    jobs.put(jobId, current.withError("CPU unavailable"));
                    return;
                }
                IActionSource actionSource = IActionSource.ofPlayer(player, host);
                ICraftingSubmitResult submit = grid.getCraftingService().submitJob(plan, host, target, false, actionSource);
                if (!submit.successful()) {
                    jobs.put(jobId, current.withError("Crafting submit failed: " + submit.errorCode()));
                    return;
                }

                ICraftingLink link = submit.link();
                if (link == null) {
                    jobs.put(jobId, current.withError("Crafting link unavailable"));
                    return;
                }

                jobs.put(jobId, current.withLink(link));
            });
        });

        return new AiTerminalData.AeCraftRequest(jobId, job.state().name().toLowerCase(Locale.ROOT), Optional.empty());
    }

    public AiTerminalData.AeJobStatus jobStatus(String jobId) {
        CraftJob job = jobs.get(jobId);
        if (job == null) {
            return new AiTerminalData.AeJobStatus(jobId, "unknown", List.of(), Optional.of("Job not found"));
        }

        job = refreshJob(job);
        jobs.put(jobId, job);

        Optional<String> error = job.error();
        return new AiTerminalData.AeJobStatus(jobId, job.state().name().toLowerCase(Locale.ROOT), job.missingItems(), error);
    }

    public AiTerminalData.AeJobStatus cancelJob(String jobId) {
        CraftJob job = jobs.get(jobId);
        if (job == null) {
            return new AiTerminalData.AeJobStatus(jobId, "unknown", List.of(), Optional.of("Job not found"));
        }

        if (job.link() != null) {
            job.link().cancel();
        }

        job = job.withState(CraftJobState.CANCELED);
        jobs.put(jobId, job);
        return new AiTerminalData.AeJobStatus(jobId, job.state().name().toLowerCase(Locale.ROOT), job.missingItems(), job.error());
    }

    public com.google.common.collect.ImmutableSet<ICraftingLink> getRequestedJobs() {
        return com.google.common.collect.ImmutableSet.copyOf(jobs.values().stream()
                .map(CraftJob::link)
                .filter(link -> link != null && !link.isCanceled() && !link.isDone())
                .toList());
    }

    public long insertCraftedItems(IGrid grid, ICraftingLink link, AEKey what, long amount, Actionable mode, AeTerminalHost host) {
        return grid.getStorageService().getInventory().insert(what, amount, mode, IActionSource.ofMachine(host));
    }

    public void jobStateChange(ICraftingLink link) {
        jobs.values().stream()
                .filter(job -> link.equals(job.link()))
                .findFirst()
                .ifPresent(job -> jobs.put(job.jobId(), job.withState(link.isDone() ? CraftJobState.DONE : CraftJobState.CANCELED)));
    }

    public void clearJobs() {
        jobs.clear();
    }

    private static Optional<Integer> parseOffset(String token) {
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseOffsetNullable(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return parseOffset(token);
    }

    private static AEItemKey resolveKey(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item != null ? AEItemKey.of(item) : null;
    }

    private static CompletableFuture<ICraftingPlan> toCompletable(java.util.concurrent.Future<ICraftingPlan> future) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return future.get(30000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("Crafting calculation timed out", e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static ICraftingCPU selectCpu(Set<ICraftingCPU> cpus, @Nullable String cpuName) {
        if (cpuName == null || cpuName.isBlank()) {
            return null;
        }
        String target = cpuName.toLowerCase(Locale.ROOT);
        for (ICraftingCPU cpu : cpus) {
            var name = cpu.getName();
            if (name != null && name.getString().toLowerCase(Locale.ROOT).contains(target)) {
                return cpu;
            }
        }
        return null;
    }

    private static List<AiTerminalData.AePlanItem> toPlanItems(KeyCounter counter) {
        if (counter == null || counter.isEmpty()) {
            return List.of();
        }
        List<AiTerminalData.AePlanItem> out = new ArrayList<>();
        for (var entry : counter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key instanceof AEItemKey itemKey) {
                out.add(new AiTerminalData.AePlanItem(itemKey.getId().toString(), amount));
            }
        }
        return out;
    }

    private CraftJob refreshJob(CraftJob job) {
        if (job.state() == CraftJobState.CALCULATING) {
            return job;
        }
        ICraftingLink link = job.link();
        if (link == null) {
            return job;
        }
        if (link.isCanceled()) {
            return job.withState(CraftJobState.CANCELED);
        }
        if (link.isDone()) {
            return job.withState(CraftJobState.DONE);
        }
        return job;
    }

    private record SimulationRequester(Player player, AeTerminalHost host) implements ICraftingSimulationRequester {
        @Override
        public IActionSource getActionSource() {
            return IActionSource.ofPlayer(player, host);
        }
    }

    private enum CraftJobState {
        CALCULATING,
        SUBMITTED,
        DONE,
        CANCELED,
        FAILED
    }

    private record CraftJob(String jobId, CraftJobState state, ICraftingLink link, List<AiTerminalData.AePlanItem> missingItems, Optional<String> error) {
        CraftJob {
            missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }

        CraftJob withPlan(ICraftingPlan plan, List<AiTerminalData.AePlanItem> missing) {
            return new CraftJob(jobId, plan.simulation() ? CraftJobState.FAILED : CraftJobState.SUBMITTED, link, missing, plan.simulation()
                    ? Optional.of("Missing items: " + missing.stream().map(AiTerminalData.AePlanItem::itemId).collect(Collectors.joining(", ")))
                    : Optional.empty());
        }

        CraftJob withLink(ICraftingLink link) {
            return new CraftJob(jobId, CraftJobState.SUBMITTED, link, missingItems, error);
        }

        CraftJob withMissingItems(List<AiTerminalData.AePlanItem> missing) {
            return new CraftJob(jobId, state, link, missing, error);
        }

        CraftJob withError(String message) {
            return new CraftJob(jobId, CraftJobState.FAILED, link, missingItems, Optional.ofNullable(message));
        }

        CraftJob withState(CraftJobState state) {
            return new CraftJob(jobId, state, link, missingItems, error);
        }
    }
}
