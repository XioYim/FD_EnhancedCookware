package com.xioyim.fdenhancedcookware.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FDEnhancedCookware.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                StartCookingPacket.class,
                StartCookingPacket::encode,
                StartCookingPacket::decode,
                StartCookingPacket::handle
        );
        CHANNEL.registerMessage(
                id++,
                FillGridPacket.class,
                FillGridPacket::encode,
                FillGridPacket::decode,
                FillGridPacket::handle
        );
        CHANNEL.registerMessage(
                id,
                AddHeatPacket.class,
                AddHeatPacket::encode,
                AddHeatPacket::decode,
                AddHeatPacket::handle
        );
    }
}
