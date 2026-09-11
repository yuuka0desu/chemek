package com.generalchem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 化学模组原版锁定按钮图案（alchemylib:textures/gui/widgets.png）：
 * 锁上 (u=25, v=0)，解锁 (u=45, v=0)，尺寸 20x20。
 */
public final class AlchemyLockButton {
    public static final ResourceLocation WIDGETS = new ResourceLocation("alchemylib", "textures/gui/widgets.png");
    public static final int LOCKED_U = 25;
    public static final int UNLOCKED_U = 45;
    public static final int ICON_V = 0;
    public static final int ICON_SIZE = 20;

    private AlchemyLockButton() {
    }

    public static void drawLockIcon(GuiGraphics graphics, int x, int y, boolean locked) {
        graphics.blit(WIDGETS, x, y, locked ? LOCKED_U : UNLOCKED_U, ICON_V, ICON_SIZE, ICON_SIZE, 256, 256);
    }

    public static Component lockTooltip(boolean locked) {
        return Component.translatable(locked
                ? "alchemylib.container.unlock_recipe" : "alchemylib.container.lock_recipe");
    }
}
