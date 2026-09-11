package com.generalchem;

import com.smashingmods.alchemistry.common.recipe.compactor.CompactorRecipe;
import com.smashingmods.alchemistry.common.recipe.combiner.CombinerRecipe;
import com.smashingmods.alchemistry.common.recipe.dissolver.DissolverRecipe;
import com.smashingmods.alchemistry.registry.RecipeRegistry;
import com.smashingmods.alchemylib.api.blockentity.processing.ProcessingBlockEntity;
import com.smashingmods.alchemylib.api.blockentity.processing.SearchableBlockEntity;
import com.smashingmods.alchemylib.api.recipe.AbstractProcessingRecipe;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.security.SecurityMode;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.EnergySlotInfo;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Mekanism 原生化工厂方块实体：侧面配置、物流颜色/严格输入、自动输出、升级、
 * 安全模式与红石控制全部由 Mekanism 的 {@link TileComponentConfig}、
 * {@link TileComponentEjector}、{@link TileComponentUpgrade}、
 * {@link TileComponentSecurity} 与红石页签直接驱动。
 */
public final class ChemekFactoryBlockEntity extends TileEntityMekanism implements ISideConfiguration, MenuProvider, ProcessingBlockEntity, SearchableBlockEntity {
    public static final int MAX_UPGRADES_PER_TYPE = 8;

    private final ChemekFactoryType type;
    private final ChemekFactoryTier tier;
    private final int units;
    private final FactorySlotLayout layout;

    private TileComponentConfig configComponent;
    private TileComponentEjector ejectorComponent;

    private List<IInventorySlot> inputSlots;
    private List<IInventorySlot> outputSlots;
    private List<IInventorySlot> mainInputSlots;
    private List<IInventorySlot> catalystSlots;
    private IInventorySlot[] unitSlots;

    private IExtendedFluidTank fluidTank;
    private MachineEnergyContainer<ChemekFactoryBlockEntity> energyContainer;

    private final List<List<ItemStack>> dissolverPending = new ArrayList<>();
    private final float[] progress;
    private final boolean[] pulseArmed;
    private int lastEnergyUsage;

    private boolean recipeLocked;
    @Nullable
    private ResourceLocation lockedRecipeId;
    private boolean paused;
    private boolean canProcess;
    private boolean recipeSelectorOpen;
    private String searchText = "";

    public ChemekFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(new FactoryBlockProvider(state.getBlock()), pos, state);
        ChemekFactoryBlock block = (ChemekFactoryBlock) state.getBlock();
        this.type = block.getFactoryType();
        this.tier = block.getTier();
        this.units = tier.getUnits();
        this.layout = new FactorySlotLayout(type, units);
        this.progress = new float[units];
        this.pulseArmed = new boolean[units];
        for (int unit = 0; unit < units; unit++) {
            dissolverPending.add(new ArrayList<>());
        }
        for (int unit = 0; unit < units; unit++) {
            for (int input = 0; input < layout.inputsPerUnit(); input++) {
                inputSlots.add(unitSlots[layout.unitInputSlot(unit, input)]);
            }
        }

        configComponent = new TileComponentConfig(this, supportedTransmissions());
        configureSides();
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, ejectTransmissions());
        // 注意：TileComponentConfig/TileComponentEjector 构造器内部已自注册 addComponent。
    }

    public static FloatingLong feToJoules(long fe) {
        return IEnergyConversionHelper.INSTANCE.feConversion().convertFrom(FloatingLong.create(fe));
    }

    public static long joulesToFe(FloatingLong joules) {
        return IEnergyConversionHelper.INSTANCE.feConversion().convertToAsLong(joules);
    }

    private void syncState() {
        setChanged();
    }

    private TransmissionType[] supportedTransmissions() {
        return hasFluidTank()
                ? new TransmissionType[]{TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.ENERGY}
                : new TransmissionType[]{TransmissionType.ITEM, TransmissionType.ENERGY};
    }

    private TransmissionType[] ejectTransmissions() {
        return new TransmissionType[]{TransmissionType.ITEM};
    }

    private void configureSides() {
        ConfigInfo itemInfo = configComponent.getConfig(TransmissionType.ITEM);
        itemInfo.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, new ArrayList<>(inputSlots)));
        itemInfo.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, new ArrayList<>(outputSlots)));
        List<IInventorySlot> ioSlots = new ArrayList<>(inputSlots);
        ioSlots.addAll(outputSlots);
        itemInfo.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, ioSlots));
        if (type == ChemekFactoryType.DISSOLVER) {
            itemInfo.addSlotInfo(DataType.INPUT_1, new InventorySlotInfo(true, false, new ArrayList<>(mainInputSlots)));
            itemInfo.addSlotInfo(DataType.INPUT_2, new InventorySlotInfo(true, false, new ArrayList<>(catalystSlots)));
        }

        if (type == ChemekFactoryType.DISSOLVER) {
            itemInfo.setDataType(DataType.INPUT_1, RelativeSide.TOP);
            itemInfo.setDataType(DataType.INPUT_2, RelativeSide.LEFT);
        } else {
            itemInfo.setDataType(DataType.INPUT, RelativeSide.TOP);
        }
        itemInfo.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);

        ConfigInfo energyInfo = configComponent.getConfig(TransmissionType.ENERGY);
        energyInfo.addSlotInfo(DataType.ENERGY, new EnergySlotInfo(true, false, energyContainer));
        energyInfo.setDataType(DataType.ENERGY, RelativeSide.BACK);

        if (hasFluidTank()) {
            ConfigInfo fluidInfo = configComponent.getConfig(TransmissionType.FLUID);
            if (type.hasFluidInput()) {
                fluidInfo.addSlotInfo(DataType.INPUT, new FluidSlotInfo(true, false, fluidTank));
                fluidInfo.setDataType(DataType.INPUT, RelativeSide.TOP);
            }
            if (type.hasFluidOutput()) {
                fluidInfo.addSlotInfo(DataType.OUTPUT, new FluidSlotInfo(false, true, fluidTank));
                fluidInfo.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            }
        }
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        ChemekFactoryBlock block = (ChemekFactoryBlock) getBlockState().getBlock();
        ChemekFactoryType blockType = block.getFactoryType();
        ChemekFactoryTier blockTier = block.getTier();
        FactorySlotLayout blockLayout = new FactorySlotLayout(blockType, blockTier.getUnits());
        FactoryGuiLayout gui = new FactoryGuiLayout(blockType, blockTier);
        IInventorySlot[] slots = new IInventorySlot[blockLayout.upgradeInputSlot()];
        this.unitSlots = slots;
        this.inputSlots = new ArrayList<>();
        this.outputSlots = new ArrayList<>();
        this.mainInputSlots = new ArrayList<>();
        this.catalystSlots = new ArrayList<>();

        InventorySlotHelper helper = InventorySlotHelper.forSide(this::getDirection);
        for (int unit = 0; unit < blockLayout.units(); unit++) {
            for (int input = 0; input < blockLayout.inputsPerUnit(); input++) {
                IInventorySlot slot = BasicInventorySlot.at(BasicInventorySlot.alwaysTrueBi,
                        BasicInventorySlot.alwaysTrueBi, listener,
                        gui.inputX(unit, input), gui.inputY(input));
                slots[blockLayout.unitInputSlot(unit, input)] = slot;
                helper.addSlot(slot);
                if (blockType == ChemekFactoryType.DISSOLVER) {
                    if (input == 0) {
                        mainInputSlots.add(slot);
                    } else {
                        catalystSlots.add(slot);
                    }
                }
            }
            for (int output = 0; output < blockLayout.outputsPerUnit(); output++) {
                IInventorySlot slot = BasicInventorySlot.at(BasicInventorySlot.alwaysFalse, listener,
                        gui.outputX(unit, output), gui.outputY(output));
                slots[blockLayout.unitOutputSlot(unit, output)] = slot;
                helper.addSlot(slot);
                outputSlots.add(slot);
            }
        }
        for (int output = 0; output < blockLayout.sharedOutputs(); output++) {
            IInventorySlot slot = BasicInventorySlot.at(BasicInventorySlot.alwaysFalse, listener,
                    gui.sharedOutputX(output), gui.sharedOutputY(output));
            slots[blockLayout.sharedOutputSlot(output)] = slot;
            helper.addSlot(slot);
            outputSlots.add(slot);
        }
        return helper.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        ChemekFactoryBlock block = (ChemekFactoryBlock) getBlockState().getBlock();
        ChemekFactoryType blockType = block.getFactoryType();
        FluidTankHelper helper = FluidTankHelper.forSide(this::getDirection);
        if (blockType.hasFluidInput() || blockType.hasFluidOutput()) {
            int capacity = blockType.getFluidTankBuckets() * 1_000 * block.getTier().getUnits() / 3;
            fluidTank = BasicFluidTank.create(capacity, fluid -> true, listener);
            helper.addTank(fluidTank);
        }
        return helper.build();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        energyContainer = MachineEnergyContainer.input(this, listener);
        EnergyContainerHelper helper = EnergyContainerHelper.forSide(this::getDirection);
        helper.addContainer(energyContainer);
        return helper.build();
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        updatePulseArming();
        if (upgradeComponent != null) {
            upgradeComponent.tickServer();
        }
        ejectorComponent.tickServer();
        lastEnergyUsage = 0;
        tickUnits();
    }

    private void updatePulseArming() {
        if (getControlType() == RedstoneControl.PULSE && isPowered() && !wasPowered()) {
            Arrays.fill(pulseArmed, true);
        }
    }

    @Override
    public void recalculateUpgrades(Upgrade upgrade) {
        if (upgrade == Upgrade.ENERGY) {
            energyContainer.setMaxEnergy(feToJoules(energyCapacity()));
        }
    }

    private int baseCapacity() {
        return tier.getEnergyCapacity();
    }

    private int energyCapacity() {
        return (int) Math.min(Integer.MAX_VALUE,
                baseCapacity() * Math.pow(GeneralChemConfig.ENERGY_UPGRADE_CAPACITY_FACTOR.get(), getEnergyUpgrades()));
    }

    private double energyFactor() {
        return (1.0 + getSpeedUpgrades() * GeneralChemConfig.SPEED_UPGRADE_ENERGY_FACTOR.get())
                * Math.pow(GeneralChemConfig.ENERGY_UPGRADE_ENERGY_FACTOR.get(), getEnergyUpgrades());
    }

    private double progressPerTick() {
        return 1.0 + getSpeedUpgrades() * GeneralChemConfig.SPEED_UPGRADE_FACTOR.get();
    }

    public int getSpeedUpgrades() {
        return upgradeComponent == null ? 0 : upgradeComponent.getUpgrades(Upgrade.SPEED);
    }

    public int getEnergyUpgrades() {
        return upgradeComponent == null ? 0 : upgradeComponent.getUpgrades(Upgrade.ENERGY);
    }

    private boolean redstoneAllows(int unit) {
        return switch (getControlType()) {
            case DISABLED -> true;
            case HIGH -> isPowered();
            case LOW -> !isPowered();
            case PULSE -> pulseArmed[unit];
        };
    }

    private void tickUnits() {
        if (paused) {
            return;
        }
        float maxProgress = GeneralChemConfig.MAX_PROGRESS.get();
        for (int unit = 0; unit < units; unit++) {
            if (!redstoneAllows(unit) || !hasValidInput(unit) || !canOutput(unit)) {
                continue;
            }
            FloatingLong cost = feToJoules(getCurrentUnitEnergyCost());
            if (energyContainer.getEnergy().smallerThan(cost)) {
                continue;
            }
            energyContainer.extract(cost, Action.EXECUTE, AutomationType.INTERNAL);
            lastEnergyUsage += getCurrentUnitEnergyCost();
            progress[unit] += (float) progressPerTick();
            if (progress[unit] >= maxProgress) {
                progress[unit] = 0;
                processRecipe(unit);
                if (getControlType() == RedstoneControl.PULSE) {
                    pulseArmed[unit] = false;
                }
            }
        }
        canProcess = units > 0 && hasAnyValidInput();
    }

    private boolean hasAnyValidInput() {
        for (int unit = 0; unit < units; unit++) {
            if (hasValidInput(unit)) {
                return true;
            }
        }
        return false;
    }

    public int getCurrentUnitEnergyCost() {
        return (int) Math.ceil(type.energyPerTick() * energyFactor());
    }

    public int getLastEnergyUsage() {
        return lastEnergyUsage;
    }

    public int progressPercent(int unit) {
        return (int) (progress[unit] / GeneralChemConfig.MAX_PROGRESS.get() * 100);
    }

    // ===== 配方逻辑（与旧实现一致，改为 Mek 槽位 API） =====

    private boolean hasValidInput(int unit) {
        return switch (type) {
            case COMBINER -> matchingCombinerRecipe(unit) != null;
            case DISSOLVER -> dissolverPending.get(unit).isEmpty() && matchingDissolverRecipe(unit) != null;
            case COMPACTOR -> matchingCompactorRecipe(unit) != null;
        };
    }

    private boolean canOutput(int unit) {
        return switch (type) {
            case COMBINER -> {
                CombinerRecipe recipe = matchingCombinerRecipe(unit);
                yield recipe != null && canInsertUnitOutput(unit, recipe.getOutput());
            }
            case DISSOLVER -> dissolverPending.get(unit).isEmpty();
            case COMPACTOR -> {
                CompactorRecipe recipe = matchingCompactorRecipe(unit);
                yield recipe != null && canInsertUnitOutput(unit, recipe.getOutput());
            }
        };
    }

    private boolean canInsertUnitOutput(int unit, ItemStack output) {
        ItemStack remaining = output.copy();
        for (int i = 0; i < layout.outputsPerUnit() && !remaining.isEmpty(); i++) {
            IInventorySlot slot = unitSlots[layout.unitOutputSlot(unit, i)];
            ItemStack existing = slot.getStack();
            if (existing.isEmpty()) {
                remaining.shrink(Math.min(remaining.getCount(), remaining.getMaxStackSize()));
            } else if (ItemStack.isSameItemSameTags(existing, remaining)) {
                remaining.shrink(Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount()));
            }
        }
        return remaining.isEmpty();
    }

    private void processRecipe(int unit) {
        switch (type) {
            case COMBINER -> processCombiner(unit);
            case DISSOLVER -> processDissolver(unit);
            case COMPACTOR -> processCompactor(unit);
        }
    }

    @Nullable
    private CombinerRecipe matchingCombinerRecipe(int unit) {
        for (CombinerRecipe recipe : RecipeRegistry.getRecipesByType(RecipeRegistry.COMBINER_TYPE.get(), level)) {
            if (allowedByLock(recipe) && findCombinerAssignment(recipe, unit) != null) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private int[] findCombinerAssignment(CombinerRecipe recipe, int unit) {
        List<com.smashingmods.alchemylib.api.item.IngredientStack> ingredients = recipe.getInput();
        int nonEmpty = 0;
        for (int slot = 0; slot < layout.inputsPerUnit(); slot++) {
            if (!unitSlots[layout.unitInputSlot(unit, slot)].getStack().isEmpty()) {
                nonEmpty++;
            }
        }
        if (nonEmpty != ingredients.size()) {
            return null;
        }
        int[] assignment = new int[ingredients.size()];
        Arrays.fill(assignment, -1);
        return assignCombinerIngredient(unit, ingredients, 0, new boolean[layout.inputsPerUnit()], assignment)
                ? assignment : null;
    }

    private boolean assignCombinerIngredient(int unit,
                                             List<com.smashingmods.alchemylib.api.item.IngredientStack> ingredients,
                                             int ingredientIndex, boolean[] usedSlots, int[] assignment) {
        if (ingredientIndex >= ingredients.size()) {
            return true;
        }
        com.smashingmods.alchemylib.api.item.IngredientStack ingredient = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < layout.inputsPerUnit(); slot++) {
            ItemStack stack = unitSlots[layout.unitInputSlot(unit, slot)].getStack();
            if (!usedSlots[slot] && stack.getCount() >= ingredient.getCount() && ingredient.matches(stack)) {
                usedSlots[slot] = true;
                assignment[ingredientIndex] = slot;
                if (assignCombinerIngredient(unit, ingredients, ingredientIndex + 1, usedSlots, assignment)) {
                    return true;
                }
                assignment[ingredientIndex] = -1;
                usedSlots[slot] = false;
            }
        }
        return false;
    }

    private void processCombiner(int unit) {
        CombinerRecipe recipe = matchingCombinerRecipe(unit);
        if (recipe == null) {
            return;
        }
        List<com.smashingmods.alchemylib.api.item.IngredientStack> ingredients = recipe.getInput();
        int[] assignment = findCombinerAssignment(recipe, unit);
        if (assignment == null) {
            return;
        }
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            shrinkSlot(layout.unitInputSlot(unit, assignment[ingredientIndex]),
                    ingredients.get(ingredientIndex).getCount());
        }
        insertUnitOutput(unit, recipe.getOutput().copy());
    }

    @Nullable
    private DissolverRecipe matchingDissolverRecipe(int unit) {
        ItemStack input = unitSlots[layout.unitInputSlot(unit, 0)].getStack();
        ItemStack catalyst = unitSlots[layout.unitInputSlot(unit, 1)].getStack();
        for (DissolverRecipe recipe : RecipeRegistry.getRecipesByType(RecipeRegistry.DISSOLVER_TYPE.get(), level)) {
            if (allowedByLock(recipe)
                    && recipe.matches(input)
                    && input.getCount() >= recipe.getInput().getCount()
                    && recipe.matchesCatalyst(catalyst)) {
                return recipe;
            }
        }
        return null;
    }

    private void processDissolver(int unit) {
        DissolverRecipe recipe = matchingDissolverRecipe(unit);
        if (recipe == null) {
            return;
        }
        shrinkSlot(layout.unitInputSlot(unit, 0), recipe.getInput().getCount());
        NonNullList<ItemStack> outputs = recipe.getOutput().calculateOutput();
        List<ItemStack> pending = dissolverPending.get(unit);
        for (ItemStack output : outputs) {
            if (!output.isEmpty()) {
                pending.add(output.copy());
            }
        }
        flushDissolverPending(unit);
    }

    @Nullable
    private CompactorRecipe matchingCompactorRecipe(int unit) {
        ItemStack input = unitSlots[layout.unitInputSlot(unit, 0)].getStack();
        for (CompactorRecipe recipe : RecipeRegistry.getRecipesByType(RecipeRegistry.COMPACTOR_TYPE.get(), level)) {
            if (allowedByLock(recipe) && recipe.matches(input) && input.getCount() >= recipe.getInput().getCount()) {
                return recipe;
            }
        }
        return null;
    }

    private void processCompactor(int unit) {
        CompactorRecipe recipe = matchingCompactorRecipe(unit);
        if (recipe == null) {
            return;
        }
        shrinkSlot(layout.unitInputSlot(unit, 0), recipe.getInput().getCount());
        insertUnitOutput(unit, recipe.getOutput().copy());
    }

    private void shrinkSlot(int slotIndex, int count) {
        IInventorySlot slot = unitSlots[slotIndex];
        slot.setStackSize(slot.getStack().getCount() - count, Action.EXECUTE);
    }

    private ItemStack insertUnitOutput(int unit, ItemStack stack) {
        for (int i = 0; i < layout.outputsPerUnit() && !stack.isEmpty(); i++) {
            stack = insertIntoSlot(unitSlots[layout.unitOutputSlot(unit, i)], stack);
        }
        return stack;
    }

    private ItemStack insertSharedOutput(ItemStack stack) {
        for (int i = 0; i < layout.sharedOutputs() && !stack.isEmpty(); i++) {
            stack = insertIntoSlot(unitSlots[layout.sharedOutputSlot(i)], stack);
        }
        return stack;
    }

    private ItemStack insertIntoSlot(IInventorySlot slot, ItemStack stack) {
        ItemStack existing = slot.getStack();
        if (existing.isEmpty()) {
            int moved = Math.min(stack.getCount(), slot.getLimit(stack));
            ItemStack inserted = stack.copy();
            inserted.setCount(moved);
            slot.setStack(inserted);
            stack.shrink(moved);
        } else if (ItemStack.isSameItemSameTags(existing, stack)) {
            int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                slot.setStack(existing);
                stack.shrink(moved);
            }
        }
        return stack;
    }

    private void flushDissolverPending() {
        if (type != ChemekFactoryType.DISSOLVER) {
            return;
        }
        for (int unit = 0; unit < units; unit++) {
            flushDissolverPending(unit);
        }
    }

    private void flushDissolverPending(int unit) {
        List<ItemStack> pending = dissolverPending.get(unit);
        int index = 0;
        while (index < pending.size()) {
            ItemStack remaining = insertSharedOutput(pending.get(index).copy());
            if (remaining.isEmpty()) {
                pending.remove(index);
            } else {
                pending.set(index, remaining);
                break;
            }
        }
    }

    // ===== 配方锁与预览（同步 alchemylib ProcessingBlockEntity 语义） =====

    private boolean allowedByLock(AbstractProcessingRecipe recipe) {
        // 仅当玩家主动锁定后，才把处理范围限制在选定配方；未锁定时保持动态匹配。
        return !recipeLocked || lockedRecipeId == null || lockedRecipeId.equals(recipe.getId());
    }

    @Override
    public boolean isRecipeLocked() {
        return recipeLocked;
    }

    @Override
    public void setRecipeLocked(boolean locked) {
        if (locked) {
            ResourceLocation captured = captureRecipeId();
            if (captured != null) {
                lockedRecipeId = captured;
            }
        }
        // 解锁保留已选定配方，便于重新锁定与持久化恢复。
        recipeLocked = locked;
        syncState();
    }

    @Nullable
    private ResourceLocation captureRecipeId() {
        for (int unit = 0; unit < units; unit++) {
            AbstractProcessingRecipe recipe = matchingAnyRecipe(unit);
            if (recipe != null) {
                return recipe.getId();
            }
        }
        return null;
    }

    @Nullable
    private AbstractProcessingRecipe matchingAnyRecipe(int unit) {
        return switch (type) {
            case COMBINER -> matchingCombinerRecipe(unit);
            case DISSOLVER -> matchingDissolverRecipe(unit);
            case COMPACTOR -> matchingCompactorRecipe(unit);
        };
    }

    public ItemStack previewOutput(int unit, int outputIndex) {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        try {
            // 已选定/锁定的配方直接驱动预览（不依赖当前输入与锁定状态）。
            AbstractProcessingRecipe selected = findByLockedId();
            if (selected != null) {
                return productOf(selected, outputIndex);
            }
            return switch (type) {
                case COMBINER, COMPACTOR -> {
                    AbstractProcessingRecipe recipe = matchingAnyRecipe(unit);
                    yield recipe == null ? ItemStack.EMPTY : productOf(recipe, outputIndex);
                }
                case DISSOLVER -> {
                    if (unit != 0) {
                        yield ItemStack.EMPTY;
                    }
                    DissolverRecipe recipe = matchingDissolverRecipe(0);
                    yield recipe == null ? ItemStack.EMPTY : productOf(recipe, outputIndex);
                }
            };
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** 查询某单元输入槽当前物品（供配方选择页判断输入是否为空）。 */
    public ItemStack inputStack(int unit, int input) {
        if (input < 0 || input >= layout.inputsPerUnit() || unit < 0 || unit >= units) {
            return ItemStack.EMPTY;
        }
        return unitSlots[layout.unitInputSlot(unit, input)].getStack();
    }

    /** 吸收由原化学模组机器转换迁移过来的物品（输入按序填充输入槽，输出进输出槽/共享输出）。 */
    public void absorbLegacyItems(List<ItemStack> legacyInputs, List<ItemStack> legacyOutputs) {
        for (ItemStack stack : legacyInputs) {
            if (!stack.isEmpty()) {
                migrateInto(stack.copy(), true);
            }
        }
        for (ItemStack stack : legacyOutputs) {
            if (!stack.isEmpty()) {
                migrateInto(stack.copy(), false);
            }
        }
        syncState();
    }

    private void migrateInto(ItemStack stack, boolean input) {
        for (int slot = 0; slot < unitSlots.length && !stack.isEmpty(); slot++) {
            boolean candidate = input ? layout.isInputSlot(slot) : layout.isOutputSlot(slot);
            if (candidate) {
                stack = insertIntoSlot(unitSlots[slot], stack);
            }
        }
    }

    /** 配方产物（用于预览栏与输出槽 ghost 预览）。 */
    public static ItemStack productOf(AbstractProcessingRecipe recipe, int outputIndex) {
        if (recipe == null || outputIndex != 0) {
            return ItemStack.EMPTY;
        }
        if (recipe instanceof CompactorRecipe compactor) {
            return compactor.getOutput().copy();
        }
        if (recipe instanceof CombinerRecipe combiner) {
            return combiner.getOutput().copy();
        }
        if (recipe instanceof DissolverRecipe dissolver) {
            for (ItemStack stack : dissolver.getOutput().calculateOutput()) {
                if (!stack.isEmpty()) {
                    return stack.copy();
                }
            }
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** 供容器同步的锁定配方 id（0 或 1 个元素）。 */
    public List<String> getLockedRecipeIdList() {
        List<String> ids = new ArrayList<>(1);
        if (lockedRecipeId != null) {
            ids.add(lockedRecipeId.toString());
        }
        return ids;
    }

    /**
     * 输入槽 ghost 预览：当该输入槽为空时，返回当前匹配配方在此槽位所需的物品；
     * 槽内已有物品时返回空（不显示 ghost）。
     */
    public ItemStack previewInput(int unit, int inputIndex) {
        if (level == null || inputIndex < 0 || inputIndex >= layout.inputsPerUnit()) {
            return ItemStack.EMPTY;
        }
        try {
            if (!unitSlots[layout.unitInputSlot(unit, inputIndex)].getStack().isEmpty()) {
                return ItemStack.EMPTY;
            }
            AbstractProcessingRecipe recipe = matchingAnyRecipe(unit);
            if (recipe == null) {
                return ItemStack.EMPTY;
            }
            if (recipe instanceof DissolverRecipe dissolver) {
                if (inputIndex == 0) {
                    return representative(dissolver.getInput());
                }
                return dissolver.requiresCatalyst() ? representative(dissolver.getCatalyst()) : ItemStack.EMPTY;
            }
            if (recipe instanceof CombinerRecipe combiner) {
                List<com.smashingmods.alchemylib.api.item.IngredientStack> ingredients = combiner.getInput();
                return inputIndex < ingredients.size() ? representative(ingredients.get(inputIndex)) : ItemStack.EMPTY;
            }
            if (recipe instanceof CompactorRecipe compactor) {
                return inputIndex == 0 ? representative(compactor.getInput()) : ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack representative(com.smashingmods.alchemylib.api.item.IngredientStack stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = stack.getIngredient().getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = items[0].copy();
        out.setCount(Math.max(1, stack.getCount()));
        return out;
    }

    @Override
    public boolean isProcessingPaused() {
        return paused;
    }

    @Override
    public void setPaused(boolean paused) {
        this.paused = paused;
        syncState();
    }

    @Override
    public boolean isSideConfigScreenOpen() {
        return false;
    }

    @Override
    public void setSideConfigScreenState(boolean state) {
        // Mek 原生侧配置页签接管该状态。
    }

    // ===== 配方选择页状态（对齐 alchemylib SearchableBlockEntity 语义） =====

    @Override
    public void setRecipeSelectorOpen(boolean open) {
        this.recipeSelectorOpen = open;
    }

    @Override
    public boolean isRecipeSelectorOpen() {
        return recipeSelectorOpen;
    }

    @Override
    public String getSearchText() {
        return searchText;
    }

    @Override
    public void setSearchText(String text) {
        this.searchText = text == null ? "" : text;
    }

    @Override
    public boolean getCanProcess() {
        return canProcess;
    }

    @Override
    public void setCanProcess(boolean canProcess) {
        this.canProcess = canProcess;
    }

    @Override
    public int getProgress() {
        return progress.length == 0 ? 0 : (int) progress[0];
    }

    @Override
    public void setProgress(int progress) {
        if (this.progress.length > 0) {
            this.progress[0] = progress;
        }
    }

    @Override
    public int getMaxProgress() {
        return GeneralChemConfig.MAX_PROGRESS.get();
    }

    @Override
    public void setMaxProgress(int maxProgress) {
        // 进度上限由配置控制。
    }

    @Override
    public void incrementProgress() {
        if (progress.length > 0) {
            progress[0] += (float) progressPerTick();
        }
    }

    @Override
    public void updateRecipe() {
        // 工厂每 tick 动态匹配配方；锁定状态下仅使用锁定配方。
    }

    @Override
    public boolean canProcessRecipe() {
        return hasAnyValidInput();
    }

    @Override
    public void processRecipe() {
        for (int unit = 0; unit < units; unit++) {
            if (hasValidInput(unit)) {
                processRecipe(unit);
                return;
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends AbstractProcessingRecipe> void setRecipe(R recipe) {
        if (recipe == null) {
            lockedRecipeId = null;
            recipeLocked = false;
        } else {
            // 选择配方仅登记选定项；是否锁定由玩家通过锁定按钮决定。
            lockedRecipeId = recipe.getId();
        }
        syncState();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends AbstractProcessingRecipe> R getRecipe() {
        AbstractProcessingRecipe recipe = lockedRecipeId == null
                ? matchingAnyRecipe(0)
                : findByLockedId();
        return (R) recipe;
    }

    @Nullable
    private AbstractProcessingRecipe findByLockedId() {
        if (lockedRecipeId == null) {
            return null;
        }
        for (AbstractProcessingRecipe recipe : getAllRecipes()) {
            if (lockedRecipeId.equals(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }

    @Override
    public LinkedList<AbstractProcessingRecipe> getAllRecipes() {
        return switch (type) {
            case COMBINER -> new LinkedList<>(RecipeRegistry.getRecipesByType(
                    RecipeRegistry.COMBINER_TYPE.get(), level));
            case DISSOLVER -> new LinkedList<>(RecipeRegistry.getRecipesByType(
                    RecipeRegistry.DISSOLVER_TYPE.get(), level));
            case COMPACTOR -> new LinkedList<>(RecipeRegistry.getRecipesByType(
                    RecipeRegistry.COMPACTOR_TYPE.get(), level));
        };
    }

    /** 供容器同步的配方 id 列表（客户端用于配方选择页）。 */
    public List<String> getRecipeIds() {
        List<String> ids = new ArrayList<>();
        if (level == null) {
            return ids;
        }
        for (AbstractProcessingRecipe recipe : getAllRecipes()) {
            ids.add(recipe.getId().toString());
        }
        return ids;
    }

    @Override
    public void tick() {
        onUpdateServer();
    }

    // ===== 持久化与迁移 =====

    @Override
    protected void addGeneralPersistentData(CompoundTag tag) {
        super.addGeneralPersistentData(tag);
        for (int i = 0; i < units; i++) {
            tag.putFloat("Progress" + i, progress[i]);
        }
        writePending(tag);
        tag.putBoolean("recipeLocked", recipeLocked);
        if (lockedRecipeId != null) {
            tag.putString("recipeId", lockedRecipeId.toString());
        }
        tag.putBoolean("paused", paused);
    }

    @Override
    protected void loadGeneralPersistentData(CompoundTag tag) {
        super.loadGeneralPersistentData(tag);
        for (int i = 0; i < units; i++) {
            progress[i] = tag.getFloat("Progress" + i);
        }
        if (tag.contains("DissolverPending", Tag.TAG_LIST)) {
            readPending(tag);
        }
        recipeLocked = tag.getBoolean("recipeLocked");
        if (tag.contains("recipeId", Tag.TAG_STRING)) {
            lockedRecipeId = new ResourceLocation(tag.getString("recipeId"));
        }
        paused = tag.getBoolean("paused");
        migrateLegacyData(tag);
        // 容量不随 NBT 持久化：加载完成后按已安装的能量升级重算上限。
        if (upgradeComponent != null) {
            recalculateUpgrades(Upgrade.ENERGY);
        }
    }

    private void migrateLegacyData(CompoundTag tag) {
        if (tag.contains("Items", Tag.TAG_COMPOUND)) {
            migrateLegacyItems(tag.getCompound("Items"));
        }
        if (tag.contains("Fluid", Tag.TAG_COMPOUND) && fluidTank != null) {
            fluidTank.setStack(FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid")));
        }
        if (tag.contains("Energy", Tag.TAG_INT)) {
            energyContainer.setEnergy(feToJoules(tag.getInt("Energy")));
        }
        if (tag.contains("SpeedUpgrades", Tag.TAG_INT) && upgradeComponent != null) {
            upgradeComponent.addUpgrades(Upgrade.SPEED, tag.getInt("SpeedUpgrades"));
        }
        if (tag.contains("EnergyUpgrades", Tag.TAG_INT) && upgradeComponent != null) {
            upgradeComponent.addUpgrades(Upgrade.ENERGY, tag.getInt("EnergyUpgrades"));
        }
        if (tag.contains("SideConfig", Tag.TAG_COMPOUND)) {
            migrateLegacySideConfig(tag.getCompound("SideConfig"));
        }
        if (tag.hasUUID("Owner") && getSecurity() != null) {
            getSecurity().setOwnerUUID(tag.getUUID("Owner"));
        }
        if (tag.contains("SecurityMode", Tag.TAG_INT) && getSecurity() != null) {
            SecurityMode[] modes = SecurityMode.values();
            int index = tag.getInt("SecurityMode");
            if (index >= 0 && index < modes.length) {
                getSecurity().setMode(modes[index]);
            }
        }
        if (tag.contains("RedstoneControl", Tag.TAG_INT)) {
            RedstoneControl control = RedstoneControl.byIndexStatic(tag.getInt("RedstoneControl"));
            if (control != null) {
                setControlType(control);
            }
        }
    }

    private void migrateLegacyItems(CompoundTag itemsTag) {
        int oldInputs = switch (type) {
            case COMBINER -> 3;
            case DISSOLVER, COMPACTOR -> 1;
        };
        int oldOutputs = switch (type) {
            case DISSOLVER -> 2;
            default -> 1;
        };
        int oldStride = oldInputs + oldOutputs;
        net.minecraftforge.items.ItemStackHandler old = new net.minecraftforge.items.ItemStackHandler(units * oldStride + 4);
        old.deserializeNBT(itemsTag);
        for (int unit = 0; unit < units; unit++) {
            int oldStart = unit * oldStride;
            for (int input = 0; input < Math.min(oldInputs, layout.inputsPerUnit()); input++) {
                unitSlots[layout.unitInputSlot(unit, input)].setStack(old.getStackInSlot(oldStart + input).copy());
            }
            if (type == ChemekFactoryType.DISSOLVER) {
                for (int output = 0; output < oldOutputs; output++) {
                    ItemStack remaining = insertSharedOutput(old.getStackInSlot(oldStart + oldInputs + output).copy());
                    if (!remaining.isEmpty()) {
                        dissolverPending.get(unit).add(remaining);
                    }
                }
            } else {
                for (int output = 0; output < Math.min(oldOutputs, layout.outputsPerUnit()); output++) {
                    unitSlots[layout.unitOutputSlot(unit, output)].setStack(
                            old.getStackInSlot(oldStart + oldInputs + output).copy());
                }
            }
        }
        int oldUpgradeStart = units * oldStride;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = old.getStackInSlot(oldUpgradeStart + i);
            if (GeneralChem.isSpeedUpgrade(stack)) {
                upgradeComponent.addUpgrades(Upgrade.SPEED, stack.getCount());
            } else if (GeneralChem.isEnergyUpgrade(stack)) {
                upgradeComponent.addUpgrades(Upgrade.ENERGY, stack.getCount());
            }
        }
    }

    private void migrateLegacySideConfig(CompoundTag sideConfig) {
        for (TransmissionType transmission : supportedTransmissions()) {
            int[] values = sideConfig.getIntArray(transmission.getName());
            if (values.length != RelativeSide.values().length) {
                continue;
            }
            ConfigInfo info = configComponent.getConfig(transmission);
            for (RelativeSide side : RelativeSide.values()) {
                DataType mode = DataType.byIndexStatic(values[side.ordinal()]);
                if (mode != null && info.getSupportedDataTypes().contains(mode)) {
                    info.setDataType(mode, side);
                }
            }
            if (transmission == TransmissionType.ITEM || transmission == TransmissionType.FLUID) {
                info.setEjecting(sideConfig.getBoolean(transmission.getName() + "AutoEject"));
            }
        }
        ejectorComponent.setStrictInput(sideConfig.getBoolean("StrictInput"));
        int[] colors = sideConfig.getIntArray("InputColors");
        if (colors.length == RelativeSide.values().length) {
            for (RelativeSide side : RelativeSide.values()) {
                ejectorComponent.setInputColor(side, colorByIndex(colors[side.ordinal()]));
            }
        }
        ejectorComponent.setOutputColor(colorByIndex(sideConfig.getInt("OutputColor")));
    }

    @Nullable
    private mekanism.api.text.EnumColor colorByIndex(int index) {
        List<mekanism.api.text.EnumColor> colors = mekanism.common.util.TransporterUtils.colors;
        return index >= 0 && index < colors.size() ? colors.get(index) : null;
    }

    private void writePending(CompoundTag tag) {
        ListTag unitsTag = new ListTag();
        for (List<ItemStack> pending : dissolverPending) {
            CompoundTag unitTag = new CompoundTag();
            ListTag stacks = new ListTag();
            for (ItemStack stack : pending) {
                if (!stack.isEmpty()) {
                    stacks.add(stack.save(new CompoundTag()));
                }
            }
            unitTag.put("Stacks", stacks);
            unitsTag.add(unitTag);
        }
        tag.put("DissolverPending", unitsTag);
    }

    private void readPending(CompoundTag tag) {
        dissolverPending.forEach(List::clear);
        ListTag unitsTag = tag.getList("DissolverPending", Tag.TAG_COMPOUND);
        for (int unit = 0; unit < Math.min(units, unitsTag.size()); unit++) {
            ListTag stacks = unitsTag.getCompound(unit).getList("Stacks", Tag.TAG_COMPOUND);
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = ItemStack.of(stacks.getCompound(i));
                if (!stack.isEmpty()) {
                    dissolverPending.get(unit).add(stack);
                }
            }
        }
    }

    // ===== 等级升级迁移 =====

    public void transferTo(ChemekFactoryBlockEntity target) {
        for (int slot = 0; slot < Math.min(unitSlots.length, target.unitSlots.length); slot++) {
            target.unitSlots[slot].setStack(unitSlots[slot].getStack().copy());
        }
        for (int unit = 0; unit < Math.min(units, target.units); unit++) {
            for (ItemStack pending : dissolverPending.get(unit)) {
                target.dissolverPending.get(unit).add(pending.copy());
            }
            target.progress[unit] = progress[unit];
            target.pulseArmed[unit] = pulseArmed[unit];
        }
        for (TransmissionType transmission : configComponent.getTransmissions()) {
            ConfigInfo source = configComponent.getConfig(transmission);
            ConfigInfo targetInfo = target.configComponent.getConfig(transmission);
            if (targetInfo == null) {
                continue;
            }
            for (RelativeSide side : RelativeSide.values()) {
                DataType mode = source.getDataType(side);
                if (targetInfo.getSupportedDataTypes().contains(mode)) {
                    targetInfo.setDataType(mode, side);
                }
            }
            targetInfo.setEjecting(source.isEjecting());
        }
        target.ejectorComponent.setStrictInput(ejectorComponent.hasStrictInput());
        target.ejectorComponent.setOutputColor(ejectorComponent.getOutputColor());
        for (RelativeSide side : RelativeSide.values()) {
            target.ejectorComponent.setInputColor(side, ejectorComponent.getInputColor(side));
        }
        if (upgradeComponent != null && target.upgradeComponent != null) {
            for (Upgrade upgrade : Upgrade.values()) {
                target.upgradeComponent.addUpgrades(upgrade, upgradeComponent.getUpgrades(upgrade));
            }
        }
        if (getSecurity() != null && target.getSecurity() != null) {
            target.getSecurity().setOwnerUUID(getSecurity().getOwnerUUID());
            target.getSecurity().setMode(getSecurity().getMode());
        }
        target.setControlType(getControlType());
        target.recipeLocked = recipeLocked;
        target.lockedRecipeId = lockedRecipeId;
        target.paused = paused;
        target.energyContainer.setEnergy(energyContainer.getEnergy());
        target.energyContainer.setMaxEnergy(energyContainer.getMaxEnergy());
        if (target.fluidTank != null && fluidTank != null) {
            target.fluidTank.setStack(fluidTank.getFluid().copy());
        }
        target.markDirtyComparator();
    }

    // ===== 访问器 =====

    public ChemekFactoryType getFactoryType() {
        return type;
    }

    public ChemekFactoryTier getTier() {
        return tier;
    }

    public int getUnits() {
        return units;
    }

    public FactorySlotLayout getSlotLayout() {
        return layout;
    }

    public boolean hasFluidTank() {
        return type.hasFluidInput() || type.hasFluidOutput();
    }

    public IExtendedFluidTank getFluidTank() {
        return fluidTank;
    }

    public MachineEnergyContainer<ChemekFactoryBlockEntity> getEnergyContainer() {
        return energyContainer;
    }

    public List<IInventorySlot> getFactorySlots() {
        List<IInventorySlot> slots = new ArrayList<>(unitSlots.length);
        for (IInventorySlot slot : unitSlots) {
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
    }

    @Override
    public Component getName() {
        return getDisplayName();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chemek." + type.getName() + "_factory_" + tier.getName());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChemekFactoryMenu(containerId, playerInventory, this);
    }

    @Override
    public TileComponentConfig getConfig() {
        return configComponent;
    }

    @Override
    public TileComponentEjector getEjector() {
        return ejectorComponent;
    }

    private static final class FactoryBlockProvider implements IBlockProvider {
        private final Block block;

        FactoryBlockProvider(Block block) {
            this.block = block;
        }

        @Override
        public Block getBlock() {
            return block;
        }

        @Override
        public Item asItem() {
            return block.asItem();
        }

        @Override
        public ResourceLocation getRegistryName() {
            return BuiltInRegistries.BLOCK.getKey(block);
        }
    }
}
