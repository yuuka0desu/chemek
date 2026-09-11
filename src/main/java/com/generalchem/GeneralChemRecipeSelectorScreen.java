package com.generalchem;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smashingmods.alchemistry.client.container.RecipeDisplayUtil;
import com.smashingmods.alchemylib.api.recipe.AbstractProcessingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Optional;

/**
 * 配方选择页：按炼金化学原版 RecipeSelectorScreen 1:1 复刻
 * （同一贴图 alchemistry:textures/gui/recipe_select_gui.png、同一布局常量、
 * 同一滚动/搜索/预览逻辑，并复用原版 RecipeDisplayUtil 工具链）。
 * 选择动作走容器 clickMenuButton 通道，服务端执行 ProcessingBlockEntity#setRecipe 语义。
 */
public final class GeneralChemRecipeSelectorScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("alchemistry", "textures/gui/recipe_select_gui.png");
    private static final int IMAGE_WIDTH = 184;
    private static final int IMAGE_HEIGHT = 162;
    private static final int MAX_DISPLAYED_RECIPES = 30;
    private static final int COLUMNS = 5;

    private final ChemekFactoryMenu menu;
    private final int parentTopPos;
    private final LinkedList<AbstractProcessingRecipe> recipes = new LinkedList<>();
    private final LinkedList<AbstractProcessingRecipe> displayedRecipes = new LinkedList<>();
    private final EditBox searchBox;

    private int leftPos;
    private int topPos;
    private int recipeBoxLeftPos;
    private int recipeBoxTopPos;
    private float scrollOffset;
    private boolean scrolling;
    private int startIndex;

    public GeneralChemRecipeSelectorScreen(ChemekFactoryMenu menu, int parentTopPos) {
        super(Component.empty());
        this.menu = menu;
        this.parentTopPos = parentTopPos;
        this.searchBox = new EditBox(Minecraft.getInstance().font, 0, 0, 92, 12, Component.empty());
        this.searchBox.setValue(menu.getFactory().getSearchText());
        this.searchBox.setResponder(query -> {
            menu.getFactory().setSearchText(query);
            searchRecipeList(query);
        });
        this.recipes.addAll(menu.getFactory().getAllRecipes());
        if (!menu.getFactory().getSearchText().isEmpty()) {
            searchRecipeList(menu.getFactory().getSearchText());
        }
    }

    @Override
    protected void init() {
        leftPos = (width - IMAGE_WIDTH) / 2;
        topPos = parentTopPos;
        recipeBoxLeftPos = leftPos + 58;
        recipeBoxTopPos = topPos + 26;
        searchBox.setX(leftPos + 58);
        searchBox.setY(topPos + 11);
        searchBox.setHint(Component.translatable("alchemistry.container.search"));
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);
        if (displayedRecipes.isEmpty()) {
            resetDisplayedRecipes();
        }
    }

    public void resetDisplayedRecipes() {
        displayedRecipes.clear();
        displayedRecipes.addAll(recipes);
        displayedRecipes.sort(Comparator.comparing(AbstractProcessingRecipe::getId,
                ResourceLocation::compareNamespaced));
    }

    private void searchRecipeList(String query) {
        displayedRecipes.clear();
        String needle = query.toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            resetDisplayedRecipes();
            return;
        }
        LinkedList<AbstractProcessingRecipe> matched = new LinkedList<>();
        for (AbstractProcessingRecipe recipe : menu.getFactory().getAllRecipes()) {
            if (matchesSearch(recipe, needle)) {
                matched.add(recipe);
            }
        }
        displayedRecipes.addAll(matched);
    }

    private static boolean matchesSearch(AbstractProcessingRecipe recipe, String needle) {
        var pair = RecipeDisplayUtil.getSearchablePair(recipe);
        ResourceLocation id = pair.getLeft();
        String name = pair.getRight();
        if (needle.charAt(0) == '@') {
            if (needle.contains(" ")) {
                String[] parts = needle.split(" ");
                if (parts.length > 1) {
                    return id.getNamespace().contains(parts[0].substring(1))
                            && id.getPath().contains(parts[1]);
                }
                return id.getNamespace().contains(needle.substring(1));
            }
            return id.getNamespace().contains(needle.substring(1));
        }
        return name.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Nullable
    private AbstractProcessingRecipe currentRecipe() {
        String locked = menu.getLockedRecipeId();
        if (locked.isEmpty()) {
            return null;
        }
        for (AbstractProcessingRecipe recipe : recipes) {
            if (recipe.getId().toString().equals(locked)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean isScrollBarActive() {
        return displayedRecipes.size() > MAX_DISPLAYED_RECIPES;
    }

    private int getOffscreenRows() {
        return (displayedRecipes.size() + 6 - 1) / 6 - 3;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBg(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRecipeBox(graphics, mouseX, mouseY);
        // 化学模组原版锁定图案（选定配方界面同样可切换锁定）。
        AlchemyLockButton.drawLockIcon(graphics, leftPos + 6, topPos + 6, menu.isRecipeLocked());
        if (mouseX >= leftPos + 6 && mouseX < leftPos + 6 + AlchemyLockButton.ICON_SIZE
                && mouseY >= topPos + 6 && mouseY < topPos + 6 + AlchemyLockButton.ICON_SIZE) {
            graphics.renderTooltip(font, AlchemyLockButton.lockTooltip(menu.isRecipeLocked()), mouseX, mouseY);
        }
    }

    private void renderBg(GuiGraphics graphics) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    private void renderRecipeBox(GuiGraphics graphics, int mouseX, int mouseY) {
        int endIndex = startIndex + MAX_DISPLAYED_RECIPES;
        renderScrollbar(graphics);
        renderRecipeButtons(graphics, mouseX, mouseY, endIndex);
        renderRecipeButtonItems(graphics, mouseX, mouseY, endIndex);
        renderCurrentRecipe(graphics, mouseX, mouseY);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int y = (int) (93.0F * scrollOffset);
        graphics.blit(TEXTURE, leftPos + 154, topPos + 28 + y + (isScrollBarActive() ? 0 : 12),
                162, 12, 12, 15);
    }

    private void renderRecipeButtons(GuiGraphics graphics, int mouseX, int mouseY, int endIndex) {
        for (int index = startIndex; index < endIndex && index < displayedRecipes.size(); index++) {
            int local = index - startIndex;
            int x = recipeBoxLeftPos + local % COLUMNS * 18;
            int y = recipeBoxTopPos + local / COLUMNS * 18 + 2;
            int v = 162;
            if (index == displayedRecipes.indexOf(currentRecipe())) {
                v += 18;
            } else if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                v += 36;
            }
            graphics.blit(TEXTURE, x, y, 0, v, 18, 18);
        }
    }

    private void renderRecipeButtonItems(GuiGraphics graphics, int mouseX, int mouseY, int endIndex) {
        for (int index = startIndex; index < endIndex && index < displayedRecipes.size(); index++) {
            int local = index - startIndex;
            ItemStack target = RecipeDisplayUtil.getTarget(displayedRecipes.get(index));
            int x = recipeBoxLeftPos + local % COLUMNS * 18 + 1;
            int y = recipeBoxTopPos + local / COLUMNS * 18 + 3;
            renderFloatingItem(graphics, target, x, y);
            if (mouseX >= x - 1 && mouseX <= x + 16 && mouseY >= y - 1 && mouseY <= y + 16) {
                graphics.renderTooltip(font, RecipeDisplayUtil.getItemTooltipComponent(target,
                        Component.translatable("alchemistry.container.select_recipe")),
                        Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private void renderCurrentRecipe(GuiGraphics graphics, int mouseX, int mouseY) {
        AbstractProcessingRecipe recipe = currentRecipe();
        if (recipe != null) {
            recipeLooper((index, size, x, y) -> {
                if (inputStack(index).isEmpty()) {
                    renderSlot(graphics, x, y);
                    renderFloatingItem(graphics, RecipeDisplayUtil.getRecipeInputByIndex(recipe, index), x + 1, y + 1);
                }
            });
            recipeLooper((index, size, x, y) -> {
                if (inputStack(index).isEmpty()) {
                    ItemStack input = RecipeDisplayUtil.getRecipeInputByIndex(recipe, index);
                    if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17
                            && !input.isEmpty()) {
                        graphics.renderTooltip(font, RecipeDisplayUtil.getItemTooltipComponent(input,
                                Component.translatable("alchemistry.container.required_input")),
                                Optional.empty(), mouseX, mouseY);
                    }
                }
            });
            ItemStack target = RecipeDisplayUtil.getTarget(recipe);
            renderFloatingItem(graphics, target, leftPos + 21, topPos + 30);
            if (mouseX >= leftPos + 17 && mouseX < leftPos + 41
                    && mouseY >= topPos + 27 && mouseY <= topPos + 50) {
                graphics.renderTooltip(font, RecipeDisplayUtil.getItemTooltipComponent(target,
                        Component.translatable("alchemistry.container.current_recipe")),
                        Optional.empty(), mouseX, mouseY);
            }
        } else {
            recipeLooper((index, size, x, y) -> renderSlot(graphics, x, y));
        }
    }

    private void recipeLooper(RecipeLoopConsumer consumer) {
        int inputSize = menu.getInputsPerUnit();
        int cols = inputSize / 2 + inputSize % 2;
        int rows = inputSize / 2 + inputSize % 2;
        int baseX = cols == 1 ? leftPos + 20 : leftPos + 11;
        int baseY = topPos + 59;
        for (int row = 0; row < cols; row++) {
            for (int col = 0; col < rows; col++) {
                int index = col + row * 2;
                consumer.accept(index, inputSize, baseX + col * 18, baseY + row * 18);
            }
        }
    }

    private void renderSlot(GuiGraphics graphics, int x, int y) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TEXTURE, x, y, 0, 216, 18, 18);
    }

    private void renderFloatingItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderFakeItem(stack, x, y);
    }

    private ItemStack inputStack(int index) {
        return menu.getFactory().inputStack(0, index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 6 && mouseX < leftPos + 6 + AlchemyLockButton.ICON_SIZE
                && mouseY >= topPos + 6 && mouseY < topPos + 6 + AlchemyLockButton.ICON_SIZE) {
            sendButton(ChemekFactoryMenu.LOCK_BUTTON_ID);
            return true;
        }
        // 选择配方格（与原版一致：锁定时不可选择新配方）。
        int endIndex = startIndex + MAX_DISPLAYED_RECIPES;
        for (int index = startIndex; index < endIndex && index < displayedRecipes.size(); index++) {
            int local = index - startIndex;
            double dx = mouseX - (recipeBoxLeftPos + local % COLUMNS * 18);
            double dy = mouseY - (recipeBoxTopPos + local / COLUMNS * 18);
            if (dx > 0 && dx <= 19 && dy > 0 && dy <= 19
                    && !menu.isRecipeLocked()) {
                AbstractProcessingRecipe recipe = displayedRecipes.get(index);
                int originalIndex = menu.getRecipeIds().indexOf(recipe.getId().toString());
                if (originalIndex >= 0) {
                    sendButton(ChemekFactoryMenu.RECIPE_BUTTON_BASE + originalIndex);
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                }
                return true;
            }
        }
        // 滚动条区域按下进入拖动。
        int barX = leftPos + 154;
        int barTop = topPos + 28;
        if (mouseX >= barX && mouseX < barX + 12 && mouseY >= barTop && mouseY < barTop + 108) {
            scrolling = true;
        } else {
            scrolling = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling && isScrollBarActive()) {
            int top = topPos + 28;
            int bottom = top + 108;
            scrollOffset = Mth.clamp((float) (mouseY - top - 7.5) / (bottom - top - 15.0F), 0.0F, 1.0F);
            startIndex = (int) (scrollOffset * getOffscreenRows() + 0.5) * COLUMNS;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftPos && mouseX < leftPos + IMAGE_WIDTH
                && mouseY >= topPos && mouseY < topPos + IMAGE_HEIGHT
                && isScrollBarActive()) {
            int offscreenRows = getOffscreenRows();
            scrollOffset = Mth.clamp(scrollOffset - (float) delta / offscreenRows, 0.0F, 1.0F);
            startIndex = (int) (scrollOffset * offscreenRows + 0.5) * COLUMNS;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 69 && searchBox.isFocused()) {
            return false;
        }
        if (keyCode == 258 && !searchBox.isFocused()) {
            searchBox.setFocused(true);
            searchBox.setCanLoseFocus(true);
            searchBox.active = true;
        } else if (keyCode == 256 && searchBox.isFocused()) {
            searchBox.setFocused(false);
            searchBox.setCanLoseFocus(false);
            searchBox.active = false;
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId, id));
        }
    }

    @Override
    public void onClose() {
        menu.getFactory().setRecipeSelectorOpen(false);
        if (minecraft != null) {
            minecraft.popGuiLayer();
        }
    }

    @FunctionalInterface
    private interface RecipeLoopConsumer {
        void accept(int index, int size, int x, int y);
    }
}
