package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable caller-scenario stack sensible-loss receipt for coupled sulfur/nitrogen hydrotreating.
 *
 * <p>
 * The receipt combines a qualified wet flue-gas molar flow with caller-owned stack and reference temperatures and a
 * caller-owned mean wet-flue-gas molar heat capacity. It reconciles the resulting sensible stack loss to the furnace
 * loss already qualified by the upstream utility receipt. It does not calculate heat capacity, heat transfer, or
 * equipment performance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOJOULE_PER_HOUR_PER_MEGAWATT = 3600000.0;

  private final RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustionBalance;
  private final double stackGasTemperatureKelvin;
  private final double referenceTemperatureKelvin;
  private final double meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin;
  private final double temperatureDifferenceKelvin;
  private final double stackSensibleLossMegaWatt;
  private final double residualFurnaceLossMegaWatt;
  private final double stackLossFractionOfFuelChemicalPower;
  private final double stackLossFractionOfFurnaceLoss;
  private final double residualLossFractionOfFuelChemicalPower;
  private final double furnaceLossClosureResidualMegaWatt;

  private RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustionBalance,
      double stackGasTemperatureKelvin, double referenceTemperatureKelvin,
      double meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin, double temperatureDifferenceKelvin,
      double stackSensibleLossMegaWatt, double residualFurnaceLossMegaWatt, double stackLossFractionOfFuelChemicalPower,
      double stackLossFractionOfFurnaceLoss, double residualLossFractionOfFuelChemicalPower,
      double furnaceLossClosureResidualMegaWatt) {
    this.combustionBalance = combustionBalance;
    this.stackGasTemperatureKelvin = stackGasTemperatureKelvin;
    this.referenceTemperatureKelvin = referenceTemperatureKelvin;
    this.meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin = meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin;
    this.temperatureDifferenceKelvin = temperatureDifferenceKelvin;
    this.stackSensibleLossMegaWatt = stackSensibleLossMegaWatt;
    this.residualFurnaceLossMegaWatt = residualFurnaceLossMegaWatt;
    this.stackLossFractionOfFuelChemicalPower = stackLossFractionOfFuelChemicalPower;
    this.stackLossFractionOfFurnaceLoss = stackLossFractionOfFurnaceLoss;
    this.residualLossFractionOfFuelChemicalPower = residualLossFractionOfFuelChemicalPower;
    this.furnaceLossClosureResidualMegaWatt = furnaceLossClosureResidualMegaWatt;
  }

  /**
   * Calculate caller-owned wet-flue-gas sensible loss and reconcile it to total furnace loss.
   *
   * @param combustionBalance qualified complete-combustion receipt
   * @param stackGasTemperatureKelvin caller-supplied wet-flue-gas stack temperature in K
   * @param referenceTemperatureKelvin caller-supplied sensible-enthalpy reference temperature in K
   * @param meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin caller-supplied mean wet-flue-gas molar heat capacity
   * between the reference and stack temperatures in kJ/(kmol K)
   * @return immutable stack sensible-loss receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance calculate(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustionBalance,
      double stackGasTemperatureKelvin, double referenceTemperatureKelvin,
      double meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin) {
    Objects.requireNonNull(combustionBalance, "combustionBalance");
    requireFinitePositive("stackGasTemperatureKelvin", stackGasTemperatureKelvin);
    requireFinitePositive("referenceTemperatureKelvin", referenceTemperatureKelvin);
    requireFinitePositive("meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin",
        meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin);
    if (stackGasTemperatureKelvin < referenceTemperatureKelvin) {
      throw new IllegalArgumentException("stackGasTemperatureKelvin must not be below referenceTemperatureKelvin");
    }

    double temperatureDifference = stackGasTemperatureKelvin - referenceTemperatureKelvin;
    double wetFlueGas = combustionBalance.getWetFlueGasKmolPerHour();
    double stackSensibleLoss = wetFlueGas * meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin
        * temperatureDifference / KILOJOULE_PER_HOUR_PER_MEGAWATT;
    RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utility = combustionBalance.getUtilityBalance();
    double furnaceLoss = utility.getFurnaceLossMegaWatt();
    double fuelChemicalPower = utility.getFuelChemicalPowerMegaWatt();
    double tolerance = 1.0e-12 * Math.max(1.0, furnaceLoss + stackSensibleLoss);
    if (!Double.isFinite(stackSensibleLoss) || stackSensibleLoss < 0.0 || stackSensibleLoss > furnaceLoss + tolerance) {
      throw new IllegalArgumentException("stack sensible loss must fit within the qualified furnace loss");
    }

    double residualFurnaceLoss = Math.max(0.0, furnaceLoss - stackSensibleLoss);
    double stackFractionOfFuel = fuelChemicalPower == 0.0 ? 0.0 : stackSensibleLoss / fuelChemicalPower;
    double stackFractionOfLoss = furnaceLoss == 0.0 ? 0.0 : stackSensibleLoss / furnaceLoss;
    double residualFractionOfFuel = fuelChemicalPower == 0.0 ? 0.0 : residualFurnaceLoss / fuelChemicalPower;
    double closureResidual = furnaceLoss - stackSensibleLoss - residualFurnaceLoss;

    if (!allFiniteNonNegative(temperatureDifference, stackSensibleLoss, residualFurnaceLoss, stackFractionOfFuel,
        stackFractionOfLoss, residualFractionOfFuel) || !Double.isFinite(closureResidual)
        || Math.abs(closureResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed stack-loss receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance(combustionBalance,
        stackGasTemperatureKelvin, referenceTemperatureKelvin, meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin,
        temperatureDifference, stackSensibleLoss, residualFurnaceLoss, stackFractionOfFuel, stackFractionOfLoss,
        residualFractionOfFuel, closureResidual);
  }

  private static void requireFinitePositive(String name, double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
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

  /** @return upstream qualified complete-combustion receipt */
  public RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance getCombustionBalance() {
    return combustionBalance;
  }

  /** @return caller-supplied wet-flue-gas stack temperature in K */
  public double getStackGasTemperatureKelvin() {
    return stackGasTemperatureKelvin;
  }

  /** @return caller-supplied sensible-enthalpy reference temperature in K */
  public double getReferenceTemperatureKelvin() {
    return referenceTemperatureKelvin;
  }

  /** @return caller-supplied mean wet-flue-gas molar heat capacity in kJ/(kmol K) */
  public double getMeanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin() {
    return meanWetFlueGasMolarHeatCapacityKiloJoulePerKmolKelvin;
  }

  /** @return stack minus reference temperature in K */
  public double getTemperatureDifferenceKelvin() {
    return temperatureDifferenceKelvin;
  }

  /** @return wet-flue-gas sensible stack loss in MW */
  public double getStackSensibleLossMegaWatt() {
    return stackSensibleLossMegaWatt;
  }

  /** @return qualified furnace loss not assigned to wet-flue-gas sensible loss in MW */
  public double getResidualFurnaceLossMegaWatt() {
    return residualFurnaceLossMegaWatt;
  }

  /** @return sensible stack loss divided by fuel chemical power */
  public double getStackLossFractionOfFuelChemicalPower() {
    return stackLossFractionOfFuelChemicalPower;
  }

  /** @return sensible stack loss divided by qualified total furnace loss */
  public double getStackLossFractionOfFurnaceLoss() {
    return stackLossFractionOfFurnaceLoss;
  }

  /** @return residual furnace loss divided by fuel chemical power */
  public double getResidualLossFractionOfFuelChemicalPower() {
    return residualLossFractionOfFuelChemicalPower;
  }

  /** @return total furnace loss minus stack sensible and residual losses in MW */
  public double getFurnaceLossClosureResidualMegaWatt() {
    return furnaceLossClosureResidualMegaWatt;
  }
}
