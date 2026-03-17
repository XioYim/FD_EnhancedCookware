package com.xioyim.fdenhancedcookware.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;
import com.xioyim.fdenhancedcookware.blockentity.CookingState;
import com.xioyim.fdenhancedcookware.config.CookpotConfig;
import com.xioyim.fdenhancedcookware.init.ModMenuTypes;

public class GrandCookpotMenu extends AbstractContainerMenu {

    private final GrandCookpotBlockEntity blockEntity;
    private final ContainerData data;

    // ===================== 菜单槽位索引 =====================
    public static final int INPUT_SLOT_START  = 0;
    public static final int INPUT_SLOT_END    = 11;
    public static final int VESSEL_SLOT       = 12;
    public static final int OUTPUT_SLOT       = 13;
    public static final int PREVIEW_SLOT_IDX  = 14;
    public static final int FUEL_SLOT         = 15;
    public static final int LIQUID_SLOT       = 16;
    public static final int INV_SLOT_START    = 17;
    public static final int INV_SLOT_END      = 43;
    public static final int HOTBAR_SLOT_START = 44;
    public static final int HOTBAR_SLOT_END   = 52;

    // ===================== GUI 坐标常量 =====================
    public static final int GUI_WIDTH  = 176;
    public static final int GUI_HEIGHT = 166;

    // 输入网格 4×3，左对齐
    public static final int GRID_START_X = 8;
    public static final int GRID_START_Y = 17;

    // 箭头/状态按钮
    public static final int ARROW_X = 159;
    public static final int ARROW_Y = 14;
    public static final int ARROW_W = 9;
    public static final int ARROW_H = 9;

    // 输出槽
    public static final int OUTPUT_X = 97;
    public static final int OUTPUT_Y = 27;

    // 器皿槽（与第3排输入槽对齐）
    public static final int VESSEL_X = 97;
    public static final int VESSEL_Y = 53;

    // 燃料槽（x=119, y=53）
    public static final int FUEL_X = 120;
    public static final int FUEL_Y = 53;

    // 液体物品槽（x=138, y=53）
    public static final int LIQUID_X = 139;
    public static final int LIQUID_Y = 53;

    // 火焰图标（x=129, y=29，17×11）
    public static final int FLAME_X = 129;
    public static final int FLAME_Y = 29;
    public static final int FLAME_W = 17;
    public static final int FLAME_H = 11;

    // 热量进度条（x=124, y=42，27×4）
    public static final int HEAT_BAR_X = 124;
    public static final int HEAT_BAR_Y = 42;
    public static final int HEAT_BAR_W = 27;
    public static final int HEAT_BAR_H = 4;

    // 液体显示槽（x=158, y=25，11×45，含1px边框，内部9×43）
    public static final int FLUID_X = 158;
    public static final int FLUID_Y = 25;
    public static final int FLUID_W = 11;
    public static final int FLUID_H = 45;

    // 玩家物品栏
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 84;
    public static final int HOTBAR_Y     = 142;

    // ===================== ContainerData 索引 =====================
    public static final int DATA_PROGRESS      = 0;
    public static final int DATA_MAX_PROGRESS  = 1;
    public static final int DATA_STATE         = 2;  // CookingState.ordinal()
    public static final int DATA_EXTRA_HEAT    = 3;  // 0–120
    public static final int DATA_FLUID_AMOUNT  = 4;  // 0–1000 mL
    public static final int DATA_FLUID_TYPE_ID = 5;  // BuiltInRegistries.FLUID 数字 ID
    public static final int DATA_INFINITE_HEAT = 6;  // 1=无限热量（下界炉灶地狱火），0=普通
    public static final int DATA_COUNT         = 7;

    private int progressValue    = 0;
    private int maxProgressValue = 0;
    private int stateValue       = 0;
    private int extraHeatValue   = 0;
    private int fluidAmountValue = 0;
    private int fluidTypeIdValue = 0;
    private int infiniteHeatValue = 0;

    // ===================== 构造 =====================

    public GrandCookpotMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntity(playerInv, buf));
    }

    private static GrandCookpotBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        BlockEntity be = playerInv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof GrandCookpotBlockEntity pot) return pot;
        throw new IllegalStateException("Block entity is not GrandCookpotBlockEntity");
    }

    public GrandCookpotMenu(int containerId, Inventory playerInv, GrandCookpotBlockEntity be) {
        super(ModMenuTypes.GRAND_COOKPOT_MENU.get(), containerId);
        this.blockEntity = be;

        this.data = new SimpleContainerData(DATA_COUNT) {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS      -> be.getProgress();
                    case DATA_MAX_PROGRESS  -> be.getMaxProgress();
                    case DATA_STATE         -> be.getCookingState().ordinal();
                    case DATA_EXTRA_HEAT    -> be.getExtraHeat();
                    case DATA_FLUID_AMOUNT  -> be.getFluidAmount();
                    case DATA_FLUID_TYPE_ID -> {
                        Fluid f = be.getCurrentFluid();
                        yield (f == null || f == Fluids.EMPTY) ? 0 : BuiltInRegistries.FLUID.getId(f);
                    }
                    case DATA_INFINITE_HEAT -> be.isInfiniteHeat() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override
            public void set(int index, int value) {
                if (index == DATA_PROGRESS)      progressValue    = value;
                if (index == DATA_MAX_PROGRESS)  maxProgressValue = value;
                if (index == DATA_STATE)         stateValue       = value;
                if (index == DATA_EXTRA_HEAT)    extraHeatValue   = value;
                if (index == DATA_FLUID_AMOUNT)  fluidAmountValue  = value;
                if (index == DATA_FLUID_TYPE_ID) fluidTypeIdValue  = value;
                if (index == DATA_INFINITE_HEAT) infiniteHeatValue = value;
            }
        };
        addDataSlots(data);

        IItemHandler handler = be.getItemHandler();

        // 4×3 输入格（槽 0–11）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                int slotIndex = col + row * 4;
                addSlot(new SlotItemHandler(handler, slotIndex,
                        GRID_START_X + col * 18,
                        GRID_START_Y + row * 18));
            }
        }

        // 器皿槽（槽 12）
        addSlot(new SlotItemHandler(handler, GrandCookpotBlockEntity.SLOT_VESSEL,
                VESSEL_X, VESSEL_Y));

        // 输出槽（槽 13，只取不放）
        addSlot(new SlotItemHandler(handler, GrandCookpotBlockEntity.SLOT_OUTPUT,
                OUTPUT_X, OUTPUT_Y) {
            @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }
            @Override public boolean mayPickup(@NotNull Player player)  { return true; }
            @Override
            public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
                be.awardPendingExperience(player);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    be.executeOutputCommandsPlayer(sp);
                }
                super.onTake(player, stack);
            }
        });

        // 隐藏预览槽（槽 14，不可操作）
        addSlot(new SlotItemHandler(handler, GrandCookpotBlockEntity.SLOT_PREVIEW,
                -10000, -10000) {
            @Override public boolean mayPlace(@NotNull ItemStack stack)  { return false; }
            @Override public boolean mayPickup(@NotNull Player player)   { return false; }
        });

        // 燃料槽（槽 15）—— 无热源时锁定，仅接受配置中的有效燃料物品
        addSlot(new SlotItemHandler(handler, GrandCookpotBlockEntity.SLOT_FUEL,
                FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                if (GrandCookpotMenu.this.getCookingState() == CookingState.NO_HEAT) return false;
                return CookpotConfig.getFuelHeat(stack.getItem()) > 0;
            }
        });

        // 液体物品槽（槽 16）—— 无热源时锁定，仅接受配置中的有效液体物品
        addSlot(new SlotItemHandler(handler, GrandCookpotBlockEntity.SLOT_LIQUID_INPUT,
                LIQUID_X, LIQUID_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                if (GrandCookpotMenu.this.getCookingState() == CookingState.NO_HEAT) return false;
                return CookpotConfig.getLiquidEntry(stack.getItem()) != null;
            }
        });

        // 玩家背包（3 行）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        PLAYER_INV_Y + row * 18));
            }
        }

        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col,
                    PLAYER_INV_X + col * 18, HOTBAR_Y));
        }
    }

    // ===================== 快速移动（Shift+点击） =====================

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack returnStack = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            returnStack = stack.copy();

            if (index == OUTPUT_SLOT) {
                if (!moveItemStackTo(stack, INV_SLOT_START, HOTBAR_SLOT_END + 1, true))
                    return ItemStack.EMPTY;
                slot.onQuickCraft(stack, returnStack);
            } else if (index == PREVIEW_SLOT_IDX) {
                return ItemStack.EMPTY;
            } else if (index >= INV_SLOT_START) {
                // 从玩家物品栏：根据物品类型优先路由到对应专用槽
                boolean isFuel   = CookpotConfig.getFuelHeat(stack.getItem()) > 0;
                boolean isLiquid = CookpotConfig.getLiquidEntry(stack.getItem()) != null;

                boolean moved;
                if (isFuel) {
                    // 燃料物品：燃料槽优先 → 输入格 → 器皿槽
                    moved = moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false)
                            || moveItemStackTo(stack, INPUT_SLOT_START, INPUT_SLOT_END + 1, false)
                            || moveItemStackTo(stack, VESSEL_SLOT, VESSEL_SLOT + 1, false);
                } else if (isLiquid) {
                    // 液体物品：液体槽优先 → 输入格 → 器皿槽
                    moved = moveItemStackTo(stack, LIQUID_SLOT, LIQUID_SLOT + 1, false)
                            || moveItemStackTo(stack, INPUT_SLOT_START, INPUT_SLOT_END + 1, false)
                            || moveItemStackTo(stack, VESSEL_SLOT, VESSEL_SLOT + 1, false);
                } else {
                    // 普通物品：输入格 → 器皿 → 燃料 → 液体
                    moved = moveItemStackTo(stack, INPUT_SLOT_START, INPUT_SLOT_END + 1, false)
                            || moveItemStackTo(stack, VESSEL_SLOT, VESSEL_SLOT + 1, false)
                            || moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false)
                            || moveItemStackTo(stack, LIQUID_SLOT, LIQUID_SLOT + 1, false);
                }

                if (!moved) {
                    // 背包 ↔ 快捷栏 互相补位
                    if (index < HOTBAR_SLOT_START) {
                        if (!moveItemStackTo(stack, HOTBAR_SLOT_START, HOTBAR_SLOT_END + 1, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (!moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END + 1, false))
                            return ItemStack.EMPTY;
                    }
                }
            } else {
                // 从机器槽：放回玩家物品栏
                if (!moveItemStackTo(stack, INV_SLOT_START, HOTBAR_SLOT_END + 1, false))
                    return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            if (stack.getCount() == returnStack.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }

        return returnStack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return blockEntity.stillValid(player);
    }

    // ===================== Getters =====================

    public int getProgress()      { return progressValue; }
    public int getMaxProgress()   { return maxProgressValue; }
    public int getExtraHeat()     { return extraHeatValue; }
    public int getFluidAmount()   { return fluidAmountValue; }
    public boolean isInfiniteHeat() { return infiniteHeatValue != 0; }

    /**
     * 客户端获取当前液体对象（通过 ContainerData 同步的数字 ID 反查）。
     * 使用 & 0xFFFF 处理有符号 short 溢出（short 传输限制），支持最多 65535 种流体。
     */
    public Fluid getFluid() {
        int id = fluidTypeIdValue & 0xFFFF;
        if (id == 0) return Fluids.EMPTY;
        Fluid f = BuiltInRegistries.FLUID.byId(id);
        return f == null ? Fluids.EMPTY : f;
    }

    public CookingState getCookingState() {
        // 服务端：直接读取 BE，保证实时准确（ContainerData set() 仅在客户端调用）
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            return blockEntity.getCookingState();
        }
        // 客户端：读取 ContainerData 同步过来的值
        CookingState[] values = CookingState.values();
        int idx = stateValue;
        return (idx >= 0 && idx < values.length) ? values[idx] : CookingState.NO_HEAT;
    }

    public GrandCookpotBlockEntity getBlockEntity() { return blockEntity; }
}
