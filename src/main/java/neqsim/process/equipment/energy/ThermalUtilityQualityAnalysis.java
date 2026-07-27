package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.UtilityLevel;

/**
 * Thermodynamic feasibility and reversible-exergy analysis for a {@link UtilityEnergyBus}.
 *
 * <p>
 * Thermal power alone does not determine whether a utility can serve a process duty. Heating utilities must be hot
 * enough above the process temperature, while cooling utilities must be cold enough below it. The exergy methods use
 * the logarithmic-mean utility temperature and the Carnot quality factor to report the minimum reversible work
 * associated with a thermal duty. They are screening metrics, not replacements for a full entropy balance.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ThermalUtilityQualityAnalysis {
  private ThermalUtilityQualityAnalysis() {
  }

  public static boolean canServeProcessTemperature(UtilityEnergyBus utilityBus, double processTemperature,
      double minimumApproachTemperature) {
    UtilityEnergyBus bus = requireBus(utilityBus);
    validateTemperature(processTemperature, "Process temperature");
    validateApproach(minimumApproachTemperature);
    double supplyTemperature = bus.getSupplyTemperature();
    if (!Double.isFinite(supplyTemperature) || supplyTemperature <= 0.0) {
      return false;
    }
    if (isHeatingUtility(bus.getUtilityLevel())) {
      return supplyTemperature >= processTemperature + minimumApproachTemperature;
    }
    return supplyTemperature <= processTemperature - minimumApproachTemperature;
  }

  public static void requireFeasibleProcessTemperature(UtilityEnergyBus utilityBus, double processTemperature,
      double minimumApproachTemperature) {
    if (!canServeProcessTemperature(utilityBus, processTemperature, minimumApproachTemperature)) {
      UtilityEnergyBus bus = requireBus(utilityBus);
      String direction = isHeatingUtility(bus.getUtilityLevel()) ? "above" : "below";
      throw new IllegalStateException("Utility " + bus.getName() + " supply temperature must be at least "
          + minimumApproachTemperature + " K " + direction + " the process temperature");
    }
  }

  public static double getEffectiveTemperature(UtilityEnergyBus utilityBus) {
    UtilityEnergyBus bus = requireBus(utilityBus);
    double supplyTemperature = bus.getSupplyTemperature();
    double returnTemperature = bus.getReturnTemperature();
    if (!Double.isFinite(supplyTemperature) || supplyTemperature <= 0.0 || !Double.isFinite(returnTemperature)
        || returnTemperature <= 0.0) {
      throw new IllegalStateException(
          "Configure positive finite utility supply and return temperatures before exergy analysis");
    }
    if (Math.abs(supplyTemperature - returnTemperature) <= Math.max(supplyTemperature, returnTemperature) * 1.0e-12) {
      return 0.5 * (supplyTemperature + returnTemperature);
    }
    double logarithmicMean = (supplyTemperature - returnTemperature) / Math.log(supplyTemperature / returnTemperature);
    if (!Double.isFinite(logarithmicMean) || logarithmicMean <= 0.0) {
      throw new IllegalStateException("Utility temperatures do not define a finite logarithmic-mean temperature");
    }
    return logarithmicMean;
  }

  public static double getExergyFactor(UtilityEnergyBus utilityBus, double referenceTemperature) {
    validateTemperature(referenceTemperature, "Reference temperature");
    return Math.abs(1.0 - referenceTemperature / getEffectiveTemperature(utilityBus));
  }

  public static double getExergyRateForDuty(UtilityEnergyBus utilityBus, double thermalDuty,
      double referenceTemperature) {
    if (!Double.isFinite(thermalDuty) || thermalDuty < 0.0) {
      throw new IllegalArgumentException("Thermal duty must be non-negative and finite");
    }
    return thermalDuty * getExergyFactor(utilityBus, referenceTemperature);
  }

  public static double getServedExergyRate(UtilityEnergyBus utilityBus, double referenceTemperature) {
    UtilityEnergyBus bus = requireBus(utilityBus);
    return getExergyRateForDuty(bus, requireReport(bus).getServedDemand(), referenceTemperature);
  }

  public static double getUnmetExergyRate(UtilityEnergyBus utilityBus, double referenceTemperature) {
    UtilityEnergyBus bus = requireBus(utilityBus);
    return getExergyRateForDuty(bus, requireReport(bus).getUnmetDemand(), referenceTemperature);
  }

  public static double getCurtailedExergyRate(UtilityEnergyBus utilityBus, double referenceTemperature) {
    UtilityEnergyBus bus = requireBus(utilityBus);
    return getExergyRateForDuty(bus, requireReport(bus).getCurtailedSupply(), referenceTemperature);
  }

  public static boolean isHeatingUtility(UtilityLevel utilityLevel) {
    if (utilityLevel == null || utilityLevel == UtilityLevel.UNSPECIFIED) {
      throw new IllegalArgumentException("A specified utility level is required");
    }
    return utilityLevel == UtilityLevel.HIGH_PRESSURE_STEAM || utilityLevel == UtilityLevel.MEDIUM_PRESSURE_STEAM
        || utilityLevel == UtilityLevel.LOW_PRESSURE_STEAM || utilityLevel == UtilityLevel.HOT_OIL;
  }

  private static UtilityEnergyBus requireBus(UtilityEnergyBus utilityBus) {
    if (utilityBus == null) {
      throw new IllegalArgumentException("Utility bus is required");
    }
    return utilityBus;
  }

  private static EnergyNetworkReport requireReport(UtilityEnergyBus utilityBus) {
    EnergyNetworkReport report = utilityBus.getLastReport();
    if (report == null) {
      throw new IllegalStateException("Solve the utility energy bus before requesting exergy KPIs");
    }
    return report;
  }

  private static void validateTemperature(double temperature, String name) {
    if (!Double.isFinite(temperature) || temperature <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  private static void validateApproach(double approach) {
    if (!Double.isFinite(approach) || approach < 0.0) {
      throw new IllegalArgumentException("Minimum approach temperature must be non-negative and finite");
    }
  }
}
