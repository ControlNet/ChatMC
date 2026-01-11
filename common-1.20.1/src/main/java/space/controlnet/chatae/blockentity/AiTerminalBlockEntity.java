package space.controlnet.chatae.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.chatae.ChatAERegistries;
import space.controlnet.chatae.menu.AiTerminalMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AiTerminalBlockEntity extends BlockEntity implements ExtendedMenuProvider, IInWorldGridNodeHost, IActionHost, ICraftingRequester {
    private static final String NBT_NODE = "gridNode";

    private final IManagedGridNode mainNode;
    private boolean nodeInitScheduled;
    private final Map<String, CraftJob> jobs = new ConcurrentHashMap<>();

    public AiTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ChatAERegistries.AI_TERMINAL_BE.get(), pos, state);

        this.mainNode = GridHelper.createManagedNode(this, new NodeListener());
        this.mainNode
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setTagName("ai_terminal")
                .setVisualRepresentation(ChatAERegistries.AI_TERMINAL_ITEM.get());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chatae.ai_terminal");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AiTerminalMenu(containerId, inventory, worldPosition);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.worldPosition);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide() && !nodeInitScheduled) {
            nodeInitScheduled = true;
            GridHelper.onFirstTick(this, be -> be.mainNode.create(level, be.worldPosition));
        }
    }

    @Override
    public void setRemoved() {
        this.mainNode.destroy();
        super.setRemoved();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(NBT_NODE)) {
            this.mainNode.loadFromNBT(tag.getCompound(NBT_NODE));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag nodeTag = new CompoundTag();
        this.mainNode.saveToNBT(nodeTag);
        tag.put(NBT_NODE, nodeTag);
    }

    @Override
    public appeng.api.networking.IGridNode getGridNode(Direction direction) {
        return this.mainNode.getNode();
    }

    public record Ae2Entry(String itemId, long amount, boolean craftable) {
    }

    public record Ae2ListResult(List<Ae2Entry> results, Optional<String> nextPageToken, Optional<String> error) {
        public Ae2ListResult {
            results = List.copyOf(results);
            nextPageToken = nextPageToken == null ? Optional.empty() : nextPageToken;
            error = error == null ? Optional.empty() : error;
        }
    }

    public record Ae2PlanItem(String itemId, long amount) {
    }

    public record Ae2CraftSimulation(String jobId, String status, List<Ae2PlanItem> missingItems, Optional<String> error) {
        public Ae2CraftSimulation {
            missingItems = List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }
    }

    public record Ae2CraftRequest(String jobId, String status, Optional<String> error) {
        public Ae2CraftRequest {
            error = error == null ? Optional.empty() : error;
        }
    }

    public record Ae2JobStatus(String jobId, String status, Optional<String> error) {
        public Ae2JobStatus {
            error = error == null ? Optional.empty() : error;
        }
    }

    public Ae2ListResult listItems(String query, boolean craftableOnly, int limit, Optional<String> pageToken) {
        Optional<IGrid> gridOpt = Optional.ofNullable(mainNode.getGrid());
        if (gridOpt.isEmpty()) {
            return new Ae2ListResult(List.of(), Optional.empty(), Optional.of("AE2 network not connected"));
        }

        IGrid grid = gridOpt.get();
        KeyCounter inv = grid.getStorageService().getCachedInventory();

        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int offset = pageToken.flatMap(AiTerminalBlockEntity::parseOffset).orElse(0);

        var craftables = craftableOnly ? grid.getCraftingService().getCraftables(AEItemKey.filter()) : null;

        List<AEItemKey> keys = inv.keySet().stream()
                .filter(AEItemKey::is)
                .map(k -> (AEItemKey) k)
                .filter(k -> q.isEmpty() || k.getId().toString().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(k -> k.getId().toString()))
                .collect(Collectors.toList());

        List<Ae2Entry> out = new ArrayList<>(Math.min(safeLimit, keys.size()));
        for (int i = offset; i < keys.size() && out.size() < safeLimit; i++) {
            AEItemKey key = keys.get(i);
            long amount = inv.get(key);
            boolean craftable = craftables != null && craftables.contains(key);
            if (craftableOnly && !craftable) {
                continue;
            }
            out.add(new Ae2Entry(key.getId().toString(), amount, craftable));
        }

        Optional<String> next = (offset + safeLimit) < keys.size() ? Optional.of(Integer.toString(offset + safeLimit)) : Optional.empty();
        return new Ae2ListResult(out, next, Optional.empty());
    }

    public Ae2ListResult listCraftables(String query, int limit, Optional<String> pageToken) {
        Optional<IGrid> gridOpt = Optional.ofNullable(mainNode.getGrid());
        if (gridOpt.isEmpty()) {
            return new Ae2ListResult(List.of(), Optional.empty(), Optional.of("AE2 network not connected"));
        }

        IGrid grid = gridOpt.get();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int offset = pageToken.flatMap(AiTerminalBlockEntity::parseOffset).orElse(0);

        List<AEItemKey> keys = grid.getCraftingService().getCraftables(AEItemKey.filter()).stream()
                .filter(AEItemKey::is)
                .map(k -> (AEItemKey) k)
                .filter(k -> q.isEmpty() || k.getId().toString().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(k -> k.getId().toString()))
                .collect(Collectors.toList());

        List<Ae2Entry> out = new ArrayList<>(Math.min(safeLimit, keys.size()));
        for (int i = offset; i < keys.size() && out.size() < safeLimit; i++) {
            AEItemKey key = keys.get(i);
            out.add(new Ae2Entry(key.getId().toString(), 0, true));
        }

        Optional<String> next = (offset + safeLimit) < keys.size() ? Optional.of(Integer.toString(offset + safeLimit)) : Optional.empty();
        return new Ae2ListResult(out, next, Optional.empty());
    }

    public Ae2CraftSimulation simulateCraft(Player player, String itemId, long count) {
        Optional<IGrid> gridOpt = Optional.ofNullable(mainNode.getGrid());
        if (gridOpt.isEmpty()) {
            return new Ae2CraftSimulation("", "error", List.of(), Optional.of("AE2 network not connected"));
        }

        AEItemKey key = resolveKey(itemId);
        if (key == null) {
            return new Ae2CraftSimulation("", "error", List.of(), Optional.of("Unknown item: " + itemId));
        }

        String jobId = UUID.randomUUID().toString();
        CraftJob job = new CraftJob(jobId, CraftJobState.CALCULATING, null, Optional.empty());
        jobs.put(jobId, job);

        ICraftingSimulationRequester requester = new SimulationRequester(player, this);
        CompletableFuture<ICraftingPlan> future = toCompletable(gridOpt.get().getCraftingService().beginCraftingCalculation(level, requester, key, count, CalculationStrategy.REPORT_MISSING_ITEMS));
        future.whenComplete((plan, error) -> {
            if (error != null) {
                jobs.put(jobId, job.withError("Crafting simulation failed: " + error.getMessage()));
                return;
            }
            if (plan == null) {
                jobs.put(jobId, job.withError("Crafting simulation failed"));
                return;
            }
            List<Ae2PlanItem> missing = toPlanItems(plan.missingItems());
            jobs.put(jobId, job.withPlan(plan, missing));
        });

        return new Ae2CraftSimulation(jobId, job.state().name().toLowerCase(Locale.ROOT), List.of(), Optional.empty());
    }

    public Ae2CraftRequest requestCraft(Player player, String itemId, long count, Optional<String> cpuName) {
        Optional<IGrid> gridOpt = Optional.ofNullable(mainNode.getGrid());
        if (gridOpt.isEmpty()) {
            return new Ae2CraftRequest("", "error", Optional.of("AE2 network not connected"));
        }

        AEItemKey key = resolveKey(itemId);
        if (key == null) {
            return new Ae2CraftRequest("", "error", Optional.of("Unknown item: " + itemId));
        }

        String jobId = UUID.randomUUID().toString();
        CraftJob job = new CraftJob(jobId, CraftJobState.CALCULATING, null, Optional.empty());
        jobs.put(jobId, job);

        ICraftingSimulationRequester requester = new SimulationRequester(player, this);
        CompletableFuture<ICraftingPlan> future = toCompletable(gridOpt.get().getCraftingService().beginCraftingCalculation(level, requester, key, count, CalculationStrategy.REPORT_MISSING_ITEMS));
        future.whenComplete((plan, error) -> {
            if (error != null) {
                jobs.put(jobId, job.withError("Crafting calculation failed: " + error.getMessage()));
                return;
            }
            if (plan == null) {
                jobs.put(jobId, job.withError("Crafting calculation failed"));
                return;
            }

            List<Ae2PlanItem> missing = toPlanItems(plan.missingItems());
            if (plan.simulation()) {
                jobs.put(jobId, job.withError("Missing items: " + missing.stream().map(Ae2PlanItem::itemId).collect(Collectors.joining(", "))));
                return;
            }

            Level levelRef = this.level;
            if (levelRef == null || levelRef.isClientSide()) {
                jobs.put(jobId, job.withError("No server level"));
                return;
            }

            levelRef.getServer().execute(() -> {
                IGrid grid = gridOpt.get();
                ICraftingCPU target = selectCpu(grid.getCraftingService().getCpus(), cpuName);
                IActionSource actionSource = IActionSource.ofPlayer(player, this);
                ICraftingSubmitResult submit = grid.getCraftingService().submitJob(plan, this, target, false, actionSource);
                if (!submit.successful()) {
                    jobs.put(jobId, job.withError("Crafting submit failed: " + submit.errorCode()));
                    return;
                }

                ICraftingLink link = submit.link();
                if (link == null) {
                    jobs.put(jobId, job.withError("Crafting link unavailable"));
                    return;
                }

                jobs.put(jobId, job.withLink(link));
            });
        });

        return new Ae2CraftRequest(jobId, job.state().name().toLowerCase(Locale.ROOT), Optional.empty());
    }

    public Ae2JobStatus jobStatus(String jobId) {
        CraftJob job = jobs.get(jobId);
        if (job == null) {
            return new Ae2JobStatus(jobId, "unknown", Optional.of("Job not found"));
        }

        job = refreshJob(job);
        jobs.put(jobId, job);

        Optional<String> error = job.error();
        return new Ae2JobStatus(jobId, job.state().name().toLowerCase(Locale.ROOT), error);
    }

    public Ae2JobStatus cancelJob(String jobId) {
        CraftJob job = jobs.get(jobId);
        if (job == null) {
            return new Ae2JobStatus(jobId, "unknown", Optional.of("Job not found"));
        }

        if (job.link() != null) {
            job.link().cancel();
        }

        job = job.withState(CraftJobState.CANCELED);
        jobs.put(jobId, job);
        return new Ae2JobStatus(jobId, job.state().name().toLowerCase(Locale.ROOT), job.error());
    }

    private static Optional<Integer> parseOffset(String token) {
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private AEItemKey resolveKey(String itemId) {
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
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static ICraftingCPU selectCpu(Set<ICraftingCPU> cpus, Optional<String> cpuName) {
        if (cpuName.isEmpty()) {
            return null;
        }
        String target = cpuName.get().toLowerCase(Locale.ROOT);
        for (ICraftingCPU cpu : cpus) {
            var name = cpu.getName();
            if (name != null && name.getString().toLowerCase(Locale.ROOT).contains(target)) {
                return cpu;
            }
        }
        return null;
    }

    private static List<Ae2PlanItem> toPlanItems(KeyCounter counter) {
        if (counter == null || counter.isEmpty()) {
            return List.of();
        }
        List<Ae2PlanItem> out = new ArrayList<>();
        for (var entry : counter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key instanceof AEItemKey itemKey) {
                out.add(new Ae2PlanItem(itemKey.getId().toString(), amount));
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

    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    @Override
    public com.google.common.collect.ImmutableSet<ICraftingLink> getRequestedJobs() {
        return com.google.common.collect.ImmutableSet.copyOf(jobs.values().stream()
                .map(CraftJob::link)
                .filter(link -> link != null && !link.isCanceled() && !link.isDone())
                .toList());
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return 0;
        }
        return grid.getStorageService().getInventory().insert(what, amount, mode, IActionSource.ofMachine(this));
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        jobs.values().stream()
                .filter(job -> link.equals(job.link()))
                .findFirst()
                .ifPresent(job -> jobs.put(job.jobId(), job.withState(link.isDone() ? CraftJobState.DONE : CraftJobState.CANCELED)));
    }

    private record SimulationRequester(Player player, IActionHost host) implements ICraftingSimulationRequester {
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

    private record CraftJob(String jobId, CraftJobState state, ICraftingLink link, Optional<String> error) {
        CraftJob {
            error = error == null ? Optional.empty() : error;
        }

        CraftJob withPlan(ICraftingPlan plan, List<Ae2PlanItem> missing) {
            return new CraftJob(jobId, plan.simulation() ? CraftJobState.FAILED : CraftJobState.SUBMITTED, link, plan.simulation()
                    ? Optional.of("Missing items: " + missing.stream().map(Ae2PlanItem::itemId).collect(Collectors.joining(", ")))
                    : Optional.empty());
        }

        CraftJob withLink(ICraftingLink link) {
            return new CraftJob(jobId, CraftJobState.SUBMITTED, link, error);
        }

        CraftJob withError(String message) {
            return new CraftJob(jobId, CraftJobState.FAILED, link, Optional.ofNullable(message));
        }

        CraftJob withState(CraftJobState state) {
            return new CraftJob(jobId, state, link, error);
        }
    }

    private final class NodeListener implements appeng.api.networking.IGridNodeListener<AiTerminalBlockEntity> {
        @Override
        public void onSaveChanges(AiTerminalBlockEntity host, appeng.api.networking.IGridNode node) {
            host.setChanged();
        }

        @Override
        public void onGridChanged(AiTerminalBlockEntity host, appeng.api.networking.IGridNode node) {
            host.setChanged();
        }

        @Override
        public void onStateChanged(AiTerminalBlockEntity host, appeng.api.networking.IGridNode node, appeng.api.networking.IGridNodeListener.State state) {
            host.setChanged();
        }
    }
}
