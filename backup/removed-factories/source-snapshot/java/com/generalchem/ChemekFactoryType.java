package com.generalchem;

public enum ChemekFactoryType {
    /** 化合工厂：每单元 4 化学物品输入 + 1 输出。 */
    COMBINER("combiner", 4, 1, false, false, 4),
    /** 溶解工厂：每单元主料 + 催化剂，所有单元共享 12 个输出槽。 */
    DISSOLVER("dissolver", 2, 0, false, false, 4),
    /** 压缩工厂：每单元 1 输入 + 1 输出。 */
    COMPACTOR("compactor", 1, 1, false, false, 4),
    /** 液化工厂：每单元 1 物品输入 + 共享流体输出。 */
    LIQUIFIER("liquifier", 1, 0, true, false, 4),
    /** 雾化工厂：共享流体输入 + 每单元 1 物品输出。 */
    ATOMIZER("atomizer", 0, 1, false, true, 4);

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
            case LIQUIFIER -> GeneralChemConfig.LIQUIFIER_ENERGY_PER_TICK.get();
            case ATOMIZER -> GeneralChemConfig.ATOMIZER_ENERGY_PER_TICK.get();
        };
    }
}
