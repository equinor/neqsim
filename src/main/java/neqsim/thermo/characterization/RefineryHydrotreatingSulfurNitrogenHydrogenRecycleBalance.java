package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable hydrogen recycle and purge receipt for a coupled sulfur/nitrogen hydrotreating screen.
 *
 * <p>
 * The calculation composes a {@link RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance} with explicit component
 * recovery and common purge assumptions. It is component bookkeeping, not a separator, phase-equilibrium, compressor,
 * reactor, or catalyst model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supplyBalance;
  private final double hydrogenRecoveryFraction;
  private final double hydrogenSulfideRecoveryFraction;
  private final double ammoniaRecoveryFraction;
  private final double nonHydrogenRecoveryFraction;
  private final double purgeFraction;
  private final double effectiveHydrogenRecycleFraction;
  private final double effectiveHydrogenSulfideRecycleFraction;
  private final double effectiveAmmoniaRecycleFraction;
  private final double effectiveNonHydrogenRecycleFraction;
  private final double freshHydrogenMoles;
  private final double freshNonHydrogenMoles;
  private final double freshMakeupGasMoles;
  private final double freshMakeupGasMassKg;
  private final double reactorOutletHydrogenMoles;
  private final double reactorOutletHydrogenSulfideMoles;
  private final double reactorOutletAmmoniaMoles;
  private final double reactorOutletNonHydrogenMoles;
  private final double reactorOutletGasMoles;
  private final double reactorOutletGasMassKg;
  private final double recycleHydrogenMoles;
  private final double recycleHydrogenSulfideMoles;
  private final double recycleAmmoniaMoles;
  private final double recycleNonHydrogenMoles;
  private final double recycleGasMoles;
  private final double recycleGasMassKg;
  private final double exportHydrogenMoles;
  private final double exportHydrogenSulfideMoles;
  private final double exportAmmoniaMoles;
  private final double exportNonHydrogenMoles;
  private final double exportGasMoles;
  private final double exportGasMassKg;
  private final double exportHydrogenMoleFraction;
  private final double exportHydrogenSulfideMoleFraction;
  private final double exportAmmoniaMoleFraction;
  private final double exportNonHydrogenMoleFraction;
  private final double freshHydrogenReductionFraction;
  private final double hydrogenBalanceResidualMoles;
  private final double hydrogenSulfideBalanceResidualMoles;
  private final double ammoniaBalanceResidualMoles;
  private final double nonHydrogenBalanceResidualMoles;
  private final double overallMassBalanceResidualKg;

  private RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance(
      RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supplyBalance, double hydrogenRecoveryFraction,
      double hydrogenSulfideRecoveryFraction, double ammoniaRecoveryFraction, double nonHydrogenRecoveryFraction,
      double purgeFraction, double effectiveHydrogenRecycleFraction,
      double effectiveHydrogenSulfideRecycleFraction, double effectiveAmmoniaRecycleFraction,
      double effectiveNonHydrogenRecycleFraction, double freshHydrogenMoles, double freshNonHydrogenMoles,
      double freshMakeupGasMoles, double freshMakeupGasMassKg, double reactorOutletHydrogenMoles,
      double reactorOutletHydrogenSulfideMoles, double reactorOutletAmmoniaMoles,
      double reactorOutletNonHydrogenMoles, double reactorOutletGasMoles, double reactorOutletGasMassKg,
      double recycleHydrogenMoles, double recycleHydrogenSulfideMoles, double recycleAmmoniaMoles,
      double recycleNonHydrogenMoles, double recycleGasMoles, double recycleGasMassKg, double exportHydrogenMoles,
      double exportHydrogenSulfideMoles, double exportAmmoniaMoles, double exportNonHydrogenMoles,
      double exportGasMoles, double exportGasMassKg, double exportHydrogenMoleFraction,
      double exportHydrogenSulfideMoleFraction, double exportAmmoniaMoleFraction,
      double exportNonHydrogenMoleFraction, double freshHydrogenReductionFraction,
      double hydrogenBalanceResidualMoles, double hydrogenSulfideBalanceResidualMoles,
      double ammoniaBalanceResidualMoles, double nonHydrogenBalanceResidualMoles,
      double overallMassBalanceResidualKg) {
    this.supplyBalance = supplyBalance;
    this.hydrogenRecoveryFraction = hydrogenRecoveryFraction;
    this.hydrogenSulfideRecoveryFraction = hydrogenSulfideRecoveryFraction;
    this.ammoniaRecoveryFraction = ammoniaRecoveryFraction;
    this.nonHydrogenRecoveryFraction = nonHydrogenRecoveryFraction;
    this.purgeFraction = purgeFraction;
    this.effectiveHydrogenRecycleFraction = effectiveHydrogenRecycleFraction;
    this.effectiveHydrogenSulfideRecycleFraction = effectiveHydrogenSulfideRecycleFraction;
    this.effectiveAmmoniaRecycleFraction = effectiveAmmoniaRecycleFraction;
    this.effectiveNonHydrogenRecycleFraction = effectiveNonHydrogenRecycleFraction;
    this.freshHydrogenMoles = freshHydrogenMoles;
    this.freshNonHydrogenMoles = freshNonHydrogenMoles;
    this.freshMakeupGasMoles = freshMakeupGasMoles;
    this.freshMakeupGasMassKg = freshMakeupGasMassKg;
    this.reactorOutletHydrogenMoles = reactorOutletHydrogenMoles;
    this.reactorOutletHydrogenSulfideMoles = reactorOutletHydrogenSulfideMoles;
    this.reactorOutletAmmoniaMoles = reactorOutletAmmoniaMoles;
    this.reactorOutletNonHydrogenMoles = reactorOutletNonHydrogenMoles;
    this.reactorOutletGasMoles = reactorOutletGasMoles;
    this.reactorOutletGasMassKg = reactorOutletGasMassKg;
    this.recycleHydrogenMoles = recycleHydrogenMoles;
    this.recycleHydrogenSulfideMoles = recycleHydrogenSulfideMoles;
    this.recycleAmmoniaMoles = recycleAmmoniaMoles;
    this.recycleNonHydrogenMoles = recycleNonHydrogenMoles;
    this.recycleGasMoles = recycleGasMoles;
    this.recycleGasMassKg = recycleGasMassKg;
    this.exportHydrogenMoles = exportHydrogenMoles;
    this.exportHydrogenSulfideMoles = exportHydrogenSulfideMoles;
    this.exportAmmoniaMoles = exportAmmoniaMoles;
    this.exportNonHydrogenMoles = exportNonHydrogenMoles;
    this.exportGasMoles = exportGasMoles;
    this.exportGasMassKg = exportGasMassKg;
    this.exportHydrogenMoleFraction = exportHydrogenMoleFraction;
    this.exportHydrogenSulfideMoleFraction = exportHydrogenSulfideMoleFraction;
    this.exportAmmoniaMoleFraction = exportAmmoniaMoleFraction;
    this.exportNonHydrogenMoleFraction = exportNonHydrogenMoleFraction;
    this.freshHydrogenReductionFraction = freshHydrogenReductionFraction;
    this.hydrogenBalanceResidualMoles = hydrogenBalanceResidualMoles;
    this.hydrogenSulfideBalanceResidualMoles = hydrogenSulfideBalanceResidualMoles;
    this.ammoniaBalanceResidualMoles = ammoniaBalanceResidualMoles;
    this.nonHydrogenBalanceResidualMoles = nonHydrogenBalanceResidualMoles;
    this.overallMassBalanceResidualKg = overallMassBalanceResidualKg;
  }

  /**
   * Calculate a steady-state coupled component recycle and purge balance.
   *
   * @param supplyBalance qualified once-through coupled gas-supply receipt
   * @param hydrogenRecoveryFraction fraction of reactor-outlet H2 recovered before purge
   * @param hydrogenSulfideRecoveryFraction fraction of reactor-outlet H2S recovered before purge
   * @param ammoniaRecoveryFraction fraction of reactor-outlet NH3 recovered before purge
   * @param nonHydrogenRecoveryFraction fraction of reactor-outlet non-H2 recovered before purge
   * @param purgeFraction fraction of each recovered component purged instead of recycled
   * @return immutable coupled recycle and purge receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance calculate(
      RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supplyBalance, double hydrogenRecoveryFraction,
      double hydrogenSulfideRecoveryFraction, double ammoniaRecoveryFraction, double nonHydrogenRecoveryFraction,
      double purgeFraction) {
    Objects.requireNonNull(supplyBalance, "supplyBalance");
    requireFraction("hydrogenRecoveryFraction", hydrogenRecoveryFraction);
    requireFraction("hydrogenSulfideRecoveryFraction", hydrogenSulfideRecoveryFraction);
    requireFraction("ammoniaRecoveryFraction", ammoniaRecoveryFraction);
    requireFraction("nonHydrogenRecoveryFraction", nonHydrogenRecoveryFraction);
    requireFraction("purgeFraction", purgeFraction);

    double recycleMultiplier = 1.0 - purgeFraction;
    double effectiveHydrogenRecycleFraction = hydrogenRecoveryFraction * recycleMultiplier;
    double effectiveHydrogenSulfideRecycleFraction = hydrogenSulfideRecoveryFraction * recycleMultiplier;
    double effectiveAmmoniaRecycleFraction = ammoniaRecoveryFraction * recycleMultiplier;
    double effectiveNonHydrogenRecycleFraction = nonHydrogenRecoveryFraction * recycleMultiplier;

    double reactorOutletHydrogenMoles = supplyBalance.getUnreactedHydrogenMoles();
    double recycleHydrogenMoles = effectiveHydrogenRecycleFraction * reactorOutletHydrogenMoles;
    double freshHydrogenMoles = supplyBalance.getHydrogenSuppliedMoles() - recycleHydrogenMoles;
    double freshMakeupGasMoles = freshHydrogenMoles / supplyBalance.getMakeupHydrogenMoleFraction();
    double freshNonHydrogenMoles = freshMakeupGasMoles - freshHydrogenMoles;

    double generatedHydrogenSulfideMoles = supplyBalance.getHydrogenSulfideMoles();
    double generatedAmmoniaMoles = supplyBalance.getAmmoniaMoles();
    double reactorOutletHydrogenSulfideMoles = steadyStateOutlet("hydrogen sulfide",
        generatedHydrogenSulfideMoles, effectiveHydrogenSulfideRecycleFraction);
    double reactorOutletAmmoniaMoles = steadyStateOutlet("ammonia", generatedAmmoniaMoles,
        effectiveAmmoniaRecycleFraction);
    double reactorOutletNonHydrogenMoles = steadyStateOutlet("non-hydrogen makeup", freshNonHydrogenMoles,
        effectiveNonHydrogenRecycleFraction);

    double recycleHydrogenSulfideMoles = effectiveHydrogenSulfideRecycleFraction * reactorOutletHydrogenSulfideMoles;
    double recycleAmmoniaMoles = effectiveAmmoniaRecycleFraction * reactorOutletAmmoniaMoles;
    double recycleNonHydrogenMoles = effectiveNonHydrogenRecycleFraction * reactorOutletNonHydrogenMoles;
    double exportHydrogenMoles = reactorOutletHydrogenMoles - recycleHydrogenMoles;
    double exportHydrogenSulfideMoles = reactorOutletHydrogenSulfideMoles - recycleHydrogenSulfideMoles;
    double exportAmmoniaMoles = reactorOutletAmmoniaMoles - recycleAmmoniaMoles;
    double exportNonHydrogenMoles = reactorOutletNonHydrogenMoles - recycleNonHydrogenMoles;

    double reactorOutletGasMoles = reactorOutletHydrogenMoles + reactorOutletHydrogenSulfideMoles
        + reactorOutletAmmoniaMoles + reactorOutletNonHydrogenMoles;
    double recycleGasMoles = recycleHydrogenMoles + recycleHydrogenSulfideMoles + recycleAmmoniaMoles
        + recycleNonHydrogenMoles;
    double exportGasMoles = exportHydrogenMoles + exportHydrogenSulfideMoles + exportAmmoniaMoles
        + exportNonHydrogenMoles;

    double hydrogenMolarMass = RefineryHydrotreatingSulfurBalance.HYDROGEN_MOLAR_MASS_KG_PER_MOL;
    double hydrogenSulfideMolarMass = RefineryHydrotreatingSulfurBalance.HYDROGEN_SULFIDE_MOLAR_MASS_KG_PER_MOL;
    double ammoniaMolarMass = RefineryHydrotreatingNitrogenBalance.AMMONIA_MOLAR_MASS_KG_PER_MOL;
    double nonHydrogenMolarMass = supplyBalance.getNonHydrogenMolarMassKgPerMol();
    double freshMakeupGasMassKg = freshHydrogenMoles * hydrogenMolarMass
        + freshNonHydrogenMoles * nonHydrogenMolarMass;
    double reactorOutletGasMassKg = reactorOutletHydrogenMoles * hydrogenMolarMass
        + reactorOutletHydrogenSulfideMoles * hydrogenSulfideMolarMass
        + reactorOutletAmmoniaMoles * ammoniaMolarMass
        + reactorOutletNonHydrogenMoles * nonHydrogenMolarMass;
    double recycleGasMassKg = recycleHydrogenMoles * hydrogenMolarMass
        + recycleHydrogenSulfideMoles * hydrogenSulfideMolarMass + recycleAmmoniaMoles * ammoniaMolarMass
        + recycleNonHydrogenMoles * nonHydrogenMolarMass;
    double exportGasMassKg = exportHydrogenMoles * hydrogenMolarMass
        + exportHydrogenSulfideMoles * hydrogenSulfideMolarMass + exportAmmoniaMoles * ammoniaMolarMass
        + exportNonHydrogenMoles * nonHydrogenMolarMass;

    double exportHydrogenMoleFraction = 0.0;
    double exportHydrogenSulfideMoleFraction = 0.0;
    double exportAmmoniaMoleFraction = 0.0;
    double exportNonHydrogenMoleFraction = 0.0;
    if (exportGasMoles > 0.0) {
      exportHydrogenMoleFraction = exportHydrogenMoles / exportGasMoles;
      exportHydrogenSulfideMoleFraction = exportHydrogenSulfideMoles / exportGasMoles;
      exportAmmoniaMoleFraction = exportAmmoniaMoles / exportGasMoles;
      exportNonHydrogenMoleFraction = exportNonHydrogenMoles / exportGasMoles;
    }

    double freshHydrogenReductionFraction = 0.0;
    if (supplyBalance.getHydrogenSuppliedMoles() > 0.0) {
      freshHydrogenReductionFraction = recycleHydrogenMoles / supplyBalance.getHydrogenSuppliedMoles();
    }
    double hydrogenBalanceResidualMoles = freshHydrogenMoles + recycleHydrogenMoles
        - supplyBalance.getHydrogenSuppliedMoles();
    double hydrogenSulfideBalanceResidualMoles = generatedHydrogenSulfideMoles + recycleHydrogenSulfideMoles
        - reactorOutletHydrogenSulfideMoles;
    double ammoniaBalanceResidualMoles = generatedAmmoniaMoles + recycleAmmoniaMoles
        - reactorOutletAmmoniaMoles;
    double nonHydrogenBalanceResidualMoles = freshNonHydrogenMoles + recycleNonHydrogenMoles
        - reactorOutletNonHydrogenMoles;
    RefineryHydrotreatingSulfurNitrogenBalance materialBalance = supplyBalance.getMaterialBalance();
    double overallMassBalanceResidualKg = materialBalance.getFeedMassKg() + freshMakeupGasMassKg
        - materialBalance.getProductMassKg() - exportGasMassKg;

    double moleTolerance = 1.0e-12 * Math.max(1.0, supplyBalance.getHydrogenSuppliedMoles());
    double massToleranceKg = 1.0e-12 * Math.max(1.0, materialBalance.getFeedMassKg() + freshMakeupGasMassKg);
    if (!allFiniteNonNegative(freshHydrogenMoles, freshNonHydrogenMoles, freshMakeupGasMoles,
        freshMakeupGasMassKg, reactorOutletHydrogenMoles, reactorOutletHydrogenSulfideMoles,
        reactorOutletAmmoniaMoles, reactorOutletNonHydrogenMoles, reactorOutletGasMoles, reactorOutletGasMassKg,
        recycleHydrogenMoles, recycleHydrogenSulfideMoles, recycleAmmoniaMoles, recycleNonHydrogenMoles,
        recycleGasMoles, recycleGasMassKg, exportHydrogenMoles, exportHydrogenSulfideMoles, exportAmmoniaMoles,
        exportNonHydrogenMoles, exportGasMoles, exportGasMassKg)
        || Math.abs(hydrogenBalanceResidualMoles) > moleTolerance
        || Math.abs(hydrogenSulfideBalanceResidualMoles) > moleTolerance
        || Math.abs(ammoniaBalanceResidualMoles) > moleTolerance
        || Math.abs(nonHydrogenBalanceResidualMoles) > moleTolerance
        || Math.abs(overallMassBalanceResidualKg) > massToleranceKg) {
      throw new IllegalArgumentException("inputs do not define a closed coupled recycle balance");
    }

    return new RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance(supplyBalance, hydrogenRecoveryFraction,
        hydrogenSulfideRecoveryFraction, ammoniaRecoveryFraction, nonHydrogenRecoveryFraction, purgeFraction,
        effectiveHydrogenRecycleFraction, effectiveHydrogenSulfideRecycleFraction, effectiveAmmoniaRecycleFraction,
        effectiveNonHydrogenRecycleFraction, freshHydrogenMoles, freshNonHydrogenMoles, freshMakeupGasMoles,
        freshMakeupGasMassKg, reactorOutletHydrogenMoles, reactorOutletHydrogenSulfideMoles,
        reactorOutletAmmoniaMoles, reactorOutletNonHydrogenMoles, reactorOutletGasMoles, reactorOutletGasMassKg,
        recycleHydrogenMoles, recycleHydrogenSulfideMoles, recycleAmmoniaMoles, recycleNonHydrogenMoles,
        recycleGasMoles, recycleGasMassKg, exportHydrogenMoles, exportHydrogenSulfideMoles, exportAmmoniaMoles,
        exportNonHydrogenMoles, exportGasMoles, exportGasMassKg, exportHydrogenMoleFraction,
        exportHydrogenSulfideMoleFraction, exportAmmoniaMoleFraction, exportNonHydrogenMoleFraction,
        freshHydrogenReductionFraction, hydrogenBalanceResidualMoles, hydrogenSulfideBalanceResidualMoles,
        ammoniaBalanceResidualMoles, nonHydrogenBalanceResidualMoles, overallMassBalanceResidualKg);
  }

  private static double steadyStateOutlet(String name, double sourceMoles, double effectiveRecycleFraction) {
    if (sourceMoles == 0.0) {
      return 0.0;
    }
    double denominator = 1.0 - effectiveRecycleFraction;
    if (denominator <= 0.0) {
      throw new IllegalArgumentException(name + " accumulates without a purge or rejection path");
    }
    return sourceMoles / denominator;
  }

  private static void requireFraction(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
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

  /** @return upstream once-through coupled gas-supply receipt */
  public RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance getSupplyBalance() {
    return supplyBalance;
  }

  /** @return H2 recovery fraction before purge */
  public double getHydrogenRecoveryFraction() {
    return hydrogenRecoveryFraction;
  }

  /** @return H2S recovery fraction before purge */
  public double getHydrogenSulfideRecoveryFraction() {
    return hydrogenSulfideRecoveryFraction;
  }

  /** @return NH3 recovery fraction before purge */
  public double getAmmoniaRecoveryFraction() {
    return ammoniaRecoveryFraction;
  }

  /** @return non-H2 recovery fraction before purge */
  public double getNonHydrogenRecoveryFraction() {
    return nonHydrogenRecoveryFraction;
  }

  /** @return common recovered-gas purge fraction */
  public double getPurgeFraction() {
    return purgeFraction;
  }

  /** @return effective H2 recycle fraction after purge */
  public double getEffectiveHydrogenRecycleFraction() {
    return effectiveHydrogenRecycleFraction;
  }

  /** @return effective H2S recycle fraction after purge */
  public double getEffectiveHydrogenSulfideRecycleFraction() {
    return effectiveHydrogenSulfideRecycleFraction;
  }

  /** @return effective NH3 recycle fraction after purge */
  public double getEffectiveAmmoniaRecycleFraction() {
    return effectiveAmmoniaRecycleFraction;
  }

  /** @return effective non-H2 recycle fraction after purge */
  public double getEffectiveNonHydrogenRecycleFraction() {
    return effectiveNonHydrogenRecycleFraction;
  }

  /** @return fresh H2 requirement in moles */
  public double getFreshHydrogenMoles() {
    return freshHydrogenMoles;
  }

  /** @return fresh non-H2 makeup in moles */
  public double getFreshNonHydrogenMoles() {
    return freshNonHydrogenMoles;
  }

  /** @return total fresh makeup gas in moles */
  public double getFreshMakeupGasMoles() {
    return freshMakeupGasMoles;
  }

  /** @return total fresh makeup gas mass in kilograms */
  public double getFreshMakeupGasMassKg() {
    return freshMakeupGasMassKg;
  }

  /** @return reactor-outlet H2 in moles */
  public double getReactorOutletHydrogenMoles() {
    return reactorOutletHydrogenMoles;
  }

  /** @return reactor-outlet H2S in moles */
  public double getReactorOutletHydrogenSulfideMoles() {
    return reactorOutletHydrogenSulfideMoles;
  }

  /** @return reactor-outlet NH3 in moles */
  public double getReactorOutletAmmoniaMoles() {
    return reactorOutletAmmoniaMoles;
  }

  /** @return reactor-outlet non-H2 in moles */
  public double getReactorOutletNonHydrogenMoles() {
    return reactorOutletNonHydrogenMoles;
  }

  /** @return total reactor-outlet gas in moles */
  public double getReactorOutletGasMoles() {
    return reactorOutletGasMoles;
  }

  /** @return total reactor-outlet gas mass in kilograms */
  public double getReactorOutletGasMassKg() {
    return reactorOutletGasMassKg;
  }

  /** @return recycled H2 in moles */
  public double getRecycleHydrogenMoles() {
    return recycleHydrogenMoles;
  }

  /** @return recycled H2S in moles */
  public double getRecycleHydrogenSulfideMoles() {
    return recycleHydrogenSulfideMoles;
  }

  /** @return recycled NH3 in moles */
  public double getRecycleAmmoniaMoles() {
    return recycleAmmoniaMoles;
  }

  /** @return recycled non-H2 in moles */
  public double getRecycleNonHydrogenMoles() {
    return recycleNonHydrogenMoles;
  }

  /** @return total recycle gas in moles */
  public double getRecycleGasMoles() {
    return recycleGasMoles;
  }

  /** @return total recycle gas mass in kilograms */
  public double getRecycleGasMassKg() {
    return recycleGasMassKg;
  }

  /** @return exported H2 in moles */
  public double getExportHydrogenMoles() {
    return exportHydrogenMoles;
  }

  /** @return exported H2S in moles */
  public double getExportHydrogenSulfideMoles() {
    return exportHydrogenSulfideMoles;
  }

  /** @return exported NH3 in moles */
  public double getExportAmmoniaMoles() {
    return exportAmmoniaMoles;
  }

  /** @return exported non-H2 in moles */
  public double getExportNonHydrogenMoles() {
    return exportNonHydrogenMoles;
  }

  /** @return total export gas in moles */
  public double getExportGasMoles() {
    return exportGasMoles;
  }

  /** @return total export gas mass in kilograms */
  public double getExportGasMassKg() {
    return exportGasMassKg;
  }

  /** @return H2 mole fraction in export gas */
  public double getExportHydrogenMoleFraction() {
    return exportHydrogenMoleFraction;
  }

  /** @return H2S mole fraction in export gas */
  public double getExportHydrogenSulfideMoleFraction() {
    return exportHydrogenSulfideMoleFraction;
  }

  /** @return NH3 mole fraction in export gas */
  public double getExportAmmoniaMoleFraction() {
    return exportAmmoniaMoleFraction;
  }

  /** @return non-H2 mole fraction in export gas */
  public double getExportNonHydrogenMoleFraction() {
    return exportNonHydrogenMoleFraction;
  }

  /** @return fraction of once-through H2 supply replaced by recycle */
  public double getFreshHydrogenReductionFraction() {
    return freshHydrogenReductionFraction;
  }

  /** @return H2 balance residual in moles */
  public double getHydrogenBalanceResidualMoles() {
    return hydrogenBalanceResidualMoles;
  }

  /** @return H2S balance residual in moles */
  public double getHydrogenSulfideBalanceResidualMoles() {
    return hydrogenSulfideBalanceResidualMoles;
  }

  /** @return NH3 balance residual in moles */
  public double getAmmoniaBalanceResidualMoles() {
    return ammoniaBalanceResidualMoles;
  }

  /** @return non-H2 balance residual in moles */
  public double getNonHydrogenBalanceResidualMoles() {
    return nonHydrogenBalanceResidualMoles;
  }

  /** @return overall fresh-feed mass-balance residual in kilograms */
  public double getOverallMassBalanceResidualKg() {
    return overallMassBalanceResidualKg;
  }
}
