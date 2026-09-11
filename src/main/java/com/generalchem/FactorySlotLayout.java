package com.generalchem;

import java.util.ArrayList;
import java.util.List;

public final class FactorySlotLayout {
    public static final int UPGRADE_SLOTS = 2;

    private final ChemekFactoryType type;
    private final int units;
    private final int inputsPerUnit;
    private final int outputsPerUnit;
    private final int sharedOutputs;

    public FactorySlotLayout(ChemekFactoryType type, int units) {
        this.type = type;
        this.units = units;
        this.inputsPerUnit = type.getInputsPerUnit();
        this.outputsPerUnit = type.getOutputsPerUnit();
        // 溶解工厂共享输出 = 输入槽数 x 3（每单元仅主料计入输入，催化剂槽不计）。
        this.sharedOutputs = type == ChemekFactoryType.DISSOLVER ? units * 3 : 0;
    }

    public int units() {
        return units;
    }

    public int inputsPerUnit() {
        return inputsPerUnit;
    }

    public int outputsPerUnit() {
        return outputsPerUnit;
    }

    public int sharedOutputs() {
        return sharedOutputs;
    }

    public int unitStride() {
        return inputsPerUnit + outputsPerUnit;
    }

    public int unitInputStart(int unit) {
        return unit * unitStride();
    }

    public int unitInputSlot(int unit, int input) {
        return unitInputStart(unit) + input;
    }

    public int unitOutputStart(int unit) {
        return unitInputStart(unit) + inputsPerUnit;
    }

    public int unitOutputSlot(int unit, int output) {
        return unitOutputStart(unit) + output;
    }

    public int sharedOutputStart() {
        return units * unitStride();
    }

    public int sharedOutputSlot(int output) {
        return sharedOutputStart() + output;
    }

    public int upgradeInputSlot() {
        return sharedOutputStart() + sharedOutputs;
    }

    public int upgradeOutputSlot() {
        return upgradeInputSlot() + 1;
    }

    public int totalSlots() {
        return upgradeInputSlot() + UPGRADE_SLOTS;
    }

    public List<Integer> inputSlots() {
        List<Integer> slots = new ArrayList<>(units * inputsPerUnit);
        for (int unit = 0; unit < units; unit++) {
            for (int input = 0; input < inputsPerUnit; input++) {
                slots.add(unitInputSlot(unit, input));
            }
        }
        return slots;
    }

    public List<Integer> inputSlots(int inputIndex) {
        List<Integer> slots = new ArrayList<>(units);
        if (inputIndex < 0 || inputIndex >= inputsPerUnit) {
            return slots;
        }
        for (int unit = 0; unit < units; unit++) {
            slots.add(unitInputSlot(unit, inputIndex));
        }
        return slots;
    }

    public List<Integer> outputSlots() {
        List<Integer> slots = new ArrayList<>(Math.max(sharedOutputs, units * outputsPerUnit));
        if (sharedOutputs > 0) {
            for (int output = 0; output < sharedOutputs; output++) {
                slots.add(sharedOutputSlot(output));
            }
        } else {
            for (int unit = 0; unit < units; unit++) {
                for (int output = 0; output < outputsPerUnit; output++) {
                    slots.add(unitOutputSlot(unit, output));
                }
            }
        }
        return slots;
    }

    public boolean isInputSlot(int slot) {
        for (int unit = 0; unit < units; unit++) {
            int start = unitInputStart(unit);
            if (slot >= start && slot < start + inputsPerUnit) {
                return true;
            }
        }
        return false;
    }

    public boolean isInputSlot(int slot, int inputIndex) {
        return inputIndex >= 0 && inputIndex < inputsPerUnit
                && slot >= 0
                && slot < units * unitStride()
                && slot % unitStride() == inputIndex;
    }

    public boolean isOutputSlot(int slot) {
        if (sharedOutputs > 0) {
            return slot >= sharedOutputStart() && slot < sharedOutputStart() + sharedOutputs;
        }
        for (int unit = 0; unit < units; unit++) {
            int start = unitOutputStart(unit);
            if (slot >= start && slot < start + outputsPerUnit) {
                return true;
            }
        }
        return false;
    }

    public ChemekFactoryType type() {
        return type;
    }
}
