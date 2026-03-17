package com.xioyim.fdenhancedcookware.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/**
 * 带数量和可选 NBT 的材料包装类（用于高级厨锅的无序配方匹配）。
 *
 * JSON 字段：
 *   "item"  - 物品 ID（或使用 "tag"）
 *   "count" - 所需数量（默认 1）
 *   "nbt"   - 可选 SNBT 字符串，物品必须包含该 NBT（部分匹配）
 */
public record CountedIngredient(Ingredient ingredient, int count, @Nullable CompoundTag nbt) {

    public static final CountedIngredient EMPTY =
            new CountedIngredient(Ingredient.EMPTY, 0, null);

    public boolean isEmpty() {
        return ingredient.isEmpty() || count <= 0;
    }

    /**
     * 测试物品栈是否满足：类型、数量、NBT（若配置了 nbt）。
     */
    public boolean test(ItemStack stack) {
        if (isEmpty()) return stack.isEmpty();
        if (!ingredient.test(stack)) return false;
        if (stack.getCount() < count) return false;
        if (nbt != null) {
            CompoundTag stackNbt = stack.getTag();
            if (stackNbt == null) return false;
            if (!nbtContains(stackNbt, nbt)) return false;
        }
        return true;
    }

    /**
     * 部分匹配：actual 中必须包含 required 的每个键且值相等。
     * CompoundTag 键递归匹配；ListTag 和基础类型精确相等。
     */
    private static boolean nbtContains(CompoundTag actual, CompoundTag required) {
        for (String key : required.getAllKeys()) {
            Tag req = required.get(key);
            Tag act = actual.get(key);
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

    // ---- JSON 序列化 ----

    public static CountedIngredient fromJson(JsonObject json) {
        if (json.size() == 0) return EMPTY;

        JsonObject ingredientJson = new JsonObject();
        if (json.has("item")) ingredientJson.add("item", json.get("item"));
        if (json.has("tag"))  ingredientJson.add("tag",  json.get("tag"));
        Ingredient ingredient = Ingredient.fromJson(ingredientJson);

        int count = GsonHelper.getAsInt(json, "count", 1);

        CompoundTag nbt = null;
        if (json.has("nbt")) {
            try {
                nbt = TagParser.parseTag(GsonHelper.getAsString(json, "nbt"));
            } catch (CommandSyntaxException e) {
                throw new JsonParseException("Invalid NBT in ingredient: " + e.getMessage());
            }
        }

        return new CountedIngredient(ingredient, count, nbt);
    }

    // ---- 网络序列化 ----

    public static CountedIngredient fromNetwork(FriendlyByteBuf buf) {
        Ingredient ingredient = Ingredient.fromNetwork(buf);
        int count = buf.readVarInt();
        CompoundTag nbt = buf.readBoolean() ? buf.readNbt() : null;
        return new CountedIngredient(ingredient, count, nbt);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        ingredient.toNetwork(buf);
        buf.writeVarInt(count);
        if (nbt != null) {
            buf.writeBoolean(true);
            buf.writeNbt(nbt);
        } else {
            buf.writeBoolean(false);
        }
    }
}
