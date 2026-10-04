package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable makeup-gas and outlet-gas material balance for a hydrotreating screening case.
 *
 * <p>
 * The calculation composes a {@link RefineryHydrotreatingSulfurBalance} with explicit hydrogen purity, excess supply,
 * and non-hydrogen molar-mass assumptions. It does not predict reaction kinetics, phase equilibrium, recycle behavior,
 * catalyst performance, or operating conditions.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingHydrogenSupplyBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrotreatingSulfurBalance sulfurBalance;
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
  private final double outletGasMoles;
  private final double outletGasMassKg;
  private final double outletHydrogenMoleFraction;
  private final double outletHydrogenSulfideMoleFraction;
  private final double outletNonHydrogenMoleFraction;
  private final double overallMassBalanceResidualKg;

  private RefineryHydrotreatingHydrogenSupplyBalance(RefineryHydrotreatingSulfurBalance sulfurBalance,
      double hydrogenSupplyFactor, double makeupHydrogenMoleFraction, double nonHydrogenMolarMassKgPerMol,
      double hydrogenConsumedMoles, double hydrogenSuppliedMoles, double unreactedHydrogenMoles,
      double nonHydrogenMoles, double makeupGasMoles, double makeupGasMassKg, double hydrogenSulfideMoles,
      double outletGasMoles, double outletGasMassKg, double outletHydrogenMoleFraction,
      double outletHydrogenSulfideMoleFraction, double outletNonHydrogenMoleFraction,
      double overallMassBalanceResidualKg) {
    this.sulfurBalance = sulfurBalance;
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
    this.outletGasMoles = outletGasMoles;
    this.outletGasMassKg = outletGasMassKg;
    this.outletHydrogenMoleFraction = outletHydrogenMoleFraction;
    this.outletHydrogenSulfideMoleFraction = outletHydrogenSulfideMoleFraction;
    this.outletNonHydrogenMoleFraction = outletNonHydrogenMoleFraction;
    this.overallMassBalanceResidualKg = overallMassBalanceResidualKg;
  }

  /**
   * Calculate the makeup-gas and outlet-gas material balance.
   *
   * @param sulfurBalance qualified sulfur-removal receipt to compose without mutation
   * @param hydrogenSupplyFactor moles of hydrogen supplied per mole consumed; at least one
   * @param makeupHydrogenMoleFraction hydrogen mole fraction in makeup gas, in (0, 1]
   * @param nonHydrogenMolarMassKgPerMol average molar mass of the non-hydrogen makeup fraction
   * @return immutable gas-supply balance receipt
   */
  public static RefineryHydrotreatingHydrogenSupplyBalance calculate(RefineryHydrotreatingSulfurBalance sulfurBalance,
      double hydrogenSupplyFactor, double makeupHydrogenMoleFraction, double nonHydrogenMolarMassKgPerMol) {
    Objects.requireNonNull(sulfurBalance, "sulfurBalance");
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

    double hydrogenConsumedMoles = sulfurBalance.getHydrogenConsumedMassKg()
        / RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double hydrogenSuppliedMoles = hydrogenConsumedMoles * hydrogenSupplyFactor;
    double makeupGasMoles = hydrogenSuppliedMoles / makeupHydrogenMoleFraction;
    double nonHydrogenMoles = makeupGasMoles - hydrogenSuppliedMoles;
    double unreactedHydrogenMoles = hydrogenSuppliedMoles - hydrogenConsumedMoles;
    double hydrogenSulfideMoles = sulfurBalance.getHydrogenSulfideProducedMassKg()
        / RefineryHydrotreatingSulfurBalance.HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL;
    double makeupGasMassKg = hydrogenSuppliedMoles * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
        + nonHydrogenMoles * nonHydrogenMolarMassKgPerMol;
    double outletGasMoles = unreactedHydrogenMoles + hydrogenSulfideMoles + nonHydrogenMoles;
    double outletGasMassKg = unreactedHydrogenMoles * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL
        + sulfurBalance.getHydrogenSulfideProducedMassKg() + nonHydrogenMoles * nonHydrogenMolarMassKgPerMol;

    double outletHydrogenMoleFraction = 0.0;
    double outletHydrogenSulfideMoleFraction = 0.0;
    double outletNonHydrogenMoleFraction = 0.0;
    if (outletGasMoles > 0.0) {
      outletHydrogenMoleFraction = unreactedHydrogenMoles / outletGasMoles;
      outletHydrogenSulfideMoleFraction = hydrogenSulfideMoles / outletGasMoles;
      outletNonHydrogenMoleFraction = nonHydrogenMoles / outletGasMoles;
    }

    double overallMassBalanceResidualKg = sulfurBalance.getFeedMassKg() + makeupGasMassKg
        - sulfurBalance.getProductMassKg() - outletGasMassKg;
    double toleranceKg = 1.0e-12 * Math.max(1.0, sulfurBalance.getFeedMassKg() + makeupGasMassKg);
    if (!Double.isFinite(makeupGasMassKg) || !Double.isFinite(outletGasMassKg) || makeupGasMassKg < 0.0
        || outletGasMassKg < 0.0 || hydrogenConsumedMoles < 0.0 || hydrogenSuppliedMoles < hydrogenConsumedMoles
        || unreactedHydrogenMoles < 0.0 || nonHydrogenMoles < 0.0 || hydrogenSulfideMoles < 0.0
        || Math.abs(overallMassBalanceResidualKg) > toleranceKg) {
      throw new IllegalArgumentException("inputs do not define a closed gas-supply balance");
    }

    return new RefineryHydrotreatingHydrogenSupplyBalance(sulfurBalance, hydrogenSupplyFactor,
        makeupHydrogenMoleFraction, nonHydrogenMolarMassKgPerMol, hydrogenConsumedMoles, hydrogenSuppliedMoles,
        unreactedHydrogenMoles, nonHydrogenMoles, makeupGasMoles, makeupGasMassKg, hydrogenSulfideMoles, outletGasMoles,
        outletGasMassKg, outletHydrogenMoleFraction, outletHydrogenSulfideMoleFraction, outletNonHydrogenMoleFraction,
        overallMassBalanceResidualKg);
  }

  /** @return upstream sulfur-removal receipt */
  public RefineryHydrotreatingSulfurBalance getSulfurBalance() {
    return sulfurBalance;
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

  /** @return non-hydrogen mole fraction in outlet gas */
  public double getOutletNonHydrogenMoleFraction() {
    return outletNonHydrogenMoleFraction;
  }

  /** @return overall feed-plus-makeup mass-balance residual in kilograms */
  public double getOverallMassBalanceResidualKg() {
    return overallMassBalanceResidualKg;
  }
}
