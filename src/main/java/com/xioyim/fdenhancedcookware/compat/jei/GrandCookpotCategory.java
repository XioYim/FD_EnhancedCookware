package com.xioyim.fdenhancedcookware.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.init.ModBlocks;
import com.xioyim.fdenhancedcookware.recipe.CountedIngredient;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe.RequiredBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JEI 配方分类（高级厨锅）。
 * 背景裁剪自主 GUI 纹理 (x6,y14)→(x171,y72)，尺寸 165×58。
 * 所有坐标均相对于裁剪后的背景左上角，即原 GUI 坐标 - (6, 14)。
 */
public class GrandCookpotCategory implements IRecipeCategory<GrandCookpotRecipe> {

    public static final RecipeType<GrandCookpotRecipe> RECIPE_TYPE =
            RecipeType.create(FDEnhancedCookware.MODID, "grand_cookpot", GrandCookpotRecipe.class);

    // 主 GUI 纹理（与 GrandCookpotScreen 共用）
    private static final ResourceLocation BACKGROUND_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/grand_cookpot_bg.png");

    // 小图标纹理
    private static final ResourceLocation ARROW_IDLE =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/arrow_idle.png");
    private static final ResourceLocation FLAME_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/flame.png");
    private static final ResourceLocation ROARING_FLAME_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/roaring_flame.png");
    private static final ResourceLocation HOT_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/hot.png");
    private static final ResourceLocation FLUID_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/fluid.png");

    // ── JEI 背景尺寸 ─────────────────────────────────────────────────────────
    // 裁剪范围：主 GUI (6,14) → (171,72) = 165×58
    public static final int WIDTH  = 165;
    public static final int HEIGHT = 58;

    // ── 偏移量（主 GUI → JEI 坐标）─────────────────────────────────────────
    private static final int OX = 6;   // offsetX
    private static final int OY = 14;  // offsetY

    // ── JEI 坐标（= 主 GUI 坐标 − offset） ────────────────────────────────────
    // 主 GUI: GRID_START (8,17), slots 4×3
    private static final int GRID_X   = 8  - OX;  // = 2
    private static final int GRID_Y   = 17 - OY;  // = 3
    // 主 GUI: ARROW (159,14), 9×9
    private static final int ARROW_X  = 159 - OX; // = 153
    private static final int ARROW_Y  = 14  - OY; // = 0
    private static final int ARROW_W  = 9;
    private static final int ARROW_H  = 9;
    // 主 GUI: OUTPUT (97,27)
    private static final int OUTPUT_X = 97  - OX; // = 91
    private static final int OUTPUT_Y = 27  - OY; // = 13
    // 主 GUI: VESSEL (97,53)
    private static final int VESSEL_X = 97  - OX; // = 91
    private static final int VESSEL_Y = 53  - OY; // = 39
    // 主 GUI: FLAME (129,29), 17×11
    private static final int FLAME_X  = 129 - OX; // = 123
    private static final int FLAME_Y  = 29  - OY; // = 15
    private static final int FLAME_W  = 17;
    private static final int FLAME_H  = 11;
    // 主 GUI: HEAT_BAR (124,42), 27×4
    private static final int HEAT_X   = 124 - OX; // = 118
    private static final int HEAT_Y   = 42  - OY; // = 28
    private static final int HEAT_W   = 27;
    private static final int HEAT_H   = 4;
    // 主 GUI: FLUID (158,25), 11×45
    private static final int FLUID_X  = 158 - OX; // = 152
    private static final int FLUID_Y  = 25  - OY; // = 11
    private static final int FLUID_W  = 11;
    private static final int FLUID_H  = 45;

    private final IDrawable icon;
    private final IDrawable background;

    public GrandCookpotCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModBlocks.GRAND_COOKPOT.get()));
        // 裁剪主 GUI 纹理作为背景，纹理视为 256×256（Minecraft GUI 标准）
        this.background = guiHelper.drawableBuilder(BACKGROUND_TEX, OX, OY, WIDTH, HEIGHT)
                .setTextureSize(256, 256)
                .build();
    }

    @Override public @NotNull RecipeType<GrandCookpotRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public @NotNull Component getTitle() {
        return Component.translatable("jei.fd_enhancedcookware.grand_cookpot");
    }
    @Override public @NotNull IDrawable getIcon()  { return icon; }
    @Override public int getWidth()                { return WIDTH; }
    @Override public int getHeight()               { return HEIGHT; }
    @Override @SuppressWarnings("removal") public @NotNull IDrawable getBackground() { return background; }

    // ── 槽位注册 ─────────────────────────────────────────────────────────────

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder,
                          @NotNull GrandCookpotRecipe recipe,
                          @NotNull IFocusGroup focuses) {

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(recipe.getResult());

        if (!recipe.getContainer().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, VESSEL_X, VESSEL_Y)
                    .addIngredients(recipe.getContainer());
        }

        List<CountedIngredient> ingredients = recipe.getCountedIngredients();
        for (int idx = 0; idx < 12; idx++) {
            int row = idx / 4;
            int col = idx % 4;
            var slot = builder.addSlot(
                    RecipeIngredientRole.INPUT,
                    GRID_X + col * 18,
                    GRID_Y + row * 18);

            if (idx < ingredients.size()) {
                CountedIngredient ci = ingredients.get(idx);
                if (!ci.isEmpty()) {
                    List<ItemStack> stacks = Arrays.stream(ci.ingredient().getItems())
                            .map(s -> {
                                ItemStack copy = s.copy();
                                copy.setCount(ci.count());
                                if (ci.nbt() != null) copy.setTag(ci.nbt().copy());
                                return copy;
                            }).toList();
                    slot.addItemStacks(stacks);
                }
            }
        }
        // 注意：不添加燃料槽和液体输入槽（不支持 JEI 转移）
    }

    // ── 绘制 ──────────────────────────────────────────────────────────────────

    @Override
    public void draw(@NotNull GrandCookpotRecipe recipe,
                     @NotNull IRecipeSlotsView slotsView,
                     @NotNull GuiGraphics graphics,
                     double mouseX, double mouseY) {

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ── 液体槽渲染（在边框下面先画）────────────────────────────────────
        drawFluidTank(recipe, graphics);

        // ── 液体边框叠加 ────────────────────────────────────────────────────
        graphics.blit(FLUID_TEX, FLUID_X, FLUID_Y, 0, 0, FLUID_W, FLUID_H, FLUID_W, FLUID_H);

        // ── 火焰图标（始终显示；有热量消耗或 require_roaring 时使用剧烈火焰贴图）
        ResourceLocation flameTex = (recipe.isRequireRoaring() || recipe.getHeatConsumption() > 0)
                ? ROARING_FLAME_TEX : FLAME_TEX;
        graphics.blit(flameTex, FLAME_X, FLAME_Y, 0, 0, FLAME_W, FLAME_H, FLAME_W, FLAME_H);

        // ── 热量进度条（根据 heatConsumption 比例填充）─────────────────────
        if (recipe.getHeatConsumption() > 0) {
            int fillW = Math.max(1, Math.min(HEAT_W,
                    (recipe.getHeatConsumption() * HEAT_W) / 100));
            graphics.blit(HOT_TEX, HEAT_X, HEAT_Y, 0, 0, fillW, HEAT_H, HEAT_W, HEAT_H);
        }

        // ── 灰色状态按钮 ────────────────────────────────────────────────────
        graphics.blit(ARROW_IDLE, ARROW_X, ARROW_Y, 0, 0, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
    }

    // ── 液体槽流体渲染 ───────────────────────────────────────────────────────

    private void drawFluidTank(GrandCookpotRecipe recipe, GuiGraphics graphics) {
        if (recipe.getRequiredFluid() == null || recipe.getFluidConsumption() <= 0) return;

        Fluid fluid = BuiltInRegistries.FLUID.get(recipe.getRequiredFluid());
        if (fluid == null || fluid == Fluids.EMPTY) return;

        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluidType());
        ResourceLocation stillTex = ext.getStillTexture();
        if (stillTex == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(stillTex);

        int tint = ext.getTintColor();
        float r = ((tint >> 16) & 0xFF) / 255.0f;
        float g = ((tint >> 8)  & 0xFF) / 255.0f;
        float b = (tint         & 0xFF) / 255.0f;
        float a = ((tint >> 24) & 0xFF) / 255.0f;
        if (a == 0f) a = 1.0f;

        // 内部区域：9×43，从 (FLUID_X+1, FLUID_Y+1) 开始
        int innerX = FLUID_X + 1;
        int innerY = FLUID_Y + 1;
        int innerW = 9;
        int innerH = 43;
        int fillH  = Math.max(1, Math.min(innerH,
                (recipe.getFluidConsumption() * innerH) / 1000));
        int fillTopY = innerY + (innerH - fillH);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);

        graphics.flush();

        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        int drawn = 0;
        while (drawn < fillH) {
            int segH = Math.min(16, fillH - drawn);
            float u0 = sprite.getU(0);
            float u1 = sprite.getU((innerW * 16f) / 16f);
            float v0 = sprite.getV(0);
            float v1 = sprite.getV((segH  * 16f) / 16f);
            int sx = innerX;
            int sy = fillTopY + drawn;
            buf.vertex(matrix, sx,          sy + segH, 0).uv(u0, v1).endVertex();
            buf.vertex(matrix, sx + innerW, sy + segH, 0).uv(u1, v1).endVertex();
            buf.vertex(matrix, sx + innerW, sy,        0).uv(u1, v0).endVertex();
            buf.vertex(matrix, sx,          sy,        0).uv(u0, v0).endVertex();
            drawn += segH;
        }
        tesselator.end();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ── 悬停提示 ─────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public @NotNull List<Component> getTooltipStrings(@NotNull GrandCookpotRecipe recipe,
                                                       @NotNull IRecipeSlotsView slotsView,
                                                       double mouseX, double mouseY) {
        // 火焰图标悬停（有热量消耗或 require_roaring 时提示）
        if (inRegion(mouseX, mouseY, FLAME_X, FLAME_Y, FLAME_W, FLAME_H)) {
            if (recipe.isRequireRoaring() || recipe.getHeatConsumption() > 0) {
                return List.of(Component.translatable("jei.fd_enhancedcookware.tooltip_roaring_hover")
                        .withStyle(ChatFormatting.RED));
            }
            return List.of();
        }

        // 热量条悬停
        if (inRegion(mouseX, mouseY, HEAT_X, HEAT_Y, HEAT_W, HEAT_H)) {
            if (recipe.getHeatConsumption() > 0) {
                return List.of(Component.translatable("jei.fd_enhancedcookware.tooltip_heat_bar",
                        recipe.getHeatConsumption()).withStyle(ChatFormatting.GOLD));
            }
            return List.of();
        }

        // 液体槽悬停
        if (inRegion(mouseX, mouseY, FLUID_X, FLUID_Y, FLUID_W, FLUID_H)) {
            if (recipe.getRequiredFluid() != null && recipe.getFluidConsumption() > 0) {
                String fname = fluidDisplayName(recipe.getRequiredFluid());
                return List.of(Component.translatable("jei.fd_enhancedcookware.tooltip_fluid_hover",
                        fname, recipe.getFluidConsumption()).withStyle(ChatFormatting.AQUA));
            }
            return List.of();
        }

        // 灰色按钮悬停：完整配方需求
        if (!inRegion(mouseX, mouseY, ARROW_X, ARROW_Y, ARROW_W, ARROW_H)) {
            return List.of();
        }

        List<Component> tips = new ArrayList<>();

        // 配方标题（首行，金色）
        String titleText = recipe.getRecipeTitle();
        if (titleText == null || titleText.isEmpty()) {
            tips.add(Component.translatable("tooltip.fd_enhancedcookware.recipe_title")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tips.add(Component.literal(titleText).withStyle(ChatFormatting.GOLD));
        }

        // 所需时间
        int sec = (int) Math.ceil(recipe.getCookingTime() / 20.0);
        tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_time", sec)
                .withStyle(ChatFormatting.WHITE));

        // 获取经验
        tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_exp",
                String.format("%.1f", recipe.getExperience()))
                .withStyle(ChatFormatting.WHITE));

        // 所需热量
        if (recipe.getHeatConsumption() > 0) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_heat",
                    recipe.getHeatConsumption()).withStyle(ChatFormatting.GOLD));
        } else {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_heat_none")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 所需液体
        if (recipe.getRequiredFluid() != null && recipe.getFluidConsumption() > 0) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_fluid",
                    fluidDisplayName(recipe.getRequiredFluid()),
                    recipe.getFluidConsumption()).withStyle(ChatFormatting.AQUA));
        } else {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_fluid_none")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 所需权限（标签）
        if (recipe.isShowTagsInJei() && recipe.isRequireTags()
                && !recipe.getRequiredTags().isEmpty()) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_tags",
                    String.join(", ", recipe.getRequiredTags()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_tags_none")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 所需炉灶
        if (recipe.isRequireBlock() && !recipe.getRequiredBlocks().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (RequiredBlock rb : recipe.getRequiredBlocks()) {
                names.add(blockDisplayName(rb.getBlockId()));
            }
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_stove",
                    String.join(" / ", names)).withStyle(ChatFormatting.YELLOW));
        } else {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_stove_any")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 所需火焰（有热量消耗或 require_roaring 均视为需要高燃）
        if (recipe.isRequireRoaring() || recipe.getHeatConsumption() > 0) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_flame_roaring")
                    .withStyle(ChatFormatting.RED));
        } else {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_flame_normal")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 所需特殊火焰
        boolean hasSoul = false, hasNether = false;
        for (RequiredBlock rb : recipe.getRequiredBlocks()) {
            String soulVal = rb.getRequiredProperties().get("soul");
            if ("true".equals(soulVal))  hasSoul   = true;
            if ("false".equals(soulVal)) hasNether = true;
        }
        if (hasSoul && hasNether) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_special_both")
                    .withStyle(ChatFormatting.BLUE));
        } else if (hasSoul) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_special_soul")
                    .withStyle(ChatFormatting.BLUE));
        } else if (hasNether) {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_special_nether")
                    .withStyle(ChatFormatting.RED));
        } else {
            tips.add(Component.translatable("jei.fd_enhancedcookware.tooltip_req_special_none")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 分隔线 + 配方提示
        if (!recipe.getJeiDescription().isEmpty()) {
            tips.add(Component.translatable("tooltip.fd_enhancedcookware.separator").withStyle(ChatFormatting.DARK_GRAY));
            for (String line : recipe.getJeiDescription()) {
                tips.add(Component.literal(line));
            }
        }

        return tips;
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────────

    private static boolean inRegion(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String blockDisplayName(ResourceLocation rl) {
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) {
            net.minecraft.world.item.Item item = block.asItem();
            if (item != Items.AIR) {
                return new ItemStack(item).getHoverName().getString();
            }
        }
        String path = rl.getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String fluidDisplayName(ResourceLocation rl) {
        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        if (fluid != null && fluid != Fluids.EMPTY) {
            return fluid.getFluidType().getDescription().getString();
        }
        return rl.getPath().replace('_', ' ');
    }
}
