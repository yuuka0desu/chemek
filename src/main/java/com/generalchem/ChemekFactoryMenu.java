package com.generalchem;

import mekanism.api.inventory.IInventorySlot;
import com.smashingmods.alchemylib.api.recipe.AbstractProcessingRecipe;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.inventory.container.sync.list.SyncableStringList;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class ChemekFactoryMenu extends MekanismTileContainer<ChemekFactoryBlockEntity> {
    public static final int STANDARD_INPUT_Y = 13;
    public static final int STANDARD_PROGRESS_Y = 33;
    public static final int STANDARD_OUTPUT_Y = 57;

    /** 配方锁按钮的 vanilla clickMenuButton 通道 ID。 */
    public static final int LOCK_BUTTON_ID = 0;
    /** 解除配方锁。 */
    public static final int UNLOCK_BUTTON_ID = 1;
    /** 配方选择通道起始 ID（后续为配方列表索引）。 */
    public static final int RECIPE_BUTTON_BASE = 1000;

    private final ChemekFactoryBlockEntity factory;
    private final FactorySlotLayout layout;
    private final FactoryGuiLayout guiLayout;
    private final List<SyncableInt> unitProgress = new ArrayList<>();
    private final SyncableInt energyUsage;
    private final List<SyncableInt> fluidSync = new ArrayList<>();
    private final SyncableBoolean recipeLocked;
    private final List<SyncableItemStack> unitPreview = new ArrayList<>();
    private final SyncableStringList recipeIdSync;
    private final SyncableStringList lockedRecipeIdSync;
    private List<String> recipeIds = new ArrayList<>();
    private String lockedRecipeId = "";

    public ChemekFactoryMenu(int containerId, Inventory playerInventory, ChemekFactoryBlockEntity factory) {
        super(GeneralChem.FACTORY_MENU_TYPE, containerId, playerInventory, factory);
        this.factory = factory;
        this.layout = factory.getSlotLayout();
        this.guiLayout = new FactoryGuiLayout(factory.getFactoryType(), factory.getTier());
        for (int unit = 0; unit < factory.getUnits(); unit++) {
            final int u = unit;
            SyncableInt sync = SyncableInt.create(() -> factory.progressPercent(u), value -> {
            });
            unitProgress.add(sync);
            track(sync);
        }
        for (int unit = 0; unit < factory.getUnits(); unit++) {
            for (int output = 0; output < factory.getSlotLayout().outputsPerUnit(); output++) {
                final int u = unit;
                final int o = output;
                SyncableItemStack preview = SyncableItemStack.create(() -> factory.previewOutput(u, o), value -> {
                });
                unitPreview.add(preview);
                track(preview);
            }
        }
        if (factory.getFactoryType() == ChemekFactoryType.DISSOLVER) {
            for (int output = 0; output < factory.getSlotLayout().sharedOutputs(); output++) {
                final int o = output;
                SyncableItemStack preview = SyncableItemStack.create(() -> factory.previewOutput(0, o), value -> {
                });
                unitPreview.add(preview);
                track(preview);
            }
        }
        energyUsage = SyncableInt.create(factory::getLastEnergyUsage, value -> {
        });
        track(energyUsage);
        recipeLocked = SyncableBoolean.create(factory::isRecipeLocked, factory::setRecipeLocked);
        track(recipeLocked);
        recipeIdSync = SyncableStringList.create(factory::getRecipeIds,
                ids -> recipeIds = new ArrayList<>(ids));
        track(recipeIdSync);
        lockedRecipeIdSync = SyncableStringList.create(factory::getLockedRecipeIdList,
                ids -> lockedRecipeId = ids.isEmpty() ? "" : ids.get(0));
        track(lockedRecipeIdSync);
        if (factory.hasFluidTank()) {
            SyncableInt amount = SyncableInt.create(() -> factory.getFluidTank().getFluidAmount(), value -> {
            });
            SyncableInt capacity = SyncableInt.create(() -> factory.getFluidTank().getCapacity(), value -> {
            });
            SyncableInt fluidId = SyncableInt.create(() -> factory.getFluidTank().isEmpty()
                    ? 0 : BuiltInRegistries.FLUID.getId(factory.getFluidTank().getFluid().getFluid()), value -> {
            });
            fluidSync.add(amount);
            fluidSync.add(capacity);
            fluidSync.add(fluidId);
            track(amount);
            track(capacity);
            track(fluidId);
        }
    }

    public ChemekFactoryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readFactory(playerInventory, buffer));
    }

    private static ChemekFactoryBlockEntity readFactory(Inventory inventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof ChemekFactoryBlockEntity factory) {
            return factory;
        }
        throw new IllegalStateException("Missing general chemistry factory at " + pos);
    }

    // 玩家物品栏槽位坐标必须与 GUI 布局一致（此方法在 super() 期间被调用，不能依赖实例字段）。

    @Override
    protected int getInventoryXOffset() {
        return new FactoryGuiLayout(tile.getFactoryType(), tile.getTier()).inventoryX();
    }

    @Override
    protected int getInventoryYOffset() {
        return new FactoryGuiLayout(tile.getFactoryType(), tile.getTier()).inventoryY();
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        // 注意：此方法在 super() 构造期间被调用，this.factory 尚未初始化，必须使用父类的 tile 字段。
        for (IInventorySlot slot : tile.getFactorySlots()) {
            addSlot(slot.createContainerSlot());
        }
    }

    public ChemekFactoryBlockEntity getFactory() {
        return factory;
    }

    public FactorySlotLayout getLayout() {
        return layout;
    }

    public ChemekFactoryType getFactoryType() {
        return factory.getFactoryType();
    }

    public int getUnits() {
        return layout.units();
    }

    public int getInputsPerUnit() {
        return layout.inputsPerUnit();
    }

    public int getOutputsPerUnit() {
        return layout.outputsPerUnit();
    }

    public int getUnitProgress(int unit) {
        return unit < unitProgress.size() ? unitProgress.get(unit).get() : 0;
    }

    public boolean isRecipeLocked() {
        return recipeLocked.get();
    }

    public ItemStack getPreviewOutput(int unit, int output) {
        FactorySlotLayout slotLayout = getLayout();
        int previewIndex = 0;
        for (int u = 0; u < unit; u++) {
            previewIndex += slotLayout.outputsPerUnit();
        }
        previewIndex += output;
        return previewIndex < unitPreview.size() ? unitPreview.get(previewIndex).get() : ItemStack.EMPTY;
    }

    public ItemStack getSharedPreviewOutput(int output) {
        int start = getUnits() * getLayout().outputsPerUnit();
        int index = start + output;
        return index < unitPreview.size() ? unitPreview.get(index).get() : ItemStack.EMPTY;
    }

    /** 输入槽 ghost 需求预览（槽空时显示配方所需物品）。 */
    public ItemStack getPreviewInput(int unit, int input) {
        return factory.previewInput(unit, input);
    }

    /** 独立成品预览槽内容：锁定/选定配方优先，其次为当前匹配配方产物。 */
    public ItemStack getProductPreview() {
        if (!lockedRecipeId.isEmpty()) {
            try {
                ResourceLocation id = new ResourceLocation(lockedRecipeId);
                for (AbstractProcessingRecipe recipe : factory.getAllRecipes()) {
                    if (recipe.getId().equals(id)) {
                        return ChemekFactoryBlockEntity.productOf(recipe, 0);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return factory.previewOutput(0, 0);
    }

    /** 当前锁定/选定配方 id（可能为空串）。 */
    public String getLockedRecipeId() {
        return lockedRecipeId;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == LOCK_BUTTON_ID) {
            if (!player.level().isClientSide()) {
                factory.setRecipeLocked(!factory.isRecipeLocked());
            }
            return true;
        }
        if (id >= RECIPE_BUTTON_BASE) {
            if (!player.level().isClientSide()) {
                int index = id - RECIPE_BUTTON_BASE;
                List<String> ids = factory.getRecipeIds();
                if (index >= 0 && index < ids.size()) {
                    ResourceLocation wanted = new ResourceLocation(ids.get(index));
                    for (var recipe : factory.getAllRecipes()) {
                        if (recipe.getId().equals(wanted)) {
                            // 对齐 alchemistry SetRecipePacket 的服务端语义：
                            // setProgress(0) -> setRecipe(recipe) -> setChanged()
                            factory.setProgress(0);
                            factory.setRecipe(recipe);
                            break;
                        }
                    }
                }
            }
            return true;
        }
        if (id == UNLOCK_BUTTON_ID) {
            if (!player.level().isClientSide()) {
                factory.setRecipeLocked(false);
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public boolean stillValid(Player player) {
        return factory.getLevel() != null
                && factory.getLevel().getBlockEntity(factory.getBlockPos()) == factory
                && super.m_6875_(player);
    }

    public int getEnergyStoredFe() {
        return (int) Math.min(Integer.MAX_VALUE, ChemekFactoryBlockEntity.joulesToFe(factory.getEnergyContainer().getEnergy()));
    }

    public int getMaxEnergyFe() {
        return (int) Math.min(Integer.MAX_VALUE, ChemekFactoryBlockEntity.joulesToFe(factory.getEnergyContainer().getMaxEnergy()));
    }

    public int getCurrentEnergyUsage() {
        return energyUsage.get();
    }

    public boolean hasFluid() {
        return !fluidSync.isEmpty();
    }

    public int getFluidAmount() {
        return fluidSync.isEmpty() ? 0 : fluidSync.get(0).get();
    }

    public int getFluidCapacity() {
        return fluidSync.isEmpty() ? 0 : fluidSync.get(1).get();
    }

    public int getFluidRegistryId() {
        return fluidSync.isEmpty() ? 0 : fluidSync.get(2).get();
    }

    public Fluid getSyncedFluid() {
        Fluid fluid = BuiltInRegistries.FLUID.byId(getFluidRegistryId());
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    public int getGuiWidth() {
        return guiLayout.guiWidth();
    }

    public int getGuiHeight() {
        return guiLayout.guiHeight();
    }

    public int getInventoryX() {
        return guiLayout.inventoryX();
    }

    public int getInventoryY() {
        return guiLayout.inventoryY();
    }

    public int getProcessStartX() {
        return guiLayout.processStartX();
    }

    public int getProcessSpacing() {
        return guiLayout.processSpacing();
    }

    public int getProcessX(int unit) {
        return guiLayout.processX(unit);
    }

    public int getInputX(int unit, int input) {
        return guiLayout.inputX(unit, input);
    }

    public int getInputY(int input) {
        return guiLayout.inputY(input);
    }

    public int getProgressX(int unit) {
        return guiLayout.progressX(unit);
    }

    public int getProgressY() {
        return guiLayout.progressY();
    }

    public int getOutputX(int unit, int output) {
        return guiLayout.outputX(unit, output);
    }

    public int getOutputY(int output) {
        return guiLayout.outputY(output);
    }

    public int getSharedOutputX(int output) {
        return guiLayout.sharedOutputX(output);
    }

    public int getSharedOutputY(int output) {
        return guiLayout.sharedOutputY(output);
    }

    public int getEnergyBarX() {
        return guiLayout.energyBarX();
    }

    public int getEnergyBarY() {
        return guiLayout.energyBarY();
    }

    public int getFluidGaugeX() {
        return guiLayout.fluidGaugeX();
    }

    public int getFluidGaugeY() {
        return guiLayout.fluidGaugeY();
    }

    public int controlRowY() {
        return guiLayout.controlRowY();
    }

    public List<String> getRecipeIds() {
        return recipeIds;
    }
}
