package com.xioyim.fdenhancedcookware.blockentity;

/**
 * 高级厨锅的烹饪状态，决定箭头按钮的显示外观与行为。
 */
public enum CookingState {
    /** 橙色：下方无热源 */
    NO_HEAT,
    /** 灰色：有热源但无匹配配方 */
    BLOCKED,
    /** 绿色：热源就绪 + 配方匹配，等待玩家点击开始 */
    NORMAL,
    /** 黄色：烹饪中（计时器递减） */
    CRAFTING,
    /** 红色：烹饪完成，输出槽有产物，玩家取走后才能再次开始 */
    READY,
    /** 红色：材料匹配但当前炉灶不符合配方要求 */
    WRONG_STOVE,
    /** 红色：配方与炉灶均匹配，但玩家缺少所需标签（短暂显示后恢复） */
    MISSING_TAGS
}
