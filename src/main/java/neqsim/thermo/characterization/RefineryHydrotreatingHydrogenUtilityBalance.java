package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable hydrogen utility receipt derived from a qualified hydrotreating throughput balance.
 *
 * <p>
 * The receipt converts external fresh, consumed, and exported hydrogen rates to lower-heating-value power and
 * caller-priced cost rates. Internal recycle is excluded from the external boundary. The calculation does not predict
 * reaction heat, process duty, hydrogen price, emissions, or equipment performance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingHydrogenUtilityBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double MOLES_PER_KILOMOLE = 1000.0;
  private static final double SECONDS_PER_HOUR = 3600.0;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingThroughputBalance throughputBalance;
  private final double hydrogenLowerHeatingValueMegaJoulePerKg;
  private final double hydrogenCostPerKg;
  private final double freshHydrogenMassFlowKgPerHour;
  private final double consumedHydrogenMassFlowKgPerHour;
  private final double exportHydrogenMassFlowKgPerHour;
  private final double freshHydrogenChemicalPowerMegaWatt;
  private final double consumedHydrogenChemicalPowerMegaWatt;
  private final double exportHydrogenChemicalPowerMegaWatt;
  private final double hydrogenEnergyBalanceResidualMegaWatt;
  private final double freshHydrogenCostPerHour;
  private final double freshHydrogenCostPerTonneFeed;
  private final double freshHydrogenUtilizationFraction;

  private RefineryHydrotreatingHydrogenUtilityBalance(RefineryHydrotreatingThroughputBalance throughputBalance,
      double hydrogenLowerHeatingValueMegaJoulePerKg, double hydrogenCostPerKg,
      double freshHydrogenMassFlowKgPerHour, double consumedHydrogenMassFlowKgPerHour,
      double exportHydrogenMassFlowKgPerHour, double freshHydrogenChemicalPowerMegaWatt,
      double consumedHydrogenChemicalPowerMegaWatt, double exportHydrogenChemicalPowerMegaWatt,
      double hydrogenEnergyBalanceResidualMegaWatt, double freshHydrogenCostPerHour,
      double freshHydrogenCostPerTonneFeed, double freshHydrogenUtilizationFraction) {
    this.throughputBalance = throughputBalance;
    this.hydrogenLowerHeatingValueMegaJoulePerKg = hydrogenLowerHeatingValueMegaJoulePerKg;
    this.hydrogenCostPerKg = hydrogenCostPerKg;
    this.freshHydrogenMassFlowKgPerHour = freshHydrogenMassFlowKgPerHour;
    this.consumedHydrogenMassFlowKgPerHour = consumedHydrogenMassFlowKgPerHour;
    this.exportHydrogenMassFlowKgPerHour = exportHydrogenMassFlowKgPerHour;
    this.freshHydrogenChemicalPowerMegaWatt = freshHydrogenChemicalPowerMegaWatt;
    this.consumedHydrogenChemicalPowerMegaWatt = consumedHydrogenChemicalPowerMegaWatt;
    this.exportHydrogenChemicalPowerMegaWatt = exportHydrogenChemicalPowerMegaWatt;
    this.hydrogenEnergyBalanceResidualMegaWatt = hydrogenEnergyBalanceResidualMegaWatt;
    this.freshHydrogenCostPerHour = freshHydrogenCostPerHour;
    this.freshHydrogenCostPerTonneFeed = freshHydrogenCostPerTonneFeed;
    this.freshHydrogenUtilizationFraction = freshHydrogenUtilizationFraction;
  }

  /**
   * Calculate external hydrogen energy and cost rates.
   *
   * @param throughputBalance qualified hydrotreating throughput receipt
   * @param hydrogenLowerHeatingValueMegaJoulePerKg explicit hydrogen LHV basis in MJ/kg
   * @param hydrogenCostPerKg explicit price in caller-owned currency units per kg
   * @return immutable hydrogen utility receipt
   */
  public static RefineryHydrotreatingHydrogenUtilityBalance calculate(
      RefineryHydrotreatingThroughputBalance throughputBalance,
      double hydrogenLowerHeatingValueMegaJoulePerKg, double hydrogenCostPerKg) {
    Objects.requireNonNull(throughputBalance, "throughputBalance");
    if (!Double.isFinite(hydrogenLowerHeatingValueMegaJoulePerKg)
        || hydrogenLowerHeatingValueMegaJoulePerKg <= 0.0) {
      throw new IllegalArgumentException(
          "hydrogenLowerHeatingValueMegaJoulePerKg must be finite and positive");
    }
    if (!Double.isFinite(hydrogenCostPerKg) || hydrogenCostPerKg < 0.0) {
      throw new IllegalArgumentException("hydrogenCostPerKg must be finite and non-negative");
    }

    double hydrogenMolarMassKgPerKmol =
        RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
            * MOLES_PER_KILOMOLE;
    double freshHydrogenMassFlow = throughputBalance.getFreshHydrogenMolarFlowKmolPerHour()
        * hydrogenMolarMassKgPerKmol;
    double consumedHydrogenMassFlow = throughputBalance.getHydrogenConsumedMassFlowKgPerHour();
    double exportHydrogenMassFlow = throughputBalance.getExportHydrogenMolarFlowKmolPerHour()
        * hydrogenMolarMassKgPerKmol;
    double massResidual =
        freshHydrogenMassFlow - consumedHydrogenMassFlow - exportHydrogenMassFlow;
    double massTolerance = 1.0e-12 * Math.max(1.0, freshHydrogenMassFlow);
    if (!allFiniteNonNegative(
            freshHydrogenMassFlow, consumedHydrogenMassFlow, exportHydrogenMassFlow)
        || Math.abs(massResidual) > massTolerance) {
      throw new IllegalArgumentException(
          "throughput balance does not define a closed external hydrogen balance");
    }

    double freshPower =
        freshHydrogenMassFlow * hydrogenLowerHeatingValueMegaJoulePerKg / SECONDS_PER_HOUR;
    double consumedPower =
        consumedHydrogenMassFlow * hydrogenLowerHeatingValueMegaJoulePerKg
            / SECONDS_PER_HOUR;
    double exportPower =
        exportHydrogenMassFlow * hydrogenLowerHeatingValueMegaJoulePerKg / SECONDS_PER_HOUR;
    double energyResidual = freshPower - consumedPower - exportPower;
    double freshCostPerHour = freshHydrogenMassFlow * hydrogenCostPerKg;
    double freshCostPerTonneFeed =
        freshCostPerHour * KILOGRAMS_PER_TONNE
            / throughputBalance.getFeedMassFlowKgPerHour();
    double utilizationFraction =
        freshHydrogenMassFlow == 0.0 ? 0.0 : consumedHydrogenMassFlow / freshHydrogenMassFlow;

    return new RefineryHydrotreatingHydrogenUtilityBalance(throughputBalance,
        hydrogenLowerHeatingValueMegaJoulePerKg, hydrogenCostPerKg, freshHydrogenMassFlow,
        consumedHydrogenMassFlow, exportHydrogenMassFlow, freshPower, consumedPower, exportPower,
        energyResidual, freshCostPerHour, freshCostPerTonneFeed, utilizationFraction);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream throughput receipt */
  public RefineryHydrotreatingThroughputBalance getThroughputBalance() {
    return throughputBalance;
  }

  /** @return explicit hydrogen LHV basis in MJ/kg */
  public double getHydrogenLowerHeatingValueMegaJoulePerKg() {
    return hydrogenLowerHeatingValueMegaJoulePerKg;
  }

  /** @return explicit hydrogen price in caller-owned currency units per kg */
  public double getHydrogenCostPerKg() {
    return hydrogenCostPerKg;
  }

  /** @return external fresh H2 mass flow in kg/h */
  public double getFreshHydrogenMassFlowKgPerHour() {
    return freshHydrogenMassFlowKgPerHour;
  }

  /** @return consumed H2 mass flow in kg/h */
  public double getConsumedHydrogenMassFlowKgPerHour() {
    return consumedHydrogenMassFlowKgPerHour;
  }

  /** @return exported H2 mass flow in kg/h */
  public double getExportHydrogenMassFlowKgPerHour() {
    return exportHydrogenMassFlowKgPerHour;
  }

  /** @return fresh-H2 LHV power in MW */
  public double getFreshHydrogenChemicalPowerMegaWatt() {
    return freshHydrogenChemicalPowerMegaWatt;
  }

  /** @return consumed-H2 LHV power in MW */
  public double getConsumedHydrogenChemicalPowerMegaWatt() {
    return consumedHydrogenChemicalPowerMegaWatt;
  }

  /** @return exported-H2 LHV power in MW */
  public double getExportHydrogenChemicalPowerMegaWatt() {
    return exportHydrogenChemicalPowerMegaWatt;
  }

  /** @return external hydrogen-energy residual in MW */
  public double getHydrogenEnergyBalanceResidualMegaWatt() {
    return hydrogenEnergyBalanceResidualMegaWatt;
  }

  /** @return fresh-hydrogen cost rate in caller-owned currency units per hour */
  public double getFreshHydrogenCostPerHour() {
    return freshHydrogenCostPerHour;
  }

  /** @return fresh-hydrogen cost in caller-owned currency units per tonne of liquid feed */
  public double getFreshHydrogenCostPerTonneFeed() {
    return freshHydrogenCostPerTonneFeed;
  }

  /** @return fraction of external fresh H2 consumed */
  public double getFreshHydrogenUtilizationFraction() {
    return freshHydrogenUtilizationFraction;
  }
}
