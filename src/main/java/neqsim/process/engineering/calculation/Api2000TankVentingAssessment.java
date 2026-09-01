package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable normal and emergency tank-venting demand/capacity screening result. */
public final class Api2000TankVentingAssessment implements EngineeringConstraintResult, Serializable {
  private static final long serialVersionUID = 1000L;
  private final String standardEdition;
  private final double fillingOutbreathingRateM3PerS;
  private final double withdrawalInbreathingRateM3PerS;
  private final double requiredNormalOutbreathingRateM3PerS;
  private final double requiredNormalInbreathingRateM3PerS;
  private final double requiredEmergencyOutbreathingRateM3PerS;
  private final double normalOutbreathingUtilization;
  private final double normalInbreathingUtilization;
  private final double emergencyOutbreathingUtilization;
  private final double normalOutbreathingCapacityMarginM3PerS;
  private final double normalInbreathingCapacityMarginM3PerS;
  private final double emergencyOutbreathingCapacityMarginM3PerS;
  private final double normalPositivePressureMarginPa;
  private final double vacuumPressureMarginPa;
  private final double emergencyPositivePressureMarginPa;
  private final boolean normalOutbreathingCapacityAdequate;
  private final boolean normalInbreathingCapacityAdequate;
  private final boolean emergencyOutbreathingCapacityAdequate;
  private final boolean normalPositivePressureWithinTankLimit;
  private final boolean vacuumPressureWithinTankLimit;
  private final boolean emergencyPositivePressureWithinTankLimit;

  Api2000TankVentingAssessment(Api2000TankVentingScreeningKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    fillingOutbreathingRateM3PerS = input.getLiquidFillingRateM3PerS() * input.getFillingOutbreathingVolumeRatio();
    withdrawalInbreathingRateM3PerS = input.getLiquidWithdrawalRateM3PerS()
        * input.getWithdrawalInbreathingVolumeRatio();
    requiredNormalOutbreathingRateM3PerS = fillingOutbreathingRateM3PerS + input.getThermalOutbreathingRateM3PerS()
        + input.getOtherNormalOutbreathingRateM3PerS();
    requiredNormalInbreathingRateM3PerS = withdrawalInbreathingRateM3PerS + input.getThermalInbreathingRateM3PerS()
        + input.getOtherNormalInbreathingRateM3PerS();
    requiredEmergencyOutbreathingRateM3PerS = input.getTotalEmergencyOutbreathingRateM3PerS();
    normalOutbreathingUtilization = requiredNormalOutbreathingRateM3PerS
        / input.getNormalOutbreathingRatedCapacityM3PerS();
    normalInbreathingUtilization = requiredNormalInbreathingRateM3PerS
        / input.getNormalInbreathingRatedCapacityM3PerS();
    emergencyOutbreathingUtilization = requiredEmergencyOutbreathingRateM3PerS
        / input.getEmergencyOutbreathingRatedCapacityM3PerS();
    normalOutbreathingCapacityMarginM3PerS = input.getNormalOutbreathingRatedCapacityM3PerS()
        - requiredNormalOutbreathingRateM3PerS;
    normalInbreathingCapacityMarginM3PerS = input.getNormalInbreathingRatedCapacityM3PerS()
        - requiredNormalInbreathingRateM3PerS;
    emergencyOutbreathingCapacityMarginM3PerS = input.getEmergencyOutbreathingRatedCapacityM3PerS()
        - requiredEmergencyOutbreathingRateM3PerS;
    normalPositivePressureMarginPa = input.getTankMaximumPositiveGaugePressurePa()
        - input.getNormalOutbreathingRatedGaugePressurePa();
    vacuumPressureMarginPa = input.getTankMaximumVacuumPressurePa() - input.getNormalInbreathingRatedVacuumPressurePa();
    emergencyPositivePressureMarginPa = input.getTankMaximumPositiveGaugePressurePa()
        - input.getEmergencyOutbreathingRatedGaugePressurePa();
    normalOutbreathingCapacityAdequate = normalOutbreathingUtilization <= 1.0;
    normalInbreathingCapacityAdequate = normalInbreathingUtilization <= 1.0;
    emergencyOutbreathingCapacityAdequate = emergencyOutbreathingUtilization <= 1.0;
    normalPositivePressureWithinTankLimit = normalPositivePressureMarginPa >= 0.0;
    vacuumPressureWithinTankLimit = vacuumPressureMarginPa >= 0.0;
    emergencyPositivePressureWithinTankLimit = emergencyPositivePressureMarginPa >= 0.0;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return filling displacement contribution to normal outbreathing in reference m3/s */
  public double getFillingOutbreathingRateM3PerS() {
    return fillingOutbreathingRateM3PerS;
  }

  /** @return withdrawal displacement contribution to normal inbreathing in reference m3/s */
  public double getWithdrawalInbreathingRateM3PerS() {
    return withdrawalInbreathingRateM3PerS;
  }

  /** @return aggregated normal outbreathing demand in reference m3/s */
  public double getRequiredNormalOutbreathingRateM3PerS() {
    return requiredNormalOutbreathingRateM3PerS;
  }

  /** @return aggregated normal inbreathing demand in reference m3/s */
  public double getRequiredNormalInbreathingRateM3PerS() {
    return requiredNormalInbreathingRateM3PerS;
  }

  /** @return externally established total emergency outbreathing demand in reference m3/s */
  public double getRequiredEmergencyOutbreathingRateM3PerS() {
    return requiredEmergencyOutbreathingRateM3PerS;
  }

  /** @return normal outbreathing demand divided by rated capacity */
  public double getNormalOutbreathingUtilization() {
    return normalOutbreathingUtilization;
  }

  /** @return normal inbreathing demand divided by rated capacity */
  public double getNormalInbreathingUtilization() {
    return normalInbreathingUtilization;
  }

  /** @return emergency outbreathing demand divided by rated capacity */
  public double getEmergencyOutbreathingUtilization() {
    return emergencyOutbreathingUtilization;
  }

  /** @return rated normal outbreathing capacity minus demand in reference m3/s */
  public double getNormalOutbreathingCapacityMarginM3PerS() {
    return normalOutbreathingCapacityMarginM3PerS;
  }

  /** @return rated normal inbreathing capacity minus demand in reference m3/s */
  public double getNormalInbreathingCapacityMarginM3PerS() {
    return normalInbreathingCapacityMarginM3PerS;
  }

  /** @return rated emergency outbreathing capacity minus total demand in reference m3/s */
  public double getEmergencyOutbreathingCapacityMarginM3PerS() {
    return emergencyOutbreathingCapacityMarginM3PerS;
  }

  /** @return tank positive-pressure limit minus normal device rated pressure in Pa */
  public double getNormalPositivePressureMarginPa() {
    return normalPositivePressureMarginPa;
  }

  /** @return tank vacuum limit minus normal device rated vacuum magnitude in Pa */
  public double getVacuumPressureMarginPa() {
    return vacuumPressureMarginPa;
  }

  /** @return tank positive-pressure limit minus emergency device rated pressure in Pa */
  public double getEmergencyPositivePressureMarginPa() {
    return emergencyPositivePressureMarginPa;
  }

  /** @return whether normal outbreathing rated capacity covers the aggregated demand */
  public boolean isNormalOutbreathingCapacityAdequate() {
    return normalOutbreathingCapacityAdequate;
  }

  /** @return whether normal inbreathing rated capacity covers the aggregated demand */
  public boolean isNormalInbreathingCapacityAdequate() {
    return normalInbreathingCapacityAdequate;
  }

  /** @return whether emergency rated capacity covers the externally established total demand */
  public boolean isEmergencyOutbreathingCapacityAdequate() {
    return emergencyOutbreathingCapacityAdequate;
  }

  /** @return whether the normal outbreathing rated pressure is within the tank limit */
  public boolean isNormalPositivePressureWithinTankLimit() {
    return normalPositivePressureWithinTankLimit;
  }

  /** @return whether the rated vacuum magnitude is within the tank vacuum limit */
  public boolean isVacuumPressureWithinTankLimit() {
    return vacuumPressureWithinTankLimit;
  }

  /** @return whether the emergency rated pressure is within the tank positive-pressure limit */
  public boolean isEmergencyPositivePressureWithinTankLimit() {
    return emergencyPositivePressureWithinTankLimit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean allConstraintsSatisfied() {
    return normalOutbreathingCapacityAdequate && normalInbreathingCapacityAdequate
        && emergencyOutbreathingCapacityAdequate && normalPositivePressureWithinTankLimit
        && vacuumPressureWithinTankLimit && emergencyPositivePressureWithinTankLimit;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("scope", "CALLER_CONTROLLED_FIXED_ROOF_TANK_VENT_DEMAND_AND_CAPACITY_SCREEN");
    result.put("fillingOutbreathingRateM3PerS", Double.valueOf(fillingOutbreathingRateM3PerS));
    result.put("withdrawalInbreathingRateM3PerS", Double.valueOf(withdrawalInbreathingRateM3PerS));
    result.put("requiredNormalOutbreathingRateM3PerS", Double.valueOf(requiredNormalOutbreathingRateM3PerS));
    result.put("requiredNormalInbreathingRateM3PerS", Double.valueOf(requiredNormalInbreathingRateM3PerS));
    result.put("requiredEmergencyOutbreathingRateM3PerS", Double.valueOf(requiredEmergencyOutbreathingRateM3PerS));
    result.put("normalOutbreathingUtilization", Double.valueOf(normalOutbreathingUtilization));
    result.put("normalInbreathingUtilization", Double.valueOf(normalInbreathingUtilization));
    result.put("emergencyOutbreathingUtilization", Double.valueOf(emergencyOutbreathingUtilization));
    result.put("normalOutbreathingCapacityMarginM3PerS", Double.valueOf(normalOutbreathingCapacityMarginM3PerS));
    result.put("normalInbreathingCapacityMarginM3PerS", Double.valueOf(normalInbreathingCapacityMarginM3PerS));
    result.put("emergencyOutbreathingCapacityMarginM3PerS", Double.valueOf(emergencyOutbreathingCapacityMarginM3PerS));
    result.put("normalPositivePressureMarginPa", Double.valueOf(normalPositivePressureMarginPa));
    result.put("vacuumPressureMarginPa", Double.valueOf(vacuumPressureMarginPa));
    result.put("emergencyPositivePressureMarginPa", Double.valueOf(emergencyPositivePressureMarginPa));
    result.put("normalOutbreathingCapacityAdequate", Boolean.valueOf(normalOutbreathingCapacityAdequate));
    result.put("normalInbreathingCapacityAdequate", Boolean.valueOf(normalInbreathingCapacityAdequate));
    result.put("emergencyOutbreathingCapacityAdequate", Boolean.valueOf(emergencyOutbreathingCapacityAdequate));
    result.put("normalPositivePressureWithinTankLimit", Boolean.valueOf(normalPositivePressureWithinTankLimit));
    result.put("vacuumPressureWithinTankLimit", Boolean.valueOf(vacuumPressureWithinTankLimit));
    result.put("emergencyPositivePressureWithinTankLimit", Boolean.valueOf(emergencyPositivePressureWithinTankLimit));
    result.put("allCallerControlledConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
