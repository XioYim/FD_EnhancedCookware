package com.xioyim.fdenhancedcookware.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.blockentity.GrandCookpotBlockEntity;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FDEnhancedCookware.MODID);

    public static final RegistryObject<BlockEntityType<GrandCookpotBlockEntity>> GRAND_COOKPOT =
            BLOCK_ENTITIES.register("grand_cookpot",
                    () -> BlockEntityType.Builder
                            .of(GrandCookpotBlockEntity::new, ModBlocks.GRAND_COOKPOT.get())
                            .build(null));
}
