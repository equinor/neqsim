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

/** Selects compressor driver rating and records operating-envelope requirements across design cases. */
public final class CompressorPackageDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String compressorTag;
  private final String powerMetricId;
  private final String volumeFlowMetricId;
  private final double driverMarginFraction;
  private final double[] driverCandidatesKw;

  public CompressorPackageDesignModule(String compressorTag, String powerMetricId, String volumeFlowMetricId,
      double driverMarginFraction, double[] driverCandidatesKw) {
    this.compressorTag = compressorTag;
    this.powerMetricId = powerMetricId;
    this.volumeFlowMetricId = volumeFlowMetricId;
    this.driverMarginFraction = driverMarginFraction;
    this.driverCandidatesKw = driverCandidatesKw.clone();
  }

  @Override
  public String getId() {
    return "30-compressor-package-design-" + compressorTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue power = DesignModuleSupport.requireMetric(caseReport, powerMetricId);
    EngineeringDesignEnvelope.GoverningValue volumeFlow = DesignModuleSupport.requireMetric(caseReport,
        volumeFlowMetricId);
    double requiredDriverPower = power.getValue() * (1.0 + driverMarginFraction);
    double selectedDriverPower = select(requiredDriverPower);
    double utilization = power.getValue() / selectedDriverPower;
    String powerKey = compressorTag + ".driverRatedPower";
    String utilizationKey = compressorTag + ".driverUtilization";
    String capacityKey = compressorTag + ".maximumActualInletFlow";

    return EngineeringDesignModuleResult
        .builder(getId(), "Compressor case envelope and discrete driver selection", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(powerKey, requiredDriverPower, "kW").candidates(driverCandidatesKw)
            .governingCaseId(power.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(utilizationKey, utilization, "fraction")
            .governingCaseId(power.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(capacityKey, volumeFlow.getValue(), "m3/s")
            .governingCaseId(volumeFlow.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(compressorTag + ".driver-utilization",
            "Driver utilization at governing shaft power", utilizationKey, 1.0 / (1.0 + driverMarginFraction),
            "fraction", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("driverMarginFraction", Double.valueOf(driverMarginFraction))
        .warning("Vendor compressor map, driver starting study, torsional study, and certified package data required")
        .build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : driverCandidatesKw) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No driver candidate satisfies " + required + " kW for " + compressorTag);
    }
    return selected;
  }
}
