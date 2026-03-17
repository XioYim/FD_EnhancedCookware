package com.xioyim.fdenhancedcookware.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.xioyim.fdenhancedcookware.FDEnhancedCookware;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 高级厨锅公共配置（COMMON 类型，生成于 config/fd_enhancedcookware-common.toml）。
 * Grand Cookpot common config (COMMON type, generated at config/fd_enhancedcookware-common.toml).
 *
 * ════════════════════════════════════════════════════════════════════════════
 *  燃料配置 / Fuel Config
 * ════════════════════════════════════════════════════════════════════════════
 *
 * fuel_items 格式 / Format:  "modid:item=热量值 / heatAmount"
 *   热量值范围 1-100 / Heat range: 1-100
 *   物品只有在当前热量 + 物品热量值 <= 100 时才会被自动消耗
 *   Item is only consumed when (currentHeat + itemHeat) <= 100
 *   示例 / Examples:
 *     "minecraft:blaze_powder=10"
 *     "minecraft:blaze_rod=30"
 *
 * ════════════════════════════════════════════════════════════════════════════
 *  液体配置 / Liquid Config
 * ════════════════════════════════════════════════════════════════════════════
 *
 * liquid_items 格式 / Format:  "modid:item=modid:fluid,毫升数,返还物品(可选) / amountMl,returnItem(optional)"
 *   消耗液体物品后，若配置了返还物品，会将其放回液体槽（槽空时）或丢到地面（槽占用时）。
 *   After consuming the liquid item, the return item is placed back in the liquid slot (if empty)
 *   or dropped at the block position (if occupied).
 *   示例 / Examples:
 *     "minecraft:water_bucket=minecraft:water,500,minecraft:bucket"
 *     "minecraft:milk_bucket=minecraft:milk,1000,minecraft:bucket"
 *     "minecraft:lava_bucket=minecraft:lava,1000,minecraft:bucket"
 *     "minecraft:potion=minecraft:water,250,minecraft:glass_bottle"  ← 无返还示例：省略第3段
 *
 * ════════════════════════════════════════════════════════════════════════════
 *  配方 JSON 字段说明 / Recipe JSON Field Reference
 *  路径 / Path: data/[modid]/recipes/[name].json
 * ════════════════════════════════════════════════════════════════════════════
 *
 * ─── 必填字段 / Required Fields ───────────────────────────────────────────
 *
 * "type": "fd_enhancedcookware:grand_cookpot"
 *   配方类型标识，固定值，必须填写。
 *   Recipe type identifier, fixed value, required.
 *
 * "ingredients": [ ... ]
 *   输入格材料列表（最多12个条目，对应4×3输入格）。无序匹配，顺序不影响结果。
 *   Input ingredient list (up to 12 entries for the 4×3 grid). Unordered matching.
 *   每个条目支持以下字段 / Each entry supports:
 *
 *     "item": "modid:item_id"
 *       指定精确物品ID。与 "tag" 二选一。
 *       Exact item ID. Mutually exclusive with "tag".
 *
 *     "tag": "forge:tag_path"
 *       指定物品标签，接受所有匹配该标签的物品。与 "item" 二选一。
 *       Item tag; accepts any item matching the tag. Mutually exclusive with "item".
 *
 *     "count": 1
 *       所需数量，默认为 1。同一种材料需要多个时可合并为一个条目。
 *       Required quantity, default 1. Can combine same item into one entry.
 *
 *     "nbt": "{key:value, ...}"
 *       可选。SNBT 格式的 NBT 约束，物品必须包含（部分匹配）这些 NBT 数据才算匹配。
 *       Optional. SNBT string; item must contain (partial match) this NBT to match.
 *       示例 / Example: "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}"
 *
 * "result": { "item": "modid:item_id", "count": 1, "nbt": "{...}" }
 *   输出物品。"count" 默认为 1，"nbt" 可选，将直接附加到输出物品上。
 *   Output item. "count" defaults to 1. "nbt" is optional and applied directly to the output.
 *
 * "cookingtime": 200
 *   烹饪所需刻数（20 刻 = 1 秒）。默认 200（10 秒）。
 *   Cooking duration in ticks (20 ticks = 1 second). Default 200 (10 sec).
 *
 * ─── 可选字段 / Optional Fields ───────────────────────────────────────────
 *
 * "container": { "item": "modid:item_id" }
 *   器皿要求。制作时消耗器皿槽中的一个该物品。省略则不需要器皿。
 *   Vessel requirement. Consumes one item from the vessel slot during crafting. Omit if none needed.
 *   也可使用标签 / Tag also supported: { "tag": "forge:vessels" }
 *
 * "experience": 0.0
 *   完成配方时给予玩家的经验值（浮点数）。默认 0。
 *   Experience awarded to the player on completion. Default 0.
 *
 * ─── 炉灶要求 / Stove Requirement ─────────────────────────────────────────
 *
 * "require_block": false
 *   是否启用炉灶限制。true 时必须搭配 "required_blocks" 使用。默认 false（接受任意热源）。
 *   Enable stove restriction. Must be used with "required_blocks". Default false (any heat source).
 *
 * "required_blocks": [ "modid:block_id", "modid:block_id[prop=val]", ... ]
 *   允许的炉灶列表（OR 逻辑，满足任意一个即可）。仅在 require_block=true 时生效。
 *   List of allowed stoves (OR logic, any one suffices). Only active when require_block=true.
 *   支持的炉灶 / Supported stoves:
 *     "farmersdelight:stove"                         — 炉灶 / Stove
 *     "mynethersdelight:nether_stove"                — 下界炉灶（任意火焰）/ Nether Stove (any flame)
 *     "mynethersdelight:nether_stove[soul=false]"    — 下界炉灶，地狱火 / Nether fire only
 *     "mynethersdelight:nether_stove[soul=true]"     — 下界炉灶，灵魂火 / Soul fire only
 *     "dungeonsdelight:dungeon_stove"                — 地牢炉灶 / Dungeon Stove
 *     "endersdelight:endstone_stove"                 — 末地炉灶 / Ender Stove
 *   方括号内可指定任意 BlockState 属性 / Square brackets accept any BlockState property:
 *     "modid:block[prop1=val1,prop2=val2]"
 *   炉灶必须处于点燃状态（lit=true）才算有效热源，该检查由系统自动执行。
 *   Stove must be lit (lit=true) to count as valid; this check is performed automatically.
 *
 * ─── 火焰强度要求 / Flame Intensity Requirement ───────────────────────────
 *
 * "require_roaring": false
 *   是否要求高燃火焰（即锅内 extraHeat > 0）。默认 false。
 *   Require roaring flame (cookpot extraHeat > 0). Default false.
 *   注意：只要 heat_consumption > 0，系统也会自动视为高燃要求。
 *   Note: if heat_consumption > 0, the system also treats this as requiring roaring flame.
 *
 * "heat_consumption": 0
 *   每次制作消耗的热量值（0-100）。消耗后从锅内 extraHeat 扣除。默认 0（不消耗）。
 *   Heat consumed per craft (0-100), deducted from cookpot extraHeat. Default 0 (none).
 *   设置 > 0 时隐含 require_roaring=true（当前热量需 >= 该值方可开始）。
 *   Setting > 0 implies require_roaring=true (current heat must be >= this value to start).
 *
 * ─── 液体要求 / Fluid Requirement ─────────────────────────────────────────
 *
 * "required_fluid": "modid:fluid_id"
 *   配方所需的液体类型。省略或设为 null 则不需要液体。
 *   Required fluid type. Omit or set null if no fluid is needed.
 *   示例 / Examples: "minecraft:water", "minecraft:lava", "minecraft:milk"
 *
 * "fluid_consumption": 0
 *   每次制作消耗的液体毫升数（mL）。仅在 required_fluid 不为空时生效。默认 0。
 *   Millilitres of fluid consumed per craft. Only active when required_fluid is set. Default 0.
 *   液体可通过向液体槽投入配置文件中定义的物品（如水桶）来添加。
 *   Fluid is added by inserting configured items (e.g., water bucket) into the liquid slot.
 *
 * ─── 玩家标签要求 / Player Tag Requirement ────────────────────────────────
 *
 * "require_tags": false
 *   是否启用玩家标签限制。默认 false。
 *   Enable player tag restriction. Default false.
 *
 * "required_tags": [ "tag1", "tag2" ]
 *   玩家必须同时拥有的所有标签（AND 逻辑）。仅在 require_tags=true 时生效。
 *   Player must have ALL listed tags (AND logic). Only active when require_tags=true.
 *   通过命令添加标签 / Add tag via command: /tag <player> add <tagName>
 *
 * "show_tags_in_jei": false
 *   是否在 JEI 配方页面中显示所需标签信息。默认 false（隐藏标签要求）。
 *   Whether to show required tags in the JEI recipe page. Default false (hidden).
 *
 * ─── 命令触发 / Command Triggers ──────────────────────────────────────────
 *
 * "start_command_player": "command" 或 / or ["cmd1", "cmd2"]
 *   玩家点击开始烹饪时执行的命令（以玩家为执行者 @s）。可以是字符串或数组。
 *   Command(s) executed when the player clicks to start cooking (executor: @s = the player).
 *   可以是单条字符串或命令数组 / Can be a single string or an array.
 *
 * "tick_command_block": "command" 或 / or ["cmd1", "cmd2"]
 *   烹饪过程中定期执行的命令（以方块坐标为执行位置）。可以是字符串或数组。
 *   Command(s) executed periodically during cooking (executed at block coordinates).
 *
 * "tick_interval": 1
 *   tick_command_block 每隔多少刻执行一次。默认 1（每刻）。建议设置 >=5 避免性能问题。
 *   How often (in ticks) tick_command_block fires. Default 1 (every tick). Recommend >= 5.
 *
 * "finish_command": "command" 或 / or ["cmd1", "cmd2"]
 *   烹饪完成（产物放入输出槽）时执行的命令（以方块坐标为执行位置）。
 *   Command(s) executed when cooking finishes (product placed in output slot, at block pos).
 *
 * "output_command_player": "command" 或 / or ["cmd1", "cmd2"]
 *   玩家取走输出物品时对该玩家执行的命令（以玩家为执行者 @s）。
 *   Command(s) executed when the player takes the output item (executor: @s = that player).
 *
 * ─── JEI 描述 / JEI Description ───────────────────────────────────────────
 *
 * "jei_description": ["§c描述行1", "§7描述行2"]
 *   在 JEI 配方页面悬浮提示最下方显示的自定义说明文本。支持 § 颜色代码。
 *   Custom description lines shown at the bottom of the JEI recipe tooltip. Supports § color codes.
 *   可以是单条字符串或字符串数组 / Can be a single string or an array.
 *
 * ════════════════════════════════════════════════════════════════════════════
 *  配方完整示例 / Full Recipe Example
 * ════════════════════════════════════════════════════════════════════════════
 *
 * {
 *   "type": "fd_enhancedcookware:grand_cookpot",
 *   "ingredients": [
 *     {"item": "minecraft:potato", "count": 3},
 *     {"tag": "forge:milk", "count": 1},
 *     {"item": "minecraft:enchanted_book", "count": 1,
 *      "nbt": "{StoredEnchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}"}
 *   ],
 *   "container": {"item": "minecraft:bowl"},
 *   "result": {"item": "farmersdelight:vegetable_soup", "count": 1,
 *              "nbt": "{CustomTag:1b}"},
 *   "cookingtime": 400,
 *   "experience": 1.0,
 *   "require_block": true,
 *   "required_blocks": [
 *     "farmersdelight:stove",
 *     "mynethersdelight:nether_stove[soul=false]"
 *   ],
 *   "require_roaring": true,
 *   "heat_consumption": 20,
 *   "required_fluid": "minecraft:water",
 *   "fluid_consumption": 250,
 *   "require_tags": true,
 *   "required_tags": ["chef"],
 *   "show_tags_in_jei": true,
 *   "start_command_player": "say 开始烹饪！",
 *   "tick_command_block": "particle minecraft:smoke ~ ~1 ~ 0.2 0.2 0.2 0 3",
 *   "tick_interval": 5,
 *   "finish_command": ["playsound minecraft:block.note_block.pling master @a[distance=..10] ~ ~ ~ 1 1"],
 *   "output_command_player": "effect give @s minecraft:speed 30 1",
 *   "jei_description": ["§c此配方需要特殊条件", "§7详情请咨询管理员"]
 * }
 */
@Mod.EventBusSubscriber(modid = FDEnhancedCookware.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CookpotConfig {

    public static final ForgeConfigSpec SPEC;

    // ── 配置项 ──────────────────────────────────────────────────────────────

    public static ForgeConfigSpec.ConfigValue<List<? extends String>> FUEL_ITEMS;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> LIQUID_ITEMS;

    // ── 展示实体偏移 ────────────────────────────────────────────────────────
    public static ForgeConfigSpec.DoubleValue DISPLAY_OFFSET_X;
    public static ForgeConfigSpec.DoubleValue DISPLAY_OFFSET_Y;
    public static ForgeConfigSpec.DoubleValue DISPLAY_OFFSET_Z;

    // ── 首次拾取提示 ────────────────────────────────────────────────────────
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> FIRST_PICKUP_MESSAGE;

    // ── 热量衰减间隔 ─────────────────────────────────────────────────────────
    public static ForgeConfigSpec.IntValue HEAT_DECAY_INTERVAL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("fuel");
        FUEL_ITEMS = builder
                .comment(
                        "向高级厨锅添加额外热量的燃料物品列表。",
                        "格式：\"模组ID:物品ID=热量值\"（热量值范围 1-120）。",
                        "默认：烈焰粉=10，烈焰棒=30。",
                        "Items that add extra heat to the Grand Cookpot.",
                        "Format: \"modid:item=heatAmount\" (heat range: 1-120)",
                        "Defaults: blaze_powder=10, blaze_rod=30"
                )
                .defineListAllowEmpty(
                        List.of("fuel_items"),
                        () -> List.of(
                                "minecraft:blaze_powder=10",
                                "minecraft:blaze_rod=30"
                        ),
                        obj -> obj instanceof String s && s.contains("=")
                );
        builder.pop();

        builder.push("liquid");
        LIQUID_ITEMS = builder
                .comment(
                        "向高级厨锅液体槽添加液体的物品列表。",
                        "格式：\"模组ID:物品ID=模组ID:液体ID,毫升数,返还物品(可选)\"。",
                        "消耗后，若液体槽为空则将返还物品放回槽中，否则丢落在方块上方。",
                        "默认：水桶/牛奶桶/熔岩桶均返还空桶。",
                        "Items that add liquid to the Grand Cookpot's fluid tank.",
                        "Format: \"modid:item=modid:fluid,amountMl,returnItem(optional)\"",
                        "After consuming, the return item (3rd segment) is placed back in the liquid slot",
                        "if it is empty, or dropped above the block if occupied.",
                        "Defaults: water/milk/lava buckets return an empty bucket"
                )
                .defineListAllowEmpty(
                        List.of("liquid_items"),
                        () -> List.of(
                                "minecraft:water_bucket=minecraft:water,500,minecraft:bucket",
                                "minecraft:milk_bucket=minecraft:milk,1000,minecraft:bucket",
                                "minecraft:lava_bucket=minecraft:lava,1000,minecraft:bucket"
                        ),
                        obj -> obj instanceof String s && s.contains("=")
                );
        builder.pop();

        builder.push("display_entity");
        DISPLAY_OFFSET_X = builder
                .comment(
                        "悬浮展示物品实体相对于锅方块位置的 X 轴偏移量。默认 0.5（水平居中）。",
                        "X offset of the floating display item entity relative to the cookpot block position.",
                        "Default 0.5 centers it horizontally.")
                .defineInRange("display_offset_x", 0.5, -2.0, 2.0);
        DISPLAY_OFFSET_Y = builder
                .comment(
                        "悬浮展示物品实体相对于锅方块位置的 Y 轴偏移量。默认 0.8（恰好在锅口上方）。",
                        "Y offset of the floating display item entity relative to the cookpot block position.",
                        "Default 0.8 places it just above the pot.")
                .defineInRange("display_offset_y", 0.8, 0, 4.0);
        DISPLAY_OFFSET_Z = builder
                .comment(
                        "悬浮展示物品实体相对于锅方块位置的 Z 轴偏移量。默认 0.5（水平居中）。",
                        "Z offset of the floating display item entity relative to the cookpot block position.",
                        "Default 0.5 centers it horizontally.")
                .defineInRange("display_offset_z", 0.5, -2.0, 2.0);
        builder.pop();

        builder.push("first_pickup");
        FIRST_PICKUP_MESSAGE = builder
                .comment(
                        "玩家首次拾取高级厨锅时发送的提示消息行列表。",
                        "支持 § 颜色代码。留空则禁用此功能。",
                        "消息发送后会为玩家添加标签 'fd_enhancedcookware.cookpot_hint_shown' 以防止重复发送。",
                        "Message lines sent to the player the first time they pick up a Grand Cookpot.",
                        "Supports § color codes. Leave empty to disable.",
                        "Player tag 'fd_enhancedcookware.cookpot_hint_shown' is added after sending to prevent repeats."
                )
                .defineListAllowEmpty(
                        List.of("first_pickup_message"),
                        () -> List.of(
                                "§6§l>>> 高级厨锅指南 <<<",
                                "§e[支持炉灶]",
                                "  §7- 农夫乐事: §f炉灶 (farmersdelight:stove)",
                                "  §7- 下界乐事: §f下界炉灶 (mynethersdelight:nether_stove)",
                                "  §7- 地牢乐事: §f地牢炉灶 (dungeonsdelight:dungeon_stove)",
                                "  §7- 末地乐事: §f末地炉灶 (endersdelight:endstone_stove)",
                                "§e[热量添加] §7(点击热量槽)",
                                "  §7- §b烈焰粉: §7+10 点热量",
                                "  §7- §b烈焰棒: §7+30 点热量",
                                "§e[液体清空]",
                                "  §7- 手持 §f空桶 §7右键点击锅",
                                "§6§l====================="
                        ),

                        obj -> obj instanceof String
                );
        builder.pop();

        builder.push("heat");
        HEAT_DECAY_INTERVAL = builder
                .comment(
                        "热量自然衰减间隔（单位：刻，20 刻 = 1 秒）。",
                        "每隔此刻数，锅内 extraHeat 减少 1 点。设置为 0 则禁用热量自然衰减（热量永不消耗）。",
                        "默认 400（即每 20 秒消耗 1 点热量）。",
                        "Heat natural decay interval in ticks (20 ticks = 1 second).",
                        "Every this many ticks, cookpot extraHeat decreases by 1.",
                        "Set to 0 to disable decay entirely (heat never drains). Default: 300 (15 seconds)."
                )
                .defineInRange("heat_decay_interval", 400, 0, 72000);
        builder.pop();

        // ══════════════════════════════════════════════════════════════════
        // 配方字段说明（只读文档，请勿修改！）
        // Recipe JSON Field Reference  (read-only, do not modify!)
        // ══════════════════════════════════════════════════════════════════
        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                " 配方 JSON 字段说明 / Recipe JSON Field Reference              ",
                " 路径 / Path: data/<modid>/recipes/<name>.json                 ",
                " 此节仅供参考，请勿修改任何值！                                ",
                " This section is for reference only — do not modify!           ",
                "═══════════════════════════════════════════════════════════════"
        ).push("recipe_reference");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【type】必填 / Required",
                "中: 配方类型标识，固定值，必须填写。",
                "EN: Recipe type identifier, fixed value, required.",
                "值 / Value: \"fd_enhancedcookware:grand_cookpot\""
        ).define("type", "fd_enhancedcookware:grand_cookpot");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【ingredients】必填 / Required",
                "中: 输入格材料列表（最多 12 个条目，对应 4×3 输入格），无序匹配。",
                "    每条目字段：item（精确物品ID）或 tag（物品标签，二选一），count（数量，默认1），nbt（可选 SNBT 约束）。",
                "EN: Input ingredient list (up to 12 entries, 4×3 grid), unordered matching.",
                "    Per-entry: item (exact ID) or tag (item tag, pick one), count (default 1), nbt (optional SNBT).",
                "示例 / Example: [{\"item\":\"minecraft:potato\",\"count\":3},{\"tag\":\"forge:milk\"},{\"item\":\"minecraft:book\",\"nbt\":\"{StoredEnchantments:[{id:\\\"minecraft:sharpness\\\",lvl:5s}]}\"}]"
        ).define("ingredients", "[ { item/tag, count, nbt }, ... ]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【result】必填 / Required",
                "中: 输出物品。count 默认为 1；nbt 可选，将直接附加到输出物品上。",
                "EN: Output item. count defaults to 1. nbt is optional and applied directly to the output.",
                "示例 / Example: {\"item\":\"farmersdelight:vegetable_soup\",\"count\":1,\"nbt\":\"{CustomTag:1b}\"}"
        ).define("result", "{ item, count, nbt }");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【cookingtime】必填 / Required",
                "中: 烹饪所需刻数（20 刻 = 1 秒）。默认 200（10 秒）。",
                "EN: Cooking duration in ticks (20 ticks = 1 second). Default 200 (10 sec).",
                "示例 / Example: 400"
        ).define("cookingtime", "200");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【container】可选 / Optional",
                "中: 器皿要求。制作时消耗器皿槽中的一个该物品。省略则不需要器皿。支持 item 或 tag。",
                "EN: Vessel requirement. Consumes one item from the vessel slot during crafting. Omit if none needed. Supports item or tag.",
                "示例 / Example: {\"item\":\"minecraft:bowl\"}  或/or  {\"tag\":\"forge:vessels\"}"
        ).define("container", "{ item }  or  { tag }  (optional)");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【experience】可选 / Optional",
                "中: 完成配方时给予玩家的经验值（浮点数）。默认 0。",
                "EN: Experience awarded to the player on completion (float). Default 0.",
                "示例 / Example: 1.5"
        ).define("experience", "0.0");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【require_block】可选 / Optional",
                "中: 是否启用炉灶限制。true 时必须搭配 required_blocks 使用。默认 false（接受任意热源）。",
                "EN: Enable stove restriction. Must be used with required_blocks. Default false (any heat source).",
                "示例 / Example: true"
        ).define("require_block", "false");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【required_blocks】可选 / Optional  （仅 require_block=true 时生效 / Only active when require_block=true）",
                "中: 允许的炉灶列表（OR 逻辑，满足任意一个即可）。方括号内可指定任意 BlockState 属性。",
                "EN: Allowed stove list (OR logic, any one suffices). Square brackets accept any BlockState property.",
                "可用炉灶 / Available stoves:",
                "  \"farmersdelight:stove\"                       — 炉灶 / Stove",
                "  \"mynethersdelight:nether_stove\"              — 下界炉灶（任意火焰）/ Nether Stove (any flame)",
                "  \"mynethersdelight:nether_stove[soul=false]\" — 下界炉灶地狱火 / Nether fire only",
                "  \"mynethersdelight:nether_stove[soul=true]\"  — 下界炉灶灵魂火 / Soul fire only",
                "  \"dungeonsdelight:dungeon_stove\"              — 地牢炉灶 / Dungeon Stove",
                "  \"endersdelight:endstone_stove\"               — 末地炉灶 / Ender Stove",
                "示例 / Example: [\"farmersdelight:stove\",\"mynethersdelight:nether_stove[soul=false]\"]"
        ).define("required_blocks", "[ \"modid:block\", \"modid:block[prop=val]\", ... ]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【require_roaring】可选 / Optional",
                "中: 是否要求高燃火焰（即锅内 extraHeat > 0）。默认 false。",
                "    注意：只要 heat_consumption > 0，系统也会自动视为高燃要求。",
                "EN: Require roaring flame (cookpot extraHeat > 0). Default false.",
                "    Note: if heat_consumption > 0, the system also treats this as requiring roaring flame.",
                "示例 / Example: true"
        ).define("require_roaring", "false");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【heat_consumption】可选 / Optional",
                "中: 每次制作消耗的热量值（0–120）。消耗后从锅内 extraHeat 扣除。默认 0（不消耗）。",
                "    设置 > 0 时隐含 require_roaring=true（当前热量需 >= 该值方可开始）。",
                "EN: Heat consumed per craft (0–120), deducted from cookpot extraHeat. Default 0 (none).",
                "    Setting > 0 implies require_roaring=true (current heat must be >= this value to start).",
                "示例 / Example: 30"
        ).define("heat_consumption", "0");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【required_fluid】可选 / Optional",
                "中: 配方所需的液体类型。省略或设为 null 则不需要液体。",
                "EN: Required fluid type. Omit or set null if no fluid is needed.",
                "示例 / Example: \"minecraft:water\"  |  \"minecraft:lava\"  |  \"minecraft:milk\""
        ).define("required_fluid", "\"modid:fluid_id\"  (optional)");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【fluid_consumption】可选 / Optional",
                "中: 每次制作消耗的液体毫升数（mL）。仅在 required_fluid 不为空时生效。默认 0。",
                "    液体通过向液体槽投入配置文件中定义的物品（如水桶）来添加，最大 1000 mL。",
                "EN: Millilitres of fluid consumed per craft. Only active when required_fluid is set. Default 0.",
                "    Fluid is added via configured liquid_items (e.g., water bucket) in the liquid slot. Max 1000 mL.",
                "示例 / Example: 500"
        ).define("fluid_consumption", "0");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【require_tags】可选 / Optional",
                "中: 是否启用玩家标签限制。默认 true。",
                "EN: Enable player tag restriction. Default true.",
                "示例 / Example: true"
        ).define("require_tags", "true");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【required_tags】可选 / Optional  （仅 require_tags=true 时生效 / Only active when require_tags=true）",
                "中: 玩家必须同时拥有的所有标签（AND 逻辑）。通过命令添加：/tag <玩家> add <标签名>",
                "EN: Player must have ALL listed tags (AND logic). Add tag via: /tag <player> add <tagName>",
                "示例 / Example: [\"chef\", \"admin\"]"
        ).define("required_tags", "[ \"tag1\", \"tag2\" ]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【show_tags_in_jei】可选 / Optional",
                "中: 是否在 JEI 配方页面中显示所需标签信息。默认 false（隐藏标签要求）。",
                "EN: Whether to show required tags in the JEI recipe page. Default false (hidden).",
                "示例 / Example: true"
        ).define("show_tags_in_jei", "false");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【recipe_title】可选 / Optional",
                "中: 配方在箭头按钮悬浮提示中显示的自定义标题（支持 § 颜色代码）。省略则显示默认标题。",
                "EN: Custom title shown in the arrow button tooltip (supports § color codes). Omit for default title.",
                "示例 / Example: \"§6§l传奇锻造\""
        ).define("recipe_title", "\"§6§l自定义标题\"  (optional)");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【start_command_player】可选 / Optional",
                "中: 玩家点击开始烹饪时执行的命令（执行者 @s = 该玩家）。可以是字符串或字符串数组。",
                "EN: Command(s) run when the player clicks to start cooking (executor @s = the player). String or array.",
                "示例 / Example: \"say 开始烹饪！\"  或/or  [\"say 开始\", \"particle minecraft:smoke ~ ~1 ~ 0.2 0.2 0.2 0 3\"]"
        ).define("start_command_player", "\"command\"  or  [\"cmd1\", \"cmd2\"]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【tick_command_block】可选 / Optional",
                "中: 烹饪过程中定期执行的命令（执行位置为方块坐标）。可以是字符串或字符串数组。",
                "EN: Command(s) executed periodically during cooking (at block coordinates). String or array.",
                "示例 / Example: \"particle minecraft:smoke ~ ~1 ~ 0.2 0.2 0.2 0 3\""
        ).define("tick_command_block", "\"command\"  or  [\"cmd1\", \"cmd2\"]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【tick_interval】可选 / Optional",
                "中: tick_command_block 每隔多少刻执行一次。默认 1（每刻）。建议 >= 5 避免性能问题。",
                "EN: How often (in ticks) tick_command_block fires. Default 1 (every tick). Recommend >= 5.",
                "示例 / Example: 10"
        ).define("tick_interval", "1");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【finish_command】可选 / Optional",
                "中: 烹饪完成（产物放入输出槽）时执行的命令（执行位置为方块坐标）。可以是字符串或字符串数组。",
                "EN: Command(s) run when cooking finishes and product is placed in output slot (at block pos). String or array.",
                "示例 / Example: [\"playsound minecraft:block.note_block.pling master @a[distance=..10] ~ ~ ~ 1 1\"]"
        ).define("finish_command", "\"command\"  or  [\"cmd1\", \"cmd2\"]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【output_command_player】可选 / Optional",
                "中: 玩家取走输出物品时对该玩家执行的命令（执行者 @s = 该玩家）。可以是字符串或字符串数组。",
                "EN: Command(s) run when the player takes the output item (executor @s = that player). String or array.",
                "示例 / Example: \"effect give @s minecraft:speed 30 1\""
        ).define("output_command_player", "\"command\"  or  [\"cmd1\", \"cmd2\"]");

        builder.comment(
                "═══════════════════════════════════════════════════════════════",
                "【jei_description】可选 / Optional",
                "中: 在 JEI 配方页面悬浮提示最下方显示的自定义说明文本。支持 § 颜色代码。可以是字符串或字符串数组。",
                "EN: Custom description lines shown at the bottom of the JEI recipe tooltip. Supports § color codes. String or array.",
                "示例 / Example: [\"§c此配方需要特殊条件\", \"§7详情请咨询管理员\"]"
        ).define("jei_description", "\"描述行\"  or  [\"line1\", \"line2\"]");

        builder.pop(); // recipe_reference

        SPEC = builder.build();
    }

    // ── 烘焙缓存 ────────────────────────────────────────────────────────────

    /** item registry key (String) → heat gain per item */
    private static Map<String, Integer> fuelMap = new HashMap<>();

    /** item registry key (String) → FluidEntry */
    private static Map<String, FluidEntry> liquidMap = new HashMap<>();

    /** 首次拾取提示消息行（§ 颜色代码字符串）。 */
    private static List<String> firstPickupMessages = new ArrayList<>();

    /**
     * 液体条目：fluid 对象 + 每次投入的 mL 数 + 消耗后返还的物品（可为 null 表示不返还）。
     * Liquid entry: fluid id, amount in mL, and optional return item after consumption.
     */
    public record FluidEntry(ResourceLocation fluidId, int amountMl, @Nullable ResourceLocation returnItem) {}

    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) bake();
    }

    @SubscribeEvent
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) bake();
    }

    public static void bake() {
        Map<String, Integer> newFuel = new HashMap<>();
        for (String entry : FUEL_ITEMS.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                try {
                    int heat = Integer.parseInt(parts[1].trim());
                    if (heat > 0) newFuel.put(parts[0].trim(), heat);
                } catch (NumberFormatException ignored) {}
            }
        }
        fuelMap = newFuel;

        Map<String, FluidEntry> newLiquid = new HashMap<>();
        for (String entry : LIQUID_ITEMS.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                String[] fluidParts = parts[1].trim().split(",", 3);
                if (fluidParts.length >= 2) {
                    try {
                        ResourceLocation fluidId = new ResourceLocation(fluidParts[0].trim());
                        int amount = Integer.parseInt(fluidParts[1].trim());
                        ResourceLocation returnItem = null;
                        if (fluidParts.length >= 3 && !fluidParts[2].trim().isEmpty()) {
                            returnItem = new ResourceLocation(fluidParts[2].trim());
                        }
                        if (amount > 0) newLiquid.put(parts[0].trim(), new FluidEntry(fluidId, amount, returnItem));
                    } catch (Exception ignored) {}
                }
            }
        }
        liquidMap = newLiquid;

        firstPickupMessages = new ArrayList<>(FIRST_PICKUP_MESSAGE.get().stream()
                .map(Object::toString).toList());

        FDEnhancedCookware.LOGGER.info("[CookpotConfig] Baked: {} fuel entries, {} liquid entries",
                fuelMap.size(), liquidMap.size());
    }

    // ── 查询 API ─────────────────────────────────────────────────────────────

    /** 返回物品对应的热量值，若不是燃料则返回 0。 */
    public static int getFuelHeat(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) return 0;
        return fuelMap.getOrDefault(key.toString(), 0);
    }

    /** 返回物品对应的液体条目，若无则返回 null。 */
    @Nullable
    public static FluidEntry getLiquidEntry(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) return null;
        return liquidMap.get(key.toString());
    }

    /** 获取当前燃料配置副本（供 JEI/GUI 展示用）。 */
    public static Map<String, Integer> getFuelMap() { return Collections.unmodifiableMap(fuelMap); }

    /** 获取当前液体配置副本。 */
    public static Map<String, FluidEntry> getLiquidMap() { return Collections.unmodifiableMap(liquidMap); }

    /** 获取首次拾取提示消息行列表（不可修改视图）。 */
    public static List<String> getFirstPickupMessages() { return Collections.unmodifiableList(firstPickupMessages); }

    // ── 展示实体偏移量查询 ────────────────────────────────────────────────────

    public static double getDisplayOffsetX() { return DISPLAY_OFFSET_X.get(); }
    public static double getDisplayOffsetY() { return DISPLAY_OFFSET_Y.get(); }
    public static double getDisplayOffsetZ() { return DISPLAY_OFFSET_Z.get(); }

    /** 热量衰减间隔（刻）。0 表示永不衰减。 */
    public static int getHeatDecayInterval() { return HEAT_DECAY_INTERVAL.get(); }
}
