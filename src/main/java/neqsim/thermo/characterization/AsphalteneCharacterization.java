package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 * Characterizes asphaltene content using SARA fractionation data.
 *
 * <p>
 * SARA analysis divides crude oil into four fractions: Saturates, Aromatics, Resins, and
 * Asphaltenes. This class uses SARA data to:
 * </p>
 * <ul>
 * <li>Calculate the Colloidal Instability Index (CII)</li>
 * <li>Estimate asphaltene pseudo-component properties</li>
 * <li>Assign CPA parameters for thermodynamic modeling</li>
 * </ul>
 *
 * <p>
 * The colloidal model treats asphaltenes as particles stabilized by resins. Precipitation occurs
 * when the resin-to-asphaltene ratio drops below a critical value.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Yen, T.F., Chilingarian, G.V. (1994). Asphaltenes and Asphalts, Vol. 1</li>
 * <li>Mullins, O.C. et al. (2007). Asphaltenes, Heavy Oils, and Petroleomics</li>
 * <li>Jamaluddin, A.K.M. et al. (2002). SPE 74393</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneCharacterization {

  /** Weight fraction of saturates (0-1). */
  private double saturates;

  /** Weight fraction of aromatics (0-1). */
  private double aromatics;

  /** Weight fraction of resins (0-1). */
  private double resins;

  /** Weight fraction of asphaltenes (0-1). */
  private double asphaltenes;

  /** Molecular weight of C7+ fraction (g/mol). */
  private double mwC7plus;

  /** Density of C7+ fraction at standard conditions (kg/m3). */
  private double densityC7plus;

  /** Estimated molecular weight of asphaltene fraction (g/mol). */
  private double mwAsphaltene = 1700.0;

  /** Estimated molecular weight of resin fraction (g/mol). */
  private double mwResin = 800.0;

  /** CPA association energy for asphaltene self-association (K). */
  private double asphalteneAssociationEnergy = 3500.0;

  /** CPA association volume for asphaltene. */
  private double asphalteneAssociationVolume = 0.05;

  /** CPA association energy for asphaltene-resin cross-association (K). */
  private double resinAsphalteneAssociationEnergy = 2500.0;

  /** Critical CII value above which asphaltenes are unstable. */
  public static final double CII_STABLE_LIMIT = 0.7;

  /** CII value above which severe instability is expected. */
  public static final double CII_UNSTABLE_LIMIT = 0.9;

  /**
   * Default constructor.
   */
  public AsphalteneCharacterization() {}

  /**
   * Constructor with SARA fractions.
   *
   * @param saturates weight fraction of saturates (0-1)
   * @param aromatics weight fraction of aromatics (0-1)
   * @param resins weight fraction of resins (0-1)
   * @param asphaltenes weight fraction of asphaltenes (0-1)
   */
  public AsphalteneCharacterization(double saturates, double aromatics, double resins,
      double asphaltenes) {
    this.saturates = saturates;
    this.aromatics = aromatics;
    this.resins = resins;
    this.asphaltenes = asphaltenes;
    validateSARA();
  }

  /**
   * Validates that SARA fractions sum to approximately 1.0.
   *
   * @throws IllegalArgumentException if fractions don't sum to ~1.0
   */
  private void validateSARA() {
    double sum = saturates + aromatics + resins + asphaltenes;
    if (Math.abs(sum - 1.0) > 0.01) {
      throw new IllegalArgumentException("SARA fractions must sum to 1.0, got: " + sum);
    }
  }

  /**
   * Sets SARA fractions from analysis data.
   *
   * @param saturates weight fraction of saturates (0-1)
   * @param aromatics weight fraction of aromatics (0-1)
   * @param resins weight fraction of resins (0-1)
   * @param asphaltenes weight fraction of asphaltenes (0-1)
   */
  public void setSARAFractions(double saturates, double aromatics, double resins,
      double asphaltenes) {
    this.saturates = saturates;
    this.aromatics = aromatics;
    this.resins = resins;
    this.asphaltenes = asphaltenes;
    validateSARA();
  }

  /**
   * Sets C7+ fraction properties for correlation calculations.
   *
   * @param mwC7plus molecular weight of C7+ fraction (g/mol)
   * @param densityC7plus density of C7+ at standard conditions (kg/m3)
   */
  public void setC7plusProperties(double mwC7plus, double densityC7plus) {
    this.mwC7plus = mwC7plus;
    this.densityC7plus = densityC7plus;
  }

  /**
   * Calculates the Colloidal Instability Index (CII).
   *
   * <p>
   * CII = (Saturates + Asphaltenes) / (Aromatics + Resins)
   * </p>
   *
   * <p>
   * Interpretation:
   * </p>
   * <ul>
   * <li>CII &lt; 0.7: Stable (asphaltenes well-peptized)</li>
   * <li>0.7 &lt;= CII &lt; 0.9: Moderate risk</li>
   * <li>CII &gt;= 0.9: High instability risk</li>
   * </ul>
   *
   * @return the colloidal instability index
   */
  public double getColloidalInstabilityIndex() {
    double denominator = aromatics + resins;
    if (denominator < 1e-10) {
      return Double.POSITIVE_INFINITY;
    }
    return (saturates + asphaltenes) / denominator;
  }

  /**
   * Calculates the Resin-to-Asphaltene ratio (R/A).
   *
   * <p>
   * Higher R/A ratios indicate better asphaltene stability. Critical R/A for stability is typically
   * around 1.0-3.0.
   * </p>
   *
   * @return the resin-to-asphaltene ratio
   */
  public double getResinToAsphalteneRatio() {
    if (asphaltenes < 1e-10) {
      return Double.POSITIVE_INFINITY;
    }
    return resins / asphaltenes;
  }

  /**
   * Estimates the molecular weight of asphaltenes based on C7+ properties.
   *
   * <p>
   * Uses correlation from Whitson and Brule (2000).
   * </p>
   *
   * @return estimated asphaltene molecular weight (g/mol)
   */
  public double estimateAsphalteneMolecularWeight() {
    if (mwC7plus > 0 && densityC7plus > 0) {
      // Correlation based on C7+ properties
      // Asphaltene MW typically 5-10x the average C7+ MW
      double sgC7plus = densityC7plus / 1000.0; // Convert to specific gravity
      mwAsphaltene = mwC7plus * (3.0 + 5.0 * sgC7plus);
    }
    return mwAsphaltene;
  }

  /**
   * Estimates the molecular weight of resins based on C7+ properties.
   *
   * @return estimated resin molecular weight (g/mol)
   */
  public double estimateResinMolecularWeight() {
    if (mwC7plus > 0 && densityC7plus > 0) {
      // Resins are typically 2-4x the average C7+ MW
      double sgC7plus = densityC7plus / 1000.0;
      mwResin = mwC7plus * (1.5 + 2.5 * sgC7plus);
    }
    return mwResin;
  }

  /**
   * Adds asphaltene and resin pseudo-components to a thermodynamic system.
   *
   * <p>
   * This method adds characterized asphaltene and resin components with appropriate CPA parameters
   * for association modeling.
   * </p>
   *
   * @param system the thermodynamic system to modify
   * @param totalC7plusMoles total moles of C7+ fraction to distribute
   */
  public void addAsphalteneComponents(SystemInterface system, double totalC7plusMoles) {
    // Calculate moles of each SARA fraction (approximate using MW estimates)
    double massC7plus = totalC7plusMoles * mwC7plus;

    double massAsphaltene = massC7plus * asphaltenes;
    double massResin = massC7plus * resins;

    double molesAsphaltene = massAsphaltene / estimateAsphalteneMolecularWeight();
    double molesResin = massResin / estimateResinMolecularWeight();

    // Add asphaltene pseudo-component
    if (molesAsphaltene > 1e-10) {
      system.addComponent("asphaltene", molesAsphaltene);
      configureAsphalteneParameters(system);
    }

    // Add resin pseudo-component
    if (molesResin > 1e-10) {
      system.addComponent("resin", molesResin);
      configureResinParameters(system);
    }
  }

  /**
   * Configures CPA parameters for asphaltene component in the system.
   *
   * @param system the thermodynamic system
   */
  private void configureAsphalteneParameters(SystemInterface system) {
    int aspIndex = system.getPhase(0).getComponent("asphaltene").getComponentNumber();
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      system.getPhase(i).getComponent(aspIndex).setMolarMass(mwAsphaltene / 1000.0);
      system.getPhase(i).getComponent(aspIndex).setAssociationEnergy(asphalteneAssociationEnergy);
      system.getPhase(i).getComponent(aspIndex).setAssociationVolume(asphalteneAssociationVolume);
    }
  }

  /**
   * Configures CPA parameters for resin component in the system.
   *
   * @param system the thermodynamic system
   */
  private void configureResinParameters(SystemInterface system) {
    int resIndex = system.getPhase(0).getComponent("resin").getComponentNumber();
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      system.getPhase(i).getComponent(resIndex).setMolarMass(mwResin / 1000.0);
      // Resins have weaker self-association but cross-associate with asphaltenes
      system.getPhase(i).getComponent(resIndex)
          .setAssociationEnergy(resinAsphalteneAssociationEnergy * 0.6);
      system.getPhase(i).getComponent(resIndex).setAssociationVolume(asphalteneAssociationVolume);
    }
  }

  /**
   * Evaluates asphaltene stability based on CII.
   *
   * @return stability assessment string
   */
  public String evaluateStability() {
    double cii = getColloidalInstabilityIndex();
    double ra = getResinToAsphalteneRatio();

    StringBuilder sb = new StringBuilder();
    sb.append("Asphaltene Stability Assessment:\n");
    sb.append(String.format("  CII = %.3f%n", cii));
    sb.append(String.format("  R/A = %.3f%n", ra));

    if (cii < CII_STABLE_LIMIT) {
      sb.append("  Status: STABLE - Low precipitation risk\n");
    } else if (cii < CII_UNSTABLE_LIMIT) {
      sb.append("  Status: MODERATE RISK - Monitor during pressure depletion\n");
    } else {
      sb.append("  Status: HIGH RISK - Significant precipitation expected\n");
    }

    if (ra > 3.0) {
      sb.append("  R/A indicates good peptization\n");
    } else if (ra > 1.0) {
      sb.append("  R/A indicates marginal peptization\n");
    } else {
      sb.append("  R/A indicates poor peptization - high flocculation risk\n");
    }

    return sb.toString();
  }

  // Getters and setters

  public double getSaturates() {
    return saturates;
  }

  public void setSaturates(double saturates) {
    this.saturates = saturates;
  }

  public double getAromatics() {
    return aromatics;
  }

  public void setAromatics(double aromatics) {
    this.aromatics = aromatics;
  }

  public double getResins() {
    return resins;
  }

  public void setResins(double resins) {
    this.resins = resins;
  }

  public double getAsphaltenes() {
    return asphaltenes;
  }

  public void setAsphaltenes(double asphaltenes) {
    this.asphaltenes = asphaltenes;
  }

  public double getMwAsphaltene() {
    return mwAsphaltene;
  }

  public void setMwAsphaltene(double mwAsphaltene) {
    this.mwAsphaltene = mwAsphaltene;
  }

  public double getMwResin() {
    return mwResin;
  }

  public void setMwResin(double mwResin) {
    this.mwResin = mwResin;
  }

  public double getAsphalteneAssociationEnergy() {
    return asphalteneAssociationEnergy;
  }

  public void setAsphalteneAssociationEnergy(double energy) {
    this.asphalteneAssociationEnergy = energy;
  }

  public double getAsphalteneAssociationVolume() {
    return asphalteneAssociationVolume;
  }

  public void setAsphalteneAssociationVolume(double volume) {
    this.asphalteneAssociationVolume = volume;
  }
}
