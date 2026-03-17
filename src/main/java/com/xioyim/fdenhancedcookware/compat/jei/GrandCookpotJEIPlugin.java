package com.xioyim.fdenhancedcookware.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.init.ModBlocks;
import com.xioyim.fdenhancedcookware.init.ModRecipeTypes;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;

import java.util.List;

@JeiPlugin
public class GrandCookpotJEIPlugin implements IModPlugin {

    public static final ResourceLocation PLUGIN_ID =
            new ResourceLocation(FDEnhancedCookware.MODID, "jei_plugin");

    @Nullable
    public static volatile IJeiRuntime jeiRuntime = null;

    @Override
    public ResourceLocation getPluginUid() { return PLUGIN_ID; }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new GrandCookpotCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        List<GrandCookpotRecipe> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.GRAND_COOKPOT.get());
        registration.addRecipes(GrandCookpotCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.GRAND_COOKPOT.get()),
                GrandCookpotCategory.RECIPE_TYPE
        );
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new GrandCookpotRecipeTransferHandler(),
                GrandCookpotCategory.RECIPE_TYPE
        );
    }

    /** 点击灰色箭头时，在 JEI 中打开高级厨锅配方列表 */
    public static void openGrandCookpotCategory() {
        if (jeiRuntime != null) {
            jeiRuntime.getRecipesGui().showTypes(List.of(GrandCookpotCategory.RECIPE_TYPE));
        }
    }
}
