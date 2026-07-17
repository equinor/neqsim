package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;

/** Preliminary two-phase separator sizing using gas capacity and liquid retention constraints. */
public final class SeparatorProcessDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String separatorTag;
  private final String gasFlowMetricId;
  private final String liquidFlowMetricId;
  private final String gasDensityMetricId;
  private final double liquidDensityKgM3;
  private final double gasLoadFactorMPerS;
  private final double retentionTimeSeconds;

  public SeparatorProcessDesignModule(String separatorTag, String gasFlowMetricId, String liquidFlowMetricId,
      String gasDensityMetricId, double liquidDensityKgM3, double gasLoadFactorMPerS, double retentionTimeSeconds) {
    this.separatorTag = separatorTag;
    this.gasFlowMetricId = gasFlowMetricId;
    this.liquidFlowMetricId = liquidFlowMetricId;
    this.gasDensityMetricId = gasDensityMetricId;
    this.liquidDensityKgM3 = liquidDensityKgM3;
    this.gasLoadFactorMPerS = gasLoadFactorMPerS;
    this.retentionTimeSeconds = retentionTimeSeconds;
  }

  @Override
  public String getId() {
    return "10-separator-process-design-" + separatorTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    if (!(process.getUnit(separatorTag) instanceof Separator)) {
      throw new IllegalArgumentException(separatorTag + " is not a Separator");
    }
    EngineeringDesignEnvelope.GoverningValue gasFlow = DesignModuleSupport.requireMetric(caseReport, gasFlowMetricId);
    EngineeringDesignEnvelope.GoverningValue liquidFlow = DesignModuleSupport.requireMetric(caseReport,
        liquidFlowMetricId);
    EngineeringDesignEnvelope.GoverningValue gasDensity = DesignModuleSupport.requireMetric(caseReport,
        gasDensityMetricId);
    double rhoGas = Math.max(gasDensity.getValue(), 1.0e-6);
    if (liquidDensityKgM3 <= rhoGas) {
      throw new IllegalArgumentException("liquid density must exceed governing gas density");
    }
    double terminalVelocity = gasLoadFactorMPerS * Math.sqrt((liquidDensityKgM3 - rhoGas) / rhoGas);
    double gasArea = Math.max(gasFlow.getValue(), 0.0) / terminalVelocity;
    double requiredDiameter = Math.sqrt(4.0 * gasArea / (Math.PI * 0.5));
    double selectedDiameter = roundUp(requiredDiameter, 0.1);
    double liquidArea = Math.PI * selectedDiameter * selectedDiameter * 0.25 * 0.5;
    double retentionLength = liquidFlow.getValue() * retentionTimeSeconds / Math.max(liquidArea, 1.0e-9);
    double selectedLength = roundUp(Math.max(3.0 * selectedDiameter, retentionLength), 0.25);
    double actualGasVelocity = gasFlow.getValue() / (Math.PI * selectedDiameter * selectedDiameter * 0.25 * 0.5);
    double achievedRetention = liquidArea * selectedLength / Math.max(liquidFlow.getValue(), 1.0e-12);
    String diameterKey = separatorTag + ".insideDiameter";
    String lengthKey = separatorTag + ".tangentLength";
    String velocityKey = separatorTag + ".gasVelocity";
    String retentionKey = separatorTag + ".liquidRetentionTime";

    return EngineeringDesignModuleResult
        .builder(getId(), "Souders-Brown and liquid retention preliminary sizing", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(diameterKey, selectedDiameter, "m")
            .governingCaseId(gasFlow.getDesignCaseId()).applier(new EngineeringDesignUpdate.Applier() {
              private static final long serialVersionUID = 1000L;

              @Override
              public void apply(ProcessSystem working, double value) {
                ((Separator) working.getUnit(separatorTag)).setInternalDiameter(value);
              }
            }).build())
        .addUpdate(EngineeringDesignUpdate.builder(lengthKey, selectedLength, "m")
            .governingCaseId(liquidFlow.getDesignCaseId()).applier(new EngineeringDesignUpdate.Applier() {
              private static final long serialVersionUID = 1000L;

              @Override
              public void apply(ProcessSystem working, double value) {
                ((Separator) working.getUnit(separatorTag)).setSeparatorLength(value);
              }
            }).build())
        .addUpdate(EngineeringDesignUpdate.builder(velocityKey, actualGasVelocity, "m/s")
            .governingCaseId(gasFlow.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(retentionKey, achievedRetention, "s")
            .governingCaseId(liquidFlow.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(separatorTag + ".gas-capacity", "Gas velocity below capacity",
            velocityKey, terminalVelocity, "m/s", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .addConstraint(new EngineeringDesignConstraint(separatorTag + ".retention", "Minimum liquid retention time",
            retentionKey, retentionTimeSeconds, "s", EngineeringDesignConstraint.Comparison.MINIMUM))
        .evidence("gasLoadFactor_mPerS", Double.valueOf(gasLoadFactorMPerS))
        .evidence("liquidDensity_kgPerM3", Double.valueOf(liquidDensityKgM3)).build();
  }

  private static double roundUp(double value, double increment) {
    return Math.ceil(value / increment) * increment;
  }
}
