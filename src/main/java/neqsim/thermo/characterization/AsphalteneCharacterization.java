package neqsim.thermo.characterization;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Characterizes asphaltene content using SARA fractionation data.
 *
 * <p>
 * SARA analysis divides crude oil into four fractions: Saturates, Aromatics, Resins, and Asphaltenes. This class uses
 * SARA data to:
 * </p>
 * <ul>
 * <li>Calculate the Colloidal Instability Index (CII)</li>
 * <li>Estimate asphaltene pseudo-component properties</li>
 * <li>Assign CPA parameters for thermodynamic modeling</li>
 * </ul>
 *
 * <p>
 * The colloidal model treats asphaltenes as particles stabilized by resins. Precipitation occurs when the
 * resin-to-asphaltene ratio drops below a critical value.
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

  /** True when the asphaltene molecular weight was set explicitly and must not be replaced by the estimate. */
  private boolean mwAsphalteneUserSet = false;

  /** True when the resin molecular weight was set explicitly and must not be replaced by the estimate. */
  private boolean mwResinUserSet = false;

  /** Density of the resin pseudo-component (g/cm3). */
  private double resinDensity = 1.0;

  /**
   * CPA association energy for asphaltene self-association (J/mol, the unit NeqSim's CPA uses). The default matches
   * the "asphaltene" entry in the component database.
   */
  private double asphalteneAssociationEnergy = 3500.0;

  /** CPA association volume for asphaltene (dimensionless). The default matches the component database (0.005). */
  private double asphalteneAssociationVolume = 0.005;

  /** CPA asphaltene-resin cross-association energy (J/mol). */
  private double resinAsphalteneAssociationEnergy = 2500.0;

  /**
   * CPA resin self-association energy (J/mol). Default 0: following Li and Firoozabadi (2010), resins only
   * cross-associate with asphaltenes ("capping" them) and do not self-associate.
   */
  private double resinSelfAssociationEnergy = 0.0;

  /** Critical CII value above which asphaltenes are unstable. */
  public static final double CII_STABLE_LIMIT = 0.7;

  /** CII value above which severe instability is expected. */
  public static final double CII_UNSTABLE_LIMIT = 0.9;

  /**
   * Default constructor.
   */
  public AsphalteneCharacterization() {
  }

  /**
   * Constructor with SARA fractions.
   *
   * @param saturates weight fraction of saturates (0-1)
   * @param aromatics weight fraction of aromatics (0-1)
   * @param resins weight fraction of resins (0-1)
   * @param asphaltenes weight fraction of asphaltenes (0-1)
   */
  public AsphalteneCharacterization(double saturates, double aromatics, double resins, double asphaltenes) {
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
    if (saturates < 0.0 || aromatics < 0.0 || resins < 0.0 || asphaltenes < 0.0) {
      throw new IllegalArgumentException("SARA fractions must not be negative");
    }
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
  public void setSARAFractions(double saturates, double aromatics, double resins, double asphaltenes) {
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
   * Higher R/A ratios indicate better asphaltene stability. Critical R/A for stability is typically around 1.0-3.0.
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
    if (!mwAsphalteneUserSet && mwC7plus > 0 && densityC7plus > 0) {
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
    if (!mwResinUserSet && mwC7plus > 0 && densityC7plus > 0) {
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
   * The asphaltene is the database component "asphaltene" (CPA scheme 1A) with the molecular weight and association
   * parameters of this object. The resin is added as a TBP pseudo-component "Resin" (named "Resin_PC" in the fluid)
   * whose critical properties come from the heavy-fraction correlations of {@link PedersenAsphalteneCharacterization}
   * at the resin molecular weight and density (molecular weight limited to 500-5000 g/mol). In a CPA fluid (SRK-CPA)
   * the resin is made associating
   * with a 2B scheme (one positive and one negative site). NeqSim's site-charge rules let only the positive site bond
   * to the asphaltene's single 1A site (charge -1); with the default resin self-association energy of 0 the resin does
   * not bond to itself, so it only bonds to asphaltene, through the cross-association set by
   * {@link #applyCrossAssociation(SystemInterface)}, which must be called after {@code setMixingRule(...)}.
   * </p>
   *
   * @param system the thermodynamic system to modify
   * @param totalC7plusMoles total moles of C7+ fraction to distribute
   * @throws IllegalStateException if the C7+ molecular weight has not been set with
   *         {@link #setC7plusProperties(double, double)}
   */
  public void addAsphalteneComponents(SystemInterface system, double totalC7plusMoles) {
    if (!(mwC7plus > 0.0)) {
      throw new IllegalStateException(
          "Set the C7+ molecular weight and density with setC7plusProperties(...) before adding asphaltene components");
    }
    double massC7plus = totalC7plusMoles * mwC7plus;

    double massAsphaltene = massC7plus * asphaltenes;
    double massResin = massC7plus * resins;

    double molesAsphaltene = massAsphaltene / estimateAsphalteneMolecularWeight();
    double molesResin = massResin / estimateResinMolecularWeight();

    if (molesAsphaltene > 1e-10) {
      system.addComponent("asphaltene", molesAsphaltene);
      configureAsphalteneParameters(system);
    }

    if (molesResin > 1e-10) {
      // Critical properties from the heavy-fraction correlations used for asphaltene (valid above the 1120 g/mol
      // limit of the fluid's automatic TBP model).
      PedersenAsphalteneCharacterization resinProperties =
          new PedersenAsphalteneCharacterization(mwResin, resinDensity, 0.0);
      resinProperties.characterize();
      system.addTBPfraction("Resin", molesResin, resinProperties.getAsphalteneMW() / 1000.0, resinDensity,
          resinProperties.getCriticalTemperature(), resinProperties.getCriticalPressure(),
          resinProperties.getAcentricFactor());
      configureResinParameters(system);
    }
  }

  /**
   * Returns the name of the resin pseudo-component in the fluid, or null if there is none.
   *
   * @param system the fluid
   * @return resin component name or null
   */
  public static String findResinName(SystemInterface system) {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      if (name.toLowerCase(java.util.Locale.ROOT).startsWith("resin")) {
        return name;
      }
    }
    return null;
  }

  /**
   * Sets the asphaltene-resin cross-association energy and volume in every CPA phase of the fluid. Call this after
   * {@code setMixingRule(...)}: setting the mixing rule again resets cross-association to the CR-1 combining rule.
   *
   * @param system a CPA fluid containing "asphaltene" and the resin pseudo-component
   * @return true if the parameters were applied, false if the fluid is not CPA or a component is missing
   */
  public boolean applyCrossAssociation(SystemInterface system) {
    String resinName = findResinName(system);
    if (resinName == null || !system.getPhase(0).hasComponent("asphaltene")
        || !(system.getPhase(0) instanceof PhaseCPAInterface)) {
      return false;
    }
    int a = system.getPhase(0).getComponent("asphaltene").getComponentNumber();
    int r = system.getPhase(0).getComponent(resinName).getComponentNumber();
    for (PhaseInterface phase : AsphalteneComponents.allPhases(system)) {
      if (phase instanceof PhaseCPAInterface) {
        ((PhaseCPAInterface) phase).setCrossAssociationParameters(a, r, resinAsphalteneAssociationEnergy,
            asphalteneAssociationVolume);
      }
    }
    return true;
  }

  /**
   * Configures CPA parameters for asphaltene component in the system.
   *
   * @param system the thermodynamic system
   */
  private void configureAsphalteneParameters(SystemInterface system) {
    int aspIndex = system.getPhase(0).getComponent("asphaltene").getComponentNumber();
    for (PhaseInterface phase : AsphalteneComponents.allPhases(system)) {
      phase.getComponent(aspIndex).setMolarMass(mwAsphaltene / 1000.0);
      phase.getComponent(aspIndex).setAssociationEnergy(asphalteneAssociationEnergy);
      phase.getComponent(aspIndex).setAssociationVolume(asphalteneAssociationVolume);
    }
  }

  /**
   * Configures the resin pseudo-component: a 2B association scheme in SRK-CPA fluids, with the resin self-association
   * energy and the asphaltene association volume.
   *
   * @param system the thermodynamic system
   */
  private void configureResinParameters(SystemInterface system) {
    String resinName = findResinName(system);
    if (resinName == null || !(system.getPhase(0) instanceof PhaseCPAInterface)) {
      return;
    }
    int resIndex = system.getPhase(0).getComponent(resinName).getComponentNumber();
    for (PhaseInterface phase : AsphalteneComponents.allPhases(system)) {
      ComponentInterface resin = phase.getComponent(resIndex);
      if (!(resin instanceof ComponentSrkCPA)) {
        continue;
      }
      resin.setAssociationSites(2, "2B");
      ((ComponentSrkCPA) resin).resizeXsitedni(phase.getNumberOfComponents());
      resin.setAssociationEnergy(resinSelfAssociationEnergy);
      resin.setAssociationVolume(asphalteneAssociationVolume);
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
    this.mwAsphalteneUserSet = true;
  }

  public double getMwResin() {
    return mwResin;
  }

  public void setMwResin(double mwResin) {
    this.mwResin = mwResin;
    this.mwResinUserSet = true;
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

  /**
   * Gets the asphaltene-resin cross-association energy (J/mol).
   *
   * @return cross-association energy
   */
  public double getResinAsphalteneAssociationEnergy() {
    return resinAsphalteneAssociationEnergy;
  }

  /**
   * Sets the asphaltene-resin cross-association energy (J/mol) used by {@link #applyCrossAssociation}.
   *
   * @param energy cross-association energy in J/mol
   */
  public void setResinAsphalteneAssociationEnergy(double energy) {
    this.resinAsphalteneAssociationEnergy = energy;
  }

  /**
   * Gets the resin self-association energy (J/mol).
   *
   * @return resin self-association energy
   */
  public double getResinSelfAssociationEnergy() {
    return resinSelfAssociationEnergy;
  }

  /**
   * Sets the resin self-association energy (J/mol). Default 0 (resins only cross-associate).
   *
   * @param energy resin self-association energy in J/mol
   */
  public void setResinSelfAssociationEnergy(double energy) {
    this.resinSelfAssociationEnergy = energy;
  }

  /**
   * Gets the resin pseudo-component density (g/cm3).
   *
   * @return resin density
   */
  public double getResinDensity() {
    return resinDensity;
  }

  /**
   * Sets the resin pseudo-component density (g/cm3), default 1.0.
   *
   * @param density resin density in g/cm3
   */
  public void setResinDensity(double density) {
    this.resinDensity = density;
  }
}
