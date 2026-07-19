package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.fielddevelopment.lifecycle.FacilityLifecycleStrategy.DevelopmentMode;

/** Facility design basis and detailed-process sizing outcome used for a lifecycle run. */
public final class FacilityDesignResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String strategyName;
  private final DevelopmentMode developmentMode;
  private final FacilityProductionRate designRates;
  private final FacilityCapacity nameplateCapacity;
  private final double designMargin;
  private final double designPowerKw;
  private final int autoSizedEquipmentCount;
  private final int mechanicalConstraintCount;
  private final String designBottleneck;
  private final double designBottleneckUtilization;
  private final double detailedCapacityMultiplier;
  private final int requiredParallelTrainCount;

  FacilityDesignResult(String strategyName, DevelopmentMode developmentMode, FacilityProductionRate designRates,
      FacilityCapacity nameplateCapacity, double designMargin, double designPowerKw, int autoSizedEquipmentCount,
      int mechanicalConstraintCount, String designBottleneck, double designBottleneckUtilization,
      double detailedCapacityMultiplier, int requiredParallelTrainCount) {
    this.strategyName = strategyName;
    this.developmentMode = developmentMode;
    this.designRates = designRates;
    this.nameplateCapacity = nameplateCapacity;
    this.designMargin = designMargin;
    this.designPowerKw = designPowerKw;
    this.autoSizedEquipmentCount = autoSizedEquipmentCount;
    this.mechanicalConstraintCount = mechanicalConstraintCount;
    this.designBottleneck = designBottleneck;
    this.designBottleneckUtilization = designBottleneckUtilization;
    this.detailedCapacityMultiplier = detailedCapacityMultiplier;
    this.requiredParallelTrainCount = requiredParallelTrainCount;
  }

  /** Returns facility strategy name. */
  public String getStrategyName() {
    return strategyName;
  }

  /** Returns greenfield or brownfield development mode. */
  public DevelopmentMode getDevelopmentMode() {
    return developmentMode;
  }

  /** Returns simultaneous process design rates. */
  public FacilityProductionRate getDesignRates() {
    return designRates;
  }

  /** Returns installed component capacity envelope. */
  public FacilityCapacity getNameplateCapacity() {
    return nameplateCapacity;
  }

  /** Returns applied design margin. */
  public double getDesignMargin() {
    return designMargin;
  }

  /** Returns calculated power at the process design case in kW. */
  public double getDesignPowerKw() {
    return designPowerKw;
  }

  /** Returns number of detailed process equipment items auto-sized. */
  public int getAutoSizedEquipmentCount() {
    return autoSizedEquipmentCount;
  }

  /** Returns number of mechanical-design constraints registered. */
  public int getMechanicalConstraintCount() {
    return mechanicalConstraintCount;
  }

  /** Returns detailed process bottleneck at the design case. */
  public String getDesignBottleneck() {
    return designBottleneck;
  }

  /** Returns raw single-train bottleneck utilization at the design case. */
  public double getDesignBottleneckUtilization() {
    return designBottleneckUtilization;
  }

  /** Returns installed equivalent capacity relative to the auto-sized single-train process model. */
  public double getDetailedCapacityMultiplier() {
    return detailedCapacityMultiplier;
  }

  /** Returns the integer parallel-train indication needed for the design bottleneck. */
  public int getRequiredParallelTrainCount() {
    return requiredParallelTrainCount;
  }
}
