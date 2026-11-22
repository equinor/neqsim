package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable caller-scenario heat-recovery receipt for coupled sulfur/nitrogen hydrotreating.
 *
 * <p>
 * The receipt applies a caller-owned recovery fraction to the qualified wet-flue-gas sensible stack loss. It closes
 * recovered heat, remaining sensible stack loss, and the unchanged residual furnace loss without sizing equipment or
 * claiming fuel, emissions, or cost savings.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stackLossBalance;
  private final double callerSpecifiedRecoveryFraction;
  private final double recoveredHeatMegaWatt;
  private final double remainingStackSensibleLossMegaWatt;
  private final double residualFurnaceLossMegaWatt;
  private final double postRecoveryFurnaceLossMegaWatt;
  private final double recoveredHeatFractionOfFuelChemicalPower;
  private final double postRecoveryFurnaceLossFractionOfFuelChemicalPower;
  private final double heatRecoveryClosureResidualMegaWatt;

  private RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stackLossBalance,
      double callerSpecifiedRecoveryFraction, double recoveredHeatMegaWatt, double remainingStackSensibleLossMegaWatt,
      double residualFurnaceLossMegaWatt, double postRecoveryFurnaceLossMegaWatt,
      double recoveredHeatFractionOfFuelChemicalPower, double postRecoveryFurnaceLossFractionOfFuelChemicalPower,
      double heatRecoveryClosureResidualMegaWatt) {
    this.stackLossBalance = stackLossBalance;
    this.callerSpecifiedRecoveryFraction = callerSpecifiedRecoveryFraction;
    this.recoveredHeatMegaWatt = recoveredHeatMegaWatt;
    this.remainingStackSensibleLossMegaWatt = remainingStackSensibleLossMegaWatt;
    this.residualFurnaceLossMegaWatt = residualFurnaceLossMegaWatt;
    this.postRecoveryFurnaceLossMegaWatt = postRecoveryFurnaceLossMegaWatt;
    this.recoveredHeatFractionOfFuelChemicalPower = recoveredHeatFractionOfFuelChemicalPower;
    this.postRecoveryFurnaceLossFractionOfFuelChemicalPower = postRecoveryFurnaceLossFractionOfFuelChemicalPower;
    this.heatRecoveryClosureResidualMegaWatt = heatRecoveryClosureResidualMegaWatt;
  }

  /**
   * Apply a caller-owned recovery fraction to qualified wet-flue-gas sensible loss.
   *
   * @param stackLossBalance qualified stack sensible-loss receipt
   * @param callerSpecifiedRecoveryFraction fraction of stack sensible loss assigned as recovered heat, from zero
   * through one
   * @return immutable heat-recovery receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance calculate(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stackLossBalance,
      double callerSpecifiedRecoveryFraction) {
    Objects.requireNonNull(stackLossBalance, "stackLossBalance");
    if (!Double.isFinite(callerSpecifiedRecoveryFraction) || callerSpecifiedRecoveryFraction < 0.0
        || callerSpecifiedRecoveryFraction > 1.0) {
      throw new IllegalArgumentException("callerSpecifiedRecoveryFraction must be finite and between zero and one");
    }

    double stackSensibleLoss = stackLossBalance.getStackSensibleLossMegaWatt();
    double recoveredHeat = stackSensibleLoss * callerSpecifiedRecoveryFraction;
    double remainingStackLoss = stackSensibleLoss - recoveredHeat;
    double residualFurnaceLoss = stackLossBalance.getResidualFurnaceLossMegaWatt();
    double postRecoveryFurnaceLoss = remainingStackLoss + residualFurnaceLoss;
    double fuelChemicalPower = stackLossBalance.getCombustionBalance().getUtilityBalance()
        .getFuelChemicalPowerMegaWatt();
    double recoveredFractionOfFuel = fuelChemicalPower == 0.0 ? 0.0 : recoveredHeat / fuelChemicalPower;
    double postRecoveryLossFractionOfFuel = fuelChemicalPower == 0.0 ? 0.0
        : postRecoveryFurnaceLoss / fuelChemicalPower;
    double originalFurnaceLoss = stackSensibleLoss + residualFurnaceLoss;
    double closureResidual = originalFurnaceLoss - recoveredHeat - postRecoveryFurnaceLoss;
    double tolerance = 1.0e-12 * Math.max(1.0, originalFurnaceLoss);

    if (!allFiniteNonNegative(recoveredHeat, remainingStackLoss, residualFurnaceLoss, postRecoveryFurnaceLoss,
        recoveredFractionOfFuel, postRecoveryLossFractionOfFuel) || !Double.isFinite(closureResidual)
        || Math.abs(closureResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed heat-recovery receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance(stackLossBalance,
        callerSpecifiedRecoveryFraction, recoveredHeat, remainingStackLoss, residualFurnaceLoss,
        postRecoveryFurnaceLoss, recoveredFractionOfFuel, postRecoveryLossFractionOfFuel, closureResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream qualified stack sensible-loss receipt */
  public RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance getStackLossBalance() {
    return stackLossBalance;
  }

  /** @return caller-specified fraction of stack sensible loss assigned as recovered heat */
  public double getCallerSpecifiedRecoveryFraction() {
    return callerSpecifiedRecoveryFraction;
  }

  /** @return caller-assigned recovered heat in MW */
  public double getRecoveredHeatMegaWatt() {
    return recoveredHeatMegaWatt;
  }

  /** @return wet-flue-gas sensible loss remaining after caller-assigned recovery in MW */
  public double getRemainingStackSensibleLossMegaWatt() {
    return remainingStackSensibleLossMegaWatt;
  }

  /** @return furnace loss not assigned to wet-flue-gas sensible loss in MW */
  public double getResidualFurnaceLossMegaWatt() {
    return residualFurnaceLossMegaWatt;
  }

  /** @return remaining stack sensible plus residual furnace loss in MW */
  public double getPostRecoveryFurnaceLossMegaWatt() {
    return postRecoveryFurnaceLossMegaWatt;
  }

  /** @return caller-assigned recovered heat divided by fuel chemical power */
  public double getRecoveredHeatFractionOfFuelChemicalPower() {
    return recoveredHeatFractionOfFuelChemicalPower;
  }

  /** @return post-recovery furnace loss divided by fuel chemical power */
  public double getPostRecoveryFurnaceLossFractionOfFuelChemicalPower() {
    return postRecoveryFurnaceLossFractionOfFuelChemicalPower;
  }

  /** @return original furnace loss minus recovered heat and post-recovery furnace loss in MW */
  public double getHeatRecoveryClosureResidualMegaWatt() {
    return heatRecoveryClosureResidualMegaWatt;
  }
}
