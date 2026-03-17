package com.xioyim.fdenhancedcookware.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.xioyim.fdenhancedcookware.init.ModMenuTypes;
import com.xioyim.fdenhancedcookware.menu.GrandCookpotMenu;
import com.xioyim.fdenhancedcookware.network.FillGridPacket;
import com.xioyim.fdenhancedcookware.network.NetworkHandler;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;

import java.util.Optional;

/**
 * JEI 配方转移：通过 FillGridPacket 将配方材料从背包填入输入格。
 */
public class GrandCookpotRecipeTransferHandler
        implements IRecipeTransferHandler<GrandCookpotMenu, GrandCookpotRecipe> {

    @Override
    public @NotNull Class<GrandCookpotMenu> getContainerClass() {
        return GrandCookpotMenu.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Optional<MenuType<GrandCookpotMenu>> getMenuType() {
        return Optional.of((MenuType<GrandCookpotMenu>) ModMenuTypes.GRAND_COOKPOT_MENU.get());
    }

    @Override
    public @NotNull RecipeType<GrandCookpotRecipe> getRecipeType() {
        return GrandCookpotCategory.RECIPE_TYPE;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(
            @NotNull GrandCookpotMenu container,
            @NotNull GrandCookpotRecipe recipe,
            @NotNull IRecipeSlotsView recipeSlotsView,
            @NotNull Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        if (doTransfer) {
            NetworkHandler.CHANNEL.sendToServer(
                    new FillGridPacket(
                            container.getBlockEntity().getBlockPos(),
                            recipe.getId()
                    )
            );
        }
        return null; // null = 可以转移（绿色按钮）
    }
}
