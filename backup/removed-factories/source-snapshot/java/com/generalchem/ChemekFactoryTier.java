package com.generalchem;

public enum ChemekFactoryTier {
    BASIC("basic", 3, 200_000),
    ADVANCED("advanced", 5, 800_000),
    ELITE("elite", 7, 3_200_000),
    ULTIMATE("ultimate", 9, 12_800_000);

    private final String name;
    private final int units;
    private final int energyCapacity;

    ChemekFactoryTier(String name, int units, int energyCapacity) {
        this.name = name;
        this.units = units;
        this.energyCapacity = energyCapacity;
    }

    public String getName() {
        return name;
    }

    public int getUnits() {
        return units;
    }

    public int getEnergyCapacity() {
        return energyCapacity;
    }

    public ChemekFactoryTier next() {
        ChemekFactoryTier[] values = values();
        return ordinal() + 1 < values.length ? values[ordinal() + 1] : null;
    }
}
