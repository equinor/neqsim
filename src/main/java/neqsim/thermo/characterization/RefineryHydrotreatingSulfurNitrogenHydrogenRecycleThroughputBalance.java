package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable rate receipt for a coupled sulfur/nitrogen hydrotreating recycle balance.
 *
 * <p>
 * This class scales a qualified basis receipt to kg/h and kmol/h. Internal recycle is reported
 * separately and is excluded from the external mass balance.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance
    implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycleBalance;
  private final double feedMassFlowKgPerHour;
  private final double basisScalePerHour;
  private final double productMassFlowKgPerHour;
  private final double hydrogenConsumedMassFlowKgPerHour;
  private final double freshMakeupGasMassFlowKgPerHour;
  private final double recycleGasMassFlowKgPerHour;
  private final double exportGasMassFlowKgPerHour;
  private final double freshHydrogenMolarFlowKmolPerHour;
  private final double freshNonHydrogenMolarFlowKmolPerHour;
  private final double recycleHydrogenMolarFlowKmolPerHour;
  private final double recycleHydrogenSulfideMolarFlowKmolPerHour;
  private final double recycleAmmoniaMolarFlowKmolPerHour;
  private final double recycleNonHydrogenMolarFlowKmolPerHour;
  private final double exportHydrogenMolarFlowKmolPerHour;
  private final double exportHydrogenSulfideMolarFlowKmolPerHour;
  private final double exportAmmoniaMolarFlowKmolPerHour;
  private final double exportNonHydrogenMolarFlowKmolPerHour;
  private final double externalMassBalanceResidualKgPerHour;

  private RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance(
      RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycleBalance,
      double feedMassFlowKgPerHour, double basisScalePerHour, double productMassFlowKgPerHour,
      double hydrogenConsumedMassFlowKgPerHour, double freshMakeupGasMassFlowKgPerHour,
      double recycleGasMassFlowKgPerHour, double exportGasMassFlowKgPerHour,
      double freshHydrogenMolarFlowKmolPerHour, double freshNonHydrogenMolarFlowKmolPerHour,
      double recycleHydrogenMolarFlowKmolPerHour,
      double recycleHydrogenSulfideMolarFlowKmolPerHour,
      double recycleAmmoniaMolarFlowKmolPerHour,
      double recycleNonHydrogenMolarFlowKmolPerHour,
      double exportHydrogenMolarFlowKmolPerHour,
      double exportHydrogenSulfideMolarFlowKmolPerHour,
      double exportAmmoniaMolarFlowKmolPerHour,
      double exportNonHydrogenMolarFlowKmolPerHour,
      double externalMassBalanceResidualKgPerHour) {
    this.recycleBalance = recycleBalance;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.basisScalePerHour = basisScalePerHour;
    this.productMassFlowKgPerHour = productMassFlowKgPerHour;
    this.hydrogenConsumedMassFlowKgPerHour = hydrogenConsumedMassFlowKgPerHour;
    this.freshMakeupGasMassFlowKgPerHour = freshMakeupGasMassFlowKgPerHour;
    this.recycleGasMassFlowKgPerHour = recycleGasMassFlowKgPerHour;
    this.exportGasMassFlowKgPerHour = exportGasMassFlowKgPerHour;
    this.freshHydrogenMolarFlowKmolPerHour = freshHydrogenMolarFlowKmolPerHour;
    this.freshNonHydrogenMolarFlowKmolPerHour = freshNonHydrogenMolarFlowKmolPerHour;
    this.recycleHydrogenMolarFlowKmolPerHour = recycleHydrogenMolarFlowKmolPerHour;
    this.recycleHydrogenSulfideMolarFlowKmolPerHour = recycleHydrogenSulfideMolarFlowKmolPerHour;
    this.recycleAmmoniaMolarFlowKmolPerHour = recycleAmmoniaMolarFlowKmolPerHour;
    this.recycleNonHydrogenMolarFlowKmolPerHour = recycleNonHydrogenMolarFlowKmolPerHour;
    this.exportHydrogenMolarFlowKmolPerHour = exportHydrogenMolarFlowKmolPerHour;
    this.exportHydrogenSulfideMolarFlowKmolPerHour = exportHydrogenSulfideMolarFlowKmolPerHour;
    this.exportAmmoniaMolarFlowKmolPerHour = exportAmmoniaMolarFlowKmolPerHour;
    this.exportNonHydrogenMolarFlowKmolPerHour = exportNonHydrogenMolarFlowKmolPerHour;
    this.externalMassBalanceResidualKgPerHour = externalMassBalanceResidualKgPerHour;
  }

  /**
   * Scale a qualified coupled recycle receipt to explicit process rates.
   *
   * @param recycleBalance qualified coupled recycle and purge receipt
   * @param feedMassFlowKgPerHour feed mass flow in kg/h
   * @return immutable throughput receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance calculate(
      RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycleBalance,
      double feedMassFlowKgPerHour) {
    Objects.requireNonNull(recycleBalance, "recycleBalance");
    if (!Double.isFinite(feedMassFlowKgPerHour) || feedMassFlowKgPerHour <= 0.0) {
      throw new IllegalArgumentException("feedMassFlowKgPerHour must be finite and positive");
    }

    RefineryHydrotreatingSulfurNitrogenBalance material =
        recycleBalance.getSupplyBalance().getMaterialBalance();
    double basisScalePerHour = feedMassFlowKgPerHour / material.getFeedMassKg();
    double molarScale = basisScalePerHour / 1000.0;
    double productMassFlowKgPerHour = material.getProductMassKg() * basisScalePerHour;
    double hydrogenConsumedMassFlowKgPerHour =
        material.getTotalHydrogenConsumedMassKg() * basisScalePerHour;
    double freshMakeupGasMassFlowKgPerHour =
        recycleBalance.getFreshMakeupGasMassKg() * basisScalePerHour;
    double recycleGasMassFlowKgPerHour =
        recycleBalance.getRecycleGasMassKg() * basisScalePerHour;
    double exportGasMassFlowKgPerHour =
        recycleBalance.getExportGasMassKg() * basisScalePerHour;
    double externalMassBalanceResidualKgPerHour = feedMassFlowKgPerHour
        + freshMakeupGasMassFlowKgPerHour - productMassFlowKgPerHour
        - exportGasMassFlowKgPerHour;

    double tolerance = 1.0e-12
        * Math.max(1.0, feedMassFlowKgPerHour + freshMakeupGasMassFlowKgPerHour);
    if (!Double.isFinite(basisScalePerHour) || basisScalePerHour <= 0.0
        || !Double.isFinite(externalMassBalanceResidualKgPerHour)
        || Math.abs(externalMassBalanceResidualKgPerHour) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed coupled throughput balance");
    }

    return new RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance(
        recycleBalance, feedMassFlowKgPerHour, basisScalePerHour, productMassFlowKgPerHour,
        hydrogenConsumedMassFlowKgPerHour, freshMakeupGasMassFlowKgPerHour,
        recycleGasMassFlowKgPerHour, exportGasMassFlowKgPerHour,
        recycleBalance.getFreshHydrogenMoles() * molarScale,
        recycleBalance.getFreshNonHydrogenMoles() * molarScale,
        recycleBalance.getRecycleHydrogenMoles() * molarScale,
        recycleBalance.getRecycleHydrogenSulfideMoles() * molarScale,
        recycleBalance.getRecycleAmmoniaMoles() * molarScale,
        recycleBalance.getRecycleNonHydrogenMoles() * molarScale,
        recycleBalance.getExportHydrogenMoles() * molarScale,
        recycleBalance.getExportHydrogenSulfideMoles() * molarScale,
        recycleBalance.getExportAmmoniaMoles() * molarScale,
        recycleBalance.getExportNonHydrogenMoles() * molarScale,
        externalMassBalanceResidualKgPerHour);
  }

  /** @return upstream coupled recycle receipt */
  public RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance getRecycleBalance() {
    return recycleBalance;
  }

  /** @return feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return basis scale in 1/h */
  public double getBasisScalePerHour() {
    return basisScalePerHour;
  }

  /** @return liquid-product mass flow in kg/h */
  public double getProductMassFlowKgPerHour() {
    return productMassFlowKgPerHour;
  }

  /** @return total consumed-H2 mass flow in kg/h */
  public double getHydrogenConsumedMassFlowKgPerHour() {
    return hydrogenConsumedMassFlowKgPerHour;
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

  /** @return recycle NH3 molar flow in kmol/h */
  public double getRecycleAmmoniaMolarFlowKmolPerHour() {
    return recycleAmmoniaMolarFlowKmolPerHour;
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

  /** @return export NH3 molar flow in kmol/h */
  public double getExportAmmoniaMolarFlowKmolPerHour() {
    return exportAmmoniaMolarFlowKmolPerHour;
  }

  /** @return export non-H2 molar flow in kmol/h */
  public double getExportNonHydrogenMolarFlowKmolPerHour() {
    return exportNonHydrogenMolarFlowKmolPerHour;
  }

  /** @return external mass-balance residual in kg/h */
  public double getExternalMassBalanceResidualKgPerHour() {
    return externalMassBalanceResidualKgPerHour;
  }
}
