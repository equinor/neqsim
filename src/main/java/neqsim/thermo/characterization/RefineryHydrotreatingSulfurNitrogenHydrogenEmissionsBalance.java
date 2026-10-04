package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable indirect-emissions receipt derived from a qualified coupled sulfur/nitrogen hydrogen utility balance.
 *
 * <p>
 * The receipt applies caller-owned hydrogen-supply emission-factor and carbon-price scenarios to external fresh
 * hydrogen. It does not select a production pathway, define a lifecycle boundary, or predict direct process emissions.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utilityBalance;
  private final double hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen;
  private final double carbonPricePerTonneCo2Equivalent;
  private final double hydrogenSupplyEmissionsKgCo2EquivalentPerHour;
  private final double hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed;
  private final double carbonCostPerHour;
  private final double carbonCostPerTonneFeed;

  private RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance(
      RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utilityBalance,
      double hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen, double carbonPricePerTonneCo2Equivalent,
      double hydrogenSupplyEmissionsKgCo2EquivalentPerHour, double hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed,
      double carbonCostPerHour, double carbonCostPerTonneFeed) {
    this.utilityBalance = utilityBalance;
    this.hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen = hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen;
    this.carbonPricePerTonneCo2Equivalent = carbonPricePerTonneCo2Equivalent;
    this.hydrogenSupplyEmissionsKgCo2EquivalentPerHour = hydrogenSupplyEmissionsKgCo2EquivalentPerHour;
    this.hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed = hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed;
    this.carbonCostPerHour = carbonCostPerHour;
    this.carbonCostPerTonneFeed = carbonCostPerTonneFeed;
  }

  /**
   * Calculate indirect hydrogen-supply emissions and caller-priced carbon cost.
   *
   * @param utilityBalance qualified coupled sulfur/nitrogen external hydrogen utility receipt
   * @param hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen explicit scenario emission factor in kg CO2e per kg
   * fresh H2
   * @param carbonPricePerTonneCo2Equivalent explicit scenario price in caller-owned currency units per tonne CO2e
   * @return immutable indirect-emissions receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance calculate(
      RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utilityBalance,
      double hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen, double carbonPricePerTonneCo2Equivalent) {
    Objects.requireNonNull(utilityBalance, "utilityBalance");
    if (!Double.isFinite(hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen)
        || hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen < 0.0) {
      throw new IllegalArgumentException(
          "hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen must be finite and non-negative");
    }
    if (!Double.isFinite(carbonPricePerTonneCo2Equivalent) || carbonPricePerTonneCo2Equivalent < 0.0) {
      throw new IllegalArgumentException("carbonPricePerTonneCo2Equivalent must be finite and non-negative");
    }

    double emissionsPerHour = utilityBalance.getFreshHydrogenMassFlowKgPerHour()
        * hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen;
    double feedMassFlowKgPerHour = utilityBalance.getThroughputBalance().getFeedMassFlowKgPerHour();
    double emissionsPerTonneFeed = emissionsPerHour * KILOGRAMS_PER_TONNE / feedMassFlowKgPerHour;
    double carbonCostPerHour = emissionsPerHour / KILOGRAMS_PER_TONNE * carbonPricePerTonneCo2Equivalent;
    double carbonCostPerTonneFeed = carbonCostPerHour * KILOGRAMS_PER_TONNE / feedMassFlowKgPerHour;

    return new RefineryHydrotreatingSulfurNitrogenHydrogenEmissionsBalance(utilityBalance,
        hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen, carbonPricePerTonneCo2Equivalent, emissionsPerHour,
        emissionsPerTonneFeed, carbonCostPerHour, carbonCostPerTonneFeed);
  }

  /** @return upstream immutable hydrogen utility receipt */
  public RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance getUtilityBalance() {
    return utilityBalance;
  }

  /** @return caller-owned hydrogen-supply emission factor in kg CO2e per kg fresh H2 */
  public double getHydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen() {
    return hydrogenSupplyEmissionFactorKgCo2EquivalentPerKgHydrogen;
  }

  /** @return caller-owned carbon price in currency units per tonne CO2e */
  public double getCarbonPricePerTonneCo2Equivalent() {
    return carbonPricePerTonneCo2Equivalent;
  }

  /** @return indirect hydrogen-supply emissions in kg CO2e/h */
  public double getHydrogenSupplyEmissionsKgCo2EquivalentPerHour() {
    return hydrogenSupplyEmissionsKgCo2EquivalentPerHour;
  }

  /** @return indirect hydrogen-supply emissions in kg CO2e per tonne liquid feed */
  public double getHydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed() {
    return hydrogenSupplyEmissionsKgCo2EquivalentPerTonneFeed;
  }

  /** @return caller-priced carbon cost in currency units/h */
  public double getCarbonCostPerHour() {
    return carbonCostPerHour;
  }

  /** @return caller-priced carbon cost in currency units per tonne liquid feed */
  public double getCarbonCostPerTonneFeed() {
    return carbonCostPerTonneFeed;
  }
}
