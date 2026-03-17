package com.xioyim.fdenhancedcookware.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;
import com.xioyim.fdenhancedcookware.blockentity.CookingState;
import com.xioyim.fdenhancedcookware.compat.jei.GrandCookpotJEIPlugin;
import com.xioyim.fdenhancedcookware.init.ModRecipeTypes;
import com.xioyim.fdenhancedcookware.menu.GrandCookpotMenu;
import com.xioyim.fdenhancedcookware.config.CookpotConfig;
import com.xioyim.fdenhancedcookware.network.AddHeatPacket;
import com.xioyim.fdenhancedcookware.network.NetworkHandler;
import com.xioyim.fdenhancedcookware.network.StartCookingPacket;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe;
import com.xioyim.fdenhancedcookware.recipe.GrandCookpotRecipe.RequiredBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GrandCookpotScreen extends AbstractContainerScreen<GrandCookpotMenu> {

    // ── 纹理路径 ──────────────────────────────────────────────────────────────
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/grand_cookpot_bg.png");

    private static final ResourceLocation ARROW_HEAT     =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/arrow_heat.png");
    private static final ResourceLocation ARROW_IDLE     =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/arrow_idle.png");
    @SuppressWarnings("unused") // reserved for future use / JEI
    private static final ResourceLocation ARROW_READY    =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/arrow_ready.png");
    private static final ResourceLocation ARROW_CRAFTING =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/arrow_crafting.png");
    private static final ResourceLocation ARROW_DONE     =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/arrow_done.png");

    /** 普通燃烧火焰（17×11）*/
    private static final ResourceLocation FLAME_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/flame.png");
    /** 剧烈燃烧火焰（17×11）*/
    private static final ResourceLocation ROARING_FLAME_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/roaring_flame.png");
    /** 热量进度条底图（27×4）*/
    private static final ResourceLocation HOT_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/hot.png");
    /** 液体槽边框叠加（11×45，中间透明）*/
    private static final ResourceLocation FLUID_TEX =
            new ResourceLocation(FDEnhancedCookware.MODID, "textures/gui/fluid.png");

    /** arrow_crafting.png 动画帧数与每帧持续 tick */
    private static final int FRAME_COUNT = 12;
    private static final int FRAME_TICKS = 3;

    public GrandCookpotScreen(GrandCookpotMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = GrandCookpotMenu.GUI_WIDTH;
        this.imageHeight = GrandCookpotMenu.GUI_HEIGHT;
        this.inventoryLabelX = GrandCookpotMenu.PLAYER_INV_X;
        this.inventoryLabelY = GrandCookpotMenu.PLAYER_INV_Y - 10;
    }

    // ===================== 渲染 =====================

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // 液体渲染（在边框下方先画）
        renderFluidTank(graphics);

        // 液体边框叠加（11×45，中间透明，覆盖在液体上方）
        graphics.blit(FLUID_TEX,
                leftPos + GrandCookpotMenu.FLUID_X,
                topPos  + GrandCookpotMenu.FLUID_Y,
                0, 0, GrandCookpotMenu.FLUID_W, GrandCookpotMenu.FLUID_H,
                GrandCookpotMenu.FLUID_W, GrandCookpotMenu.FLUID_H);

        // 状态箭头按钮
        renderArrow(graphics, leftPos, topPos);

        // 火苗始终显示（熄灭/普通/剧烈三态），热量条仅在有余热时显示
        renderFlameIcon(graphics);
        if (menu.getExtraHeat() > 0) {
            renderHeatBar(graphics);
        }
    }

    // ── 五态状态按钮 ─────────────────────────────────────────────────────────

    private void renderArrow(GuiGraphics graphics, int x, int y) {
        int ax = x + GrandCookpotMenu.ARROW_X;
        int ay = y + GrandCookpotMenu.ARROW_Y;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        switch (menu.getCookingState()) {
            case CRAFTING -> {
                long gameTime = Minecraft.getInstance().level != null
                        ? Minecraft.getInstance().level.getGameTime() : 0L;
                int frame = (int) ((gameTime / FRAME_TICKS) % FRAME_COUNT);
                int vOffset = frame * GrandCookpotMenu.ARROW_H;
                graphics.blit(ARROW_CRAFTING, ax, ay, 0, vOffset,
                        GrandCookpotMenu.ARROW_W, GrandCookpotMenu.ARROW_H,
                        GrandCookpotMenu.ARROW_W, FRAME_COUNT * GrandCookpotMenu.ARROW_H);
            }
            // NORMAL 用绿色按钮，WRONG_STOVE/MISSING_TAGS/READY 用红色按钮
            case NORMAL                            -> blitTexture(graphics, ARROW_READY, ax, ay);
            case WRONG_STOVE, MISSING_TAGS, READY  -> blitTexture(graphics, ARROW_DONE,  ax, ay);
            case NO_HEAT                                   -> blitTexture(graphics, ARROW_HEAT, ax, ay);
            default                                        -> blitTexture(graphics, ARROW_IDLE, ax, ay);
        }
    }

    /** 渲染固定尺寸按钮纹理（与 ARROW_W/H 保持一致） */
    private static void blitTexture(GuiGraphics g, ResourceLocation tex, int x, int y) {
        g.blit(tex, x, y, 0, 0,
                GrandCookpotMenu.ARROW_W, GrandCookpotMenu.ARROW_H,
                GrandCookpotMenu.ARROW_W, GrandCookpotMenu.ARROW_H);
    }

    // ── 火焰图标（17×11）────────────────────────────────────────────────
    // 无热源（NO_HEAT）→ 不渲染（背景贴图本身已含熄灭状态）
    // 有热源 + 无余热  → 普通燃烧（flame）
    // 有热源 + 有余热  → 剧烈燃烧（roaring_flame）

    private void renderFlameIcon(GuiGraphics graphics) {
        boolean heated = menu.getCookingState() != CookingState.NO_HEAT;
        if (!heated) return; // 背景已体现熄灭状态，无需叠加

        boolean hasHeat = menu.getExtraHeat() > 0;
        ResourceLocation tex = hasHeat ? ROARING_FLAME_TEX : FLAME_TEX;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(tex,
                leftPos + GrandCookpotMenu.FLAME_X,
                topPos  + GrandCookpotMenu.FLAME_Y,
                0, 0,
                GrandCookpotMenu.FLAME_W, GrandCookpotMenu.FLAME_H,
                GrandCookpotMenu.FLAME_W, GrandCookpotMenu.FLAME_H);
    }

    // ── 热量进度条（27×4，仅在 extraHeat>0 时显示，从左裁剪） ────────────────

    private void renderHeatBar(GuiGraphics graphics) {
        int heat = menu.getExtraHeat();
        if (heat <= 0) return;
        int filledWidth = Math.max(1, (heat * GrandCookpotMenu.HEAT_BAR_W) / 120);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(HOT_TEX,
                leftPos + GrandCookpotMenu.HEAT_BAR_X,
                topPos  + GrandCookpotMenu.HEAT_BAR_Y,
                0, 0,
                filledWidth, GrandCookpotMenu.HEAT_BAR_H,
                GrandCookpotMenu.HEAT_BAR_W, GrandCookpotMenu.HEAT_BAR_H);
    }

    // ── 液体槽内部流体渲染（9×43 内部区域） ─────────────────────────────────

    private void renderFluidTank(GuiGraphics graphics) {
        Fluid fluid = menu.getFluid();
        int amount  = menu.getFluidAmount();
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) return;

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
        if (a == 0f) a = 1.0f; // 部分 mod 液体 alpha=0 代表完全不透明

        // 内部区域：9×43，从 (FLUID_X+1, FLUID_Y+1) 开始（1px 边框）
        int innerX = leftPos + GrandCookpotMenu.FLUID_X + 1;
        int innerY = topPos  + GrandCookpotMenu.FLUID_Y + 1;
        int innerW = 9;
        int innerH = 43;
        int fillH  = Math.max(1, Math.min(innerH, (amount * innerH) / 1000));
        // 从底部向上填充
        int fillTopY = innerY + (innerH - fillH);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);

        // flush GuiGraphics 批处理，避免 GL 状态冲突
        graphics.flush();

        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        // 分段绘制（每段最多 16 像素高，与流体纹理原始尺寸对齐）
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

    // ===================== 标签 & 提示 =====================

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title,
                this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        int rx = mouseX - this.leftPos;
        int ry = mouseY - this.topPos;

        // 箭头按钮悬停提示
        if (isHovering(GrandCookpotMenu.ARROW_X, GrandCookpotMenu.ARROW_Y,
                GrandCookpotMenu.ARROW_W, GrandCookpotMenu.ARROW_H, mouseX, mouseY)) {
            renderArrowTooltip(graphics, rx, ry);
        }

        // 火焰图标悬停提示（始终可悬停，状态文本由 renderFlameTooltip 区分）
        if (isHovering(GrandCookpotMenu.FLAME_X, GrandCookpotMenu.FLAME_Y,
                GrandCookpotMenu.FLAME_W, GrandCookpotMenu.FLAME_H, mouseX, mouseY)) {
            renderFlameTooltip(graphics, rx, ry);
        }

        // 热量进度条悬停提示（始终可悬停）
        if (isHovering(GrandCookpotMenu.HEAT_BAR_X, GrandCookpotMenu.HEAT_BAR_Y,
                GrandCookpotMenu.HEAT_BAR_W, GrandCookpotMenu.HEAT_BAR_H, mouseX, mouseY)) {
            renderHeatBarTooltip(graphics, rx, ry);
        }

        // 液体显示悬停提示（有液体时显示）
        if (menu.getFluidAmount() > 0 && isHovering(
                GrandCookpotMenu.FLUID_X, GrandCookpotMenu.FLUID_Y,
                GrandCookpotMenu.FLUID_W, GrandCookpotMenu.FLUID_H, mouseX, mouseY)) {
            renderFluidTooltip(graphics, rx, ry);
        }
    }

    // ── 箭头按钮提示 ─────────────────────────────────────────────────────────

    private void renderArrowTooltip(GuiGraphics graphics, int rx, int ry) {
        CookingState state = menu.getCookingState();
        List<Component> lines = new ArrayList<>();

        switch (state) {
            case NO_HEAT -> lines.add(
                    Component.translatable("tooltip.fd_enhancedcookware.no_heat")
                            .withStyle(ChatFormatting.GOLD));

            case BLOCKED -> lines.add(
                    Component.translatable("tooltip.fd_enhancedcookware.view_in_jei")
                            .withStyle(ChatFormatting.GRAY));

            case NORMAL, WRONG_STOVE, MISSING_TAGS -> {
                Optional<GrandCookpotRecipe> recipeOpt = findClientRecipe();
                if (recipeOpt.isPresent()) {
                    lines.addAll(buildRequirementsTooltip(recipeOpt.get(), state == CookingState.NORMAL, state));
                } else {
                    lines.add(Component.translatable("tooltip.fd_enhancedcookware.no_recipe")
                            .withStyle(ChatFormatting.GRAY));
                }
            }

            case CRAFTING -> {
                // 标题行（CRAFTING 时无法查找配方，使用默认标题）
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.recipe_title")
                        .withStyle(ChatFormatting.GOLD));
                // 物品名 + NBT + 分隔 + 剩余时间
                ItemStack preview = menu.slots.get(GrandCookpotMenu.PREVIEW_SLOT_IDX).getItem();
                if (!preview.isEmpty()) {
                    List<Component> itemLines = preview.getTooltipLines(
                            Minecraft.getInstance().player, TooltipFlag.Default.NORMAL);
                    if (!itemLines.isEmpty()) {
                        lines.add(itemLines.get(0).copy().withStyle(ChatFormatting.WHITE));
                        for (int i = 1; i < itemLines.size(); i++) {
                            lines.add(itemLines.get(i).copy().withStyle(ChatFormatting.DARK_GRAY));
                        }
                    }
                }
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.separator").withStyle(ChatFormatting.DARK_GRAY));
                int remain = (int) Math.ceil((menu.getMaxProgress() - menu.getProgress()) / 20.0);
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.time_remaining_colon", remain)
                        .withStyle(ChatFormatting.YELLOW));
            }

            case READY -> lines.add(
                    Component.translatable("tooltip.fd_enhancedcookware.take_output")
                            .withStyle(ChatFormatting.RED));
        }

        if (!lines.isEmpty()) {
            graphics.renderTooltip(this.font, lines, Optional.<TooltipComponent>empty(), rx, ry);
        }
    }

    // ── 配方需求提示（NORMAL/WRONG_STOVE/MISSING_TAGS 共用） ─────────────────
    //   isNormal=true  → 全部满足，只显示"点击即可制作"
    //   isNormal=false → 按顺序仅显示未满足的条件（红色），全部满足后显示绿色提示

    private List<Component> buildRequirementsTooltip(GrandCookpotRecipe recipe,
                                                      boolean isNormal,
                                                      CookingState state) {
        List<Component> lines = new ArrayList<>();

        // ── 配方标题（首行，金色）────────────────────────────────────────────
        String titleText = recipe.getRecipeTitle();
        if (titleText == null || titleText.isEmpty()) {
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.recipe_title")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.literal(titleText).withStyle(ChatFormatting.GOLD));
        }

        // ── 物品名 + NBT 描述行 ───────────────────────────────────────────────
        ItemStack preview = menu.slots.get(GrandCookpotMenu.PREVIEW_SLOT_IDX).getItem();
        if (!preview.isEmpty()) {
            List<Component> itemLines = preview.getTooltipLines(
                    Minecraft.getInstance().player, TooltipFlag.Default.NORMAL);
            if (!itemLines.isEmpty()) {
                lines.add(itemLines.get(0).copy().withStyle(ChatFormatting.WHITE));
                for (int i = 1; i < itemLines.size(); i++) {
                    lines.add(itemLines.get(i).copy().withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }

        lines.add(Component.translatable("tooltip.fd_enhancedcookware.separator")
                .withStyle(ChatFormatting.DARK_GRAY));

        // ── 所需时间 + 获取经验（始终显示，在分隔线之后） ─────────────────────
        int seconds = (int) Math.ceil(recipe.getCookingTime() / 20.0);
        lines.add(Component.translatable("tooltip.fd_enhancedcookware.cooking_time", seconds)
                .withStyle(ChatFormatting.GRAY));
        if (recipe.getExperience() > 0) {
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.experience_gain",
                    String.format("%.1f", recipe.getExperience())).withStyle(ChatFormatting.GRAY));
        }

        lines.add(Component.translatable("tooltip.fd_enhancedcookware.separator")
                .withStyle(ChatFormatting.DARK_GRAY));

        if (isNormal) {
            // 全部条件满足
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.click_to_start")
                    .withStyle(ChatFormatting.GREEN));
            return lines;
        }

        // ── 仅显示未满足的条件（红色，按顺序） ────────────────────────────

        Player player = Minecraft.getInstance().player;

        // 1. 所需热量
        if (recipe.getHeatConsumption() > 0
                && menu.getExtraHeat() < recipe.getHeatConsumption()) {
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_heat",
                    recipe.getHeatConsumption()).withStyle(ChatFormatting.RED));
        }

        // 2. 所需液体
        if (recipe.getRequiredFluid() != null && recipe.getFluidConsumption() > 0) {
            Fluid needed  = BuiltInRegistries.FLUID.get(recipe.getRequiredFluid());
            Fluid current = menu.getFluid();
            boolean fluidOk = needed != null && needed != Fluids.EMPTY
                    && current != null && current != Fluids.EMPTY
                    && current.isSame(needed)
                    && menu.getFluidAmount() >= recipe.getFluidConsumption();
            if (!fluidOk) {
                String fluidName = fluidDisplayName(needed);
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_fluid",
                        fluidName, recipe.getFluidConsumption()).withStyle(ChatFormatting.RED));
            }
        }

        // 3. 所需权限（玩家标签）
        if (recipe.isRequireTags() && !recipe.getRequiredTags().isEmpty() && player != null) {
            boolean tagsOk = recipe.getRequiredTags().stream()
                    .allMatch(t -> player.getTags().contains(t));
            if (!tagsOk) {
                String tagList = String.join(", ", recipe.getRequiredTags());
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_tags", tagList)
                        .withStyle(ChatFormatting.RED));
            }
        }

        // 4. 所需炉灶（服务端已判定为 WRONG_STOVE 时显示）
        if (state == CookingState.WRONG_STOVE
                && recipe.isRequireBlock() && !recipe.getRequiredBlocks().isEmpty()) {
            List<String> stoveNames = new ArrayList<>();
            for (RequiredBlock rb : recipe.getRequiredBlocks()) {
                stoveNames.add(blockDisplayName(rb.getBlockId()));
            }
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_stove",
                    String.join(" / ", stoveNames)).withStyle(ChatFormatting.RED));
        }

        // 5. 所需火焰（高燃：extraHeat 必须 > 0）
        if ((recipe.isRequireRoaring() || recipe.getHeatConsumption() > 0)
                && menu.getExtraHeat() <= 0) {
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_flame_roaring")
                    .withStyle(ChatFormatting.RED));
        }

        // 6. 所需特殊火焰（soul 属性约束，仅在 WRONG_STOVE 时显示）
        if (state == CookingState.WRONG_STOVE) {
            boolean hasSoul = false, hasNether = false;
            for (RequiredBlock rb : recipe.getRequiredBlocks()) {
                String sv = rb.getRequiredProperties().get("soul");
                if ("true".equals(sv))  hasSoul   = true;
                if ("false".equals(sv)) hasNether = true;
            }
            if (hasSoul && hasNether) {
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_special_both")
                        .withStyle(ChatFormatting.RED));
            } else if (hasSoul) {
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_special_soul")
                        .withStyle(ChatFormatting.RED));
            } else if (hasNether) {
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_special_nether")
                        .withStyle(ChatFormatting.RED));
            }
        }

        // 兜底：若客户端所有检查都通过，但服务端仍非 NORMAL（信息未同步等情况）
        if (lines.size() <= 2) { // 只有物品名+分隔线
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.req_not_met")
                    .withStyle(ChatFormatting.RED));
        }

        return lines;
    }

    // ── 客户端配方查找（通过 RecipeManager，配方已由服务端同步） ─────────────

    private Optional<GrandCookpotRecipe> findClientRecipe() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return Optional.empty();
        ClientContainerView view = new ClientContainerView();
        return level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.GRAND_COOKPOT.get())
                .stream()
                .filter(r -> r.matches(view, level))
                .findFirst();
    }

    // ── 辅助方法：方块展示名称 ──────────────────────────────────────────────

    private String blockDisplayName(net.minecraft.resources.ResourceLocation blockId) {
        net.minecraft.world.level.block.Block block = ForgeRegistries.BLOCKS.getValue(blockId);
        if (block != null) {
            net.minecraft.world.item.Item item = block.asItem();
            if (item != Items.AIR) {
                return new ItemStack(item).getHoverName().getString();
            }
        }
        // 兜底：大写路径名
        String path = blockId.getPath();
        String formatted = path.replace('_', ' ');
        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    // ── 辅助方法：液体展示名称 ──────────────────────────────────────────────

    private String fluidDisplayName(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return "?";
        return fluid.getFluidType().getDescription().getString();
    }

    // ── 火焰图标提示（熄灭/普通/剧烈） ──────────────────────────────────────

    private void renderFlameTooltip(GuiGraphics graphics, int rx, int ry) {
        boolean heated = menu.getCookingState() != CookingState.NO_HEAT;
        boolean hasHeat = menu.getExtraHeat() > 0;
        List<Component> lines = new ArrayList<>();

        if (!heated) {
            // 熄灭状态
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.flame_off")
                    .withStyle(ChatFormatting.GRAY));
            if (hasHeat) {
                lines.add(Component.translatable("tooltip.fd_enhancedcookware.heat_value",
                        menu.getExtraHeat()).withStyle(ChatFormatting.GOLD));
            }
        } else if (hasHeat) {
            // 剧烈燃烧
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.roaring_flame")
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.heat_value",
                    menu.getExtraHeat()).withStyle(ChatFormatting.GOLD));
        } else {
            // 普通燃烧
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.burning_flame")
                    .withStyle(ChatFormatting.YELLOW));
        }

        graphics.renderTooltip(this.font, lines, Optional.<TooltipComponent>empty(), rx, ry);
    }

    // ── 热量进度条提示 ────────────────────────────────────────────────────────

    private void renderHeatBarTooltip(GuiGraphics graphics, int rx, int ry) {
        int heat = menu.getExtraHeat();
        List<Component> lines = new ArrayList<>();
        if (menu.isInfiniteHeat()) {
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.infinite_heat")
                    .withStyle(ChatFormatting.RED));
        }
        lines.add(Component.translatable("tooltip.fd_enhancedcookware.heat_current", heat)
                .withStyle(ChatFormatting.GOLD));
        // 仅当燃料槽有可添加热量的物品时才显示"点击增加热量"
        ItemStack fuelStack = menu.slots.get(GrandCookpotMenu.FUEL_SLOT).getItem();
        if (!fuelStack.isEmpty() && CookpotConfig.getFuelHeat(fuelStack.getItem()) > 0
                && heat < 120) {
            lines.add(Component.translatable("tooltip.fd_enhancedcookware.click_add_heat")
                    .withStyle(ChatFormatting.GREEN));
        }
        graphics.renderTooltip(this.font, lines, Optional.<TooltipComponent>empty(), rx, ry);
    }

    // ── 液体显示提示 ─────────────────────────────────────────────────────────

    private void renderFluidTooltip(GuiGraphics graphics, int rx, int ry) {
        Fluid fluid = menu.getFluid();
        int amount  = menu.getFluidAmount();
        List<Component> lines = new ArrayList<>();

        if (fluid != null && fluid != Fluids.EMPTY) {
            Component fluidName = fluid.getFluidType().getDescription();
            lines.add(fluidName.copy().withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("tooltip.fd_enhancedcookware.fluid_amount", amount, 1000)
                .withStyle(ChatFormatting.GRAY));

        graphics.renderTooltip(this.font, lines, Optional.<TooltipComponent>empty(), rx, ry);
    }

    // ===================== 点击按钮 =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 左键点击热量条 → 消耗一个燃料物品增加热量
            int hbx = this.leftPos + GrandCookpotMenu.HEAT_BAR_X;
            int hby = this.topPos  + GrandCookpotMenu.HEAT_BAR_Y;
            if (mouseX >= hbx && mouseX < hbx + GrandCookpotMenu.HEAT_BAR_W
                    && mouseY >= hby && mouseY < hby + GrandCookpotMenu.HEAT_BAR_H) {
                ItemStack fuelSlot = this.menu.slots.get(GrandCookpotMenu.FUEL_SLOT).getItem();
                if (!fuelSlot.isEmpty() && CookpotConfig.getFuelHeat(fuelSlot.getItem()) > 0
                        && menu.getExtraHeat() < 120) {
                    NetworkHandler.CHANNEL.sendToServer(
                            new AddHeatPacket(menu.getBlockEntity().getBlockPos()));
                    return true;
                }
            }

            int ax = this.leftPos + GrandCookpotMenu.ARROW_X;
            int ay = this.topPos  + GrandCookpotMenu.ARROW_Y;

            if (mouseX >= ax && mouseX < ax + GrandCookpotMenu.ARROW_W
                    && mouseY >= ay && mouseY < ay + GrandCookpotMenu.ARROW_H) {

                CookingState state = menu.getCookingState();
                switch (state) {
                    case NORMAL ->
                        NetworkHandler.CHANNEL.sendToServer(
                                new StartCookingPacket(menu.getBlockEntity().getBlockPos()));

                    case BLOCKED, NO_HEAT, CRAFTING, WRONG_STOVE ->
                        openJei();

                    default -> { /* READY / MISSING_TAGS 不响应点击 */ }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openJei() {
        if (ModList.get().isLoaded("jei")) {
            GrandCookpotJEIPlugin.openGrandCookpotCategory();
        }
    }

    // ===================== 内部类：客户端容器视图 =====================

    /**
     * 将菜单槽位包装为 Container 接口，供客户端侧的 RecipeManager.matches() 调用。
     * 只实现读取操作（matches 不会写入容器）。
     * 覆盖输入格 0-11 + 器皿槽 12，共 13 个槽。
     */
    private class ClientContainerView implements Container {

        @Override
        public int getContainerSize() { return 13; }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < getContainerSize(); i++) {
                if (!getItem(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public @NotNull ItemStack getItem(int slot) {
            if (slot < 0 || slot >= getContainerSize()) return ItemStack.EMPTY;
            return menu.slots.get(slot).getItem();
        }

        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) { /* read-only view */ }

        @Override
        public void setChanged() { /* no-op */ }

        @Override
        public boolean stillValid(@NotNull Player player) { return true; }

        @Override
        public void clearContent() { /* read-only view */ }
    }
}
