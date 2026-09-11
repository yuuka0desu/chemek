package com.generalchem;

import mekanism.api.security.ISecurityUtils;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneralChem.MOD_ID)
public final class FactorySecurityEvents {
    private FactorySecurityEvents() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof ChemekFactoryBlockEntity factory) {
            if (!ISecurityUtils.INSTANCE.canAccess(event.getPlayer(), factory)) {
                event.setCanceled(true);
                ISecurityUtils.INSTANCE.displayNoAccess(event.getPlayer());
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos ->
                event.getLevel().getBlockEntity(pos) instanceof ChemekFactoryBlockEntity);
    }
}
