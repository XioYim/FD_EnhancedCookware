package com.xioyim.fdenhancedcookware;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.xioyim.fdenhancedcookware.client.ClientSetup;
import com.xioyim.fdenhancedcookware.config.CookpotConfig;
import com.xioyim.fdenhancedcookware.events.ForgeEventHandler;
import com.xioyim.fdenhancedcookware.init.ModBlockEntities;
import com.xioyim.fdenhancedcookware.init.ModBlocks;
import com.xioyim.fdenhancedcookware.init.ModMenuTypes;
import com.xioyim.fdenhancedcookware.init.ModRecipeTypes;
import com.xioyim.fdenhancedcookware.network.NetworkHandler;

@Mod(FDEnhancedCookware.MODID)
public class FDEnhancedCookware {

    public static final String MODID = "fd_enhancedcookware";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public FDEnhancedCookware() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册各 DeferredRegister
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);

        // 配置文件（COMMON 类型，游戏启动时即生成于 config/ 目录）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CookpotConfig.SPEC,
                "fd_enhancedcookware-common.toml");

        // 网络包
        NetworkHandler.register();

        // Forge 游戏事件（玩家破坏炉灶 → 清空锅热量）
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler.class);

        // 创造模式标签页
        modEventBus.addListener(FDEnhancedCookware::buildCreativeTab);

        // 客户端 Screen 注册
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener((FMLClientSetupEvent e) -> ClientSetup.register());
        }

        LOGGER.info("FD EnhancedCookware is initializing...");
    }

    private static final ResourceLocation FD_TAB_KEY = new ResourceLocation("farmersdelight", "farmersdelight");

    private static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        // 原版"功能性方块"标签页
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.GRAND_COOKPOT.get());
        }
        // 农夫乐事标签页
        if (event.getTabKey().location().equals(FD_TAB_KEY)) {
            event.accept(ModBlocks.GRAND_COOKPOT.get());
        }
    }

    public static ResourceLocation modLoc(String path) {
        return new ResourceLocation(MODID, path);
    }
}
