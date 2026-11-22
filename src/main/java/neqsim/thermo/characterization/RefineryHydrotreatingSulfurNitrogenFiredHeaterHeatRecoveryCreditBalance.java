package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable caller-scenario fuel, cost, and emissions credit for utilized recovered heat.
 *
 * <p>
 * The receipt applies a caller-owned utilization fraction to a qualified fired-heater heat-recovery balance. Avoided
 * and net fuel, cost, and emissions values use only the already-qualified fired-heater efficiency, fuel LHV, price, and
 * emissions factor. It introduces no equipment, market, or lifecycle defaults.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double SECONDS_PER_HOUR = 3600.0;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance heatRecoveryBalance;
  private final double callerSpecifiedUtilizationFraction;
  private final double utilizedRecoveredHeatMegaWatt;
  private final double unutilizedRecoveredHeatMegaWatt;
  private final double avoidedFuelChemicalPowerMegaWatt;
  private final double avoidedFuelMassFlowKgPerHour;
  private final double avoidedFuelMassKgPerTonneFeed;
  private final double avoidedFuelCostPerHour;
  private final double avoidedFuelCostPerTonneFeed;
  private final double avoidedFuelEmissionsKgCo2EquivalentPerHour;
  private final double avoidedFuelEmissionsKgCo2EquivalentPerTonneFeed;
  private final double netFuelChemicalPowerMegaWatt;
  private final double netFuelMassFlowKgPerHour;
  private final double netFuelCostPerHour;
  private final double netFuelEmissionsKgCo2EquivalentPerHour;
  private final double recoveredHeatUtilizationClosureResidualMegaWatt;
  private final double fuelCreditClosureResidualMegaWatt;

  private RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance heatRecoveryBalance,
      double callerSpecifiedUtilizationFraction, double utilizedRecoveredHeatMegaWatt,
      double unutilizedRecoveredHeatMegaWatt, double avoidedFuelChemicalPowerMegaWatt,
      double avoidedFuelMassFlowKgPerHour, double avoidedFuelMassKgPerTonneFeed, double avoidedFuelCostPerHour,
      double avoidedFuelCostPerTonneFeed, double avoidedFuelEmissionsKgCo2EquivalentPerHour,
      double avoidedFuelEmissionsKgCo2EquivalentPerTonneFeed, double netFuelChemicalPowerMegaWatt,
      double netFuelMassFlowKgPerHour, double netFuelCostPerHour, double netFuelEmissionsKgCo2EquivalentPerHour,
      double recoveredHeatUtilizationClosureResidualMegaWatt, double fuelCreditClosureResidualMegaWatt) {
    this.heatRecoveryBalance = heatRecoveryBalance;
    this.callerSpecifiedUtilizationFraction = callerSpecifiedUtilizationFraction;
    this.utilizedRecoveredHeatMegaWatt = utilizedRecoveredHeatMegaWatt;
    this.unutilizedRecoveredHeatMegaWatt = unutilizedRecoveredHeatMegaWatt;
    this.avoidedFuelChemicalPowerMegaWatt = avoidedFuelChemicalPowerMegaWatt;
    this.avoidedFuelMassFlowKgPerHour = avoidedFuelMassFlowKgPerHour;
    this.avoidedFuelMassKgPerTonneFeed = avoidedFuelMassKgPerTonneFeed;
    this.avoidedFuelCostPerHour = avoidedFuelCostPerHour;
    this.avoidedFuelCostPerTonneFeed = avoidedFuelCostPerTonneFeed;
    this.avoidedFuelEmissionsKgCo2EquivalentPerHour = avoidedFuelEmissionsKgCo2EquivalentPerHour;
    this.avoidedFuelEmissionsKgCo2EquivalentPerTonneFeed = avoidedFuelEmissionsKgCo2EquivalentPerTonneFeed;
    this.netFuelChemicalPowerMegaWatt = netFuelChemicalPowerMegaWatt;
    this.netFuelMassFlowKgPerHour = netFuelMassFlowKgPerHour;
    this.netFuelCostPerHour = netFuelCostPerHour;
    this.netFuelEmissionsKgCo2EquivalentPerHour = netFuelEmissionsKgCo2EquivalentPerHour;
    this.recoveredHeatUtilizationClosureResidualMegaWatt = recoveredHeatUtilizationClosureResidualMegaWatt;
    this.fuelCreditClosureResidualMegaWatt = fuelCreditClosureResidualMegaWatt;
  }

  /**
   * Calculate caller-owned utilized-heat and avoided/net fired-heater receipts.
   *
   * @param heatRecoveryBalance qualified fired-heater heat-recovery balance
   * @param callerSpecifiedUtilizationFraction fraction of recovered heat used to displace delivered heater duty, from
   * zero through one
   * @return immutable recovered-heat credit balance
   */
  public static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance calculate(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance heatRecoveryBalance,
      double callerSpecifiedUtilizationFraction) {
    Objects.requireNonNull(heatRecoveryBalance, "heatRecoveryBalance");
    if (!Double.isFinite(callerSpecifiedUtilizationFraction) || callerSpecifiedUtilizationFraction < 0.0
        || callerSpecifiedUtilizationFraction > 1.0) {
      throw new IllegalArgumentException("callerSpecifiedUtilizationFraction must be finite and between zero and one");
    }

    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility = heatRecoveryBalance.getStackLossBalance()
        .getCombustionBalance().getUtilityBalance();
    double recoveredHeat = heatRecoveryBalance.getRecoveredHeatMegaWatt();
    double utilizedRecoveredHeat = recoveredHeat * callerSpecifiedUtilizationFraction;
    double unutilizedRecoveredHeat = recoveredHeat - utilizedRecoveredHeat;
    double deliveredHeating = utility.getDeliveredHeatingDutyMegaWatt();
    double tolerance = 1.0e-12 * Math.max(1.0, deliveredHeating + recoveredHeat);
    if (utilizedRecoveredHeat > deliveredHeating + tolerance) {
      throw new IllegalArgumentException("utilized recovered heat must not exceed delivered heater duty");
    }

    double avoidedFuelPower = utilizedRecoveredHeat / utility.getFurnaceEfficiencyFraction();
    double avoidedFuelMass = avoidedFuelPower * SECONDS_PER_HOUR / utility.getFuelLowerHeatingValueMegaJoulePerKg();
    double feedMassFlow = utility.getThermalDutyBalance().getProductDistributionReceipt().getThroughputBalance()
        .getFeedMassFlowKgPerHour();
    double avoidedFuelMassPerTonneFeed = avoidedFuelMass * KILOGRAMS_PER_TONNE / feedMassFlow;
    double avoidedFuelCost = avoidedFuelMass * utility.getFuelCostPerKg();
    double avoidedFuelCostPerTonneFeed = avoidedFuelCost * KILOGRAMS_PER_TONNE / feedMassFlow;
    double avoidedFuelEmissions = avoidedFuelMass * utility.getFuelEmissionsKgCo2EquivalentPerKg();
    double avoidedFuelEmissionsPerTonneFeed = avoidedFuelEmissions * KILOGRAMS_PER_TONNE / feedMassFlow;
    double netFuelPower = utility.getFuelChemicalPowerMegaWatt() - avoidedFuelPower;
    double netFuelMass = utility.getFuelMassFlowKgPerHour() - avoidedFuelMass;
    double netFuelCost = utility.getFuelCostPerHour() - avoidedFuelCost;
    double netFuelEmissions = utility.getFuelEmissionsKgCo2EquivalentPerHour() - avoidedFuelEmissions;
    double utilizationResidual = recoveredHeat - utilizedRecoveredHeat - unutilizedRecoveredHeat;
    double fuelCreditResidual = utility.getFuelChemicalPowerMegaWatt() - avoidedFuelPower - netFuelPower;

    if (!allFiniteNonNegative(utilizedRecoveredHeat, unutilizedRecoveredHeat, avoidedFuelPower, avoidedFuelMass,
        avoidedFuelMassPerTonneFeed, avoidedFuelCost, avoidedFuelCostPerTonneFeed, avoidedFuelEmissions,
        avoidedFuelEmissionsPerTonneFeed, netFuelPower, netFuelMass, netFuelCost, netFuelEmissions)
        || !Double.isFinite(utilizationResidual) || !Double.isFinite(fuelCreditResidual)
        || Math.abs(utilizationResidual) > tolerance || Math.abs(fuelCreditResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed recovered-heat credit balance");
    }

    return new RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalance(heatRecoveryBalance,
        callerSpecifiedUtilizationFraction, utilizedRecoveredHeat, unutilizedRecoveredHeat, avoidedFuelPower,
        avoidedFuelMass, avoidedFuelMassPerTonneFeed, avoidedFuelCost, avoidedFuelCostPerTonneFeed,
        avoidedFuelEmissions, avoidedFuelEmissionsPerTonneFeed, netFuelPower, netFuelMass, netFuelCost,
        netFuelEmissions, utilizationResidual, fuelCreditResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream qualified heat-recovery balance */
  public RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance getHeatRecoveryBalance() {
    return heatRecoveryBalance;
  }

  /** @return caller-owned fraction of recovered heat used to displace delivered heater duty */
  public double getCallerSpecifiedUtilizationFraction() {
    return callerSpecifiedUtilizationFraction;
  }

  /** @return recovered heat used to displace delivered heater duty in MW */
  public double getUtilizedRecoveredHeatMegaWatt() {
    return utilizedRecoveredHeatMegaWatt;
  }

  /** @return recovered heat not used to displace delivered heater duty in MW */
  public double getUnutilizedRecoveredHeatMegaWatt() {
    return unutilizedRecoveredHeatMegaWatt;
  }

  /** @return avoided fuel LHV input in MW */
  public double getAvoidedFuelChemicalPowerMegaWatt() {
    return avoidedFuelChemicalPowerMegaWatt;
  }

  /** @return avoided fuel mass flow in kg/h */
  public double getAvoidedFuelMassFlowKgPerHour() {
    return avoidedFuelMassFlowKgPerHour;
  }

  /** @return avoided fuel mass in kg per tonne liquid feed */
  public double getAvoidedFuelMassKgPerTonneFeed() {
    return avoidedFuelMassKgPerTonneFeed;
  }

  /** @return avoided caller-priced fuel cost in currency units/h */
  public double getAvoidedFuelCostPerHour() {
    return avoidedFuelCostPerHour;
  }

  /** @return avoided caller-priced fuel cost in currency units per tonne liquid feed */
  public double getAvoidedFuelCostPerTonneFeed() {
    return avoidedFuelCostPerTonneFeed;
  }

  /** @return avoided caller-scenario fuel emissions in kg CO2e/h */
  public double getAvoidedFuelEmissionsKgCo2EquivalentPerHour() {
    return avoidedFuelEmissionsKgCo2EquivalentPerHour;
  }

  /** @return avoided caller-scenario fuel emissions in kg CO2e per tonne liquid feed */
  public double getAvoidedFuelEmissionsKgCo2EquivalentPerTonneFeed() {
    return avoidedFuelEmissionsKgCo2EquivalentPerTonneFeed;
  }

  /** @return net fuel LHV input after the caller-owned heat-recovery credit in MW */
  public double getNetFuelChemicalPowerMegaWatt() {
    return netFuelChemicalPowerMegaWatt;
  }

  /** @return net fuel mass flow after the caller-owned heat-recovery credit in kg/h */
  public double getNetFuelMassFlowKgPerHour() {
    return netFuelMassFlowKgPerHour;
  }

  /** @return net caller-priced fuel cost after the heat-recovery credit in currency units/h */
  public double getNetFuelCostPerHour() {
    return netFuelCostPerHour;
  }

  /** @return net caller-scenario fuel emissions after the heat-recovery credit in kg CO2e/h */
  public double getNetFuelEmissionsKgCo2EquivalentPerHour() {
    return netFuelEmissionsKgCo2EquivalentPerHour;
  }

  /** @return recovered heat minus utilized and unutilized heat in MW */
  public double getRecoveredHeatUtilizationClosureResidualMegaWatt() {
    return recoveredHeatUtilizationClosureResidualMegaWatt;
  }

  /** @return original fuel power minus avoided and net fuel power in MW */
  public double getFuelCreditClosureResidualMegaWatt() {
    return fuelCreditClosureResidualMegaWatt;
  }
}
