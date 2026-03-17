package com.xioyim.fdenhancedcookware.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkEvent;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;
import com.xioyim.fdenhancedcookware.init.ModRecipeTypes;
import com.xioyim.fdenhancedcookware.recipe.CountedIngredient;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：JEI 配方转移，将配方所需物品从玩家背包填入输入格和器皿槽。
 */
public record FillGridPacket(BlockPos pos, ResourceLocation recipeId) {

    public static void encode(FillGridPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeResourceLocation(pkt.recipeId);
    }

    public static FillGridPacket decode(FriendlyByteBuf buf) {
        return new FillGridPacket(buf.readBlockPos(), buf.readResourceLocation());
    }

    public static void handle(FillGridPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pkt.pos);
            if (!(be instanceof GrandCookpotBlockEntity cookpot)) return;

            Optional<GrandCookpotRecipe> recipeOpt = player.level()
                    .getRecipeManager()
                    .getAllRecipesFor(ModRecipeTypes.GRAND_COOKPOT.get())
                    .stream()
                    .filter(r -> r.getId().equals(pkt.recipeId))
                    .findFirst();
            if (recipeOpt.isEmpty()) return;

            GrandCookpotRecipe recipe = recipeOpt.get();
            var handler = cookpot.getItemHandler();
            Inventory inv = player.getInventory();

            // ① 归还输入格现有物品
            for (int slot = 0; slot < GrandCookpotBlockEntity.SLOT_INPUT_COUNT; slot++) {
                ItemStack stack = handler.extractItem(slot, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, stack);
            }

            // ② 归还器皿槽现有物品
            ItemStack oldVessel = handler.extractItem(GrandCookpotBlockEntity.SLOT_VESSEL,
                    Integer.MAX_VALUE, false);
            if (!oldVessel.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, oldVessel);

            // 调试模式：创造模式 + 拥有 "Yim" 标签 → 直接凭空创建物品填入，无需背包
            if (player.isCreative() && player.getTags().contains("Yim")) {
                fillDebug(recipe, handler);
                inv.setChanged();
                return;
            }

            // ③ 从玩家背包填入 CountedIngredient → 输入格
            List<CountedIngredient> ingredients = recipe.getCountedIngredients();
            int fillSlot = 0;
            for (CountedIngredient ci : ingredients) {
                if (ci.isEmpty()) continue;
                int needed = ci.count();

                for (int invSlot = 0; invSlot < inv.getContainerSize() && needed > 0; invSlot++) {
                    ItemStack invStack = inv.getItem(invSlot);
                    if (invStack.isEmpty() || !ci.ingredient().test(invStack)) continue;

                    int canTake = Math.min(needed, invStack.getCount());
                    ItemStack toPlace = invStack.copyWithCount(canTake);
                    invStack.shrink(canTake);
                    if (invStack.isEmpty()) inv.setItem(invSlot, ItemStack.EMPTY);

                    if (fillSlot < GrandCookpotBlockEntity.SLOT_INPUT_COUNT) {
                        handler.insertItem(fillSlot++, toPlace, false);
                    }
                    needed -= canTake;
                }

                if (needed > 0 && fillSlot < GrandCookpotBlockEntity.SLOT_INPUT_COUNT) {
                    fillSlot++; // 缺料时跳过该槽
                }
            }

            // ④ 从玩家背包填入器皿（如果配方有 container 要求）
            if (!recipe.getContainer().isEmpty()) {
                for (int invSlot = 0; invSlot < inv.getContainerSize(); invSlot++) {
                    ItemStack invStack = inv.getItem(invSlot);
                    if (invStack.isEmpty() || !recipe.getContainer().test(invStack)) continue;
                    ItemStack vessel = invStack.copyWithCount(1);
                    invStack.shrink(1);
                    if (invStack.isEmpty()) inv.setItem(invSlot, ItemStack.EMPTY);
                    handler.insertItem(GrandCookpotBlockEntity.SLOT_VESSEL, vessel, false);
                    break; // 只需要一个
                }
            }

            inv.setChanged();
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 调试填充：直接凭空创建物品插入锅，不消耗背包（热量/液体槽不处理）。
     * 触发条件：创造模式 + 拥有 "Yim" 标签。
     */
    private static void fillDebug(GrandCookpotRecipe recipe,
                                   net.minecraftforge.items.IItemHandler handler) {
        List<CountedIngredient> ingredients = recipe.getCountedIngredients();
        int fillSlot = 0;
        for (CountedIngredient ci : ingredients) {
            if (ci.isEmpty()) continue;
            if (fillSlot >= GrandCookpotBlockEntity.SLOT_INPUT_COUNT) break;
            ItemStack[] items = ci.ingredient().getItems();
            if (items.length == 0) { fillSlot++; continue; }
            ItemStack toPlace = items[0].copyWithCount(ci.count());
            if (ci.nbt() != null) toPlace.setTag(ci.nbt().copy());
            handler.insertItem(fillSlot++, toPlace, false);
        }
        if (!recipe.getContainer().isEmpty()) {
            ItemStack[] vessels = recipe.getContainer().getItems();
            if (vessels.length > 0) {
                handler.insertItem(GrandCookpotBlockEntity.SLOT_VESSEL,
                        vessels[0].copyWithCount(1), false);
            }
        }
    }
}
