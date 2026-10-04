package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable flow-rate receipt derived from a qualified hydrotreating recycle balance.
 *
 * <p>
 * The receipt converts an upstream mass basis to kg/h and kmol/h without changing its chemistry, recovery, or purge
 * assumptions. Internal recycle is reported but excluded from the external mass balance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingThroughputBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double MOLES_PER_KILOMOLE = 1000.0;

  private final RefineryHydrotreatingHydrogenRecycleBalance recycleBalance;
  private final double feedMassFlowKgPerHour;
  private final double basisScalePerHour;
  private final double sulfurRemovedMassFlowKgPerHour;
  private final double hydrogenConsumedMassFlowKgPerHour;
  private final double liquidProductMassFlowKgPerHour;
  private final double freshMakeupGasMassFlowKgPerHour;
  private final double recycleGasMassFlowKgPerHour;
  private final double exportGasMassFlowKgPerHour;
  private final double freshHydrogenMolarFlowKmolPerHour;
  private final double freshNonHydrogenMolarFlowKmolPerHour;
  private final double recycleHydrogenMolarFlowKmolPerHour;
  private final double recycleHydrogenSulfideMolarFlowKmolPerHour;
  private final double recycleNonHydrogenMolarFlowKmolPerHour;
  private final double exportHydrogenMolarFlowKmolPerHour;
  private final double exportHydrogenSulfideMolarFlowKmolPerHour;
  private final double exportNonHydrogenMolarFlowKmolPerHour;
  private final double overallMassBalanceResidualKgPerHour;

  private RefineryHydrotreatingThroughputBalance(RefineryHydrotreatingHydrogenRecycleBalance recycleBalance,
      double feedMassFlowKgPerHour, double basisScalePerHour, double sulfurRemovedMassFlowKgPerHour,
      double hydrogenConsumedMassFlowKgPerHour, double liquidProductMassFlowKgPerHour,
      double freshMakeupGasMassFlowKgPerHour, double recycleGasMassFlowKgPerHour, double exportGasMassFlowKgPerHour,
      double freshHydrogenMolarFlowKmolPerHour, double freshNonHydrogenMolarFlowKmolPerHour,
      double recycleHydrogenMolarFlowKmolPerHour, double recycleHydrogenSulfideMolarFlowKmolPerHour,
      double recycleNonHydrogenMolarFlowKmolPerHour, double exportHydrogenMolarFlowKmolPerHour,
      double exportHydrogenSulfideMolarFlowKmolPerHour, double exportNonHydrogenMolarFlowKmolPerHour,
      double overallMassBalanceResidualKgPerHour) {
    this.recycleBalance = recycleBalance;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.basisScalePerHour = basisScalePerHour;
    this.sulfurRemovedMassFlowKgPerHour = sulfurRemovedMassFlowKgPerHour;
    this.hydrogenConsumedMassFlowKgPerHour = hydrogenConsumedMassFlowKgPerHour;
    this.liquidProductMassFlowKgPerHour = liquidProductMassFlowKgPerHour;
    this.freshMakeupGasMassFlowKgPerHour = freshMakeupGasMassFlowKgPerHour;
    this.recycleGasMassFlowKgPerHour = recycleGasMassFlowKgPerHour;
    this.exportGasMassFlowKgPerHour = exportGasMassFlowKgPerHour;
    this.freshHydrogenMolarFlowKmolPerHour = freshHydrogenMolarFlowKmolPerHour;
    this.freshNonHydrogenMolarFlowKmolPerHour = freshNonHydrogenMolarFlowKmolPerHour;
    this.recycleHydrogenMolarFlowKmolPerHour = recycleHydrogenMolarFlowKmolPerHour;
    this.recycleHydrogenSulfideMolarFlowKmolPerHour = recycleHydrogenSulfideMolarFlowKmolPerHour;
    this.recycleNonHydrogenMolarFlowKmolPerHour = recycleNonHydrogenMolarFlowKmolPerHour;
    this.exportHydrogenMolarFlowKmolPerHour = exportHydrogenMolarFlowKmolPerHour;
    this.exportHydrogenSulfideMolarFlowKmolPerHour = exportHydrogenSulfideMolarFlowKmolPerHour;
    this.exportNonHydrogenMolarFlowKmolPerHour = exportNonHydrogenMolarFlowKmolPerHour;
    this.overallMassBalanceResidualKgPerHour = overallMassBalanceResidualKgPerHour;
  }

  /**
   * Convert a qualified hydrotreating balance basis to flow rates.
   *
   * @param recycleBalance upstream immutable recycle/purge receipt
   * @param feedMassFlowKgPerHour fresh liquid feed mass flow in kg/h
   * @return immutable throughput receipt
   */
  public static RefineryHydrotreatingThroughputBalance calculate(
      RefineryHydrotreatingHydrogenRecycleBalance recycleBalance, double feedMassFlowKgPerHour) {
    Objects.requireNonNull(recycleBalance, "recycleBalance");
    if (!Double.isFinite(feedMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0) {
      throw new IllegalArgumentException("feedMassFlowKgPerHour must be finite and positive");
    }

    RefineryHydrotreatingHydrogenSupplyBalance supply = recycleBalance.getSupplyBalance();
    RefineryHydrotreatingSulfurBalance sulfur = supply.getSulfurBalance();
    double scale = feedMassFlowKgPerHour / sulfur.getFeedMassKg();
    double molarScale = scale / MOLES_PER_KILOMOLE;

    double sulfurRemovedRate = sulfur.getSulfurRemovedMassKg() * scale;
    double hydrogenConsumedRate = sulfur.getHydrogenConsumedMassKg() * scale;
    double liquidProductRate = sulfur.getProductMassKg() * scale;
    double freshMakeupRate = recycleBalance.getFreshMakeupGasMassKg() * scale;
    double recycleGasRate = recycleBalance.getRecycleGasMassKg() * scale;
    double exportGasRate = recycleBalance.getExportGasMassKg() * scale;
    double massResidual = feedMassFlowKgPerHour + freshMakeupRate - liquidProductRate - exportGasRate;

    double tolerance = 1.0e-12 * Math.max(1.0, feedMassFlowKgPerHour + freshMakeupRate);
    if (!allFiniteNonNegative(sulfurRemovedRate, hydrogenConsumedRate, liquidProductRate, freshMakeupRate,
        recycleGasRate, exportGasRate) || Math.abs(massResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed throughput balance");
    }

    return new RefineryHydrotreatingThroughputBalance(recycleBalance, feedMassFlowKgPerHour, scale, sulfurRemovedRate,
        hydrogenConsumedRate, liquidProductRate, freshMakeupRate, recycleGasRate, exportGasRate,
        recycleBalance.getFreshHydrogenMoles() * molarScale, recycleBalance.getFreshNonHydrogenMoles() * molarScale,
        recycleBalance.getRecycleHydrogenMoles() * molarScale,
        recycleBalance.getRecycleHydrogenSulfideMoles() * molarScale,
        recycleBalance.getRecycleNonHydrogenMoles() * molarScale, recycleBalance.getExportHydrogenMoles() * molarScale,
        recycleBalance.getExportHydrogenSulfideMoles() * molarScale,
        recycleBalance.getExportNonHydrogenMoles() * molarScale, massResidual);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream recycle/purge receipt */
  public RefineryHydrotreatingHydrogenRecycleBalance getRecycleBalance() {
    return recycleBalance;
  }

  /** @return fresh liquid feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return upstream-basis scale in 1/h */
  public double getBasisScalePerHour() {
    return basisScalePerHour;
  }

  /** @return sulfur removal rate in kg/h */
  public double getSulfurRemovedMassFlowKgPerHour() {
    return sulfurRemovedMassFlowKgPerHour;
  }

  /** @return hydrogen consumption rate in kg/h */
  public double getHydrogenConsumedMassFlowKgPerHour() {
    return hydrogenConsumedMassFlowKgPerHour;
  }

  /** @return liquid product mass flow in kg/h */
  public double getLiquidProductMassFlowKgPerHour() {
    return liquidProductMassFlowKgPerHour;
  }

  /** @return fresh makeup-gas mass flow in kg/h */
  public double getFreshMakeupGasMassFlowKgPerHour() {
    return freshMakeupGasMassFlowKgPerHour;
  }

  /** @return internal recycle-gas mass flow in kg/h */
  public double getRecycleGasMassFlowKgPerHour() {
    return recycleGasMassFlowKgPerHour;
  }

  /** @return exported-gas mass flow in kg/h */
  public double getExportGasMassFlowKgPerHour() {
    return exportGasMassFlowKgPerHour;
  }

  /** @return fresh H2 molar flow in kmol/h */
  public double getFreshHydrogenMolarFlowKmolPerHour() {
    return freshHydrogenMolarFlowKmolPerHour;
  }

  /** @return fresh non-H2 molar flow in kmol/h */
  public double getFreshNonHydrogenMolarFlowKmolPerHour() {
    return freshNonHydrogenMolarFlowKmolPerHour;
  }

  /** @return recycle H2 molar flow in kmol/h */
  public double getRecycleHydrogenMolarFlowKmolPerHour() {
    return recycleHydrogenMolarFlowKmolPerHour;
  }

  /** @return recycle H2S molar flow in kmol/h */
  public double getRecycleHydrogenSulfideMolarFlowKmolPerHour() {
    return recycleHydrogenSulfideMolarFlowKmolPerHour;
  }

  /** @return recycle non-H2 molar flow in kmol/h */
  public double getRecycleNonHydrogenMolarFlowKmolPerHour() {
    return recycleNonHydrogenMolarFlowKmolPerHour;
  }

  /** @return export H2 molar flow in kmol/h */
  public double getExportHydrogenMolarFlowKmolPerHour() {
    return exportHydrogenMolarFlowKmolPerHour;
  }

  /** @return export H2S molar flow in kmol/h */
  public double getExportHydrogenSulfideMolarFlowKmolPerHour() {
    return exportHydrogenSulfideMolarFlowKmolPerHour;
  }

  /** @return export non-H2 molar flow in kmol/h */
  public double getExportNonHydrogenMolarFlowKmolPerHour() {
    return exportNonHydrogenMolarFlowKmolPerHour;
  }

  /** @return external mass-balance residual in kg/h */
  public double getOverallMassBalanceResidualKgPerHour() {
    return overallMassBalanceResidualKgPerHour;
  }
}
