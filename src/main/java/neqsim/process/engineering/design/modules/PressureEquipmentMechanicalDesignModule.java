package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.processmodel.ProcessSystem;

/** Preliminary pressure-shell thickness and mass calculation for early equipment layout. */
public final class PressureEquipmentMechanicalDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final String pressureMetricId;
  private final String diameterStateKey;
  private final String lengthStateKey;
  private final double allowableStressMpa;
  private final double weldEfficiency;
  private final double corrosionAllowanceMm;
  private final double steelDensityKgM3;

  public PressureEquipmentMechanicalDesignModule(String equipmentTag, String pressureMetricId, String diameterStateKey,
      String lengthStateKey, double allowableStressMpa, double weldEfficiency, double corrosionAllowanceMm) {
    this.equipmentTag = equipmentTag;
    this.pressureMetricId = pressureMetricId;
    this.diameterStateKey = diameterStateKey;
    this.lengthStateKey = lengthStateKey;
    this.allowableStressMpa = allowableStressMpa;
    this.weldEfficiency = weldEfficiency;
    this.corrosionAllowanceMm = corrosionAllowanceMm;
    steelDensityKgM3 = 7850.0;
  }

  @Override
  public String getId() {
    return "80-pressure-mechanical-design-" + equipmentTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue pressure = DesignModuleSupport.requireMetric(caseReport, pressureMetricId);
    double diameter = state.contains(diameterStateKey) ? state.requireValue(diameterStateKey) : 1.0;
    double length = state.contains(lengthStateKey) ? state.requireValue(lengthStateKey) : 3.0;
    double designPressureBara = state.contains(equipmentTag + ".designPressure")
        ? state.requireValue(equipmentTag + ".designPressure")
        : pressure.getValue() * 1.1;
    double pressureMpa = Math.max(designPressureBara - 1.01325, 0.0) * 0.1;
    double structuralThicknessMm = pressureMpa * diameter * 1000.0
        / Math.max(2.0 * allowableStressMpa * weldEfficiency - 1.2 * pressureMpa, 1.0e-9);
    double nominalThicknessMm = roundUp(structuralThicknessMm + corrosionAllowanceMm, 1.0);
    double shellMass = Math.PI * diameter * length * nominalThicknessMm / 1000.0 * steelDensityKgM3;
    String thicknessKey = equipmentTag + ".preliminaryNominalWallThickness";
    String massKey = equipmentTag + ".preliminaryShellMass";
    String structuralKey = equipmentTag + ".requiredStructuralWallThickness";

    return EngineeringDesignModuleResult.builder(getId(), "Thin-shell pressure design screening", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(structuralKey, structuralThicknessMm, "mm")
            .governingCaseId(pressure.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(thicknessKey, nominalThicknessMm, "mm")
            .governingCaseId(pressure.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(massKey, shellMass, "kg").governingCaseId(pressure.getDesignCaseId())
            .build())
        .addConstraint(new EngineeringDesignConstraint(equipmentTag + ".wall-thickness",
            "Nominal wall includes pressure requirement and corrosion allowance", thicknessKey,
            structuralThicknessMm + corrosionAllowanceMm, "mm", EngineeringDesignConstraint.Comparison.MINIMUM))
        .evidence("allowableStress_MPa", Double.valueOf(allowableStressMpa))
        .evidence("weldEfficiency", Double.valueOf(weldEfficiency))
        .warning(
            "Code formula selection, external loads, fatigue, buckling, nozzles, MDMT and fabrication require review")
        .build();
  }

  private static double roundUp(double value, double increment) {
    return Math.ceil(value / increment) * increment;
  }
}
