package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable external liquid-product and export-gas distribution receipt for a coupled sulfur/nitrogen hydrotreating
 * screen.
 *
 * <p>
 * The receipt derives mass rates, mass fractions, and feed-normalized yields from a qualified
 * {@link RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance}. Internal recycle is excluded from every
 * external-product result.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double MOLES_PER_KMOL = 1000.0;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;

  private final RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughputBalance;
  private final double totalExternalProductMassFlowKgPerHour;
  private final double liquidProductMassFraction;
  private final double exportGasMassFraction;
  private final double exportHydrogenMassFlowKgPerHour;
  private final double exportHydrogenSulfideMassFlowKgPerHour;
  private final double exportAmmoniaMassFlowKgPerHour;
  private final double exportNonHydrogenMassFlowKgPerHour;
  private final double exportHydrogenMassFraction;
  private final double exportHydrogenSulfideMassFraction;
  private final double exportAmmoniaMassFraction;
  private final double exportNonHydrogenMassFraction;
  private final double liquidProductKgPerTonneFeed;
  private final double exportGasKgPerTonneFeed;
  private final double exportHydrogenSulfideKgPerTonneFeed;
  private final double exportAmmoniaKgPerTonneFeed;
  private final double externalProductClosureResidualKgPerHour;
  private final double exportGasComponentClosureResidualKgPerHour;

  private RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt(
      RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughputBalance,
      double totalExternalProductMassFlowKgPerHour, double liquidProductMassFraction, double exportGasMassFraction,
      double exportHydrogenMassFlowKgPerHour, double exportHydrogenSulfideMassFlowKgPerHour,
      double exportAmmoniaMassFlowKgPerHour, double exportNonHydrogenMassFlowKgPerHour,
      double exportHydrogenMassFraction, double exportHydrogenSulfideMassFraction, double exportAmmoniaMassFraction,
      double exportNonHydrogenMassFraction, double liquidProductKgPerTonneFeed, double exportGasKgPerTonneFeed,
      double exportHydrogenSulfideKgPerTonneFeed, double exportAmmoniaKgPerTonneFeed,
      double externalProductClosureResidualKgPerHour, double exportGasComponentClosureResidualKgPerHour) {
    this.throughputBalance = throughputBalance;
    this.totalExternalProductMassFlowKgPerHour = totalExternalProductMassFlowKgPerHour;
    this.liquidProductMassFraction = liquidProductMassFraction;
    this.exportGasMassFraction = exportGasMassFraction;
    this.exportHydrogenMassFlowKgPerHour = exportHydrogenMassFlowKgPerHour;
    this.exportHydrogenSulfideMassFlowKgPerHour = exportHydrogenSulfideMassFlowKgPerHour;
    this.exportAmmoniaMassFlowKgPerHour = exportAmmoniaMassFlowKgPerHour;
    this.exportNonHydrogenMassFlowKgPerHour = exportNonHydrogenMassFlowKgPerHour;
    this.exportHydrogenMassFraction = exportHydrogenMassFraction;
    this.exportHydrogenSulfideMassFraction = exportHydrogenSulfideMassFraction;
    this.exportAmmoniaMassFraction = exportAmmoniaMassFraction;
    this.exportNonHydrogenMassFraction = exportNonHydrogenMassFraction;
    this.liquidProductKgPerTonneFeed = liquidProductKgPerTonneFeed;
    this.exportGasKgPerTonneFeed = exportGasKgPerTonneFeed;
    this.exportHydrogenSulfideKgPerTonneFeed = exportHydrogenSulfideKgPerTonneFeed;
    this.exportAmmoniaKgPerTonneFeed = exportAmmoniaKgPerTonneFeed;
    this.externalProductClosureResidualKgPerHour = externalProductClosureResidualKgPerHour;
    this.exportGasComponentClosureResidualKgPerHour = exportGasComponentClosureResidualKgPerHour;
  }

  /**
   * Derive an external product-distribution receipt from a qualified coupled throughput balance.
   *
   * @param throughputBalance qualified coupled recycle-throughput receipt
   * @return immutable external product-distribution receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt calculate(
      RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance throughputBalance) {
    Objects.requireNonNull(throughputBalance, "throughputBalance");

    double feed = throughputBalance.getFeedMassFlowKgPerHour();
    double liquid = throughputBalance.getProductMassFlowKgPerHour();
    double exportGas = throughputBalance.getExportGasMassFlowKgPerHour();
    double totalExternalProduct = liquid + exportGas;
    double exportHydrogen = throughputBalance.getExportHydrogenMolarFlowKmolPerHour() * MOLES_PER_KMOL
        * RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double exportHydrogenSulfide = throughputBalance.getExportHydrogenSulfideMolarFlowKmolPerHour() * MOLES_PER_KMOL
        * RefineryHydrotreatingSulfurBalance.HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL;
    double exportAmmonia = throughputBalance.getExportAmmoniaMolarFlowKmolPerHour() * MOLES_PER_KMOL
        * RefineryHydrotreatingNitrogenBalance.AMMONIA_MOLAR_MASS_KG_PER_MOL;
    double nonHydrogenMolarMass = throughputBalance.getRecycleBalance().getSupplyBalance()
        .getNonHydrogenMolarMassKgPerMol();
    double exportNonHydrogen = throughputBalance.getExportNonHydrogenMolarFlowKmolPerHour() * MOLES_PER_KMOL
        * nonHydrogenMolarMass;

    double componentSum = exportHydrogen + exportHydrogenSulfide + exportAmmonia + exportNonHydrogen;
    double productClosure = totalExternalProduct - liquid - exportGas;
    double gasClosure = exportGas - componentSum;
    double tolerance = 1.0e-12 * Math.max(1.0, totalExternalProduct);
    if (!allFiniteNonNegative(feed, liquid, exportGas, totalExternalProduct, exportHydrogen, exportHydrogenSulfide,
        exportAmmonia, exportNonHydrogen) || Math.abs(productClosure) > tolerance || Math.abs(gasClosure) > tolerance) {
      throw new IllegalArgumentException("throughput does not define a closed external product distribution");
    }

    double liquidFraction = liquid / totalExternalProduct;
    double gasFraction = exportGas / totalExternalProduct;
    double exportHydrogenFraction = exportGas == 0.0 ? 0.0 : exportHydrogen / exportGas;
    double exportHydrogenSulfideFraction = exportGas == 0.0 ? 0.0 : exportHydrogenSulfide / exportGas;
    double exportAmmoniaFraction = exportGas == 0.0 ? 0.0 : exportAmmonia / exportGas;
    double exportNonHydrogenFraction = exportGas == 0.0 ? 0.0 : exportNonHydrogen / exportGas;
    double tonnesPerHour = feed / KILOGRAMS_PER_TONNE;

    return new RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt(throughputBalance, totalExternalProduct,
        liquidFraction, gasFraction, exportHydrogen, exportHydrogenSulfide, exportAmmonia, exportNonHydrogen,
        exportHydrogenFraction, exportHydrogenSulfideFraction, exportAmmoniaFraction, exportNonHydrogenFraction,
        liquid / tonnesPerHour, exportGas / tonnesPerHour, exportHydrogenSulfide / tonnesPerHour,
        exportAmmonia / tonnesPerHour, productClosure, gasClosure);
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream qualified coupled recycle-throughput receipt */
  public RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance getThroughputBalance() {
    return throughputBalance;
  }

  /** @return total external liquid-product plus export-gas mass flow in kg/h */
  public double getTotalExternalProductMassFlowKgPerHour() {
    return totalExternalProductMassFlowKgPerHour;
  }

  /** @return liquid-product mass fraction of total external products */
  public double getLiquidProductMassFraction() {
    return liquidProductMassFraction;
  }

  /** @return export-gas mass fraction of total external products */
  public double getExportGasMassFraction() {
    return exportGasMassFraction;
  }

  /** @return exported hydrogen mass flow in kg/h */
  public double getExportHydrogenMassFlowKgPerHour() {
    return exportHydrogenMassFlowKgPerHour;
  }

  /** @return exported hydrogen-sulfide mass flow in kg/h */
  public double getExportHydrogenSulfideMassFlowKgPerHour() {
    return exportHydrogenSulfideMassFlowKgPerHour;
  }

  /** @return exported ammonia mass flow in kg/h */
  public double getExportAmmoniaMassFlowKgPerHour() {
    return exportAmmoniaMassFlowKgPerHour;
  }

  /** @return exported non-hydrogen makeup-gas mass flow in kg/h */
  public double getExportNonHydrogenMassFlowKgPerHour() {
    return exportNonHydrogenMassFlowKgPerHour;
  }

  /** @return hydrogen mass fraction of export gas */
  public double getExportHydrogenMassFraction() {
    return exportHydrogenMassFraction;
  }

  /** @return hydrogen-sulfide mass fraction of export gas */
  public double getExportHydrogenSulfideMassFraction() {
    return exportHydrogenSulfideMassFraction;
  }

  /** @return ammonia mass fraction of export gas */
  public double getExportAmmoniaMassFraction() {
    return exportAmmoniaMassFraction;
  }

  /** @return non-hydrogen makeup-gas mass fraction of export gas */
  public double getExportNonHydrogenMassFraction() {
    return exportNonHydrogenMassFraction;
  }

  /** @return liquid product in kg per tonne liquid feed */
  public double getLiquidProductKgPerTonneFeed() {
    return liquidProductKgPerTonneFeed;
  }

  /** @return export gas in kg per tonne liquid feed */
  public double getExportGasKgPerTonneFeed() {
    return exportGasKgPerTonneFeed;
  }

  /** @return exported hydrogen sulfide in kg per tonne liquid feed */
  public double getExportHydrogenSulfideKgPerTonneFeed() {
    return exportHydrogenSulfideKgPerTonneFeed;
  }

  /** @return exported ammonia in kg per tonne liquid feed */
  public double getExportAmmoniaKgPerTonneFeed() {
    return exportAmmoniaKgPerTonneFeed;
  }

  /** @return external liquid-plus-gas closure residual in kg/h */
  public double getExternalProductClosureResidualKgPerHour() {
    return externalProductClosureResidualKgPerHour;
  }

  /** @return export-gas component closure residual in kg/h */
  public double getExportGasComponentClosureResidualKgPerHour() {
    return exportGasComponentClosureResidualKgPerHour;
  }
}
