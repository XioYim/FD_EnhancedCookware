package com.xioyim.fdenhancedcookware.recipe;

import com.google.gson.*;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import com.xioyim.fdenhancedcookware.init.ModRecipeTypes;

import javax.annotation.Nullable;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 高级厨锅配方。
 *
 * JSON 完整格式：
 * {
 *   "type": "fd_enhancedcookware:grand_cookpot",
 *   "ingredients": [
 *     {"item": "minecraft:potato", "count": 3},
 *     {"tag": "forge:milk", "count": 1}
 *   ],
 *   "container": {"item": "minecraft:bowl"},
 *   "result": {"item": "farmersdelight:vegetable_soup", "count": 1, "nbt": "{...}"},
 *   "cookingtime": 400,
 *   "experience": 1.0,
 *
 *   // 工作炉灶（OR 逻辑，满足任意一个即可）
 *   "require_block": false,
 *   "required_blocks": ["farmersdelight:stove", "mynethersdelight:nether_stove[soul=false]"],
 *
 *   // 玩家标签限制
 *   "require_tags": false,
 *   "required_tags": ["tag1", "tag2"],
 *   "show_tags_in_jei": false,
 *
 *   // 高热量要求（需要炉灶"剧烈燃烧"，即额外热量 > 0）
 *   "require_roaring": false,
 *   "heat_consumption": 0,          // 每次制作消耗的热量值（0 = 不消耗）
 *
 *   // 液体要求
 *   "required_fluid": "minecraft:water",  // 省略或 null = 无液体要求
 *   "fluid_consumption": 0,               // 每次制作消耗的 mL 数（0 = 不消耗）
 *
 *   "recipe_title": "自定义标题",   // 可选，显示在悬停提示首行（金色），默认"配方信息"
 *   "start_command_player": ["say 开始！"],
 *   "tick_command_block": "particle minecraft:smoke ~ ~1 ~ 0.2 0.2 0.2 0 3",
 *   "tick_interval": 5,
 *   "finish_command": ["say 完成！"],
 *   "output_command_player": ["effect give @s minecraft:speed 30 1"],
 *   "jei_description": ["§c此配方需要特殊条件"]
 * }
 */
public class GrandCookpotRecipe implements Recipe<Container> {

    public static final int MAX_INGREDIENTS = 12; // 4×3 输入格

    private final ResourceLocation id;
    private final List<CountedIngredient> ingredients;
    private final Ingredient container;
    private final ItemStack result;
    private final int cookingTime;
    private final float experience;

    // 工作方块限制（OR 逻辑，默认关）
    private final boolean requireBlock;
    private final List<RequiredBlock> requiredBlocks;

    // 玩家标签限制（默认关）
    private final boolean requireTags;
    private final List<String> requiredTags;
    private final boolean showTagsInJei;

    // 高热量要求（剧烈燃烧）
    private final boolean requireRoaring;
    private final int heatConsumption;

    // 液体要求
    @Nullable private final ResourceLocation requiredFluid; // null = 无要求
    private final int fluidConsumption; // mL per craft

    // 命令附加效果
    private final List<String> startCommandPlayer;
    private final List<String> tickCommandBlock;
    private final int tickInterval;
    private final List<String> finishCommand;
    private final List<String> outputCommandPlayer;

    // JEI 自定义描述行（始终显示在 JEI 提示最下方；支持 § 颜色符号）
    private final List<String> jeiDescription;

    // 配方标题（显示在按钮悬停提示最顶部，空字符串 = 使用默认"配方信息"）
    private final String recipeTitle;

    public GrandCookpotRecipe(ResourceLocation id,
                               List<CountedIngredient> ingredients,
                               Ingredient container,
                               ItemStack result,
                               int cookingTime,
                               float experience,
                               boolean requireBlock,
                               List<RequiredBlock> requiredBlocks,
                               boolean requireTags,
                               List<String> requiredTags,
                               boolean showTagsInJei,
                               boolean requireRoaring,
                               int heatConsumption,
                               @Nullable ResourceLocation requiredFluid,
                               int fluidConsumption,
                               List<String> startCommandPlayer,
                               List<String> tickCommandBlock,
                               int tickInterval,
                               List<String> finishCommand,
                               List<String> outputCommandPlayer,
                               List<String> jeiDescription,
                               String recipeTitle) {
        this.id                  = id;
        this.ingredients         = Collections.unmodifiableList(ingredients);
        this.container           = container;
        this.result              = result;
        this.cookingTime         = cookingTime;
        this.experience          = experience;
        this.requireBlock        = requireBlock;
        this.requiredBlocks      = Collections.unmodifiableList(requiredBlocks);
        this.requireTags         = requireTags;
        this.requiredTags        = Collections.unmodifiableList(requiredTags);
        this.showTagsInJei       = showTagsInJei;
        this.requireRoaring      = requireRoaring;
        this.heatConsumption     = Math.max(0, heatConsumption);
        this.requiredFluid       = requiredFluid;
        this.fluidConsumption    = Math.max(0, fluidConsumption);
        this.startCommandPlayer  = Collections.unmodifiableList(startCommandPlayer);
        this.tickCommandBlock    = Collections.unmodifiableList(tickCommandBlock);
        this.tickInterval        = Math.max(1, tickInterval);
        this.finishCommand       = Collections.unmodifiableList(finishCommand);
        this.outputCommandPlayer = Collections.unmodifiableList(outputCommandPlayer);
        this.jeiDescription      = Collections.unmodifiableList(jeiDescription);
        this.recipeTitle         = recipeTitle != null ? recipeTitle : "";
    }

    // ===================== 配方匹配（无序） =====================

    @Override
    public boolean matches(Container container, Level level) {
        return matchesIngredients(container) && matchesVessel(container);
    }

    public boolean matchesIngredients(Container container) {
        List<ItemStack> available = new ArrayList<>();
        for (int slot = 0; slot < 12; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) available.add(stack.copy());
        }
        for (CountedIngredient ci : ingredients) {
            if (ci.isEmpty()) continue;
            int needed = ci.count();
            for (ItemStack avail : available) {
                if (ci.ingredient().test(avail) && nbtMatches(avail, ci)) {
                    int canUse = Math.min(needed, avail.getCount());
                    avail.shrink(canUse);
                    needed -= canUse;
                    if (needed <= 0) break;
                }
            }
            if (needed > 0) return false;
        }
        return true;
    }

    private boolean nbtMatches(ItemStack stack, CountedIngredient ci) {
        if (ci.nbt() == null) return true;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        return nbtContains(tag, ci.nbt());
    }

    private static boolean nbtContains(CompoundTag actual, CompoundTag required) {
        for (String key : required.getAllKeys()) {
            var req = required.get(key);
            var act = actual.get(key);
            if (act == null) return false;
            if (req instanceof CompoundTag reqComp) {
                if (!(act instanceof CompoundTag actComp)) return false;
                if (!nbtContains(actComp, reqComp)) return false;
            } else {
                if (!req.equals(act)) return false;
            }
        }
        return true;
    }

    public boolean matchesVessel(Container container) {
        if (this.container.isEmpty()) return true;
        ItemStack vesselStack = container.getItem(12);
        return this.container.test(vesselStack) && vesselStack.getCount() >= 1;
    }

    /**
     * 检查厨锅下方是否满足工作方块要求（OR 逻辑）。
     */
    public boolean matchesBlockRequirement(Level level, net.minecraft.core.BlockPos cookpotPos) {
        if (!requireBlock || requiredBlocks.isEmpty()) return true;
        BlockState below = level.getBlockState(cookpotPos.below());
        for (RequiredBlock rb : requiredBlocks) {
            if (rb.matches(below)) return true;
        }
        return false;
    }

    /**
     * 检查炉灶是否满足"剧烈燃烧"要求。
     * @param isRoaring 当前 extraHeat > 0
     */
    public boolean matchesRoaring(boolean isRoaring) {
        return !requireRoaring || isRoaring;
    }

    /**
     * 检查额外热量是否足够满足此配方的消耗要求。
     * heatConsumption=0 → 无要求，始终返回 true。
     */
    public boolean matchesHeat(int extraHeat) {
        if (heatConsumption <= 0) return true;
        return extraHeat >= heatConsumption;
    }

    /**
     * 检查液体槽是否满足配方液体要求。
     * @param tankFluid 当前储存的 Fluid（null 或 EMPTY = 空）
     * @param tankAmount 当前储存量（mL）
     */
    public boolean matchesFluid(@Nullable Fluid tankFluid, int tankAmount) {
        if (requiredFluid == null || fluidConsumption <= 0) return true;
        if (tankFluid == null || tankFluid == Fluids.EMPTY) return false;
        Fluid needed = BuiltInRegistries.FLUID.get(requiredFluid);
        if (needed == null || needed == Fluids.EMPTY) return true; // 配置无效 → 忽略
        return tankFluid.isSame(needed) && tankAmount >= fluidConsumption;
    }

    /**
     * 检查玩家是否满足标签要求。
     */
    public boolean matchesPlayerTags(Player player) {
        if (!requireTags || requiredTags.isEmpty()) return true;
        for (String tag : requiredTags) {
            if (!player.getTags().contains(tag)) return false;
        }
        return true;
    }

    // ===================== 材料消耗 =====================

    public void consumeIngredients(Container container) {
        List<ItemStack> slotItems = new ArrayList<>();
        for (int i = 0; i < 12; i++) slotItems.add(container.getItem(i));

        for (CountedIngredient ci : ingredients) {
            if (ci.isEmpty()) continue;
            int needed = ci.count();
            for (int i = 0; i < slotItems.size() && needed > 0; i++) {
                ItemStack stack = slotItems.get(i);
                if (stack.isEmpty()) continue;
                if (ci.ingredient().test(stack) && nbtMatches(stack, ci)) {
                    int remove = Math.min(needed, stack.getCount());
                    container.removeItem(i, remove);
                    stack.shrink(remove);
                    needed -= remove;
                }
            }
        }
    }

    // ===================== Recipe 接口 =====================

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= MAX_INGREDIENTS;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) { return result; }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.GRAND_COOKPOT_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.GRAND_COOKPOT.get();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (CountedIngredient ci : ingredients) list.add(ci.ingredient());
        return list;
    }

    // ===================== Getters =====================

    public List<CountedIngredient> getCountedIngredients()  { return ingredients; }
    public Ingredient getContainer()                         { return container; }
    public ItemStack getResult()                             { return result; }
    public int getCookingTime()                              { return cookingTime; }
    public float getExperience()                             { return experience; }
    public boolean isRequireBlock()                          { return requireBlock; }
    public List<RequiredBlock> getRequiredBlocks()           { return requiredBlocks; }
    public boolean isRequireTags()                           { return requireTags; }
    public List<String> getRequiredTags()                    { return requiredTags; }
    public boolean isShowTagsInJei()                         { return showTagsInJei; }
    public boolean isRequireRoaring()                        { return requireRoaring; }
    public int getHeatConsumption()                          { return heatConsumption; }
    @Nullable public ResourceLocation getRequiredFluid()     { return requiredFluid; }
    public int getFluidConsumption()                         { return fluidConsumption; }
    public List<String> getStartCommandPlayer()              { return startCommandPlayer; }
    public List<String> getTickCommandBlock()                { return tickCommandBlock; }
    public int getTickInterval()                             { return tickInterval; }
    public List<String> getFinishCommand()                   { return finishCommand; }
    public List<String> getOutputCommandPlayer()             { return outputCommandPlayer; }
    public List<String> getJeiDescription()                  { return jeiDescription; }
    public String getRecipeTitle()                           { return recipeTitle; }

    // ===================== RequiredBlock（工作方块 + 可选属性约束） =====================

    /**
     * 表示一个炉灶要求：方块 ID + 可选的 BlockState 属性约束。
     * 格式：
     *   "farmersdelight:stove"                          → 只匹配方块，自动要求 lit=true
     *   "mynethersdelight:nether_stove[soul=false]"     → 额外要求 soul=false
     *   "mod:block[prop1=val1,prop2=val2]"              → 多属性严格匹配
     */
    public static class RequiredBlock {
        private final ResourceLocation blockId;
        private final Map<String, String> requiredProperties;

        public RequiredBlock(ResourceLocation blockId, Map<String, String> requiredProperties) {
            this.blockId = blockId;
            this.requiredProperties = Map.copyOf(requiredProperties);
        }

        public ResourceLocation getBlockId() { return blockId; }
        public Map<String, String> getRequiredProperties() { return requiredProperties; }

        public static RequiredBlock parse(String input) {
            input = input.trim();
            int bracketStart = input.indexOf('[');
            if (bracketStart < 0) {
                return new RequiredBlock(new ResourceLocation(input), Map.of());
            }
            String blockPart = input.substring(0, bracketStart).trim();
            int bracketEnd = input.lastIndexOf(']');
            String propPart = (bracketEnd > bracketStart)
                    ? input.substring(bracketStart + 1, bracketEnd).trim() : "";
            Map<String, String> props = new LinkedHashMap<>();
            if (!propPart.isEmpty()) {
                for (String entry : propPart.split(",")) {
                    String[] kv = entry.trim().split("=", 2);
                    if (kv.length == 2) props.put(kv[0].trim(), kv[1].trim());
                }
            }
            return new RequiredBlock(new ResourceLocation(blockPart), props);
        }

        public boolean matches(BlockState state) {
            var block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block == null) return false;
            if (!state.is(block)) return false;
            if (state.hasProperty(BlockStateProperties.LIT)) {
                if (!state.getValue(BlockStateProperties.LIT)) return false;
            }
            for (Map.Entry<String, String> entry : requiredProperties.entrySet()) {
                boolean found = false;
                for (var prop : state.getProperties()) {
                    if (prop.getName().equals(entry.getKey())) {
                        found = true;
                        if (!state.getValue(prop).toString().equals(entry.getValue())) {
                            return false;
                        }
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        public void toNetwork(FriendlyByteBuf buf) {
            buf.writeResourceLocation(blockId);
            buf.writeVarInt(requiredProperties.size());
            for (Map.Entry<String, String> e : requiredProperties.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeUtf(e.getValue());
            }
        }

        public static RequiredBlock fromNetwork(FriendlyByteBuf buf) {
            ResourceLocation id = buf.readResourceLocation();
            int count = buf.readVarInt();
            Map<String, String> props = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String k = buf.readUtf();
                String v = buf.readUtf();
                props.put(k, v);
            }
            return new RequiredBlock(id, props);
        }
    }

    // ===================== 序列化器 =====================

    public static class Serializer implements RecipeSerializer<GrandCookpotRecipe> {

        private static List<String> readCommandsJson(JsonObject json, String key) {
            if (!json.has(key)) return List.of();
            JsonElement el = json.get(key);
            List<String> result = new ArrayList<>();
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) {
                    String s = e.getAsString().trim();
                    if (!s.isEmpty()) result.add(s);
                }
            } else if (el.isJsonPrimitive()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) result.add(s);
            }
            return result;
        }

        @Override
        public GrandCookpotRecipe fromJson(ResourceLocation id, JsonObject json) {
            // ingredients
            JsonArray ingJson = GsonHelper.getAsJsonArray(json, "ingredients");
            List<CountedIngredient> ingredients = new ArrayList<>();
            for (JsonElement elem : ingJson) {
                ingredients.add(CountedIngredient.fromJson(elem.getAsJsonObject()));
            }

            // container（器皿，可选）
            Ingredient container = Ingredient.EMPTY;
            if (json.has("container")) {
                container = Ingredient.fromJson(json.get("container"));
            }

            // result
            JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
            ItemStack result = ShapedRecipe.itemStackFromJson(resultJson);
            if (resultJson.has("nbt")) {
                try {
                    result.setTag(TagParser.parseTag(GsonHelper.getAsString(resultJson, "nbt")));
                } catch (Exception e) {
                    throw new JsonParseException("Invalid NBT in result: " + e.getMessage());
                }
            }

            int cookingTime = GsonHelper.getAsInt(json, "cookingtime", 200);
            float experience = GsonHelper.getAsFloat(json, "experience", 0.0f);

            // 工作方块
            boolean requireBlock = GsonHelper.getAsBoolean(json, "require_block", false);
            List<RequiredBlock> requiredBlocks = new ArrayList<>();
            if (requireBlock) {
                if (json.has("required_blocks")) {
                    JsonElement el = json.get("required_blocks");
                    if (el.isJsonArray()) {
                        for (JsonElement e : el.getAsJsonArray()) {
                            requiredBlocks.add(RequiredBlock.parse(e.getAsString()));
                        }
                    } else if (el.isJsonPrimitive()) {
                        requiredBlocks.add(RequiredBlock.parse(el.getAsString()));
                    }
                } else if (json.has("required_block")) {
                    requiredBlocks.add(RequiredBlock.parse(
                            GsonHelper.getAsString(json, "required_block")));
                }
            }

            // 玩家标签
            boolean requireTags = GsonHelper.getAsBoolean(json, "require_tags", false);
            List<String> requiredTags = new ArrayList<>();
            if (requireTags && json.has("required_tags")) {
                JsonElement tagsEl = json.get("required_tags");
                if (tagsEl.isJsonArray()) {
                    for (JsonElement e : tagsEl.getAsJsonArray()) requiredTags.add(e.getAsString());
                } else if (tagsEl.isJsonPrimitive()) {
                    requiredTags.add(tagsEl.getAsString());
                }
            }
            boolean showTagsInJei = GsonHelper.getAsBoolean(json, "show_tags_in_jei", false);

            // 高热量要求
            boolean requireRoaring  = GsonHelper.getAsBoolean(json, "require_roaring", false);
            int heatConsumption     = GsonHelper.getAsInt(json, "heat_consumption", 0);

            // 液体要求
            ResourceLocation requiredFluid = null;
            if (json.has("required_fluid") && !json.get("required_fluid").isJsonNull()) {
                String fluidStr = GsonHelper.getAsString(json, "required_fluid", "");
                if (!fluidStr.isEmpty()) requiredFluid = new ResourceLocation(fluidStr);
            }
            int fluidConsumption = GsonHelper.getAsInt(json, "fluid_consumption", 0);

            List<String> startCmd  = readCommandsJson(json, "start_command_player");
            List<String> tickCmd   = readCommandsJson(json, "tick_command_block");
            int tickInterval       = GsonHelper.getAsInt(json, "tick_interval", 1);
            List<String> finishCmd = readCommandsJson(json, "finish_command");
            List<String> outputCmd = readCommandsJson(json, "output_command_player");
            List<String> jeiDesc   = readCommandsJson(json, "jei_description");
            String recipeTitle     = GsonHelper.getAsString(json, "recipe_title", "");

            return new GrandCookpotRecipe(id, ingredients, container, result,
                    cookingTime, experience,
                    requireBlock, requiredBlocks,
                    requireTags, requiredTags, showTagsInJei,
                    requireRoaring, heatConsumption,
                    requiredFluid, fluidConsumption,
                    startCmd, tickCmd, tickInterval, finishCmd, outputCmd,
                    jeiDesc, recipeTitle);
        }

        private static void writeCommands(FriendlyByteBuf buf, List<String> cmds) {
            buf.writeVarInt(cmds.size());
            for (String s : cmds) buf.writeUtf(s);
        }

        private static List<String> readCommands(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) list.add(buf.readUtf());
            return list;
        }

        @Override
        public GrandCookpotRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<CountedIngredient> ingredients = new ArrayList<>(size);
            for (int i = 0; i < size; i++) ingredients.add(CountedIngredient.fromNetwork(buf));

            Ingredient container = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            int cookingTime = buf.readVarInt();
            float experience = buf.readFloat();

            boolean requireBlock = buf.readBoolean();
            int blockCount = buf.readVarInt();
            List<RequiredBlock> requiredBlocks = new ArrayList<>(blockCount);
            for (int i = 0; i < blockCount; i++) requiredBlocks.add(RequiredBlock.fromNetwork(buf));

            boolean requireTags = buf.readBoolean();
            int tagCount = buf.readVarInt();
            List<String> requiredTags = new ArrayList<>(tagCount);
            for (int i = 0; i < tagCount; i++) requiredTags.add(buf.readUtf());
            boolean showTagsInJei = buf.readBoolean();

            boolean requireRoaring  = buf.readBoolean();
            int heatConsumption     = buf.readVarInt();
            boolean hasFluid        = buf.readBoolean();
            ResourceLocation requiredFluid = hasFluid ? buf.readResourceLocation() : null;
            int fluidConsumption    = buf.readVarInt();

            List<String> startCmd  = readCommands(buf);
            List<String> tickCmd   = readCommands(buf);
            int tickInterval       = buf.readVarInt();
            List<String> finishCmd = readCommands(buf);
            List<String> outputCmd = readCommands(buf);
            List<String> jeiDesc   = readCommands(buf);
            String recipeTitle     = buf.readUtf();

            return new GrandCookpotRecipe(id, ingredients, container, result,
                    cookingTime, experience,
                    requireBlock, requiredBlocks,
                    requireTags, requiredTags, showTagsInJei,
                    requireRoaring, heatConsumption,
                    requiredFluid, fluidConsumption,
                    startCmd, tickCmd, tickInterval, finishCmd, outputCmd,
                    jeiDesc, recipeTitle);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, GrandCookpotRecipe recipe) {
            buf.writeVarInt(recipe.ingredients.size());
            for (CountedIngredient ci : recipe.ingredients) ci.toNetwork(buf);

            recipe.container.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.cookingTime);
            buf.writeFloat(recipe.experience);

            buf.writeBoolean(recipe.requireBlock);
            buf.writeVarInt(recipe.requiredBlocks.size());
            for (RequiredBlock rb : recipe.requiredBlocks) rb.toNetwork(buf);

            buf.writeBoolean(recipe.requireTags);
            buf.writeVarInt(recipe.requiredTags.size());
            for (String t : recipe.requiredTags) buf.writeUtf(t);
            buf.writeBoolean(recipe.showTagsInJei);

            buf.writeBoolean(recipe.requireRoaring);
            buf.writeVarInt(recipe.heatConsumption);
            buf.writeBoolean(recipe.requiredFluid != null);
            if (recipe.requiredFluid != null) buf.writeResourceLocation(recipe.requiredFluid);
            buf.writeVarInt(recipe.fluidConsumption);

            writeCommands(buf, recipe.startCommandPlayer);
            writeCommands(buf, recipe.tickCommandBlock);
            buf.writeVarInt(recipe.tickInterval);
            writeCommands(buf, recipe.finishCommand);
            writeCommands(buf, recipe.outputCommandPlayer);
            writeCommands(buf, recipe.jeiDescription);
            buf.writeUtf(recipe.recipeTitle);
        }
    }
}
