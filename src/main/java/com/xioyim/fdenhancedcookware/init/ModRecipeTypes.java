package com.xioyim.fdenhancedcookware.init;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;

public class ModRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, FDEnhancedCookware.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, FDEnhancedCookware.MODID);

    public static final RegistryObject<RecipeType<GrandCookpotRecipe>> GRAND_COOKPOT =
            RECIPE_TYPES.register("grand_cookpot",
                    () -> RecipeType.simple(new net.minecraft.resources.ResourceLocation(
                            FDEnhancedCookware.MODID, "grand_cookpot")));

    public static final RegistryObject<RecipeSerializer<GrandCookpotRecipe>> GRAND_COOKPOT_SERIALIZER =
            RECIPE_SERIALIZERS.register("grand_cookpot",
                    GrandCookpotRecipe.Serializer::new);
}
