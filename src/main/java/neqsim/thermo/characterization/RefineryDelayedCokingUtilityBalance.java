package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/** Immutable feed-basis delayed-coking heater-fuel and steam screening receipt. */
public final class RefineryDelayedCokingUtilityBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final double processDutyMegaWatt;
  private final double fuelEnergyInputMegaJoulePerHour;
  private final double fuelMassFlowKgPerHour;
  private final double steamMassFlowKgPerHour;
  private final double energyBalanceResidualMegaJoulePerHour;

  private RefineryDelayedCokingUtilityBalance(double processDutyMegaWatt,
      double fuelEnergyInputMegaJoulePerHour, double fuelMassFlowKgPerHour,
      double steamMassFlowKgPerHour, double energyBalanceResidualMegaJoulePerHour) {
    this.processDutyMegaWatt = processDutyMegaWatt;
    this.fuelEnergyInputMegaJoulePerHour = fuelEnergyInputMegaJoulePerHour;
    this.fuelMassFlowKgPerHour = fuelMassFlowKgPerHour;
    this.steamMassFlowKgPerHour = steamMassFlowKgPerHour;
    this.energyBalanceResidualMegaJoulePerHour = energyBalanceResidualMegaJoulePerHour;
  }

  /**
   * Calculate feed-basis utility demand from caller-owned engineering assumptions.
   *
   * @param yieldBalance delayed-coking yield receipt supplying feed rate
   * @param specificProcessDutyMegaJoulePerKgFeed process duty in MJ/kg fresh feed
   * @param furnaceEfficiency fuel-to-process efficiency in (0, 1]
   * @param fuelLowerHeatingValueMegaJoulePerKg fuel lower heating value in MJ/kg
   * @param steamKgPerTonneFeed steam demand in kg/t fresh feed
   * @return immutable utility receipt
   */
  public static RefineryDelayedCokingUtilityBalance calculate(
      RefineryDelayedCokingYieldBalance yieldBalance,
      double specificProcessDutyMegaJoulePerKgFeed, double furnaceEfficiency,
      double fuelLowerHeatingValueMegaJoulePerKg, double steamKgPerTonneFeed) {
    Objects.requireNonNull(yieldBalance, "yieldBalance");
    if (!Double.isFinite(specificProcessDutyMegaJoulePerKgFeed)
        || specificProcessDutyMegaJoulePerKgFeed < 0.0) {
      throw new IllegalArgumentException(
          "specificProcessDutyMegaJoulePerKgFeed must be finite and non-negative");
    }
    if (!Double.isFinite(furnaceEfficiency) || furnaceEfficiency <= 0.0
        || furnaceEfficiency > 1.0) {
      throw new IllegalArgumentException("furnaceEfficiency must be finite and in (0, 1]");
    }
    if (!Double.isFinite(fuelLowerHeatingValueMegaJoulePerKg)
        || fuelLowerHeatingValueMegaJoulePerKg <= 0.0) {
      throw new IllegalArgumentException(
          "fuelLowerHeatingValueMegaJoulePerKg must be finite and positive");
    }
    if (!Double.isFinite(steamKgPerTonneFeed) || steamKgPerTonneFeed < 0.0) {
      throw new IllegalArgumentException("steamKgPerTonneFeed must be finite and non-negative");
    }

    double processDutyMegaJoulePerHour = yieldBalance.getFeedMassFlowKgPerHour()
        * specificProcessDutyMegaJoulePerKgFeed;
    double fuelEnergyInput = processDutyMegaJoulePerHour / furnaceEfficiency;
    double fuelMassFlow = fuelEnergyInput / fuelLowerHeatingValueMegaJoulePerKg;
    double steamMassFlow = yieldBalance.getFeedMassFlowKgPerHour() * steamKgPerTonneFeed / 1000.0;
    double energyResidual = fuelEnergyInput * furnaceEfficiency - processDutyMegaJoulePerHour;
    return new RefineryDelayedCokingUtilityBalance(processDutyMegaJoulePerHour / 3600.0,
        fuelEnergyInput, fuelMassFlow, steamMassFlow, energyResidual);
  }

  /** @return useful process duty in MW */
  public double getProcessDutyMegaWatt() {
    return processDutyMegaWatt;
  }

  /** @return fuel lower-heating-value input in MJ/h */
  public double getFuelEnergyInputMegaJoulePerHour() {
    return fuelEnergyInputMegaJoulePerHour;
  }

  /** @return fuel mass flow in kg/h */
  public double getFuelMassFlowKgPerHour() {
    return fuelMassFlowKgPerHour;
  }

  /** @return steam demand in kg/h */
  public double getSteamMassFlowKgPerHour() {
    return steamMassFlowKgPerHour;
  }

  /** @return useful-energy input minus process duty in MJ/h */
  public double getEnergyBalanceResidualMegaJoulePerHour() {
    return energyBalanceResidualMegaJoulePerHour;
  }
}
