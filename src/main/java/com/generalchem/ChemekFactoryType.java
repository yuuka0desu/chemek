package com.generalchem;

public enum ChemekFactoryType {
    /** 化合工厂：每单元 4 化学物品输入 + 1 输出。 */
    COMBINER("combiner", 4, 1, false, false, 4),
    /** 溶解工厂：每单元主料 + 催化剂，共享输出 = 主料槽数 x 3（横向，高 3 行）。 */
    DISSOLVER("dissolver", 2, 0, false, false, 4),
    /** 压缩工厂：每单元 1 输入 + 1 输出。 */
    COMPACTOR("compactor", 1, 1, false, false, 4);

    private final String name;
    private final int inputsPerUnit;
    private final int outputsPerUnit;
    private final boolean fluidOutput;
    private final boolean fluidInput;
    private final int fluidTankBuckets;

    ChemekFactoryType(String name, int inputsPerUnit, int outputsPerUnit,
                      boolean fluidOutput, boolean fluidInput, int fluidTankBuckets) {
        this.name = name;
        this.inputsPerUnit = inputsPerUnit;
        this.outputsPerUnit = outputsPerUnit;
        this.fluidOutput = fluidOutput;
        this.fluidInput = fluidInput;
        this.fluidTankBuckets = fluidTankBuckets;
    }

    public String getName() {
        return name;
    }

    public int getInputsPerUnit() {
        return inputsPerUnit;
    }

    public int getOutputsPerUnit() {
        return outputsPerUnit;
    }

    public boolean hasFluidOutput() {
        return fluidOutput;
    }

    public boolean hasFluidInput() {
        return fluidInput;
    }

    public int getFluidTankBuckets() {
        return fluidTankBuckets;
    }

    public int energyPerTick() {
        return switch (this) {
            case COMBINER -> GeneralChemConfig.COMBINER_ENERGY_PER_TICK.get();
            case DISSOLVER -> GeneralChemConfig.DISSOLVER_ENERGY_PER_TICK.get();
            case COMPACTOR -> GeneralChemConfig.COMPACTOR_ENERGY_PER_TICK.get();
        };
    }
}
