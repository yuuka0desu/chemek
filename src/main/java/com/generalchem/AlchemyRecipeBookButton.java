package com.generalchem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 化学模组原版配方选择按钮图案（alchemylib:textures/gui/widgets.png）：
 * 关闭状态 (u=45, v=60)，打开状态 (u=25, v=80)，尺寸 20x20，
 * 名称为原版 "Open Recipe Selection" / "Close Recipe Selection"。
 */
public final class AlchemyRecipeBookButton {
    public static final int CLOSED_U = 45;
    public static final int CLOSED_V = 60;
    public static final int OPEN_U = 25;
    public static final int OPEN_V = 80;
    public static final int ICON_SIZE = 20;

    private AlchemyRecipeBookButton() {
    }

    public static void drawIcon(GuiGraphics graphics, int x, int y, boolean open) {
        graphics.blit(AlchemyLockButton.WIDGETS, x, y,
                open ? OPEN_U : CLOSED_U, open ? OPEN_V : CLOSED_V, ICON_SIZE, ICON_SIZE, 256, 256);
    }

    public static Component buttonName(boolean open) {
        return Component.translatable(open
                ? "alchemylib.container.close_recipe_select" : "alchemylib.container.open_recipe_select");
    }
}
