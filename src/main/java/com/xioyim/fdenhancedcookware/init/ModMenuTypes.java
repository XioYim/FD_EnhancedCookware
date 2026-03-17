package com.xioyim.fdenhancedcookware.init;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.menu.GrandCookpotMenu;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FDEnhancedCookware.MODID);

    public static final RegistryObject<MenuType<GrandCookpotMenu>> GRAND_COOKPOT_MENU =
            MENU_TYPES.register("grand_cookpot_menu",
                    () -> IForgeMenuType.create(GrandCookpotMenu::new));
}
