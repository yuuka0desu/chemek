package com.generalchem.mixin;

import com.smashingmods.alchemistry.common.block.combiner.CombinerBlock;
import com.smashingmods.alchemistry.common.block.compactor.CompactorBlock;
import com.smashingmods.alchemistry.common.block.dissolver.DissolverBlock;
import com.smashingmods.alchemylib.api.blockentity.processing.AbstractInventoryBlockEntity;
import com.generalchem.ChemekFactoryBlock;
import com.generalchem.ChemekFactoryBlockEntity;
import com.generalchem.ChemekFactoryTier;
import com.generalchem.ChemekFactoryType;
import com.generalchem.GeneralChem;
import mekanism.api.security.ISecurityUtils;
import mekanism.api.tier.BaseTier;
import mekanism.common.item.ItemTierInstaller;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 让 Mekanism 原版工厂安装器作用于化学模组：
 * <ul>
 *   <li>原炼金化学的化合/压缩/溶解机 + 基础安装器（无等级 -&gt; 基础）→ 转换为本模组的基础工厂（迁移内容）；</li>
 *   <li>本模组工厂 + 对应层级安装器 → 升级到下一等级。</li>
 * </ul>
 */
@Mixin(value = ItemTierInstaller.class, remap = false)
public abstract class TierInstallerChemekMixin {

    @Inject(method = "m_6225_", at = @At("HEAD"), cancellable = true)
    private void generalchem$upgradeChemekFactory(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return;
        }
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        ItemTierInstaller installer = (ItemTierInstaller) (Object) this;
        BaseTier from = installer.getFromTier();
        BaseTier to = installer.getToTier();
        if (to == null) {
            return;
        }

        // 1) 原化学模组机器 + 基础安装器 → 本模组基础工厂。
        BlockState clickedState = level.getBlockState(pos);
        ChemekFactoryType legacyType = legacyFactoryType(clickedState.getBlock());
        if (legacyType != null && from == null && to == BaseTier.BASIC) {
            convertLegacyMachine(level, pos, clickedState, legacyType, player);
            finishInstallation(context, player, pos);
            cir.setReturnValue(InteractionResult.CONSUME);
            return;
        }

        // 2) 本模组工厂升级。
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof ChemekFactoryBlockEntity factory)) {
            return;
        }
        if (player != null && !ISecurityUtils.INSTANCE.canAccessOrDisplayError(player, factory)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ChemekFactoryTier current = factory.getTier();
        if (current.next() == null) {
            return;
        }
        boolean matches;
        if (from == null) {
            // 基础安装器（无等级 -> 基础）：按名称对应语义解释为"当前基础工厂 -> 下一等级"。
            matches = to == BaseTier.BASIC && current == ChemekFactoryTier.BASIC;
        } else {
            matches = from.ordinal() == current.ordinal() && to.ordinal() == current.ordinal() + 1;
        }
        if (!matches) {
            return;
        }

        GeneralChem.upgradeFactory(level, pos, factory);
        finishInstallation(context, player, pos);
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    private static ChemekFactoryType legacyFactoryType(Block block) {
        if (block instanceof CombinerBlock) {
            return ChemekFactoryType.COMBINER;
        }
        if (block instanceof CompactorBlock) {
            return ChemekFactoryType.COMPACTOR;
        }
        if (block instanceof DissolverBlock) {
            return ChemekFactoryType.DISSOLVER;
        }
        return null;
    }

    private static void convertLegacyMachine(Level level, BlockPos pos, BlockState oldState,
                                             ChemekFactoryType type, Player player) {
        Direction facing = Direction.NORTH;
        if (oldState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            facing = oldState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        } else if (oldState.hasProperty(BlockStateProperties.FACING)) {
            Direction direction = oldState.getValue(BlockStateProperties.FACING);
            if (direction.getAxis().isHorizontal()) {
                facing = direction;
            }
        }

        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof AbstractInventoryBlockEntity legacy) {
            for (ItemStack stack : legacy.getInputHandler().getStacks()) {
                if (!stack.isEmpty()) {
                    inputs.add(stack.copy());
                }
            }
            for (ItemStack stack : legacy.getOutputHandler().getStacks()) {
                if (!stack.isEmpty()) {
                    outputs.add(stack.copy());
                }
            }
        }

        Block newBlock = GeneralChem.factoryBlock(type, ChemekFactoryTier.BASIC);
        level.setBlock(pos, newBlock.defaultBlockState().setValue(ChemekFactoryBlock.FACING, facing),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ChemekFactoryBlockEntity factory) {
            factory.absorbLegacyItems(inputs, outputs);
            if (player != null && factory.getSecurity() != null
                    && factory.getSecurity().getOwnerUUID() == null) {
                factory.getSecurity().setOwnerUUID(player.getUUID());
            }
        }
    }

    private static void finishInstallation(UseOnContext context, Player player, BlockPos pos) {
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        context.getLevel().playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
