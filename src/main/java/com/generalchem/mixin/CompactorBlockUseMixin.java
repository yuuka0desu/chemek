package com.generalchem.mixin;

import com.smashingmods.alchemistry.common.block.compactor.CompactorBlock;
import mekanism.common.item.ItemTierInstaller;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 手持 Mek 安装器时让原压缩机让出交互，使安装器 useOn（转换逻辑）得以执行。 */
@Mixin(value = CompactorBlock.class, remap = false)
public abstract class CompactorBlockUseMixin {
    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true)
    private void generalchem$allowTierInstaller(BlockState state, Level level, BlockPos pos, Player player,
                                                InteractionHand hand, BlockHitResult hit,
                                                CallbackInfoReturnable<InteractionResult> cir) {
        if (player.getItemInHand(hand).getItem() instanceof ItemTierInstaller) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
