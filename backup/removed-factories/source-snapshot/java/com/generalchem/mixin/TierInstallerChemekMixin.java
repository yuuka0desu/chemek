package com.generalchem.mixin;

import com.generalchem.ChemekFactoryBlockEntity;
import com.generalchem.GeneralChem;
import mekanism.api.security.ISecurityUtils;
import mekanism.api.tier.BaseTier;
import mekanism.common.item.ItemTierInstaller;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Mekanism 原版工厂安装器可以升级 Chemek Link 的化工厂：
 * 安装器 fromTier 对应当前机器等级、toTier 对应下一等级时执行升级。
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
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof ChemekFactoryBlockEntity factory)) {
            return;
        }

        Player player = context.getPlayer();
        if (player != null && !ISecurityUtils.INSTANCE.canAccessOrDisplayError(player, factory)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemTierInstaller installer = (ItemTierInstaller) (Object) this;
        BaseTier from = installer.getFromTier();
        BaseTier to = installer.getToTier();
        if (from == null || to == null
                || from.ordinal() != factory.getTier().ordinal()
                || to.ordinal() != factory.getTier().ordinal() + 1) {
            return;
        }

        GeneralChem.upgradeFactory(level, pos, factory);

        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        cir.setReturnValue(InteractionResult.CONSUME);
    }
}
