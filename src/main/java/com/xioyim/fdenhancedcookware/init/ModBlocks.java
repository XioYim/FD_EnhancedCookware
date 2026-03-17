package com.xioyim.fdenhancedcookware.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.block.GrandCookpotBlock;

import java.util.function.Supplier;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FDEnhancedCookware.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FDEnhancedCookware.MODID);

    public static final RegistryObject<Block> GRAND_COOKPOT = registerBlock(
            "grand_cookpot",
            () -> new GrandCookpotBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .requiresCorrectToolForDrops()
                            .strength(3.5F, Float.MAX_VALUE)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );

    // ---- 工具方法 ----

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> b = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }
}
