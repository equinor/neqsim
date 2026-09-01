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

/** Proposes pressure design and safeguarding limits while preserving HAZOP/LOPA review boundaries. */
public final class ProcessSafetyDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final String maximumOperatingPressureMetricId;
  private final double designPressureFactor;
  private final double minimumPressureMarginBar;
  private final double highHighTripFractionOfSet;
  private final double blowdownTargetBara;

  public ProcessSafetyDesignModule(String equipmentTag, String maximumOperatingPressureMetricId,
      double designPressureFactor, double minimumPressureMarginBar, double highHighTripFractionOfSet,
      double blowdownTargetBara) {
    this.equipmentTag = equipmentTag;
    this.maximumOperatingPressureMetricId = maximumOperatingPressureMetricId;
    this.designPressureFactor = designPressureFactor;
    this.minimumPressureMarginBar = minimumPressureMarginBar;
    this.highHighTripFractionOfSet = highHighTripFractionOfSet;
    this.blowdownTargetBara = blowdownTargetBara;
  }

  @Override
  public String getId() {
    return "50-process-safety-design-" + equipmentTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue pressure = DesignModuleSupport.requireMetric(caseReport,
        maximumOperatingPressureMetricId);
    double designPressure = Math.max(pressure.getValue() * designPressureFactor,
        pressure.getValue() + minimumPressureMarginBar);
    double psvSetPressureBarg = Math.max(designPressure - 1.01325, 0.0);
    double tripPressure = Math.min(pressure.getValue() + 0.5 * minimumPressureMarginBar,
        1.01325 + psvSetPressureBarg * highHighTripFractionOfSet);
    double tripToReliefMargin = 1.01325 + psvSetPressureBarg - tripPressure;
    String designKey = equipmentTag + ".designPressure";
    String psvKey = equipmentTag + ".proposedPsvSetPressure";
    String tripKey = equipmentTag + ".proposedHighHighTripPressure";
    String marginKey = equipmentTag + ".tripToReliefMargin";
    String blowdownKey = equipmentTag + ".blowdownTargetPressure";

    return EngineeringDesignModuleResult.builder(getId(), "NORSOK P-002/API pressure safeguarding proposal", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(designKey, designPressure, "bara")
            .governingCaseId(pressure.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(psvKey, psvSetPressureBarg, "barg")
            .governingCaseId(pressure.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(tripKey, tripPressure, "bara")
            .governingCaseId(pressure.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(marginKey, tripToReliefMargin, "bar")
            .governingCaseId(pressure.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(blowdownKey, blowdownTargetBara, "bara").build())
        .addConstraint(new EngineeringDesignConstraint(equipmentTag + ".trip-below-relief",
            "Positive separation between proposed trip and relief set pressure", marginKey, 0.0, "bar",
            EngineeringDesignConstraint.Comparison.MINIMUM))
        .evidence("standards", "NORSOK P-002; API 520/521")
        .warning(
            "Scenario credibility, accumulation, set pressure, SIL target and final shutdown action require review")
        .build();
  }
}
