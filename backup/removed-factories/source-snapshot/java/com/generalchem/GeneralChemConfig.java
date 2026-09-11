package com.generalchem;

import net.minecraftforge.common.ForgeConfigSpec;

public final class GeneralChemConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue COMBINER_ENERGY_PER_TICK;
    public static final ForgeConfigSpec.IntValue DISSOLVER_ENERGY_PER_TICK;
    public static final ForgeConfigSpec.IntValue COMPACTOR_ENERGY_PER_TICK;
    public static final ForgeConfigSpec.IntValue LIQUIFIER_ENERGY_PER_TICK;
    public static final ForgeConfigSpec.IntValue ATOMIZER_ENERGY_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_PROGRESS;
    public static final ForgeConfigSpec.DoubleValue SPEED_UPGRADE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue SPEED_UPGRADE_ENERGY_FACTOR;
    public static final ForgeConfigSpec.DoubleValue ENERGY_UPGRADE_CAPACITY_FACTOR;
    public static final ForgeConfigSpec.DoubleValue ENERGY_UPGRADE_ENERGY_FACTOR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("energy");
        COMBINER_ENERGY_PER_TICK = builder.comment("Energy per tick per active unit for combiner factories.")
                .defineInRange("combinerEnergyPerTick", 50, 1, 1_000_000);
        DISSOLVER_ENERGY_PER_TICK = builder.defineInRange("dissolverEnergyPerTick", 20, 1, 1_000_000);
        COMPACTOR_ENERGY_PER_TICK = builder.defineInRange("compactorEnergyPerTick", 50, 1, 1_000_000);
        LIQUIFIER_ENERGY_PER_TICK = builder.defineInRange("liquifierEnergyPerTick", 50, 1, 1_000_000);
        ATOMIZER_ENERGY_PER_TICK = builder.defineInRange("atomizerEnergyPerTick", 50, 1, 1_000_000);
        MAX_PROGRESS = builder.comment("Base progress ticks per operation.")
                .defineInRange("maxProgress", 100, 1, 100_000);
        builder.pop();

        builder.push("upgrades");
        SPEED_UPGRADE_FACTOR = builder.comment("Progress gain factor per speed upgrade: 1 + n * factor.")
                .defineInRange("speedUpgradeFactor", 0.2, 0.0, 10.0);
        SPEED_UPGRADE_ENERGY_FACTOR = builder.comment("Energy multiplier per speed upgrade: 1 + n * factor.")
                .defineInRange("speedUpgradeEnergyFactor", 0.2, 0.0, 10.0);
        ENERGY_UPGRADE_CAPACITY_FACTOR = builder.comment("Capacity multiplier per energy upgrade: factor^n.")
                .defineInRange("energyUpgradeCapacityFactor", 1.5, 1.0, 10.0);
        ENERGY_UPGRADE_ENERGY_FACTOR = builder.comment("Energy cost multiplier per energy upgrade: factor^n.")
                .defineInRange("energyUpgradeEnergyFactor", 0.95, 0.01, 1.0);
        builder.pop();

        SPEC = builder.build();
    }

    private GeneralChemConfig() {
    }
}
