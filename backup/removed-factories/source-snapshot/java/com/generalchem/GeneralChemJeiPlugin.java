package com.generalchem;

import com.smashingmods.alchemistry.client.jei.RecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JEI 官方接口接入：通过 {@link IGuiContainerHandler#getGuiClickableAreas} 把
 * 工厂界面的进度箭头注册为 JEI 配方点击区域（悬停由 JEI 原生显示配方提示，
 * 点击由 JEI 原生打开对应炼金化学机器的配方页）。
 */
@JeiPlugin
public final class GeneralChemJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(GeneralChem.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ChemekFactoryScreen.class, new IGuiContainerHandler<ChemekFactoryScreen>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(ChemekFactoryScreen screen, double mouseX, double mouseY) {
                RecipeType<?> recipeType = recipeType(screen.getMenu().getFactoryType());
                List<IGuiClickableArea> areas = new ArrayList<>();
                for (Rect2i rect : screen.jeiProgressAreas()) {
                    areas.add(IGuiClickableArea.createBasic(
                            rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), recipeType));
                }
                return areas;
            }
        });
    }

    private static RecipeType<?> recipeType(ChemekFactoryType type) {
        return switch (type) {
            case COMBINER -> RecipeTypes.COMBINER;
            case DISSOLVER -> RecipeTypes.DISSOLVER;
            case COMPACTOR -> RecipeTypes.COMPACTOR;
            case LIQUIFIER -> RecipeTypes.LIQUIFIER;
            case ATOMIZER -> RecipeTypes.ATOMIZER;
        };
    }
}
