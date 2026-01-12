package space.controlnet.chatae.part;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import space.controlnet.chatae.ChatAENetwork;
import space.controlnet.chatae.forge.ForgePartRegistries;
import space.controlnet.chatae.menu.AiTerminalMenu;
import space.controlnet.chatae.core.terminal.AiTerminalData;
import space.controlnet.chatae.terminal.AiTerminalHost;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class AiTerminalPart extends AbstractDisplayPart implements AiTerminalHost, ExtendedMenuProvider {
    @PartModels
    public static final ResourceLocation MODEL_OFF = new ResourceLocation("ae2", "part/terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = new ResourceLocation("ae2", "part/terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final Map<String, CraftJob> jobs = new ConcurrentHashMap<>();

    public AiTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
        this.getMainNode()
                .setInWorldNode(true)
                .setTagName("ai_terminal")
                .setVisualRepresentation(ForgePartRegistries.AI_TERMINAL_PART_ITEM.get());
    }

    @Override
    public void setPartHostInfo(Direction side, IPartHost host, BlockEntity blockEntity) {
        super.setPartHostInfo(side, host, blockEntity);
        this.getMainNode().setExposedOnSides(EnumSet.of(side));
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (player.level().isClientSide()) {
            return true;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            MenuRegistry.openExtendedMenu(serverPlayer, this);
            ChatAENetwork.sendSessionSnapshot(serverPlayer);
        }
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.chatae.ai_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AiTerminalMenu(containerId, inventory, this, getHostPos(), getSide());
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getHostPos());
        buf.writeBoolean(true);
        buf.writeEnum(getSide());
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    public AiTerminalData.Ae2ListResult listItems(String query, boolean craftableOnly, int limit, @Nullable String pageToken) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.Ae2ListResult(List.of(), Optional.empty(), Optional.of("AE2 network not connected"));
        }

        IGrid grid = gridOpt.get();
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

        List<AiTerminalData.Ae2Entry> out = new ArrayList<>(Math.min(safeLimit, keys.size()));
        for (int i = offset; i < keys.size() && out.size() < safeLimit; i++) {
            AEItemKey key = keys.get(i);
            long amount = inv.get(key);
            boolean craftable = craftables != null && craftables.contains(key);
            if (craftableOnly && !craftable) {
                continue;
            }
            out.add(new AiTerminalData.Ae2Entry(key.getId().toString(), amount, craftable));
        }

        Optional<String> next = (offset + safeLimit) < keys.size() ? Optional.of(Integer.toString(offset + safeLimit)) : Optional.empty();
        return new AiTerminalData.Ae2ListResult(out, next, Optional.empty());
    }

    @Override
    public AiTerminalData.Ae2ListResult listCraftables(String query, int limit, @Nullable String pageToken) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.Ae2ListResult(List.of(), Optional.empty(), Optional.of("AE2 network not connected"));
        }

        IGrid grid = gridOpt.get();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int offset = parseOffsetNullable(pageToken).orElse(0);

        List<AEItemKey> keys = grid.getCraftingService().getCraftables(AEItemKey.filter()).stream()
                .filter(AEItemKey::is)
                .map(k -> (AEItemKey) k)
                .filter(k -> q.isEmpty() || k.getId().toString().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(k -> k.getId().toString()))
                .toList();

        List<AiTerminalData.Ae2Entry> out = new ArrayList<>(Math.min(safeLimit, keys.size()));
        for (int i = offset; i < keys.size() && out.size() < safeLimit; i++) {
            AEItemKey key = keys.get(i);
            out.add(new AiTerminalData.Ae2Entry(key.getId().toString(), 0, true));
        }

        Optional<String> next = (offset + safeLimit) < keys.size() ? Optional.of(Integer.toString(offset + safeLimit)) : Optional.empty();
        return new AiTerminalData.Ae2ListResult(out, next, Optional.empty());
    }

    @Override
    public AiTerminalData.Ae2CraftSimulation simulateCraft(Player player, String itemId, long count) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.Ae2CraftSimulation("", "error", List.of(), Optional.of("AE2 network not connected"));
        }

        AEItemKey key = resolveKey(itemId);
        if (key == null) {
            return new AiTerminalData.Ae2CraftSimulation("", "error", List.of(), Optional.of("Unknown item: " + itemId));
        }

        String jobId = UUID.randomUUID().toString();
        CraftJob job = new CraftJob(jobId, CraftJobState.CALCULATING, null, List.of(), Optional.empty());
        jobs.put(jobId, job);

        ICraftingSimulationRequester requester = new SimulationRequester(player, this);
        CompletableFuture<ICraftingPlan> future = toCompletable(gridOpt.get().getCraftingService().beginCraftingCalculation(getHostLevel(), requester, key, count, CalculationStrategy.REPORT_MISSING_ITEMS));
        future.whenComplete((plan, error) -> {
            if (error != null) {
                jobs.put(jobId, job.withError("Crafting simulation failed: " + error.getMessage()));
                return;
            }
            if (plan == null) {
                jobs.put(jobId, job.withError("Crafting simulation failed"));
                return;
            }
            List<AiTerminalData.Ae2PlanItem> missing = toPlanItems(plan.missingItems());
            jobs.put(jobId, job.withPlan(plan, missing));
        });

        return new AiTerminalData.Ae2CraftSimulation(jobId, job.state().name().toLowerCase(Locale.ROOT), job.missingItems(), Optional.empty());
    }

    @Override
    public AiTerminalData.Ae2CraftRequest requestCraft(Player player, String itemId, long count, @Nullable String cpuName) {
        Optional<IGrid> gridOpt = Optional.ofNullable(getMainNode().getGrid());
        if (gridOpt.isEmpty()) {
            return new AiTerminalData.Ae2CraftRequest("", "error", Optional.of("AE2 network not connected"));
        }

        AEItemKey key = resolveKey(itemId);
        if (key == null) {
            return new AiTerminalData.Ae2CraftRequest("", "error", Optional.of("Unknown item: " + itemId));
        }

        String jobId = UUID.randomUUID().toString();
        CraftJob job = new CraftJob(jobId, CraftJobState.CALCULATING, null, List.of(), Optional.empty());
        jobs.put(jobId, job);

        ICraftingSimulationRequester requester = new SimulationRequester(player, this);
        CompletableFuture<ICraftingPlan> future = toCompletable(gridOpt.get().getCraftingService().beginCraftingCalculation(getHostLevel(), requester, key, count, CalculationStrategy.REPORT_MISSING_ITEMS));
        future.whenComplete((plan, error) -> {
            if (error != null) {
                jobs.put(jobId, job.withError("Crafting calculation failed: " + error.getMessage()));
                return;
            }
            if (plan == null) {
                jobs.put(jobId, job.withError("Crafting calculation failed"));
                return;
            }

            List<AiTerminalData.Ae2PlanItem> missing = toPlanItems(plan.missingItems());
            if (plan.simulation()) {
                jobs.put(jobId, job.withPlan(plan, missing));
                return;
            }

            Level levelRef = getHostLevel();
            if (levelRef == null || levelRef.isClientSide()) {
                jobs.put(jobId, job.withError("No server level"));
                return;
            }

            jobs.put(jobId, job.withMissingItems(missing));

            levelRef.getServer().execute(() -> {
                CraftJob current = jobs.getOrDefault(jobId, job);
                IGrid grid = gridOpt.get();
                ICraftingCPU target = selectCpu(grid.getCraftingService().getCpus(), cpuName);
                if (cpuName != null && !cpuName.isBlank() && target == null) {
                    jobs.put(jobId, current.withError("CPU unavailable"));
                    return;
                }
                IActionSource actionSource = IActionSource.ofPlayer(player, this);
                ICraftingSubmitResult submit = grid.getCraftingService().submitJob(plan, this, target, false, actionSource);
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

        return new AiTerminalData.Ae2CraftRequest(jobId, job.state().name().toLowerCase(Locale.ROOT), Optional.empty());
    }

    @Override
    public AiTerminalData.Ae2JobStatus jobStatus(String jobId) {
        CraftJob job = jobs.get(jobId);
        if (job == null) {
            return new AiTerminalData.Ae2JobStatus(jobId, "unknown", List.of(), Optional.of("Job not found"));
        }

        job = refreshJob(job);
        jobs.put(jobId, job);

        Optional<String> error = job.error();
        return new AiTerminalData.Ae2JobStatus(jobId, job.state().name().toLowerCase(Locale.ROOT), job.missingItems(), error);
    }

    @Override
    public AiTerminalData.Ae2JobStatus cancelJob(String jobId) {
        CraftJob job = jobs.get(jobId);
        if (job == null) {
            return new AiTerminalData.Ae2JobStatus(jobId, "unknown", List.of(), Optional.of("Job not found"));
        }

        if (job.link() != null) {
            job.link().cancel();
        }

        job = job.withState(CraftJobState.CANCELED);
        jobs.put(jobId, job);
        return new AiTerminalData.Ae2JobStatus(jobId, job.state().name().toLowerCase(Locale.ROOT), job.missingItems(), job.error());
    }

    @Override
    public BlockPos getHostPos() {
        var host = getHost();
        return host != null ? host.getBlockEntity().getBlockPos() : BlockPos.ZERO;
    }

    @Override
    public @Nullable Level getHostLevel() {
        var host = getHost();
        if (host == null) {
            return null;
        }
        return host.getBlockEntity().getLevel();
    }

    @Override
    public boolean isRemovedHost() {
        var host = getHost();
        return host == null || host.getPart(getSide()) != this || !host.isInWorld();
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
        IGrid grid = getMainNode().getGrid();
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

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public void removeFromWorld() {
        this.jobs.clear();
        super.removeFromWorld();
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

    private static List<AiTerminalData.Ae2PlanItem> toPlanItems(KeyCounter counter) {
        if (counter == null || counter.isEmpty()) {
            return List.of();
        }
        List<AiTerminalData.Ae2PlanItem> out = new ArrayList<>();
        for (var entry : counter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key instanceof AEItemKey itemKey) {
                out.add(new AiTerminalData.Ae2PlanItem(itemKey.getId().toString(), amount));
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

    private record SimulationRequester(Player player, AiTerminalHost host) implements ICraftingSimulationRequester {
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

    private record CraftJob(String jobId, CraftJobState state, ICraftingLink link, List<AiTerminalData.Ae2PlanItem> missingItems, Optional<String> error) {
        CraftJob {
            missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }

        CraftJob withPlan(ICraftingPlan plan, List<AiTerminalData.Ae2PlanItem> missing) {
            return new CraftJob(jobId, plan.simulation() ? CraftJobState.FAILED : CraftJobState.SUBMITTED, link, missing, plan.simulation()
                    ? Optional.of("Missing items: " + missing.stream().map(AiTerminalData.Ae2PlanItem::itemId).collect(Collectors.joining(", ")))
                    : Optional.empty());
        }

        CraftJob withLink(ICraftingLink link) {
            return new CraftJob(jobId, CraftJobState.SUBMITTED, link, missingItems, error);
        }

        CraftJob withMissingItems(List<AiTerminalData.Ae2PlanItem> missing) {
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
