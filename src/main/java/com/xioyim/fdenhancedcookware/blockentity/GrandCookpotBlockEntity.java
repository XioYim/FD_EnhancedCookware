package com.xioyim.fdenhancedcookware.blockentity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.xioyim.fdenhancedcookware.config.CookpotConfig;
import com.xioyim.fdenhancedcookware.init.ModBlockEntities;
import com.xioyim.fdenhancedcookware.init.ModRecipeTypes;
import com.xioyim.fdenhancedcookware.menu.GrandCookpotMenu;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;

import java.util.*;

public class GrandCookpotBlockEntity extends BlockEntity
        implements MenuProvider, HeatableBlockEntity {

    // ===================== 槽位常量 =====================
    public static final int SLOT_INPUT_START = 0;
    public static final int SLOT_INPUT_COUNT = 12;   // 4×3
    public static final int SLOT_VESSEL       = 12;  // 器皿（如碗）
    public static final int SLOT_OUTPUT       = 13;  // 输出
    public static final int SLOT_PREVIEW      = 14;  // 隐藏预览（客户端同步用）
    public static final int SLOT_FUEL         = 15;  // 燃料槽（extra heat）
    public static final int SLOT_LIQUID_INPUT = 16;  // 液体物品投入槽
    public static final int TOTAL_SLOTS       = 17;

    public static final String DISPLAY_ENTITY_TAG = "grand_cookpot_display";

    /** 下界炉灶 ResourceLocation（用于剧烈燃烧保证及玩家破坏检测）*/
    public static final ResourceLocation NETHER_STOVE_ID =
            new ResourceLocation("mynethersdelight", "nether_stove");

    // ===================== 库存 =====================
    private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < SLOT_OUTPUT) return true;           // 输入格 0-12 接受任意物品
            if (slot == SLOT_FUEL) {
                return CookpotConfig.getFuelHeat(stack.getItem()) > 0;
            }
            if (slot == SLOT_LIQUID_INPUT) {
                return CookpotConfig.getLiquidEntry(stack.getItem()) != null;
            }
            return false; // OUTPUT(13), PREVIEW(14)：不允许放入
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == SLOT_PREVIEW) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    // ===================== 烹饪状态 =====================
    private CookingState cookingState = CookingState.NO_HEAT;
    private int progress    = 0;
    private int maxProgress = 0;
    private ItemStack craftingResult  = ItemStack.EMPTY;
    private float cookingExperience   = 0f;
    private float pendingExperience   = 0f;

    // 命令附加数据
    private List<String> tickCommands            = new ArrayList<>();
    private int tickInterval                     = 1;
    private List<String> finishCommands          = new ArrayList<>();
    private List<String> pendingOutputCmdsPlayer = new ArrayList<>();

    // MISSING_TAGS 状态持续 tick 计数
    private int missingTagsTimer = 0;

    @Nullable
    private UUID displayEntityUUID = null;

    // ===================== 热量与液体系统 =====================

    /** 额外热量（燃料槽投入获得），范围 0–120。 */
    private int extraHeat = 0;

    /** 无热源时热量自然衰减计时器（每 60 tick 减 1 点热量）。 */
    private int heatDecayTimer = 0;

    /** 当前是否处于"无限热量"模式（下方为点燃地狱火下界炉灶）。 */
    private boolean isInfiniteHeat = false;

    /** 当前储存的液体，null = 空。 */
    @Nullable
    private Fluid currentFluid = null;

    /** 当前液体储量（mL），0–1000。 */
    private int fluidAmount = 0;

    /**
     * startCrafting 前对输入槽（0–SLOT_VESSEL）的快照。
     * 炉灶被破坏导致烹饪取消时，用于将材料原路返回到输入槽。
     */
    @Nullable
    private List<ItemStack> snapshotBeforeCraft = null;

    // ===================== 构造 =====================

    public GrandCookpotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAND_COOKPOT.get(), pos, state);
    }

    // ===================== Tick =====================

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  GrandCookpotBlockEntity be) {
        // 处理液体投入
        be.processLiquidSlot();

        boolean heated = be.isHeated(level, pos);

        // ── 热源丢失：统一处理（覆盖所有非 NO_HEAT 状态）──────────────────
        if (!heated) {
            if (be.isInfiniteHeat) {
                be.isInfiniteHeat = false;
                be.setChanged();
            }
            if (be.cookingState != CookingState.NO_HEAT) {
                be.onHeatLost(level);            // 取消烹饪、返还材料（不清热量）
                be.missingTagsTimer = 0;
                be.cookingState = CookingState.NO_HEAT;
                be.maxProgress   = 0;
                be.updatePreviewSlot(level, null);
                be.setChanged();
            }
            // 无热源时不衰减，重置计时器
            be.heatDecayTimer = 0;
            return;
        }
        // 下界炉灶点燃且为地狱火（非灵魂火）：强制保持满热量（120）
        boolean nowInfinite = be.isNetherStoveNonSoul(level);
        if (nowInfinite) {
            if (be.extraHeat != 120) {
                be.extraHeat = 120;
                be.setChanged();
            }
            be.heatDecayTimer = 0;
        }
        if (nowInfinite != be.isInfiniteHeat) {
            be.isInfiniteHeat = nowInfinite;
            be.setChanged();
        }

        // 热量自然衰减：间隔由配置文件决定，0 表示永不衰减
        int decayInterval = CookpotConfig.getHeatDecayInterval();
        if (be.extraHeat > 0 && decayInterval > 0) {
            be.heatDecayTimer++;
            if (be.heatDecayTimer >= decayInterval) {
                be.heatDecayTimer = 0;
                be.extraHeat--;
                be.setChanged();
            }
        } else {
            be.heatDecayTimer = 0;
        }

        // ── 有热源时的状态机 ──────────────────────────────────────────────
        switch (be.cookingState) {
            case NO_HEAT, BLOCKED, NORMAL, WRONG_STOVE -> {
                Optional<GrandCookpotRecipe> recipe = be.findMatchingRecipe(level);
                if (recipe.isPresent()) {
                    be.maxProgress = recipe.get().getCookingTime();
                    be.setStateAndSync(CookingState.NORMAL);
                    be.updatePreviewSlot(level, recipe.get());
                } else {
                    be.maxProgress = 0;
                    Optional<GrandCookpotRecipe> anyRecipe = be.findRecipeIngredientOnly(level);
                    if (anyRecipe.isPresent()) {
                        be.setStateAndSync(CookingState.WRONG_STOVE);
                        be.updatePreviewSlot(level, anyRecipe.get());
                    } else {
                        be.setStateAndSync(CookingState.BLOCKED);
                        be.updatePreviewSlot(level, null);
                    }
                }
            }
            case MISSING_TAGS -> {
                if (--be.missingTagsTimer <= 0) {
                    be.setStateAndSync(CookingState.NORMAL);
                }
            }
            case CRAFTING -> {
                if (!be.tickCommands.isEmpty() && be.progress % be.tickInterval == 0) {
                    be.executeTickCommands((ServerLevel) level);
                }
                be.progress++;
                be.setChanged();
                if (be.progress >= be.maxProgress) {
                    be.snapshotBeforeCraft = null; // 烹饪成功：丢弃快照
                    be.finishCrafting(level);
                }
            }
            case READY -> {
                if (be.itemHandler.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
                    if (level instanceof ServerLevel sl) be.killDisplayEntities(sl);
                    be.setStateAndSync(CookingState.BLOCKED);
                    be.setChanged();
                }
            }
        }
    }

    /**
     * 热源丢失时的统一清理（不清空热量，热量由玩家主动破坏炉灶时才清零；
     * 燃料槽物品保留在槽中，不弹出）：
     * 若正在烹饪：杀死展示实体、从快照返还材料、重置烹饪数据
     */
    private void onHeatLost(Level level) {
        // 如果正在烹饪，取消并返还材料
        if (cookingState == CookingState.CRAFTING) {
            if (level instanceof ServerLevel sl) killDisplayEntities(sl);

            // 从快照恢复输入槽 + 器皿槽（0 到 SLOT_VESSEL 含）
            if (snapshotBeforeCraft != null) {
                for (int i = 0; i < snapshotBeforeCraft.size(); i++) {
                    itemHandler.setStackInSlot(i, snapshotBeforeCraft.get(i).copy());
                }
                snapshotBeforeCraft = null;
            }

            // 重置烹饪相关数据
            craftingResult    = ItemStack.EMPTY;
            progress          = 0;
            maxProgress       = 0;
            cookingExperience = 0f;
            tickCommands      = new ArrayList<>();
            tickInterval      = 1;
            finishCommands    = new ArrayList<>();
            pendingOutputCmdsPlayer = new ArrayList<>();
            itemHandler.setStackInSlot(SLOT_PREVIEW, ItemStack.EMPTY);
        }
    }

    /** 由玩家破坏下方炉灶时调用，立即清空锅内热量。 */
    public void clearExtraHeat() {
        extraHeat = 0;
        setChanged();
    }

    /** 玩家右键锅使用空桶时调用，清空锅内所有液体。 */
    public void clearFluid() {
        fluidAmount = 0;
        currentFluid = null;
        setChanged();
    }

    /**
     * 玩家点击燃料槽时调用：消耗一个燃料物品，增加对应热量，最大 120。
     * 即使当前热量已接近上限，仍会消耗物品并将热量封顶在 120。
     */
    public void addHeatFromFuel(ServerPlayer player) {
        if (extraHeat >= 120) return;
        ItemStack fuel = itemHandler.getStackInSlot(SLOT_FUEL);
        if (fuel.isEmpty()) return;
        int heat = CookpotConfig.getFuelHeat(fuel.getItem());
        if (heat <= 0) return;
        extraHeat = Math.min(120, extraHeat + heat);
        itemHandler.extractItem(SLOT_FUEL, 1, false);
        setChanged();
    }

    public static void animationTick(Level level, BlockPos pos, BlockState state,
                                     GrandCookpotBlockEntity be) {
    }

    // ===================== 燃料/液体槽处理 =====================

    /** 每 tick 尝试消耗一个液体物品以注入液体（无热源或同类型不匹配时停止）。 */
    private void processLiquidSlot() {
        if (cookingState == CookingState.NO_HEAT) return; // 无热源时禁止消耗
        ItemStack liquidItem = itemHandler.getStackInSlot(SLOT_LIQUID_INPUT);
        if (liquidItem.isEmpty()) return;
        CookpotConfig.FluidEntry entry = CookpotConfig.getLiquidEntry(liquidItem.getItem());
        if (entry == null) return;

        // 只允许同类型或空槽
        Fluid entryFluid = BuiltInRegistries.FLUID.get(entry.fluidId());
        if (entryFluid == null || entryFluid == Fluids.EMPTY) return;
        if (currentFluid != null && currentFluid != Fluids.EMPTY && !currentFluid.isSame(entryFluid)) return;
        if (fluidAmount >= 1000) return; // 已满，不再消耗

        currentFluid = entryFluid;
        fluidAmount = Math.min(1000, fluidAmount + entry.amountMl());
        itemHandler.extractItem(SLOT_LIQUID_INPUT, 1, false);

        // 返还容器物品（如空桶、玻璃瓶）
        if (entry.returnItem() != null && level != null) {
            net.minecraft.world.item.Item returnObj = ForgeRegistries.ITEMS.getValue(entry.returnItem());
            if (returnObj != null) {
                ItemStack returnStack = new ItemStack(returnObj, 1);
                if (!returnStack.isEmpty()) {
                    ItemStack slotNow = itemHandler.getStackInSlot(SLOT_LIQUID_INPUT);
                    if (slotNow.isEmpty()) {
                        // 槽已空 → 直接放回液体槽
                        itemHandler.setStackInSlot(SLOT_LIQUID_INPUT, returnStack);
                    } else {
                        // 槽仍有物品（玩家放了多个）→ 丢到方块上方
                        ItemEntity drop = new ItemEntity(level,
                                worldPosition.getX() + 0.5,
                                worldPosition.getY() + 1.0,
                                worldPosition.getZ() + 0.5,
                                returnStack);
                        drop.setDeltaMovement(0, 0.1, 0);
                        level.addFreshEntity(drop);
                    }
                }
            }
        }

        setChanged();
    }

    /**
     * 检查锅正下方是否是点燃且非灵魂火焰的下界炉灶。
     * 满足时锅的 extraHeat 始终 ≥ 1（由 serverTick 保证）。
     */
    private boolean isNetherStoveNonSoul(Level level) {
        BlockState below = level.getBlockState(worldPosition.below());
        var block = ForgeRegistries.BLOCKS.getValue(NETHER_STOVE_ID);
        if (block == null) return false;
        if (!below.is(block)) return false;
        // 必须点燃
        if (below.hasProperty(BlockStateProperties.LIT)
                && !below.getValue(BlockStateProperties.LIT)) return false;
        // 必须 soul=false
        for (var prop : below.getProperties()) {
            if ("soul".equals(prop.getName())) {
                return "false".equals(below.getValue(prop).toString());
            }
        }
        return false;
    }

    // ===================== 开始烹饪 =====================

    public void startCrafting(ServerPlayer player) {
        if (level == null || level.isClientSide) return;
        if (cookingState != CookingState.NORMAL) return;
        if (!itemHandler.getStackInSlot(SLOT_OUTPUT).isEmpty()) return;

        Optional<GrandCookpotRecipe> recipeOpt = findMatchingRecipe(level);
        if (recipeOpt.isEmpty()) return;

        GrandCookpotRecipe recipe = recipeOpt.get();

        // 检查玩家标签
        if (!recipe.matchesPlayerTags(player)) {
            cookingState     = CookingState.MISSING_TAGS;
            missingTagsTimer = 100;
            setChanged();
            return;
        }

        // 烹饪前先快照输入槽 + 器皿槽（0 到 SLOT_VESSEL），用于炉灶破坏时返还
        snapshotBeforeCraft = new ArrayList<>();
        for (int i = 0; i <= SLOT_VESSEL; i++) {
            snapshotBeforeCraft.add(itemHandler.getStackInSlot(i).copy());
        }

        // 消耗输入格材料
        ContainerWrapper wrapper = new ContainerWrapper(itemHandler, TOTAL_SLOTS);
        recipe.consumeIngredients(wrapper);

        // 开始时立即消耗器皿
        if (!recipe.getContainer().isEmpty()) {
            itemHandler.extractItem(SLOT_VESSEL, 1, false);
        }

        // 消耗热量
        if (recipe.getHeatConsumption() > 0 && extraHeat > 0) {
            extraHeat -= recipe.getHeatConsumption();
            // 下界炉灶：消耗后立即恢复满热量
            if (isNetherStoveNonSoul(level)) {
                extraHeat = 120;
            }
            extraHeat = Math.max(0, extraHeat);
            setChanged();
        }

        // 消耗液体
        if (recipe.getRequiredFluid() != null && recipe.getFluidConsumption() > 0) {
            fluidAmount -= recipe.getFluidConsumption();
            if (fluidAmount <= 0) {
                fluidAmount = 0;
                currentFluid = null;
            }
            setChanged();
        }

        // 设置烹饪参数
        cookingState      = CookingState.CRAFTING;
        craftingResult    = recipe.getResult().copy();
        maxProgress       = recipe.getCookingTime();
        progress          = 0;
        cookingExperience = recipe.getExperience();
        tickCommands             = new ArrayList<>(recipe.getTickCommandBlock());
        tickInterval             = recipe.getTickInterval();
        finishCommands           = new ArrayList<>(recipe.getFinishCommand());
        pendingOutputCmdsPlayer  = new ArrayList<>(recipe.getOutputCommandPlayer());

        ServerLevel sl = (ServerLevel) level;

        // 召唤展示实体（位置由配置文件控制）
        killDisplayEntities(sl);
        ItemEntity display = new ItemEntity(sl,
                worldPosition.getX() + CookpotConfig.getDisplayOffsetX(),
                worldPosition.getY() + CookpotConfig.getDisplayOffsetY(),
                worldPosition.getZ() + CookpotConfig.getDisplayOffsetZ(),
                craftingResult.copy());
        display.addTag(DISPLAY_ENTITY_TAG);
        display.setNeverPickUp();
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setDeltaMovement(Vec3.ZERO);
        display.lifespan = Integer.MAX_VALUE;
        sl.addFreshEntity(display);
        displayEntityUUID = display.getUUID();

        // 执行 start_command_player
        if (!recipe.getStartCommandPlayer().isEmpty()) {
            CommandSourceStack src = player.createCommandSourceStack()
                    .withPermission(4)
                    .withPosition(Vec3.atCenterOf(worldPosition))
                    .withLevel(sl).withSuppressedOutput();
            MinecraftServer server = sl.getServer();
            for (String cmd : recipe.getStartCommandPlayer()) {
                server.getCommands().performPrefixedCommand(src, cmd);
            }
        }

        updatePreviewSlot(level, recipe);
        setChanged();
    }

    private void executeTickCommands(ServerLevel sl) {
        MinecraftServer server = sl.getServer();
        if (server == null || tickCommands.isEmpty()) return;
        CommandSourceStack src = server.createCommandSourceStack()
                .withLevel(sl).withPosition(Vec3.atCenterOf(worldPosition))
                .withPermission(4).withSuppressedOutput();
        for (String cmd : tickCommands) {
            server.getCommands().performPrefixedCommand(src, cmd);
        }
    }

    private void finishCrafting(Level level) {
        ItemStack current = itemHandler.getStackInSlot(SLOT_OUTPUT);
        if (current.isEmpty()) {
            itemHandler.setStackInSlot(SLOT_OUTPUT, craftingResult.copy());
        } else if (ItemStack.isSameItemSameTags(current, craftingResult)
                && current.getCount() + craftingResult.getCount() <= current.getMaxStackSize()) {
            current.grow(craftingResult.getCount());
        }

        pendingExperience += cookingExperience;

        if (!finishCommands.isEmpty() && level instanceof ServerLevel sl) {
            MinecraftServer server = sl.getServer();
            if (server != null) {
                CommandSourceStack src = server.createCommandSourceStack()
                        .withLevel(sl).withPosition(Vec3.atCenterOf(worldPosition))
                        .withPermission(4).withSuppressedOutput();
                for (String cmd : finishCommands) {
                    server.getCommands().performPrefixedCommand(src, cmd);
                }
            }
        }

        cookingState      = CookingState.READY;
        progress          = 0;
        maxProgress       = 0;
        craftingResult    = ItemStack.EMPTY;
        cookingExperience = 0f;
        tickCommands      = new ArrayList<>();
        tickInterval      = 1;
        finishCommands    = new ArrayList<>();
        itemHandler.setStackInSlot(SLOT_PREVIEW, ItemStack.EMPTY);
        setChanged();
    }

    public void awardPendingExperience(Player player) {
        if (pendingExperience <= 0f) return;
        int exp = (int) pendingExperience;
        float remainder = pendingExperience - exp;
        if (player.level().random.nextFloat() < remainder) exp++;
        if (exp > 0) player.giveExperiencePoints(exp);
        pendingExperience = 0f;
        setChanged();
    }

    public void executeOutputCommandsPlayer(net.minecraft.server.level.ServerPlayer player) {
        if (pendingOutputCmdsPlayer.isEmpty()) return;
        if (!(level instanceof ServerLevel sl)) return;
        MinecraftServer server = sl.getServer();
        if (server == null) return;
        CommandSourceStack src = player.createCommandSourceStack()
                .withPermission(4)
                .withPosition(Vec3.atCenterOf(worldPosition))
                .withLevel(sl).withSuppressedOutput();
        for (String cmd : pendingOutputCmdsPlayer) {
            server.getCommands().performPrefixedCommand(src, cmd);
        }
        pendingOutputCmdsPlayer = new ArrayList<>();
        setChanged();
    }

    // ===================== 辅助 =====================

    private void updatePreviewSlot(Level level, @Nullable GrandCookpotRecipe recipe) {
        if (recipe == null) {
            if (!itemHandler.getStackInSlot(SLOT_PREVIEW).isEmpty())
                itemHandler.setStackInSlot(SLOT_PREVIEW, ItemStack.EMPTY);
        } else {
            ItemStack preview = recipe.getResult().copy();
            ItemStack current = itemHandler.getStackInSlot(SLOT_PREVIEW);
            if (!ItemStack.matches(current, preview))
                itemHandler.setStackInSlot(SLOT_PREVIEW, preview);
        }
    }

    private void setStateAndSync(CookingState newState) {
        if (cookingState != newState) {
            cookingState = newState;
            setChanged();
        }
    }

    /** 完整匹配：材料 + 器皿 + 炉灶 + 剧烈燃烧 + 热量充足 + 液体充足。 */
    public Optional<GrandCookpotRecipe> findMatchingRecipe(Level level) {
        ContainerWrapper wrapper = new ContainerWrapper(itemHandler, TOTAL_SLOTS);
        boolean isRoaring = extraHeat > 0;
        return level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.GRAND_COOKPOT.get())
                .stream()
                .filter(r -> r.matches(wrapper, level)
                        && r.matchesBlockRequirement(level, worldPosition)
                        && r.matchesRoaring(isRoaring)
                        && r.matchesHeat(extraHeat)        // 热量必须 >= heatConsumption
                        && r.matchesFluid(currentFluid, fluidAmount))
                .findFirst();
    }

    /**
     * 仅按材料+器皿匹配，同时做宽松方块类型检查（忽略 soul/lit 等属性），
     * 用于 WRONG_STOVE 检测。
     * <p>
     * 若配方要求特定炉灶（require_block=true），则当前方块大类（仅 block ID，不检查属性）
     * 必须与任意一个 requiredBlock 匹配，否则跳过该配方（不触发 WRONG_STOVE）。
     * 这可防止"所需特殊火焰"配方在普通炉灶旁显示为 WRONG_STOVE 状态。
     */
    private Optional<GrandCookpotRecipe> findRecipeIngredientOnly(Level level) {
        ContainerWrapper wrapper = new ContainerWrapper(itemHandler, TOTAL_SLOTS);
        BlockState below = level.getBlockState(worldPosition.below());
        return level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.GRAND_COOKPOT.get())
                .stream()
                .filter(r -> r.matches(wrapper, level))
                .filter(r -> matchesBlockTypeLoosely(r, below))
                .findFirst();
    }

    /**
     * 宽松方块类型匹配：仅检查方块 ID（block 对象相同），忽略 soul/lit 等 BlockState 属性。
     * 若配方不要求特定方块，始终返回 true。
     */
    private boolean matchesBlockTypeLoosely(GrandCookpotRecipe r, BlockState below) {
        if (!r.isRequireBlock() || r.getRequiredBlocks().isEmpty()) return true;
        return r.getRequiredBlocks().stream().anyMatch(rb -> {
            var block = ForgeRegistries.BLOCKS.getValue(rb.getBlockId());
            return block != null && below.is(block);
        });
    }

    private void killDisplayEntities(ServerLevel sl) {
        if (displayEntityUUID != null) {
            Entity e = sl.getEntity(displayEntityUUID);
            if (e != null) e.discard();
            displayEntityUUID = null;
        }
        AABB box = new AABB(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1.0, worldPosition.getY() + 2.0, worldPosition.getZ() + 1.0);
        sl.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.getTags().contains(DISPLAY_ENTITY_TAG)).forEach(Entity::discard);
    }

    public void killDisplayEntitiesPublic(ServerLevel sl) { killDisplayEntities(sl); }

    // ===================== 掉落库存 =====================

    public NonNullList<ItemStack> getDroppableInventory() {
        NonNullList<ItemStack> drops = NonNullList.create();
        // 掉落所有槽，仅排除隐藏预览槽（SLOT_PREVIEW = 14）
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (i == SLOT_PREVIEW) continue;
            drops.add(itemHandler.getStackInSlot(i));
        }
        return drops;
    }

    public CompoundTag saveInventoryToTag(CompoundTag tag) {
        tag.put("inventory", itemHandler.serializeNBT());
        return tag;
    }

    public int getRedstoneSignal() {
        int filled = 0;
        for (int i = 0; i < SLOT_VESSEL; i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) filled++;
        }
        return (int) ((float) filled / SLOT_INPUT_COUNT * 15);
    }

    // ===================== NBT =====================

    private static ListTag stringsToList(List<String> list) {
        ListTag tag = new ListTag();
        for (String s : list) tag.add(StringTag.valueOf(s));
        return tag;
    }

    private static List<String> listToStrings(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_LIST)) return new ArrayList<>();
        ListTag lt = root.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>(lt.size());
        for (int i = 0; i < lt.size(); i++) result.add(lt.getString(i));
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putString("cookingState", cookingState.name());
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        if (!craftingResult.isEmpty())
            tag.put("craftingResult", craftingResult.save(new CompoundTag()));
        if (cookingExperience != 0f) tag.putFloat("cookingExperience", cookingExperience);
        if (pendingExperience  != 0f) tag.putFloat("pendingExperience",  pendingExperience);
        if (!tickCommands.isEmpty())            tag.put("tickCommands", stringsToList(tickCommands));
        if (tickInterval != 1)                 tag.putInt("tickInterval", tickInterval);
        if (!finishCommands.isEmpty())         tag.put("finishCommands", stringsToList(finishCommands));
        if (!pendingOutputCmdsPlayer.isEmpty()) tag.put("outputCmdsPlayer", stringsToList(pendingOutputCmdsPlayer));
        if (missingTagsTimer != 0)             tag.putInt("missingTagsTimer", missingTagsTimer);
        if (displayEntityUUID != null)         tag.putUUID("displayEntity", displayEntityUUID);
        // 烹饪快照（炉灶破坏时返还用）
        if (snapshotBeforeCraft != null) {
            ListTag snapList = new ListTag();
            for (ItemStack stack : snapshotBeforeCraft) snapList.add(stack.save(new CompoundTag()));
            tag.put("snapshotBeforeCraft", snapList);
        }
        // 热量与液体
        tag.putInt("extraHeat", extraHeat);
        tag.putInt("fluidAmount", fluidAmount);
        if (currentFluid != null && currentFluid != Fluids.EMPTY) {
            ResourceLocation fluidKey = BuiltInRegistries.FLUID.getKey(currentFluid);
            if (fluidKey != null) tag.putString("fluidType", fluidKey.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory")) {
            // 强制使用当前 TOTAL_SLOTS 大小，防止旧版 NBT（槽位数更少）缩小 handler
            CompoundTag invTag = tag.getCompound("inventory").copy();
            invTag.putInt("Size", TOTAL_SLOTS);
            itemHandler.deserializeNBT(invTag);
        }
        if (tag.contains("cookingState")) {
            try { cookingState = CookingState.valueOf(tag.getString("cookingState")); }
            catch (IllegalArgumentException e) { cookingState = CookingState.NO_HEAT; }
        }
        progress          = tag.getInt("progress");
        maxProgress       = tag.getInt("maxProgress");
        craftingResult    = tag.contains("craftingResult")
                ? ItemStack.of(tag.getCompound("craftingResult")) : ItemStack.EMPTY;
        cookingExperience = tag.contains("cookingExperience") ? tag.getFloat("cookingExperience") : 0f;
        pendingExperience = tag.contains("pendingExperience")  ? tag.getFloat("pendingExperience")  : 0f;
        tickCommands             = listToStrings(tag, "tickCommands");
        tickInterval             = tag.contains("tickInterval") ? tag.getInt("tickInterval") : 1;
        finishCommands           = listToStrings(tag, "finishCommands");
        pendingOutputCmdsPlayer  = listToStrings(tag, "outputCmdsPlayer");
        missingTagsTimer         = tag.contains("missingTagsTimer") ? tag.getInt("missingTagsTimer") : 0;
        displayEntityUUID        = tag.hasUUID("displayEntity") ? tag.getUUID("displayEntity") : null;
        // 烹饪快照
        if (tag.contains("snapshotBeforeCraft", Tag.TAG_LIST)) {
            ListTag snapList = tag.getList("snapshotBeforeCraft", Tag.TAG_COMPOUND);
            snapshotBeforeCraft = new ArrayList<>();
            for (int i = 0; i < snapList.size(); i++) {
                snapshotBeforeCraft.add(ItemStack.of(snapList.getCompound(i)));
            }
        } else {
            snapshotBeforeCraft = null;
        }
        // 热量与液体
        extraHeat   = tag.getInt("extraHeat");
        fluidAmount = tag.getInt("fluidAmount");
        if (tag.contains("fluidType")) {
            Fluid f = BuiltInRegistries.FLUID.get(new ResourceLocation(tag.getString("fluidType")));
            currentFluid = (f != null && f != Fluids.EMPTY) ? f : null;
        } else {
            currentFluid = null;
        }
    }

    // ===================== Capabilities =====================

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap,
                                                       @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return lazyItemHandler.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    // ===================== MenuProvider =====================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.fd_enhancedcookware.grand_cookpot");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new GrandCookpotMenu(containerId, playerInv, this);
    }

    public boolean stillValid(Player player) {
        if (this.level != null && this.level.getBlockEntity(this.worldPosition) != this) return false;
        return player.distanceToSqr(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0D;
    }

    // ===================== Getters =====================

    public ItemStackHandler getItemHandler()    { return itemHandler; }
    public CookingState getCookingState()       { return cookingState; }
    public int getProgress()                    { return progress; }
    public int getMaxProgress()                 { return maxProgress; }
    public int getExtraHeat()                   { return extraHeat; }
    public int getFluidAmount()                 { return fluidAmount; }
    @Nullable public Fluid getCurrentFluid()    { return currentFluid; }
    public boolean isInfiniteHeat()             { return isInfiniteHeat; }

    // ===================== 内部容器包装 =====================

    public static class ContainerWrapper implements Container {
        private final ItemStackHandler handler;
        private final int size;
        public ContainerWrapper(ItemStackHandler handler, int size) {
            this.handler = handler; this.size = size;
        }
        @Override public int getContainerSize()           { return size; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < size; i++) if (!handler.getStackInSlot(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int slot)           { return handler.getStackInSlot(slot); }
        @Override public ItemStack removeItem(int slot, int amt){ return handler.extractItem(slot, amt, false); }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            ItemStack s = handler.getStackInSlot(slot).copy();
            handler.setStackInSlot(slot, ItemStack.EMPTY); return s;
        }
        @Override public void setItem(int slot, ItemStack stack){ handler.setStackInSlot(slot, stack); }
        @Override public void setChanged()                 {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int i = 0; i < size; i++) handler.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
