package com.generalchem;

/**
 * 工厂 GUI 的统一布局计算：容器槽位坐标与界面绘制坐标必须来自同一来源，
 * 否则点击区域与视觉槽位会错位。
 */
public final class FactoryGuiLayout {
    private static final int STANDARD_WIDTH = 176;
    private static final int WIDE_WIDTH = 210;

    private final ChemekFactoryType type;
    private final ChemekFactoryTier tier;
    private final int units;

    public FactoryGuiLayout(ChemekFactoryType type, ChemekFactoryTier tier) {
        this.type = type;
        this.tier = tier;
        this.units = tier.getUnits();
    }

    public int guiWidth() {
        if (type == ChemekFactoryType.COMBINER) {
            return Math.max(STANDARD_WIDTH, 36 + units * 18);
        }
        return tier == ChemekFactoryTier.ULTIMATE ? WIDE_WIDTH : STANDARD_WIDTH;
    }

    public int guiHeight() {
        return inventoryY() + 81;
    }

    public int inventoryX() {
        return (guiWidth() - 162) / 2;
    }

    public int inventoryY() {
        return switch (type) {
            case COMBINER -> 148;
            case DISSOLVER -> sharedOutputY(sharedOutputRows() * sharedOutputColumns() - 1) + 28;
            default -> 100;
        };
    }

    public int processStartX() {
        if (type == ChemekFactoryType.COMBINER) {
            return (guiWidth() - units * 18) / 2 + 2;
        }
        return switch (tier) {
            case BASIC -> 55;
            case ADVANCED -> 35;
            case ELITE -> 29;
            case ULTIMATE -> 27;
        };
    }

    public int processSpacing() {
        if (type == ChemekFactoryType.COMBINER) {
            // 生产单元彼此紧贴（宽度 = 单列输入槽宽）。
            return 18;
        }
        return switch (tier) {
            case BASIC -> 38;
            case ADVANCED -> 26;
            case ELITE, ULTIMATE -> 19;
        };
    }

    public int processX(int unit) {
        return processStartX() + unit * processSpacing();
    }

    public int inputX(int unit, int input) {
        return processX(unit);
    }

    public int inputY(int input) {
        return switch (type) {
            case COMBINER -> 28 + input * 18;
            case DISSOLVER -> 28 + input * 20;
            default -> 28;
        };
    }

    public int progressX(int unit) {
        return type == ChemekFactoryType.COMBINER ? processX(unit) + 5 : processX(unit) + 4;
    }

    public int progressY() {
        return switch (type) {
            case COMBINER -> 100;
            case DISSOLVER -> 68;
            default -> 48;
        };
    }

    public int outputX(int unit, int output) {
        return processX(unit);
    }

    public int outputY(int output) {
        return type == ChemekFactoryType.COMBINER ? 120 : 72;
    }

    public int sharedOutputX(int output) {
        int columns = sharedOutputColumns();
        return (guiWidth() - columns * 18) / 2 + output % columns * 18;
    }

    public int sharedOutputY(int output) {
        return 94 + output / sharedOutputColumns() * 18;
    }

    /** 溶解工厂共享输出列数（= 主料槽数量，每单元 1 个）。 */
    public int sharedOutputColumns() {
        return type == ChemekFactoryType.DISSOLVER ? units : 1;
    }

    /** 溶解工厂共享输出固定 3 行高。 */
    public int sharedOutputRows() {
        return type == ChemekFactoryType.DISSOLVER ? 3 : 0;
    }

    /** 顶部控件行的 y（配方选择/锁定/成品预览与输入区错开）。 */
    public int controlRowY() {
        return 4;
    }

    public int energyBarX() {
        return 8;
    }

    public int energyBarY() {
        return 16;
    }

    public int fluidGaugeX() {
        return 4;
    }

    public int fluidGaugeY() {
        return 74;
    }
}
