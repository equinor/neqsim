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

/** Preliminary heat-transfer-area selection from the governing simulated duty. */
public final class HeatExchangerPreliminaryDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final String dutyMetricId;
  private final double overallHeatTransferCoefficientWPerM2K;
  private final double correctedLmtdK;
  private final double areaMarginFraction;
  private final double[] areaCandidatesM2;

  public HeatExchangerPreliminaryDesignModule(String equipmentTag, String dutyMetricId,
      double overallHeatTransferCoefficientWPerM2K, double correctedLmtdK, double areaMarginFraction,
      double[] areaCandidatesM2) {
    this.equipmentTag = equipmentTag;
    this.dutyMetricId = dutyMetricId;
    this.overallHeatTransferCoefficientWPerM2K = overallHeatTransferCoefficientWPerM2K;
    this.correctedLmtdK = correctedLmtdK;
    this.areaMarginFraction = areaMarginFraction;
    this.areaCandidatesM2 = areaCandidatesM2.clone();
  }

  @Override
  public String getId() {
    return "32-heat-exchanger-design-" + equipmentTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue duty = DesignModuleSupport.requireMetric(caseReport, dutyMetricId);
    double requiredArea = duty.getValue() * 1000.0 / overallHeatTransferCoefficientWPerM2K / correctedLmtdK
        * (1.0 + areaMarginFraction);
    double selectedArea = select(requiredArea);
    double utilization = requiredArea / selectedArea;
    String areaKey = equipmentTag + ".preliminaryHeatTransferArea";
    String utilizationKey = equipmentTag + ".areaUtilization";
    return EngineeringDesignModuleResult.builder(getId(), "Q=UAFDeltaT preliminary thermal design", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(areaKey, requiredArea, "m2").candidates(areaCandidatesM2)
            .governingCaseId(duty.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(utilizationKey, utilization, "fraction")
            .governingCaseId(duty.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(equipmentTag + ".area-capacity",
            "Selected heat-transfer area covers governing duty", utilizationKey, 1.0, "fraction",
            EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("overallHeatTransferCoefficient_WPerM2K", Double.valueOf(overallHeatTransferCoefficientWPerM2K))
        .evidence("correctedLmtd_K", Double.valueOf(correctedLmtdK))
        .warning("Thermal rating, fouling, vibration, mechanical configuration and vendor guarantee require review")
        .build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : areaCandidatesM2) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No exchanger area candidate satisfies " + required + " m2");
    }
    return selected;
  }
}
