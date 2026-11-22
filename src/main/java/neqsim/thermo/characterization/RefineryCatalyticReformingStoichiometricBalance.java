package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Immutable material-balance receipt for a catalytic-reforming screening case.
 *
 * <p>
 * The calculation represents same-carbon-number naphthene dehydrogenation and paraffin dehydrocyclization only. It does
 * not predict reaction kinetics, equilibrium, catalyst performance, operating conditions, heat duty, hydrocracking,
 * isomerization, octane number, or plant yields.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryCatalyticReformingStoichiometricBalance implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Atomic mass of carbon in kilograms per kilomole of atoms. */
  public static final double CARBON_ATOMIC_MASS_KG_PER_KMOL = 12.011;

  /** Atomic mass of hydrogen in kilograms per kilomole of atoms. */
  public static final double HYDROGEN_ATOMIC_MASS_KG_PER_KMOL = 1.00794;

  private final int carbonNumber;
  private final double naphtheneFeedFlowKmolPerHour;
  private final double paraffinFeedFlowKmolPerHour;
  private final double naphtheneConversionFraction;
  private final double paraffinConversionFraction;
  private final double naphtheneReactionExtentKmolPerHour;
  private final double paraffinReactionExtentKmolPerHour;
  private final double unconvertedNaphtheneFlowKmolPerHour;
  private final double unconvertedParaffinFlowKmolPerHour;
  private final double aromaticProductFlowKmolPerHour;
  private final double hydrogenProductFlowKmolPerHour;
  private final double feedMassFlowKgPerHour;
  private final double aromaticProductMassFlowKgPerHour;
  private final double hydrogenProductMassFlowKgPerHour;
  private final double totalProductMassFlowKgPerHour;
  private final double aromaticProductKgPerTonneFeed;
  private final double hydrogenProductKgPerTonneFeed;
  private final double massBalanceResidualKgPerHour;
  private final double carbonAtomBalanceResidualKmolPerHour;
  private final double hydrogenAtomBalanceResidualKmolPerHour;

  private RefineryCatalyticReformingStoichiometricBalance(int carbonNumber, double naphtheneFeedFlowKmolPerHour,
      double paraffinFeedFlowKmolPerHour, double naphtheneConversionFraction, double paraffinConversionFraction,
      double naphtheneReactionExtentKmolPerHour, double paraffinReactionExtentKmolPerHour,
      double unconvertedNaphtheneFlowKmolPerHour, double unconvertedParaffinFlowKmolPerHour,
      double aromaticProductFlowKmolPerHour, double hydrogenProductFlowKmolPerHour, double feedMassFlowKgPerHour,
      double aromaticProductMassFlowKgPerHour, double hydrogenProductMassFlowKgPerHour,
      double totalProductMassFlowKgPerHour, double aromaticProductKgPerTonneFeed, double hydrogenProductKgPerTonneFeed,
      double massBalanceResidualKgPerHour, double carbonAtomBalanceResidualKmolPerHour,
      double hydrogenAtomBalanceResidualKmolPerHour) {
    this.carbonNumber = carbonNumber;
    this.naphtheneFeedFlowKmolPerHour = naphtheneFeedFlowKmolPerHour;
    this.paraffinFeedFlowKmolPerHour = paraffinFeedFlowKmolPerHour;
    this.naphtheneConversionFraction = naphtheneConversionFraction;
    this.paraffinConversionFraction = paraffinConversionFraction;
    this.naphtheneReactionExtentKmolPerHour = naphtheneReactionExtentKmolPerHour;
    this.paraffinReactionExtentKmolPerHour = paraffinReactionExtentKmolPerHour;
    this.unconvertedNaphtheneFlowKmolPerHour = unconvertedNaphtheneFlowKmolPerHour;
    this.unconvertedParaffinFlowKmolPerHour = unconvertedParaffinFlowKmolPerHour;
    this.aromaticProductFlowKmolPerHour = aromaticProductFlowKmolPerHour;
    this.hydrogenProductFlowKmolPerHour = hydrogenProductFlowKmolPerHour;
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.aromaticProductMassFlowKgPerHour = aromaticProductMassFlowKgPerHour;
    this.hydrogenProductMassFlowKgPerHour = hydrogenProductMassFlowKgPerHour;
    this.totalProductMassFlowKgPerHour = totalProductMassFlowKgPerHour;
    this.aromaticProductKgPerTonneFeed = aromaticProductKgPerTonneFeed;
    this.hydrogenProductKgPerTonneFeed = hydrogenProductKgPerTonneFeed;
    this.massBalanceResidualKgPerHour = massBalanceResidualKgPerHour;
    this.carbonAtomBalanceResidualKmolPerHour = carbonAtomBalanceResidualKmolPerHour;
    this.hydrogenAtomBalanceResidualKmolPerHour = hydrogenAtomBalanceResidualKmolPerHour;
  }

  /**
   * Calculate a same-carbon-number reforming stoichiometric receipt.
   *
   * <p>
   * The two represented reactions are {@code CnH2n -> CnH(2n-6) + 3 H2} and {@code CnH(2n+2) -> CnH(2n-6) + 4 H2}.
   *
   * @param carbonNumber carbon number shared by the naphthene, paraffin, and aromatic; at least six
   * @param naphtheneFeedFlowKmolPerHour naphthene feed flow in kmol/h
   * @param paraffinFeedFlowKmolPerHour paraffin feed flow in kmol/h
   * @param naphtheneConversionFraction naphthene conversion fraction in [0, 1]
   * @param paraffinConversionFraction paraffin conversion fraction in [0, 1]
   * @return immutable stoichiometric balance receipt
   */
  public static RefineryCatalyticReformingStoichiometricBalance calculate(int carbonNumber,
      double naphtheneFeedFlowKmolPerHour, double paraffinFeedFlowKmolPerHour, double naphtheneConversionFraction,
      double paraffinConversionFraction) {
    if (carbonNumber < 6) {
      throw new IllegalArgumentException("carbonNumber must be at least six");
    }
    requireFiniteNonNegative("naphtheneFeedFlowKmolPerHour", naphtheneFeedFlowKmolPerHour);
    requireFiniteNonNegative("paraffinFeedFlowKmolPerHour", paraffinFeedFlowKmolPerHour);
    requireClosedFraction("naphtheneConversionFraction", naphtheneConversionFraction);
    requireClosedFraction("paraffinConversionFraction", paraffinConversionFraction);
    if (naphtheneFeedFlowKmolPerHour + paraffinFeedFlowKmolPerHour <= 0.0) {
      throw new IllegalArgumentException("total feed flow must be positive");
    }

    double naphtheneReactionExtentKmolPerHour = naphtheneFeedFlowKmolPerHour * naphtheneConversionFraction;
    double paraffinReactionExtentKmolPerHour = paraffinFeedFlowKmolPerHour * paraffinConversionFraction;
    double unconvertedNaphtheneFlowKmolPerHour = naphtheneFeedFlowKmolPerHour - naphtheneReactionExtentKmolPerHour;
    double unconvertedParaffinFlowKmolPerHour = paraffinFeedFlowKmolPerHour - paraffinReactionExtentKmolPerHour;
    double aromaticProductFlowKmolPerHour = naphtheneReactionExtentKmolPerHour + paraffinReactionExtentKmolPerHour;
    double hydrogenProductFlowKmolPerHour = 3.0 * naphtheneReactionExtentKmolPerHour
        + 4.0 * paraffinReactionExtentKmolPerHour;

    double naphtheneMolarMassKgPerKmol = hydrocarbonMolarMassKgPerKmol(carbonNumber, 2 * carbonNumber);
    double paraffinMolarMassKgPerKmol = hydrocarbonMolarMassKgPerKmol(carbonNumber, 2 * carbonNumber + 2);
    double aromaticMolarMassKgPerKmol = hydrocarbonMolarMassKgPerKmol(carbonNumber, 2 * carbonNumber - 6);
    double hydrogenMolarMassKgPerKmol = 2.0 * HYDROGEN_ATOMIC_MASS_KG_PER_KMOL;

    double feedMassFlowKgPerHour = naphtheneFeedFlowKmolPerHour * naphtheneMolarMassKgPerKmol
        + paraffinFeedFlowKmolPerHour * paraffinMolarMassKgPerKmol;
    double aromaticProductMassFlowKgPerHour = aromaticProductFlowKmolPerHour * aromaticMolarMassKgPerKmol;
    double hydrogenProductMassFlowKgPerHour = hydrogenProductFlowKmolPerHour * hydrogenMolarMassKgPerKmol;
    double totalProductMassFlowKgPerHour = unconvertedNaphtheneFlowKmolPerHour * naphtheneMolarMassKgPerKmol
        + unconvertedParaffinFlowKmolPerHour * paraffinMolarMassKgPerKmol + aromaticProductMassFlowKgPerHour
        + hydrogenProductMassFlowKgPerHour;
    double massBalanceResidualKgPerHour = feedMassFlowKgPerHour - totalProductMassFlowKgPerHour;

    double carbonAtomsInKmolPerHour = carbonNumber * (naphtheneFeedFlowKmolPerHour + paraffinFeedFlowKmolPerHour);
    double carbonAtomsOutKmolPerHour = carbonNumber
        * (unconvertedNaphtheneFlowKmolPerHour + unconvertedParaffinFlowKmolPerHour + aromaticProductFlowKmolPerHour);
    double carbonAtomBalanceResidualKmolPerHour = carbonAtomsInKmolPerHour - carbonAtomsOutKmolPerHour;
    double hydrogenAtomsInKmolPerHour = 2.0 * carbonNumber * naphtheneFeedFlowKmolPerHour
        + (2.0 * carbonNumber + 2.0) * paraffinFeedFlowKmolPerHour;
    double hydrogenAtomsOutKmolPerHour = 2.0 * carbonNumber * unconvertedNaphtheneFlowKmolPerHour
        + (2.0 * carbonNumber + 2.0) * unconvertedParaffinFlowKmolPerHour
        + (2.0 * carbonNumber - 6.0) * aromaticProductFlowKmolPerHour + 2.0 * hydrogenProductFlowKmolPerHour;
    double hydrogenAtomBalanceResidualKmolPerHour = hydrogenAtomsInKmolPerHour - hydrogenAtomsOutKmolPerHour;

    double massTolerance = 1.0e-12 * Math.max(1.0, feedMassFlowKgPerHour);
    double atomTolerance = 1.0e-12 * Math.max(1.0, Math.max(carbonAtomsInKmolPerHour, hydrogenAtomsInKmolPerHour));
    if (!Double.isFinite(totalProductMassFlowKgPerHour) || totalProductMassFlowKgPerHour <= 0.0
        || Math.abs(massBalanceResidualKgPerHour) > massTolerance
        || Math.abs(carbonAtomBalanceResidualKmolPerHour) > atomTolerance
        || Math.abs(hydrogenAtomBalanceResidualKmolPerHour) > atomTolerance) {
      throw new IllegalArgumentException("inputs do not define a closed stoichiometric balance");
    }

    double tonnesFeedPerHour = feedMassFlowKgPerHour / 1000.0;
    double aromaticProductKgPerTonneFeed = aromaticProductMassFlowKgPerHour / tonnesFeedPerHour;
    double hydrogenProductKgPerTonneFeed = hydrogenProductMassFlowKgPerHour / tonnesFeedPerHour;

    return new RefineryCatalyticReformingStoichiometricBalance(carbonNumber, naphtheneFeedFlowKmolPerHour,
        paraffinFeedFlowKmolPerHour, naphtheneConversionFraction, paraffinConversionFraction,
        naphtheneReactionExtentKmolPerHour, paraffinReactionExtentKmolPerHour, unconvertedNaphtheneFlowKmolPerHour,
        unconvertedParaffinFlowKmolPerHour, aromaticProductFlowKmolPerHour, hydrogenProductFlowKmolPerHour,
        feedMassFlowKgPerHour, aromaticProductMassFlowKgPerHour, hydrogenProductMassFlowKgPerHour,
        totalProductMassFlowKgPerHour, aromaticProductKgPerTonneFeed, hydrogenProductKgPerTonneFeed,
        massBalanceResidualKgPerHour, carbonAtomBalanceResidualKmolPerHour, hydrogenAtomBalanceResidualKmolPerHour);
  }

  /**
   * Calculate a hydrocarbon molar mass from its elemental formula.
   *
   * @param carbonAtoms number of carbon atoms per molecule
   * @param hydrogenAtoms number of hydrogen atoms per molecule
   * @return molar mass in kg/kmol
   */
  private static double hydrocarbonMolarMassKgPerKmol(int carbonAtoms, int hydrogenAtoms) {
    return carbonAtoms * CARBON_ATOMIC_MASS_KG_PER_KMOL + hydrogenAtoms * HYDROGEN_ATOMIC_MASS_KG_PER_KMOL;
  }

  /**
   * Require a finite, non-negative value.
   *
   * @param name input name used in the exception message
   * @param value input value
   */
  private static void requireFiniteNonNegative(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /**
   * Require a finite fraction on the closed unit interval.
   *
   * @param name input name used in the exception message
   * @param value input value
   */
  private static void requireClosedFraction(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
    }
  }

  /** @return shared carbon number */
  public int getCarbonNumber() {
    return carbonNumber;
  }

  /** @return naphthene feed flow in kmol/h */
  public double getNaphtheneFeedFlowKmolPerHour() {
    return naphtheneFeedFlowKmolPerHour;
  }

  /** @return paraffin feed flow in kmol/h */
  public double getParaffinFeedFlowKmolPerHour() {
    return paraffinFeedFlowKmolPerHour;
  }

  /** @return naphthene conversion fraction */
  public double getNaphtheneConversionFraction() {
    return naphtheneConversionFraction;
  }

  /** @return paraffin conversion fraction */
  public double getParaffinConversionFraction() {
    return paraffinConversionFraction;
  }

  /** @return naphthene reaction extent in kmol/h */
  public double getNaphtheneReactionExtentKmolPerHour() {
    return naphtheneReactionExtentKmolPerHour;
  }

  /** @return paraffin reaction extent in kmol/h */
  public double getParaffinReactionExtentKmolPerHour() {
    return paraffinReactionExtentKmolPerHour;
  }

  /** @return unconverted naphthene flow in kmol/h */
  public double getUnconvertedNaphtheneFlowKmolPerHour() {
    return unconvertedNaphtheneFlowKmolPerHour;
  }

  /** @return unconverted paraffin flow in kmol/h */
  public double getUnconvertedParaffinFlowKmolPerHour() {
    return unconvertedParaffinFlowKmolPerHour;
  }

  /** @return aromatic product flow in kmol/h */
  public double getAromaticProductFlowKmolPerHour() {
    return aromaticProductFlowKmolPerHour;
  }

  /** @return hydrogen product flow in kmol/h */
  public double getHydrogenProductFlowKmolPerHour() {
    return hydrogenProductFlowKmolPerHour;
  }

  /** @return feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return aromatic product mass flow in kg/h */
  public double getAromaticProductMassFlowKgPerHour() {
    return aromaticProductMassFlowKgPerHour;
  }

  /** @return hydrogen product mass flow in kg/h */
  public double getHydrogenProductMassFlowKgPerHour() {
    return hydrogenProductMassFlowKgPerHour;
  }

  /** @return total product mass flow, including hydrogen, in kg/h */
  public double getTotalProductMassFlowKgPerHour() {
    return totalProductMassFlowKgPerHour;
  }

  /** @return aromatic product in kg per tonne of feed */
  public double getAromaticProductKgPerTonneFeed() {
    return aromaticProductKgPerTonneFeed;
  }

  /** @return hydrogen product in kg per tonne of feed */
  public double getHydrogenProductKgPerTonneFeed() {
    return hydrogenProductKgPerTonneFeed;
  }

  /** @return total mass-balance residual in kg/h */
  public double getMassBalanceResidualKgPerHour() {
    return massBalanceResidualKgPerHour;
  }

  /** @return carbon-atom balance residual in kmol-atoms/h */
  public double getCarbonAtomBalanceResidualKmolPerHour() {
    return carbonAtomBalanceResidualKmolPerHour;
  }

  /** @return hydrogen-atom balance residual in kmol-atoms/h */
  public double getHydrogenAtomBalanceResidualKmolPerHour() {
    return hydrogenAtomBalanceResidualKmolPerHour;
  }
}
