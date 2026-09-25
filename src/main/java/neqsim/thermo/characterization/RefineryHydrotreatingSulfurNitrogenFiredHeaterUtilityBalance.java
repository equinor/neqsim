package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable caller-scenario fired-heater utility receipt for coupled sulfur/nitrogen hydrotreating.
 *
 * <p>
 * The receipt converts the external-heating duty from a qualified thermal-duty balance to caller-owned fuel input,
 * cost, and indirect-emissions rates. It does not select a fuel, define a lifecycle boundary, or model combustion or
 * furnace performance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double SECONDS_PER_HOUR = 3600.0;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermalDutyBalance;
  private final double furnaceEfficiencyFraction;
  private final double fuelLowerHeatingValueMegaJoulePerKg;
  private final double fuelCostPerKg;
  private final double fuelEmissionsKgCo2EquivalentPerKg;
  private final double deliveredHeatingDutyMegaWatt;
  private final double coolingDutyMegaWatt;
  private final double fuelChemicalPowerMegaWatt;
  private final double furnaceLossMegaWatt;
  private final double fuelMassFlowKgPerHour;
  private final double fuelMassKgPerTonneFeed;
  private final double fuelCostPerHour;
  private final double fuelCostPerTonneFeed;
  private final double fuelEmissionsKgCo2EquivalentPerHour;
  private final double fuelEmissionsKgCo2EquivalentPerTonneFeed;
  private final double heatingDeliveryResidualMegaWatt;

  private RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance(
      RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermalDutyBalance, double furnaceEfficiencyFraction,
      double fuelLowerHeatingValueMegaJoulePerKg, double fuelCostPerKg, double fuelEmissionsKgCo2EquivalentPerKg,
      double deliveredHeatingDutyMegaWatt, double coolingDutyMegaWatt, double fuelChemicalPowerMegaWatt,
      double furnaceLossMegaWatt, double fuelMassFlowKgPerHour, double fuelMassKgPerTonneFeed, double fuelCostPerHour,
      double fuelCostPerTonneFeed, double fuelEmissionsKgCo2EquivalentPerHour,
      double fuelEmissionsKgCo2EquivalentPerTonneFeed, double heatingDeliveryResidualMegaWatt) {
    this.thermalDutyBalance = thermalDutyBalance;
    this.furnaceEfficiencyFraction = furnaceEfficiencyFraction;
    this.fuelLowerHeatingValueMegaJoulePerKg = fuelLowerHeatingValueMegaJoulePerKg;
    this.fuelCostPerKg = fuelCostPerKg;
    this.fuelEmissionsKgCo2EquivalentPerKg = fuelEmissionsKgCo2EquivalentPerKg;
    this.deliveredHeatingDutyMegaWatt = deliveredHeatingDutyMegaWatt;
    this.coolingDutyMegaWatt = coolingDutyMegaWatt;
    this.fuelChemicalPowerMegaWatt = fuelChemicalPowerMegaWatt;
    this.furnaceLossMegaWatt = furnaceLossMegaWatt;
    this.fuelMassFlowKgPerHour = fuelMassFlowKgPerHour;
    this.fuelMassKgPerTonneFeed = fuelMassKgPerTonneFeed;
    this.fuelCostPerHour = fuelCostPerHour;
    this.fuelCostPerTonneFeed = fuelCostPerTonneFeed;
    this.fuelEmissionsKgCo2EquivalentPerHour = fuelEmissionsKgCo2EquivalentPerHour;
    this.fuelEmissionsKgCo2EquivalentPerTonneFeed = fuelEmissionsKgCo2EquivalentPerTonneFeed;
    this.heatingDeliveryResidualMegaWatt = heatingDeliveryResidualMegaWatt;
  }

  /**
   * Calculate caller-owned fired-heater fuel, cost, and emissions bookkeeping.
   *
   * @param thermalDutyBalance qualified coupled thermal-duty receipt
   * @param furnaceEfficiencyFraction caller-supplied delivered-heat/fuel-LHV fraction in (0, 1]
   * @param fuelLowerHeatingValueMegaJoulePerKg caller-supplied fuel LHV in MJ/kg
   * @param fuelCostPerKg caller-supplied price in currency units per kg fuel
   * @param fuelEmissionsKgCo2EquivalentPerKg caller-supplied emissions factor in kg CO2e/kg fuel
   * @return immutable fired-heater utility receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance calculate(
      RefineryHydrotreatingSulfurNitrogenThermalDutyBalance thermalDutyBalance, double furnaceEfficiencyFraction,
      double fuelLowerHeatingValueMegaJoulePerKg, double fuelCostPerKg, double fuelEmissionsKgCo2EquivalentPerKg) {
    Objects.requireNonNull(thermalDutyBalance, "thermalDutyBalance");
    requireFinitePositive("furnaceEfficiencyFraction", furnaceEfficiencyFraction);
    if (furnaceEfficiencyFraction > 1.0) {
      throw new IllegalArgumentException("furnaceEfficiencyFraction must not exceed 1.0");
    }
    requireFinitePositive("fuelLowerHeatingValueMegaJoulePerKg", fuelLowerHeatingValueMegaJoulePerKg);
    requireFiniteNonNegative("fuelCostPerKg", fuelCostPerKg);
    requireFiniteNonNegative("fuelEmissionsKgCo2EquivalentPerKg", fuelEmissionsKgCo2EquivalentPerKg);

    double deliveredHeating = thermalDutyBalance.getExternalHeatingDutyMegaWatt();
    double coolingDuty = thermalDutyBalance.getExternalCoolingDutyMegaWatt();
    double fuelChemicalPower = deliveredHeating / furnaceEfficiencyFraction;
    double furnaceLoss = fuelChemicalPower - deliveredHeating;
    double fuelMassFlow = fuelChemicalPower * SECONDS_PER_HOUR / fuelLowerHeatingValueMegaJoulePerKg;
    double feedMassFlow = thermalDutyBalance.getProductDistributionReceipt().getThroughputBalance()
        .getFeedMassFlowKgPerHour();
    double fuelMassPerTonneFeed = fuelMassFlow * KILOGRAMS_PER_TONNE / feedMassFlow;
    double fuelCostRate = fuelMassFlow * fuelCostPerKg;
    double fuelCostPerTonneFeed = fuelCostRate * KILOGRAMS_PER_TONNE / feedMassFlow;
    double fuelEmissionsRate = fuelMassFlow * fuelEmissionsKgCo2EquivalentPerKg;
    double fuelEmissionsPerTonneFeed = fuelEmissionsRate * KILOGRAMS_PER_TONNE / feedMassFlow;
    double heatingDeliveryResidual = fuelChemicalPower * furnaceEfficiencyFraction - deliveredHeating;

    double tolerance = 1.0e-12 * Math.max(1.0, deliveredHeating + fuelChemicalPower);
    if (!allFiniteNonNegative(deliveredHeating, coolingDuty, fuelChemicalPower, furnaceLoss, fuelMassFlow,
        fuelMassPerTonneFeed, fuelCostRate, fuelCostPerTonneFeed, fuelEmissionsRate, fuelEmissionsPerTonneFeed)
        || !Double.isFinite(heatingDeliveryResidual) || Math.abs(heatingDeliveryResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed fired-heater receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance(thermalDutyBalance,
        furnaceEfficiencyFraction, fuelLowerHeatingValueMegaJoulePerKg, fuelCostPerKg,
        fuelEmissionsKgCo2EquivalentPerKg, deliveredHeating, coolingDuty, fuelChemicalPower, furnaceLoss, fuelMassFlow,
        fuelMassPerTonneFeed, fuelCostRate, fuelCostPerTonneFeed, fuelEmissionsRate, fuelEmissionsPerTonneFeed,
        heatingDeliveryResidual);
  }

  private static void requireFinitePositive(String name, double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFiniteNonNegative(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream qualified thermal-duty receipt */
  public RefineryHydrotreatingSulfurNitrogenThermalDutyBalance getThermalDutyBalance() {
    return thermalDutyBalance;
  }

  /** @return caller-supplied delivered-heat/fuel-LHV efficiency fraction */
  public double getFurnaceEfficiencyFraction() {
    return furnaceEfficiencyFraction;
  }

  /** @return caller-supplied fuel lower heating value in MJ/kg */
  public double getFuelLowerHeatingValueMegaJoulePerKg() {
    return fuelLowerHeatingValueMegaJoulePerKg;
  }

  /** @return caller-supplied fuel price in currency units per kg */
  public double getFuelCostPerKg() {
    return fuelCostPerKg;
  }

  /** @return caller-supplied fuel emissions factor in kg CO2e/kg */
  public double getFuelEmissionsKgCo2EquivalentPerKg() {
    return fuelEmissionsKgCo2EquivalentPerKg;
  }

  /** @return delivered external heating duty in MW */
  public double getDeliveredHeatingDutyMegaWatt() {
    return deliveredHeatingDutyMegaWatt;
  }

  /** @return cooling duty passed through from the thermal receipt in MW */
  public double getCoolingDutyMegaWatt() {
    return coolingDutyMegaWatt;
  }

  /** @return required fuel LHV input in MW */
  public double getFuelChemicalPowerMegaWatt() {
    return fuelChemicalPowerMegaWatt;
  }

  /** @return furnace loss on the caller efficiency basis in MW */
  public double getFurnaceLossMegaWatt() {
    return furnaceLossMegaWatt;
  }

  /** @return fuel mass flow in kg/h */
  public double getFuelMassFlowKgPerHour() {
    return fuelMassFlowKgPerHour;
  }

  /** @return fuel mass in kg per tonne liquid feed */
  public double getFuelMassKgPerTonneFeed() {
    return fuelMassKgPerTonneFeed;
  }

  /** @return fuel cost rate in caller-owned currency units per hour */
  public double getFuelCostPerHour() {
    return fuelCostPerHour;
  }

  /** @return fuel cost in caller-owned currency units per tonne liquid feed */
  public double getFuelCostPerTonneFeed() {
    return fuelCostPerTonneFeed;
  }

  /** @return caller-scenario fuel emissions in kg CO2e/h */
  public double getFuelEmissionsKgCo2EquivalentPerHour() {
    return fuelEmissionsKgCo2EquivalentPerHour;
  }

  /** @return caller-scenario fuel emissions in kg CO2e per tonne liquid feed */
  public double getFuelEmissionsKgCo2EquivalentPerTonneFeed() {
    return fuelEmissionsKgCo2EquivalentPerTonneFeed;
  }

  /** @return delivered-heating closure residual in MW */
  public double getHeatingDeliveryResidualMegaWatt() {
    return heatingDeliveryResidualMegaWatt;
  }
}
