package com.xioyim.fdenhancedcookware.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：玩家点击绿色箭头，请求开始烹饪。
 * 服务端调用 startCrafting(player) 以便检查玩家标签限制。
 */
public record StartCookingPacket(BlockPos pos) {

    public static void encode(StartCookingPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    public static StartCookingPacket decode(FriendlyByteBuf buf) {
        return new StartCookingPacket(buf.readBlockPos());
    }

    public static void handle(StartCookingPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(pkt.pos);
            if (be instanceof GrandCookpotBlockEntity cookpot) {
                cookpot.startCrafting(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
