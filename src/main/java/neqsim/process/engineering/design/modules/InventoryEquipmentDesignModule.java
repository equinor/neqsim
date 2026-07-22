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

/** Preliminary working-volume selection for tanks, drums, column sumps, and buffer vessels. */
public final class InventoryEquipmentDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final String volumeFlowMetricId;
  private final double workingTimeSeconds;
  private final double usableVolumeFraction;
  private final double[] volumeCandidatesM3;

  public InventoryEquipmentDesignModule(String equipmentTag, String volumeFlowMetricId, double workingTimeSeconds,
      double usableVolumeFraction, double[] volumeCandidatesM3) {
    this.equipmentTag = equipmentTag;
    this.volumeFlowMetricId = volumeFlowMetricId;
    this.workingTimeSeconds = workingTimeSeconds;
    this.usableVolumeFraction = usableVolumeFraction;
    this.volumeCandidatesM3 = volumeCandidatesM3.clone();
  }

  @Override
  public String getId() {
    return "15-inventory-design-" + equipmentTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue flow = DesignModuleSupport.requireMetric(caseReport, volumeFlowMetricId);
    double requiredGrossVolume = flow.getValue() * workingTimeSeconds / usableVolumeFraction;
    double selectedVolume = select(requiredGrossVolume);
    double achievedTime = selectedVolume * usableVolumeFraction / Math.max(flow.getValue(), 1.0e-12);
    String volumeKey = equipmentTag + ".grossDesignVolume";
    String timeKey = equipmentTag + ".achievedWorkingTime";
    return EngineeringDesignModuleResult.builder(getId(), "Inventory residence and surge volume sizing", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(volumeKey, requiredGrossVolume, "m3").candidates(volumeCandidatesM3)
            .governingCaseId(flow.getDesignCaseId()).build())
        .addUpdate(
            EngineeringDesignUpdate.builder(timeKey, achievedTime, "s").governingCaseId(flow.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(equipmentTag + ".working-time", "Minimum working time", timeKey,
            workingTimeSeconds, "s", EngineeringDesignConstraint.Comparison.MINIMUM))
        .warning("Operating levels, freeboard, slop, emergency volume and layout require project definition").build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : volumeCandidatesM3) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No inventory volume candidate satisfies " + required + " m3");
    }
    return selected;
  }
}
