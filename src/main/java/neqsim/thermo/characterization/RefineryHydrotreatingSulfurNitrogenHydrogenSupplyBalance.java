package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable makeup-gas and outlet-gas receipt for a coupled sulfur/nitrogen hydrotreating screen.
 *
 * <p>
 * The receipt composes a qualified {@link RefineryHydrotreatingSulfurNitrogenBalance} with explicit hydrogen-purity,
 * supply-factor, and non-hydrogen molar-mass assumptions. It performs material bookkeeping only and does not predict
 * reaction kinetics, phase equilibrium, separation, recycle, catalyst performance, or operating conditions.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrotreatingSulfurNitrogenBalance materialBalance;
  private final double hydrogenSupplyFactor;
  private final double makeupHydrogenMoleFraction;
  private final double nonHydrogenMolarMassKgPerMol;
  private final double hydrogenConsumedMoles;
  private final double hydrogenSuppliedMoles;
  private final double unreactedHydrogenMoles;
  private final double nonHydrogenMoles;
  private final double makeupGasMoles;
  private final double makeupGasMassKg;
  private final double hydrogenSulfideMoles;
  private final double ammoniaMoles;
  private final double outletGasMoles;
  private final double outletGasMassKg;
  private final double outletHydrogenMoleFraction;
  private final double outletHydrogenSulfideMoleFraction;
  private final double outletAmmoniaMoleFraction;
  private final double outletNonHydrogenMoleFraction;
  private final double overallMassBalanceResidualKg;

  private RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance(
      RefineryHydrotreatingSulfurNitrogenBalance materialBalance, double hydrogenSupplyFactor,
      double makeupHydrogenMoleFraction, double nonHydrogenMolarMassKgPerMol, double hydrogenConsumedMoles,
      double hydrogenSuppliedMoles, double unreactedHydrogenMoles, double nonHydrogenMoles, double makeupGasMoles,
      double makeupGasMassKg, double hydrogenSulfideMoles, double ammoniaMoles, double outletGasMoles,
      double outletGasMassKg, double outletHydrogenMoleFraction, double outletHydrogenSulfideMoleFraction,
      double outletAmmoniaMoleFraction, double outletNonHydrogenMoleFraction, double overallMassBalanceResidualKg) {
    this.materialBalance = materialBalance;
    this.hydrogenSupplyFactor = hydrogenSupplyFactor;
    this.makeupHydrogenMoleFraction = makeupHydrogenMoleFraction;
    this.nonHydrogenMolarMassKgPerMol = nonHydrogenMolarMassKgPerMol;
    this.hydrogenConsumedMoles = hydrogenConsumedMoles;
    this.hydrogenSuppliedMoles = hydrogenSuppliedMoles;
    this.unreactedHydrogenMoles = unreactedHydrogenMoles;
    this.nonHydrogenMoles = nonHydrogenMoles;
    this.makeupGasMoles = makeupGasMoles;
    this.makeupGasMassKg = makeupGasMassKg;
    this.hydrogenSulfideMoles = hydrogenSulfideMoles;
    this.ammoniaMoles = ammoniaMoles;
    this.outletGasMoles = outletGasMoles;
    this.outletGasMassKg = outletGasMassKg;
    this.outletHydrogenMoleFraction = outletHydrogenMoleFraction;
    this.outletHydrogenSulfideMoleFraction = outletHydrogenSulfideMoleFraction;
    this.outletAmmoniaMoleFraction = outletAmmoniaMoleFraction;
    this.outletNonHydrogenMoleFraction = outletNonHydrogenMoleFraction;
    this.overallMassBalanceResidualKg = overallMassBalanceResidualKg;
  }

  /**
   * Calculate the coupled makeup-gas and outlet-gas material balance.
   *
   * @param materialBalance qualified coupled sulfur/nitrogen receipt
   * @param hydrogenSupplyFactor moles of hydrogen supplied per mole consumed; at least one
   * @param makeupHydrogenMoleFraction hydrogen mole fraction in makeup gas, in (0, 1]
   * @param nonHydrogenMolarMassKgPerMol average molar mass of the non-hydrogen makeup fraction
   * @return immutable coupled gas-supply receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance calculate(
      RefineryHydrotreatingSulfurNitrogenBalance materialBalance, double hydrogenSupplyFactor,
      double makeupHydrogenMoleFraction, double nonHydrogenMolarMassKgPerMol) {
    Objects.requireNonNull(materialBalance, "materialBalance");
    if (!Double.isFinite(hydrogenSupplyFactor) || hydrogenSupplyFactor < 1.0) {
      throw new IllegalArgumentException("hydrogenSupplyFactor must be finite and at least one");
    }
    if (!Double.isFinite(makeupHydrogenMoleFraction) || makeupHydrogenMoleFraction <= 0.0
        || makeupHydrogenMoleFraction > 1.0) {
      throw new IllegalArgumentException("makeupHydrogenMoleFraction must be finite and in (0, 1]");
    }
    if (!Double.isFinite(nonHydrogenMolarMassKgPerMol) || nonHydrogenMolarMassKgPerMol <= 0.0) {
      throw new IllegalArgumentException("nonHydrogenMolarMassKgPerMol must be finite and positive");
    }

    double hydrogenConsumedMoles = materialBalance.getTotalHydrogenConsumedMassKg()
        / RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double hydrogenSuppliedMoles = hydrogenConsumedMoles * hydrogenSupplyFactor;
    double makeupGasMoles = hydrogenSuppliedMoles / makeupHydrogenMoleFraction;
    double nonHydrogenMoles = makeupGasMoles - hydrogenSuppliedMoles;
    double unreactedHydrogenMoles = hydrogenSuppliedMoles - hydrogenConsumedMoles;
    double hydrogenSulfideMoles = materialBalance.getHydrogenSulfideProducedMassKg()
        / RefineryHydrotreatingSulfurBalance.HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL;
    double ammoniaMoles = materialBalance.getAmmoniaProducedMassKg()
        / RefineryHydrotreatingNitrogenBalance.AMMONIA_MOLAR_MASS_KG_PER_MOL;

    double makeupGasMassKg = hydrogenSuppliedMoles * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
        + nonHydrogenMoles * nonHydrogenMolarMassKgPerMol;
    double outletGasMoles = unreactedHydrogenMoles + hydrogenSulfideMoles + ammoniaMoles + nonHydrogenMoles;
    double outletGasMassKg = unreactedHydrogenMoles * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
        + materialBalance.getHydrogenSulfideProducedMassKg() + materialBalance.getAmmoniaProducedMassKg()
        + nonHydrogenMoles * nonHydrogenMolarMassKgPerMol;

    double outletHydrogenMoleFraction = 0.0;
    double outletHydrogenSulfideMoleFraction = 0.0;
    double outletAmmoniaMoleFraction = 0.0;
    double outletNonHydrogenMoleFraction = 0.0;
    if (outletGasMoles > 0.0) {
      outletHydrogenMoleFraction = unreactedHydrogenMoles / outletGasMoles;
      outletHydrogenSulfideMoleFraction = hydrogenSulfideMoles / outletGasMoles;
      outletAmmoniaMoleFraction = ammoniaMoles / outletGasMoles;
      outletNonHydrogenMoleFraction = nonHydrogenMoles / outletGasMoles;
    }

    double overallMassBalanceResidualKg = materialBalance.getFeedMassKg() + makeupGasMassKg
        - materialBalance.getProductMassKg() - outletGasMassKg;
    double toleranceKg = 1.0e-12 * Math.max(1.0, materialBalance.getFeedMassKg() + makeupGasMassKg);
    if (!allFiniteNonNegative(hydrogenConsumedMoles, hydrogenSuppliedMoles, unreactedHydrogenMoles, nonHydrogenMoles,
        makeupGasMoles, makeupGasMassKg, hydrogenSulfideMoles, ammoniaMoles, outletGasMoles, outletGasMassKg)
        || hydrogenSuppliedMoles < hydrogenConsumedMoles || Math.abs(overallMassBalanceResidualKg) > toleranceKg) {
      throw new IllegalArgumentException("inputs do not define a closed coupled gas-supply balance");
    }

    return new RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance(materialBalance, hydrogenSupplyFactor,
        makeupHydrogenMoleFraction, nonHydrogenMolarMassKgPerMol, hydrogenConsumedMoles, hydrogenSuppliedMoles,
        unreactedHydrogenMoles, nonHydrogenMoles, makeupGasMoles, makeupGasMassKg, hydrogenSulfideMoles, ammoniaMoles,
        outletGasMoles, outletGasMassKg, outletHydrogenMoleFraction, outletHydrogenSulfideMoleFraction,
        outletAmmoniaMoleFraction, outletNonHydrogenMoleFraction, overallMassBalanceResidualKg);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream coupled material-balance receipt */
  public RefineryHydrotreatingSulfurNitrogenBalance getMaterialBalance() {
    return materialBalance;
  }

  /** @return moles of hydrogen supplied per mole consumed */
  public double getHydrogenSupplyFactor() {
    return hydrogenSupplyFactor;
  }

  /** @return hydrogen mole fraction in makeup gas */
  public double getMakeupHydrogenMoleFraction() {
    return makeupHydrogenMoleFraction;
  }

  /** @return average non-hydrogen molar mass in kilograms per mole */
  public double getNonHydrogenMolarMassKgPerMol() {
    return nonHydrogenMolarMassKgPerMol;
  }

  /** @return hydrogen consumed in moles */
  public double getHydrogenConsumedMoles() {
    return hydrogenConsumedMoles;
  }

  /** @return hydrogen supplied in moles */
  public double getHydrogenSuppliedMoles() {
    return hydrogenSuppliedMoles;
  }

  /** @return unreacted hydrogen in outlet gas, in moles */
  public double getUnreactedHydrogenMoles() {
    return unreactedHydrogenMoles;
  }

  /** @return non-hydrogen makeup gas in moles */
  public double getNonHydrogenMoles() {
    return nonHydrogenMoles;
  }

  /** @return total makeup gas in moles */
  public double getMakeupGasMoles() {
    return makeupGasMoles;
  }

  /** @return total makeup gas mass in kilograms */
  public double getMakeupGasMassKg() {
    return makeupGasMassKg;
  }

  /** @return hydrogen sulfide produced in moles */
  public double getHydrogenSulfideMoles() {
    return hydrogenSulfideMoles;
  }

  /** @return ammonia produced in moles */
  public double getAmmoniaMoles() {
    return ammoniaMoles;
  }

  /** @return total outlet gas in moles */
  public double getOutletGasMoles() {
    return outletGasMoles;
  }

  /** @return total outlet gas mass in kilograms */
  public double getOutletGasMassKg() {
    return outletGasMassKg;
  }

  /** @return hydrogen mole fraction in outlet gas */
  public double getOutletHydrogenMoleFraction() {
    return outletHydrogenMoleFraction;
  }

  /** @return hydrogen sulfide mole fraction in outlet gas */
  public double getOutletHydrogenSulfideMoleFraction() {
    return outletHydrogenSulfideMoleFraction;
  }

  /** @return ammonia mole fraction in outlet gas */
  public double getOutletAmmoniaMoleFraction() {
    return outletAmmoniaMoleFraction;
  }

  /** @return non-hydrogen mole fraction in outlet gas */
  public double getOutletNonHydrogenMoleFraction() {
    return outletNonHydrogenMoleFraction;
  }

  /** @return overall feed-plus-makeup mass residual in kilograms */
  public double getOverallMassBalanceResidualKg() {
    return overallMassBalanceResidualKg;
  }
}
