package com.xioyim.fdenhancedcookware.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;
import com.xioyim.fdenhancedcookware.config.CookpotConfig;
import com.xioyim.fdenhancedcookware.init.ModBlocks;

public class ForgeEventHandler {

    /**
     * 当玩家破坏下界炉灶时，立即清空正上方高级厨锅的热量。
     * 仅响应玩家主动破坏（BlockEvent.BreakEvent），爆炸/活塞等不触发。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Block broken = event.getState().getBlock();
        Block netherStove = ForgeRegistries.BLOCKS.getValue(GrandCookpotBlockEntity.NETHER_STOVE_ID);
        if (netherStove == null || broken != netherStove) return;

        Level level = (Level) event.getLevel();
        BlockPos above = event.getPos().above();
        if (level.getBlockEntity(above) instanceof GrandCookpotBlockEntity be) {
            be.clearExtraHeat();
        }
    }

    /**
     * 当高级厨锅下方的热源方块被破坏（任何原因，包括爆炸/活塞）时，
     * 同样通知上方的锅（若需要区分破坏来源可在此扩展）。
     * 目前仅记录—主要逻辑由 serverTick 的 isHeated() 检测负责。
     */
    @SubscribeEvent
    public static void onNeighborChanged(BlockEvent.NeighborNotifyEvent event) {
        // 保留扩展接口，当前由 serverTick 处理热源丢失状态转换
    }

    private static final String HINT_TAG = "fd_enhancedcookware.cookpot_hint_shown";

    /**
     * 玩家首次将高级厨锅拾取到物品栏时，发送配置文件中定义的多行提示消息。
     * 使用玩家记分板标签防止重复发送。
     */
    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (player.getTags().contains(HINT_TAG)) return;

        ItemStack stack = event.getStack();
        if (!stack.is(ModBlocks.GRAND_COOKPOT.get().asItem())) return;

        java.util.List<String> lines = CookpotConfig.getFirstPickupMessages();
        if (lines.isEmpty()) return;

        for (String line : lines) {
            serverPlayer.sendSystemMessage(Component.literal(line));
        }
        player.addTag(HINT_TAG);
    }
}
