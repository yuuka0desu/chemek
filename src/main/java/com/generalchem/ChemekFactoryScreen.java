package com.generalchem;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 化工厂屏幕：继承 Mekanism 的 {@link GuiConfigurableTile}，侧配置、物流
 * （颜色/严格输入/自动输出）、安全、红石、升级页签及其窗口弹窗全部由
 * Mekanism 原生 GUI 组件渲染与交互。
 */
public final class ChemekFactoryScreen extends GuiConfigurableTile<ChemekFactoryBlockEntity, ChemekFactoryMenu> {
    private final List<GuiProgress> progressElements = new ArrayList<>();

    public ChemekFactoryScreen(ChemekFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.getGuiWidth();
        this.imageHeight = menu.getGuiHeight();
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        ChemekFactoryBlockEntity factory = menu.getFactory();
        FactorySlotLayout layout = menu.getLayout();

        for (int unit = 0; unit < layout.units(); unit++) {
            for (int input = 0; input < layout.inputsPerUnit(); input++) {
                SlotType slotType = menu.getFactoryType() == ChemekFactoryType.DISSOLVER && input == 1
                        ? SlotType.INPUT_2 : SlotType.INPUT;
                final int u = unit;
                final int i = input;
                // Mek 惯例：GuiSlot 元素坐标 = 容器槽位坐标 - 1（槽框贴图从 (x-1, y-1) 起，vanilla 槽位渲染在 (x, y)）。
                addElement(new GuiSlot(slotType, this, menu.getInputX(unit, input) - 1, menu.getInputY(input) - 1)
                        .stored(() -> menu.getPreviewInput(u, i)));
            }
            for (int output = 0; output < layout.outputsPerUnit(); output++) {
                final int u = unit;
                final int o = output;
                addElement(new GuiSlot(SlotType.OUTPUT, this,
                        menu.getOutputX(unit, output) - 1, menu.getOutputY(output) - 1)
                        .stored(() -> menu.getPreviewOutput(u, o)));
            }
            final int progressUnit = unit;
            GuiProgress progress = addElement(new GuiProgress(() -> menu.getUnitProgress(progressUnit) / 100.0,
                    ProgressType.DOWN, this, menu.getProgressX(unit), menu.getProgressY()));
            progressElements.add(progress);
        }
        for (int output = 0; output < layout.sharedOutputs(); output++) {
            final int o = output;
            addElement(new GuiSlot(SlotType.OUTPUT, this,
                    menu.getSharedOutputX(output) - 1, menu.getSharedOutputY(output) - 1)
                    .stored(() -> menu.getSharedPreviewOutput(o)));
        }

        // Mek 原版垂直电量条（GuiVerticalPowerBar：原版尺寸 4x52），置于 GUI 左侧。
        addElement(new GuiVerticalPowerBar(this, factory.getEnergyContainer(),
                menu.getEnergyBarX(), menu.getEnergyBarY()));

        if (menu.hasFluid()) {
            addElement(new GuiFluidGauge(
                    () -> factory.getFluidTank(),
                    () -> List.of(factory.getFluidTank()),
                    GaugeType.STANDARD, this, menu.getFluidGaugeX(), menu.getFluidGaugeY()));
        }

        // 玩家物品栏槽框（3 行 + 快捷栏），与容器槽位使用同一坐标来源。
        int invX = menu.getInventoryX();
        int invY = menu.getInventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addElement(new GuiSlot(SlotType.NORMAL, this, invX + col * 18 - 1, invY + row * 18 - 1));
            }
        }
        for (int col = 0; col < 9; col++) {
            addElement(new GuiSlot(SlotType.NORMAL, this, invX + col * 18 - 1, invY + 58 - 1));
        }
    }

    /** 对齐 alchemylib RecipeSelectorButton 的打开/关闭流程（pushGuiLayer/popGuiLayer + recipeSelectorOpen）。 */
    private void toggleRecipeSelector() {
        ChemekFactoryBlockEntity factory = menu.getFactory();
        if (factory.isRecipeSelectorOpen()) {
            minecraft.popGuiLayer();
            factory.setRecipeSelectorOpen(false);
        } else {
            factory.setRecipeSelectorOpen(true);
            minecraft.pushGuiLayer(new GeneralChemRecipeSelectorScreen(menu, topPos));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.m_7286_(graphics, partialTick, mouseX, mouseY);
    }

    /** 供 JEI 官方接口（IGuiContainerHandler）注册点击区域：进度箭头的 GUI 相对坐标。 */
    public List<Rect2i> jeiProgressAreas() {
        List<Rect2i> areas = new ArrayList<>(progressElements.size());
        for (GuiProgress element : progressElements) {
            areas.add(new Rect2i(element.getRelativeX(), element.getRelativeY(),
                    element.getWidth(), element.getHeight()));
        }
        return areas;
    }

    public ChemekFactoryMenu getMenu() {
        return menu;
    }

    // ===== 顶部控件行（锁定 / 配方选择 / 成品预览）：直接以化学模组原版图案绘制 =====

    private static final int LOCK_OFFSET_X = 82;
    private static final int BOOK_OFFSET_X = 54;
    private static final int PREVIEW_OFFSET_X = 30;

    private int lockX() {
        return leftPos + imageWidth - LOCK_OFFSET_X;
    }

    private int bookX() {
        return leftPos + imageWidth - BOOK_OFFSET_X;
    }

    private int previewX() {
        return leftPos + imageWidth - PREVIEW_OFFSET_X;
    }

    private int controlY() {
        return topPos + menu.controlRowY() - 2;
    }

    private int previewY() {
        return topPos + menu.controlRowY() - 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawControlRow(graphics, mouseX, mouseY);
    }

    private void drawControlRow(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean locked = menu.isRecipeLocked();
        boolean selectorOpen = menu.getFactory().isRecipeSelectorOpen();

        // 成品预览槽（最右）。
        graphics.blit(SlotType.NORMAL.getTexture(), previewX(), previewY(), 0, 0, 18, 18, 18, 18);
        ItemStack preview = menu.getProductPreview();
        if (!preview.isEmpty()) {
            graphics.renderFakeItem(preview, previewX() + 1, previewY() + 1);
        }
        // 配方选择按钮（仅化合与压缩工厂；化学模组原版图案 + 名称）。
        if (hasRecipeSelector()) {
            AlchemyRecipeBookButton.drawIcon(graphics, bookX(), controlY(), selectorOpen);
        }
        // 锁定按钮（化学模组原版锁图案）。
        AlchemyLockButton.drawLockIcon(graphics, lockX(), controlY(), locked);

        // 悬停提示。
        if (isOver(mouseX, mouseY, previewX(), previewY(), 18, 18)) {
            graphics.renderTooltip(font, Component.translatable("chemek.gui.product_preview"), mouseX, mouseY);
        } else if (hasRecipeSelector() && isOver(mouseX, mouseY, bookX(), controlY(),
                AlchemyRecipeBookButton.ICON_SIZE, AlchemyRecipeBookButton.ICON_SIZE)) {
            graphics.renderTooltip(font, AlchemyRecipeBookButton.buttonName(selectorOpen), mouseX, mouseY);
        } else if (isOver(mouseX, mouseY, lockX(), controlY(), AlchemyLockButton.ICON_SIZE, AlchemyLockButton.ICON_SIZE)) {
            graphics.renderTooltip(font, AlchemyLockButton.lockTooltip(locked), mouseX, mouseY);
        }
    }

    /** 仅化合工厂与压缩工厂提供配方选择页。 */
    private boolean hasRecipeSelector() {
        return menu.getFactoryType() == ChemekFactoryType.COMBINER
                || menu.getFactoryType() == ChemekFactoryType.COMPACTOR;
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hasRecipeSelector() && isOver(mouseX, mouseY, bookX(), controlY(),
                    AlchemyRecipeBookButton.ICON_SIZE, AlchemyRecipeBookButton.ICON_SIZE)) {
                toggleRecipeSelector();
                return true;
            }
            if (isOver(mouseX, mouseY, lockX(), controlY(),
                    AlchemyLockButton.ICON_SIZE, AlchemyLockButton.ICON_SIZE)) {
                minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(
                        menu.containerId, ChemekFactoryMenu.LOCK_BUTTON_ID));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 6, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, menu.getInventoryX(),
                menu.getInventoryY() - 10, 0x404040, false);
    }
}
