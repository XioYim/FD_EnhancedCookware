package com.xioyim.fdenhancedcookware.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;

import java.util.function.Supplier;

public record AddHeatPacket(BlockPos pos) {

    public static void encode(AddHeatPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    public static AddHeatPacket decode(FriendlyByteBuf buf) {
        return new AddHeatPacket(buf.readBlockPos());
    }

    public static void handle(AddHeatPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(pkt.pos);
            if (be instanceof GrandCookpotBlockEntity cookpot) {
                cookpot.addHeatFromFuel(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
