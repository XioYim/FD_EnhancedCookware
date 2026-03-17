package com.xioyim.fdenhancedcookware.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.xioyim.fdenhancedcookware.client.screen.GrandCookpotScreen;
import com.xioyim.fdenhancedcookware.init.ModMenuTypes;

@OnlyIn(Dist.CLIENT)
public class ClientSetup {

    public static void register() {
        MenuScreens.register(ModMenuTypes.GRAND_COOKPOT_MENU.get(), GrandCookpotScreen::new);
    }
}
