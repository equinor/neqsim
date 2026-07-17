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

/** Selects pump driver rating and verifies the minimum simulated NPSH margin. */
public final class PumpPackageDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String pumpTag;
  private final String powerMetricId;
  private final String npshMarginMetricId;
  private final double driverMarginFraction;
  private final double minimumNpshMarginM;
  private final double[] driverCandidatesKw;

  public PumpPackageDesignModule(String pumpTag, String powerMetricId, String npshMarginMetricId,
      double driverMarginFraction, double minimumNpshMarginM, double[] driverCandidatesKw) {
    this.pumpTag = pumpTag;
    this.powerMetricId = powerMetricId;
    this.npshMarginMetricId = npshMarginMetricId;
    this.driverMarginFraction = driverMarginFraction;
    this.minimumNpshMarginM = minimumNpshMarginM;
    this.driverCandidatesKw = driverCandidatesKw.clone();
  }

  @Override
  public String getId() {
    return "31-pump-package-design-" + pumpTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue power = DesignModuleSupport.requireMetric(caseReport, powerMetricId);
    EngineeringDesignEnvelope.GoverningValue npsh = DesignModuleSupport.requireMetric(caseReport, npshMarginMetricId);
    double requiredPower = power.getValue() * (1.0 + driverMarginFraction);
    double selectedPower = select(requiredPower);
    String powerKey = pumpTag + ".driverRatedPower";
    String utilizationKey = pumpTag + ".driverUtilization";
    String npshKey = pumpTag + ".minimumNpshMargin";
    return EngineeringDesignModuleResult.builder(getId(), "Pump case-envelope, driver, and NPSH screening", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(powerKey, requiredPower, "kW").candidates(driverCandidatesKw)
            .governingCaseId(power.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(utilizationKey, power.getValue() / selectedPower, "fraction")
            .governingCaseId(power.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(npshKey, npsh.getValue(), "m")
            .governingCaseId(npsh.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(pumpTag + ".driver-capacity", "Pump driver capacity",
            utilizationKey, 1.0, "fraction", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .addConstraint(new EngineeringDesignConstraint(pumpTag + ".npsh", "Minimum NPSH margin", npshKey,
            minimumNpshMarginM, "m", EngineeringDesignConstraint.Comparison.MINIMUM))
        .warning("Vendor curve, minimum continuous stable flow, seal system, start-up and runout require review")
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
      throw new IllegalStateException("No pump driver candidate satisfies " + required + " kW");
    }
    return selected;
  }
}
