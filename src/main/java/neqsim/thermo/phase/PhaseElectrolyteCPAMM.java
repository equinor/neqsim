/*
 * PhaseElectrolyteCPAMM.java
 *
 * Electrolyte CPA phase implementation based on the Maribo-Mogensen PhD thesis:
 * "Development of an Electrolyte CPA Equation of State for Mixed Solvent Electrolytes" Technical
 * University of Denmark, 2014
 */

package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.component.ComponentSrkCPAMM;
import neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface;

/**
 * Electrolyte CPA (e-CPA) phase class implementing the Maribo-Mogensen model.
 *
 * <p>
 * The residual Helmholtz free energy is computed as:
 * </p>
 *
 * <pre>
 * A^res = A^SRK + A^Association + A^Debye-Hückel + A^Born
 * </pre>
 *
 * <p>
 * Key features of this model:
 * </p>
 * <ul>
 * <li>SRK cubic equation of state with CPA association term</li>
 * <li>Debye-Hückel theory for long-range electrostatic interactions (simpler than MSA)</li>
 * <li>Born solvation term with empirical Born radius correlations</li>
 * <li>Temperature-dependent ion-solvent interaction parameters</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Maribo-Mogensen, B. (2014). PhD Thesis, DTU Chemical Engineering.</li>
 * <li>Maribo-Mogensen et al., Ind. Eng. Chem. Res. 2012, 51, 5353-5363</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseElectrolyteCPAMM extends PhaseSrkCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhaseElectrolyteCPAMM.class);

  // Physical constants
  /** Vacuum permittivity [F/m] = [C²/(J·m)]. */
  private static final double VACUUM_PERMITTIVITY = 8.854187817e-12;
  /** Elementary charge [C]. */
  private static final double ELECTRON_CHARGE = 1.60217662e-19;
  /** Avogadro's number [1/mol]. */
  private static final double AVOGADRO = ThermodynamicConstantsInterface.avagadroNumber;
  /** Boltzmann constant [J/K]. */
  private static final double BOLTZMANN = ThermodynamicConstantsInterface.R / AVOGADRO;

  // State variables for electrolyte terms
  /** Solvent static permittivity (dielectric constant) [-]. */
  protected double solventPermittivity = 80.0;
  /** Temperature derivative of solvent permittivity [1/K]. */
  protected double solventPermittivitydT = 0.0;
  /** Second temperature derivative of solvent permittivity [1/K²]. */
  protected double solventPermittivitydTdT = 0.0;
  /** Mixture dielectric constant including ion contributions [-]. */
  protected double mixturePermittivity = 80.0;
  /** Debye screening length inverse (kappa) [1/m]. */
  protected double kappa = 0.0;
  /** Temperature derivative of kappa [1/(m·K)]. */
  protected double kappadT = 0.0;
  /** Born X factor: Σ(n_i * z_i² / R_Born,i) [mol/m]. */
  protected double bornX = 0.0;
  /**
   * Ion-solvent short-range interaction parameter W [J/mol]. Calculated from wij mixing rule
   * parameters which are populated from ΔU_iw values.
   */
  protected double ionSolventW = 0.0;
  /** Temperature derivative of W [J/(mol·K)]. */
  protected double ionSolventWdT = 0.0;
  /** Packing fraction η = (π N_A / 6V) Σ n_i σ_i³ [-]. */
  protected double packingFraction = 0.0;
  /** Volume derivative of packing fraction [1/m³]. */
  protected double packingFractiondV = 0.0;

  /**
   * Mean closest-approach diameter for extended Debye-Hückel [m]. Computed as the charge-weighted
   * average of ion LJ diameters: d = Σ(n_i z_i² σ_i) / Σ(n_i z_i²).
   */
  protected double meanIonDiameter = 0.0;
  /**
   * DH screening factor τ(χ) = 3ψ(χ)/χ³ where χ = κd [-]. Reduces the primitive DH (DHLL)
   * contribution to account for finite ion size. τ = 1.0 recovers the DHLL; τ ≈ 0.6 at 1 molal
   * NaCl.
   */
  protected double tauDH = 1.0;
  /**
   * Derivative of screening factor dτ/dχ [-]. Used for composition derivatives where κ changes
   * significantly with added ions.
   */
  protected double tauDHprime = 0.0;

  /**
   * Ion-solvent binary interaction parameters wij. These are populated from the ΔU_iw parameters
   * from Maribo-Mogensen thesis Table 6.11. Format: wij[i][j] where i is ion index and j is solvent
   * index. Units: Kelvin (energy/R).
   */
  protected double[][] wij = null;

  /**
   * Temperature derivatives of ion-solvent wij parameters. wijT[i][j] = d(wij)/dT. Units: K/K
   * (dimensionless).
   */
  protected double[][] wijT = null;

  // Control flags for electrolyte terms
  /** Flag to enable/disable Debye-Hückel term. */
  protected boolean debyeHuckelOn = true;
  /** Flag to enable/disable Born term. */
  protected boolean bornOn = true;
  /**
   * Flag to enable/disable short-range ion-solvent term. Uses the Furst electrolyte mixing rule
   * correlation for wij parameters, combined with the FSR2 Helmholtz framework. Enable this via
   * setShortRangeOn(true).
   */
  protected boolean shortRangeOn = true;

  /** Electrolyte mixing rule for computing W, Wi, WiT (Furst-style wij from Stokes diameter). */
  protected transient ElectrolyteMixingRulesInterface electrolyteMixingRule;

  /** Short-range W parameter from electrolyte mixing rule [m³·mol]. */
  protected double srW = 0.0;
  /** Temperature derivative of W [m³·mol/K]. */
  protected double srWT = 0.0;
  /** Second temperature derivative of W [m³·mol/K²]. */
  protected double srWTT = 0.0;

  /**
   * Constructor for PhaseElectrolyteCPAMM.
   */
  public PhaseElectrolyteCPAMM() {
    electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    // Initialize electrolyte properties after parent init
    if (initType >= 1 && numberOfMolesInPhase > 1e-50 && getMolarVolume() > 1e-50) {
      initElectrolyteProperties();
    }
  }

  /** {@inheritDoc} */
  @Override
  public PhaseElectrolyteCPAMM clone() {
    PhaseElectrolyteCPAMM clonedPhase = null;
    try {
      clonedPhase = (PhaseElectrolyteCPAMM) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    // Call grandparent's addComponent (PhaseSrkEos -> PhaseEos)
    super.addComponent(name, moles, molesInPhase, compNumber);
    // Use the specialized MM component class
    componentArray[compNumber] = new ComponentSrkCPAMM(name, moles, molesInPhase, compNumber, this);
    // Resize xsitedni arrays for all CPA components
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i] instanceof ComponentSrkCPA) {
        ((ComponentSrkCPA) componentArray[i]).resizeXsitedni(numberOfComponents);
      }
    }
  }

  /**
   * Initialize volume-dependent electrolyte properties. Called during molar volume iteration.
   */
  public void initElectrolyteProperties() {
    solventPermittivity = calcSolventPermittivity(temperature);
    solventPermittivitydT = calcSolventPermittivitydT(temperature);
    solventPermittivitydTdT = calcSolventPermittivitydTdT(temperature);
    mixturePermittivity = calcMixturePermittivity();
    kappa = calcKappa();
    kappadT = calcKappadT();
    bornX = calcBornX();
    packingFraction = calcPackingFraction();
    packingFractiondV = calcPackingFractiondV();

    // Extended Debye-Hückel: compute mean ion diameter and screening factor
    meanIonDiameter = calcMeanIonDiameter();
    if (meanIonDiameter > 1e-30 && kappa > 1e-30) {
      double chi = kappa * meanIonDiameter;
      tauDH = calcTauDH(chi);
      tauDHprime = calcTauDHprime(chi);
    } else {
      tauDH = 1.0;
      tauDHprime = 0.0;
    }

    // Compute W/WT/WTT from the Furst electrolyte mixing rule (wij from Stokes diameter)
    if (shortRangeOn && electrolyteMixingRule != null) {
      srW = electrolyteMixingRule.calcW(this, temperature, pressure, numberOfComponents);
      srWT = electrolyteMixingRule.calcWT(this, temperature, pressure, numberOfComponents);
      srWTT = electrolyteMixingRule.calcWTT(this, temperature, pressure, numberOfComponents);
    }

    // MM short-range: compute ion-solvent W from ΔU_iw parameters
    if (shortRangeOn) {
      initMixingRuleWij();
      ionSolventW = calcIonSolventW();
      ionSolventWdT = calcIonSolventWdT();
    }
  }

  /**
   * Calculate the mean closest-approach diameter for the extended Debye-Hückel. Uses the
   * charge-squared weighted average of ionic LJ diameters.
   *
   * @return mean ion diameter [m]
   */
  public double calcMeanIonDiameter() {
    double sumNZ2sigma = 0.0;
    double sumNZ2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double z = componentArray[i].getIonicCharge();
      if (z != 0) {
        double n = componentArray[i].getNumberOfMolesInPhase();
        double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10; // Å to m
        sumNZ2sigma += n * z * z * sigma;
        sumNZ2 += n * z * z;
      }
    }
    if (sumNZ2 < 1e-30) {
      return 0.0;
    }
    return sumNZ2sigma / sumNZ2;
  }

  /**
   * Extended DH charging function ψ(u) = ln(1+u) - u + u²/2. In the limit u → 0, ψ → u³/3 (recovers
   * primitive DH).
   *
   * @param u dimensionless argument κd
   * @return ψ(u) [-]
   */
  static double psiDH(double u) {
    if (u < 1e-8) {
      return u * u * u / 3.0;
    }
    return Math.log(1.0 + u) - u + 0.5 * u * u;
  }

  /**
   * First derivative of ψ: ψ'(u) = u²/(1+u). In the limit u → 0, ψ' → u².
   *
   * @param u dimensionless argument κd
   * @return ψ'(u) [-]
   */
  static double psiDHprime(double u) {
    if (u < 1e-8) {
      return u * u;
    }
    return u * u / (1.0 + u);
  }

  /**
   * DH screening factor τ(χ) = 3ψ(χ)/χ³. Equals 1.0 in the primitive DH limit (χ → 0), with values
   * less than 1 at finite concentration. For NaCl at 1 molal, τ ≈ 0.60.
   *
   * @param chi dimensionless screening parameter κd
   * @return τ [-]
   */
  static double calcTauDH(double chi) {
    if (chi < 1e-6) {
      return 1.0;
    }
    return 3.0 * psiDH(chi) / (chi * chi * chi);
  }

  /**
   * Derivative of DH screening factor: dτ/dχ. Used for the composition derivative correction of the
   * extended DH where κ changes with ion amount.
   *
   * @param chi dimensionless screening parameter κd
   * @return dτ/dχ [-]
   */
  static double calcTauDHprime(double chi) {
    if (chi < 1e-6) {
      return 0.0;
    }
    // τ(χ) = 3ψ(χ)/χ³
    // τ'(χ) = 3[ψ'(χ)χ³ - 3χ²ψ(χ)] / χ⁶ = 3[ψ'χ - 3ψ] / χ⁴
    double psi = psiDH(chi);
    double psip = psiDHprime(chi);
    return 3.0 * (psip * chi - 3.0 * psi) / (chi * chi * chi * chi);
  }

  /**
   * Get the DH screening factor τ(κd) [-].
   *
   * @return τ value (1.0 for primitive DH, less than 1 for extended DH)
   */
  public double getTauDH() {
    return tauDH;
  }

  /**
   * Get the derivative of DH screening factor dτ/dχ [-].
   *
   * @return dτ/d(κd)
   */
  public double getTauDHprime() {
    return tauDHprime;
  }

  /**
   * Get the mean ion closest-approach diameter [m].
   *
   * @return mean ion diameter in meters
   */
  public double getMeanIonDiameter() {
    return meanIonDiameter;
  }

  /**
   * Initialize the ion-solvent wij mixing rule parameters from component ΔU_iw values.
   *
   * <p>
   * Following the Maribo-Mogensen thesis, the ion-solvent short-range interaction is parameterized
   * as: ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
   * </p>
   *
   * <p>
   * These are stored in the wij/wijT arrays which follow the standard mixing rule convention. The W
   * parameter used in the short-range Helmholtz term is then calculated as: W = Σ_i Σ_j (n_i × n_j
   * × wij(T)) for ion i and solvent j.
   * </p>
   */
  public void initMixingRuleWij() {
    if (wij == null || wij.length != numberOfComponents) {
      wij = new double[numberOfComponents][numberOfComponents];
      wijT = new double[numberOfComponents][numberOfComponents];
    }

    // Populate wij from component ΔU_iw parameters
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        wij[i][j] = 0.0;
        wijT[i][j] = 0.0;

        // Ion-solvent pairs: ion at i, solvent at j
        if (componentArray[i].getIonicCharge() != 0 && componentArray[j].getIonicCharge() == 0) {
          if (componentArray[i] instanceof ComponentSrkCPAMM) {
            ComponentSrkCPAMM ionComp = (ComponentSrkCPAMM) componentArray[i];
            // wij(T) = ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
            wij[i][j] = ionComp.getIonSolventInteractionEnergy(temperature);
            wijT[i][j] = ionComp.getIonSolventInteractionEnergydT();
          }
        } else if (componentArray[i].getIonicCharge() == 0
            // Solvent-ion pairs: solvent at i, ion at j (symmetric)
            && componentArray[j].getIonicCharge() != 0) {
          if (componentArray[j] instanceof ComponentSrkCPAMM) {
            ComponentSrkCPAMM ionComp = (ComponentSrkCPAMM) componentArray[j];
            wij[i][j] = ionComp.getIonSolventInteractionEnergy(temperature);
            wijT[i][j] = ionComp.getIonSolventInteractionEnergydT();
          }
        }
      }
    }
  }

  /**
   * Get the ion-solvent interaction parameter wij at current temperature.
   *
   * @param i first component index
   * @param j second component index
   * @return wij value [K]
   */
  public double getWij(int i, int j) {
    if (wij == null || i >= wij.length || j >= wij[i].length) {
      return 0.0;
    }
    return wij[i][j];
  }

  /**
   * Get the temperature derivative of ion-solvent interaction parameter wij.
   *
   * @param i first component index
   * @param j second component index
   * @return dwij/dT [K/K]
   */
  public double getWijT(int i, int j) {
    if (wijT == null || i >= wijT.length || j >= wijT[i].length) {
      return 0.0;
    }
    return wijT[i][j];
  }

  // ==================== Dielectric Constant Mixing Rules ====================

  /**
   * Dielectric constant mixing rule options for mixed solvents.
   *
   * <p>
   * Based on Zuber et al. (2014), Fluid Phase Equilibria 376, 116-123 and Maribo-Mogensen (2014).
   * </p>
   */
  public enum DielectricMixingRule {
    /**
     * Molar average: ε_mix = Σ(x_i × ε_i). Simple linear mixing. Thermodynamically consistent with
     * complete derivatives.
     */
    MOLAR_AVERAGE,

    /**
     * Volume average: ε_mix = Σ(φ_i × ε_i). Better for water-glycol mixtures. φ_i is volume
     * fraction based on molar volumes.
     */
    VOLUME_AVERAGE,

    /**
     * Looyenga: ε_mix^(1/3) = Σ(φ_i × ε_i^(1/3)). Theoretical basis for polar molecule mixtures.
     * Reference: Looyenga (1965), Physica 31, 401-406.
     */
    LOOYENGA,

    /**
     * Oster: Specifically designed for water-alcohol mixtures. Reference: Oster (1946), J. Am.
     * Chem. Soc. 68, 2036-2041.
     */
    OSTER,

    /**
     * Lichtenecker-Rother: ε_mix = ε₁^φ₁ × ε₂^φ₂. Good for binary systems. Reference: Lichtenecker
     * (1926), Phys. Z. 27, 115.
     */
    LICHTENECKER
  }

  /** Current dielectric mixing rule. */
  protected DielectricMixingRule dielectricMixingRule = DielectricMixingRule.MOLAR_AVERAGE;

  /**
   * Set the dielectric constant mixing rule.
   *
   * @param rule the mixing rule to use
   */
  public void setDielectricMixingRule(DielectricMixingRule rule) {
    this.dielectricMixingRule = rule;
  }

  /**
   * Get the current dielectric constant mixing rule.
   *
   * @return the current mixing rule
   */
  public DielectricMixingRule getDielectricMixingRule() {
    return dielectricMixingRule;
  }

  /**
   * Calculate solvent static permittivity using the selected mixing rule.
   *
   * <p>
   * This method dispatches to the appropriate mixing rule implementation based on the
   * {@link #dielectricMixingRule} setting.
   * </p>
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  public double calcSolventPermittivity(double T) {
    switch (dielectricMixingRule) {
      case VOLUME_AVERAGE:
        return calcSolventPermittivityVolumeAvg(T);
      case LOOYENGA:
        return calcSolventPermittivityLooyenga(T);
      case OSTER:
        return calcSolventPermittivityOster(T);
      case LICHTENECKER:
        return calcSolventPermittivityLichtenecker(T);
      case MOLAR_AVERAGE:
      default:
        return calcSolventPermittivityMolarAvg(T);
    }
  }

  /**
   * Calculate solvent permittivity using molar average mixing rule.
   *
   * <pre>
   * ε_mix = Σ(x_i × ε_i) / Σ(x_i)
   * </pre>
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  private double calcSolventPermittivityMolarAvg(double T) {
    double sumEps = 0.0;
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumEps += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDielectricConstant(T);
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return sumEps / sumMoles;
  }

  /**
   * Calculate solvent permittivity using volume average mixing rule.
   *
   * <pre>
   * ε_mix = Σ(φ_i × ε_i)
   * where φ_i = (n_i × V_i) / Σ(n_j × V_j) is the volume fraction
   * </pre>
   *
   * <p>
   * This gives better accuracy for water-glycol mixtures (2.4% avg error vs 4.2% for molar average)
   * based on Ma et al. (2010) data.
   * </p>
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  private double calcSolventPermittivityVolumeAvg(double T) {
    double totalVolume = 1e-50;
    double weightedSum = 0.0;

    // First pass: calculate total molar volume
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        // Use critical volume as approximation for molar volume (Vc in L/mol)
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0; // Default to water-like
        }
        totalVolume += moles * molarVolume;
      }
    }

    // Second pass: calculate volume-weighted average
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        weightedSum += volumeFraction * componentArray[i].getDielectricConstant(T);
      }
    }
    return weightedSum;
  }

  /**
   * Calculate solvent permittivity using Looyenga mixing rule.
   *
   * <pre>
   * ε_mix^(1/3) = Σ(φ_i × ε_i^(1/3))
   * </pre>
   *
   * <p>
   * The Looyenga equation has theoretical basis for polar molecule mixtures and works well for
   * water-organic systems.
   * </p>
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  private double calcSolventPermittivityLooyenga(double T) {
    double totalVolume = 1e-50;
    double cubicRootSum = 0.0;

    // First pass: calculate total volume
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    // Second pass: calculate Looyenga sum
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        double eps_i = componentArray[i].getDielectricConstant(T);
        cubicRootSum += volumeFraction * Math.pow(eps_i, 1.0 / 3.0);
      }
    }
    return Math.pow(cubicRootSum, 3.0);
  }

  /**
   * Calculate solvent permittivity using Oster mixing rule.
   *
   * <p>
   * The Oster equation is specifically designed for water-alcohol mixtures. It accounts for the
   * non-ideal mixing behavior of polar solvents.
   * </p>
   *
   * <pre>
   * ε_mix = ε₁×φ₁ + ε₂×φ₂ + k×φ₁×φ₂×(ε₁ - ε₂)
   * </pre>
   *
   * <p>
   * where k is an interaction parameter (typically -0.2 to -0.5 for water-alcohol).
   * </p>
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  private double calcSolventPermittivityOster(double T) {
    // For systems with more than 2 solvents, fall back to volume average
    int solventCount = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        solventCount++;
      }
    }
    if (solventCount != 2) {
      return calcSolventPermittivityVolumeAvg(T);
    }

    // Find the two solvents and their properties
    double[] eps = new double[2];
    double[] phi = new double[2];
    double totalVolume = 0.0;
    int idx = 0;

    // Calculate volumes
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0 && idx < 2) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        eps[idx] = componentArray[i].getDielectricConstant(T);
        phi[idx] = moles * molarVolume;
        totalVolume += phi[idx];
        idx++;
      }
    }

    // Normalize volume fractions
    phi[0] /= totalVolume;
    phi[1] /= totalVolume;

    // Oster interaction parameter (empirical, typically -0.3 for water-methanol)
    double k = -0.3;

    // Oster equation
    return eps[0] * phi[0] + eps[1] * phi[1] + k * phi[0] * phi[1] * (eps[0] - eps[1]);
  }

  /**
   * Calculate solvent permittivity using Lichtenecker-Rother mixing rule.
   *
   * <pre>
   * ln(ε_mix) = Σ(φ_i × ln(ε_i))
   * </pre>
   *
   * <p>
   * This is equivalent to: ε_mix = ε₁^φ₁ × ε₂^φ₂ × ...
   * </p>
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  private double calcSolventPermittivityLichtenecker(double T) {
    double totalVolume = 1e-50;
    double logSum = 0.0;

    // First pass: calculate total volume
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    // Second pass: calculate log-weighted sum
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        double eps_i = componentArray[i].getDielectricConstant(T);
        if (eps_i > 0) {
          logSum += volumeFraction * Math.log(eps_i);
        }
      }
    }
    return Math.exp(logSum);
  }

  /**
   * Calculate temperature derivative of solvent permittivity using the selected mixing rule.
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  public double calcSolventPermittivitydT(double T) {
    switch (dielectricMixingRule) {
      case VOLUME_AVERAGE:
        return calcSolventPermittivitydTVolumeAvg(T);
      case LOOYENGA:
        return calcSolventPermittivitydTLooyenga(T);
      case LICHTENECKER:
        return calcSolventPermittivitydTLichtenecker(T);
      case OSTER:
        return calcSolventPermittivitydTOster(T);
      case MOLAR_AVERAGE:
      default:
        return calcSolventPermittivitydTMolarAvg(T);
    }
  }

  /**
   * Molar average temperature derivative.
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  private double calcSolventPermittivitydTMolarAvg(double T) {
    double sumEpsdT = 0.0;
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumEpsdT += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDielectricConstantdT(T);
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return sumEpsdT / sumMoles;
  }

  /**
   * Volume average temperature derivative.
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  private double calcSolventPermittivitydTVolumeAvg(double T) {
    double totalVolume = 1e-50;
    double weightedSumdT = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        weightedSumdT += volumeFraction * componentArray[i].getDielectricConstantdT(T);
      }
    }
    return weightedSumdT;
  }

  /**
   * Looyenga temperature derivative.
   *
   * <pre>
   * d(ε^(1/3))/dT = Σ(φ_i × (1/3) × ε_i^(-2/3) × dε_i/dT)
   * dε/dT = 3 × ε^(2/3) × d(ε^(1/3))/dT
   * </pre>
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  private double calcSolventPermittivitydTLooyenga(double T) {
    double totalVolume = 1e-50;
    double cubicRootSum = 0.0;
    double cubicRootSumdT = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        double eps_i = componentArray[i].getDielectricConstant(T);
        double eps_i_dT = componentArray[i].getDielectricConstantdT(T);
        cubicRootSum += volumeFraction * Math.pow(eps_i, 1.0 / 3.0);
        cubicRootSumdT += volumeFraction * (1.0 / 3.0) * Math.pow(eps_i, -2.0 / 3.0) * eps_i_dT;
      }
    }

    // ε_mix = (cubicRootSum)³, so dε/dT = 3 × (cubicRootSum)² × d(cubicRootSum)/dT
    return 3.0 * Math.pow(cubicRootSum, 2.0) * cubicRootSumdT;
  }

  /**
   * Lichtenecker temperature derivative.
   *
   * <pre>
   * d(ln ε)/dT = Σ(φ_i × (1/ε_i) × dε_i/dT)
   * dε/dT = ε × d(ln ε)/dT
   * </pre>
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  private double calcSolventPermittivitydTLichtenecker(double T) {
    double totalVolume = 1e-50;
    double logSum = 0.0;
    double logSumdT = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        double eps_i = componentArray[i].getDielectricConstant(T);
        double eps_i_dT = componentArray[i].getDielectricConstantdT(T);
        if (eps_i > 0) {
          logSum += volumeFraction * Math.log(eps_i);
          logSumdT += volumeFraction * eps_i_dT / eps_i;
        }
      }
    }

    // ε = exp(logSum), so dε/dT = ε × d(logSum)/dT
    return Math.exp(logSum) * logSumdT;
  }

  /**
   * Oster temperature derivative.
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  private double calcSolventPermittivitydTOster(double T) {
    // For non-binary systems, fall back to volume average
    int solventCount = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        solventCount++;
      }
    }
    if (solventCount != 2) {
      return calcSolventPermittivitydTVolumeAvg(T);
    }

    double[] eps = new double[2];
    double[] epsdT = new double[2];
    double[] phi = new double[2];
    double totalVolume = 0.0;
    int idx = 0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0 && idx < 2) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        eps[idx] = componentArray[i].getDielectricConstant(T);
        epsdT[idx] = componentArray[i].getDielectricConstantdT(T);
        phi[idx] = moles * molarVolume;
        totalVolume += phi[idx];
        idx++;
      }
    }

    phi[0] /= totalVolume;
    phi[1] /= totalVolume;
    double k = -0.3;

    // dε/dT = dε₁/dT×φ₁ + dε₂/dT×φ₂ + k×φ₁×φ₂×(dε₁/dT - dε₂/dT)
    return epsdT[0] * phi[0] + epsdT[1] * phi[1] + k * phi[0] * phi[1] * (epsdT[0] - epsdT[1]);
  }

  /**
   * Calculate second temperature derivative of solvent permittivity.
   *
   * <p>
   * Currently only implemented for molar average. Other mixing rules use an approximate second
   * derivative based on the molar average.
   * </p>
   *
   * @param T temperature [K]
   * @return d²ε/dT² [1/K²]
   */
  public double calcSolventPermittivitydTdT(double T) {
    // For now, use molar average for second derivative (most common case)
    double sumEpsdTdT = 0.0;
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumEpsdTdT += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDielectricConstantdTdT(T);
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return sumEpsdTdT / sumMoles;
  }

  // ==================== Composition Derivatives of Dielectric Constant ====================

  /**
   * Calculate composition derivative of solvent permittivity for component k.
   *
   * <p>
   * For molar average: dε/dn_k = (ε_k - ε_mix) / n_solvent
   * </p>
   *
   * @param k component index
   * @param T temperature [K]
   * @return dε/dn_k [-]
   */
  public double calcSolventPermittivitydn(int k, double T) {
    // Only solvent components contribute
    if (componentArray[k].getIonicCharge() != 0) {
      return 0.0;
    }

    switch (dielectricMixingRule) {
      case VOLUME_AVERAGE:
        return calcSolventPermittivitydnVolumeAvg(k, T);
      case LOOYENGA:
        return calcSolventPermittivitydnLooyenga(k, T);
      case LICHTENECKER:
        return calcSolventPermittivitydnLichtenecker(k, T);
      case MOLAR_AVERAGE:
      default:
        return calcSolventPermittivitydnMolarAvg(k, T);
    }
  }

  /**
   * Molar average composition derivative.
   *
   * <pre>
   * ε = Σ(n_i × ε_i) / Σ(n_i)
   * dε/dn_k = (ε_k - ε) / n_solvent
   * </pre>
   *
   * @param k component index
   * @param T temperature [K]
   * @return dε/dn_k [-]
   */
  private double calcSolventPermittivitydnMolarAvg(int k, double T) {
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    double eps_k = componentArray[k].getDielectricConstant(T);
    return (eps_k - solventPermittivity) / sumMoles;
  }

  /**
   * Volume average composition derivative.
   *
   * <pre>
   * ε = Σ(φ_i × ε_i) where φ_i = (n_i × V_i) / Σ(n_j × V_j)
   * dε/dn_k = V_k × (ε_k - ε) / Σ(n_j × V_j)
   * </pre>
   *
   * @param k component index
   * @param T temperature [K]
   * @return dε/dn_k [-]
   */
  private double calcSolventPermittivitydnVolumeAvg(int k, double T) {
    double totalVolume = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    double V_k = componentArray[k].getCriticalVolume();
    if (V_k <= 0) {
      V_k = 18.0;
    }
    double eps_k = componentArray[k].getDielectricConstant(T);
    return V_k * (eps_k - solventPermittivity) / totalVolume;
  }

  /**
   * Looyenga composition derivative.
   *
   * <pre>
   * ε^(1/3) = Σ(φ_i × ε_i^(1/3))
   * d(ε^(1/3))/dn_k = V_k × (ε_k^(1/3) - ε^(1/3)) / Σ(n_j × V_j)
   * dε/dn_k = 3 × ε^(2/3) × d(ε^(1/3))/dn_k
   * </pre>
   *
   * @param k component index
   * @param T temperature [K]
   * @return dε/dn_k [-]
   */
  private double calcSolventPermittivitydnLooyenga(int k, double T) {
    double totalVolume = 1e-50;
    double cubicRootSum = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
        double volumeFraction = moles * molarVolume; // Will normalize later
        double eps_i = componentArray[i].getDielectricConstant(T);
        cubicRootSum += volumeFraction * Math.pow(eps_i, 1.0 / 3.0) / totalVolume; // Placeholder
      }
    }

    // Recalculate properly
    cubicRootSum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        double volumeFraction = (moles * molarVolume) / totalVolume;
        double eps_i = componentArray[i].getDielectricConstant(T);
        cubicRootSum += volumeFraction * Math.pow(eps_i, 1.0 / 3.0);
      }
    }

    double V_k = componentArray[k].getCriticalVolume();
    if (V_k <= 0) {
      V_k = 18.0;
    }
    double eps_k = componentArray[k].getDielectricConstant(T);

    // d(ε^(1/3))/dn_k
    double dCubicRootSumdn_k = V_k * (Math.pow(eps_k, 1.0 / 3.0) - cubicRootSum) / totalVolume;

    // dε/dn_k = 3 × (cubicRootSum)² × d(cubicRootSum)/dn_k
    return 3.0 * Math.pow(cubicRootSum, 2.0) * dCubicRootSumdn_k;
  }

  /**
   * Lichtenecker composition derivative.
   *
   * <pre>
   * ln(ε) = Σ(φ_i × ln(ε_i))
   * d(ln ε)/dn_k = V_k × (ln(ε_k) - ln(ε)) / Σ(n_j × V_j)
   * dε/dn_k = ε × d(ln ε)/dn_k
   * </pre>
   *
   * @param k component index
   * @param T temperature [K]
   * @return dε/dn_k [-]
   */
  private double calcSolventPermittivitydnLichtenecker(int k, double T) {
    double totalVolume = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        double moles = componentArray[i].getNumberOfMolesInPhase();
        double molarVolume = componentArray[i].getCriticalVolume();
        if (molarVolume <= 0) {
          molarVolume = 18.0;
        }
        totalVolume += moles * molarVolume;
      }
    }

    double V_k = componentArray[k].getCriticalVolume();
    if (V_k <= 0) {
      V_k = 18.0;
    }
    double eps_k = componentArray[k].getDielectricConstant(T);
    if (eps_k <= 0 || solventPermittivity <= 0) {
      return 0.0;
    }

    // d(ln ε)/dn_k
    double dLogEpsdn_k = V_k * (Math.log(eps_k) - Math.log(solventPermittivity)) / totalVolume;

    // dε/dn_k = ε × d(ln ε)/dn_k
    return solventPermittivity * dLogEpsdn_k;
  }

  /**
   * Calculate mixed composition-temperature derivative of solvent permittivity.
   *
   * @param k component index
   * @param T temperature [K]
   * @return d²ε/(dn_k dT) [-/K]
   */
  public double calcSolventPermittivitydndT(int k, double T) {
    // Only solvent components contribute
    if (componentArray[k].getIonicCharge() != 0) {
      return 0.0;
    }

    // Use molar average derivative for all mixing rules (approximate)
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    double eps_k_dT = componentArray[k].getDielectricConstantdT(T);
    return (eps_k_dT - solventPermittivitydT) / sumMoles;
  }

  /**
   * Calculate second composition derivative of solvent permittivity.
   *
   * <pre>
   * For molar average: ε = Σ(n_i × ε_i) / Σ(n_i)
   * dε/dn_k = (ε_k - ε) / n_solvent
   * d²ε/(dn_k dn_l) = -dε/dn_k / n_solvent - dε/dn_l / n_solvent
   *                 = -(ε_k - ε)/n_solvent² - (ε_l - ε)/n_solvent²
   *                 = (2ε - ε_k - ε_l) / n_solvent²
   * </pre>
   *
   * @param k first component index
   * @param l second component index
   * @param T temperature [K]
   * @return d²ε/(dn_k dn_l) [-]
   */
  public double calcSolventPermittivitydndn(int k, int l, double T) {
    // Only solvent-solvent pairs contribute
    if (componentArray[k].getIonicCharge() != 0 || componentArray[l].getIonicCharge() != 0) {
      return 0.0;
    }

    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    double eps_k = componentArray[k].getDielectricConstant(T);
    double eps_l = componentArray[l].getDielectricConstant(T);

    return (2.0 * solventPermittivity - eps_k - eps_l) / (sumMoles * sumMoles);
  }

  /**
   * Calculate mixture permittivity including ion effects. Following Maribo-Mogensen Eq. 5.27: ε =
   * ε_solvent * (1 - η_ion) / (1 + η_ion/2) where η_ion is the ionic packing fraction.
   *
   * @return mixture permittivity [-]
   */
  public double calcMixturePermittivity() {
    double etaIon = calcIonicPackingFraction();
    if (etaIon < 1e-10) {
      return solventPermittivity;
    }
    return solventPermittivity * (1.0 - etaIon) / (1.0 + etaIon / 2.0);
  }

  /**
   * Calculate ionic packing fraction. η_ion = (π/6V) * Σ(n_i * σ_i³) for ionic species only
   *
   * @return ionic packing fraction [-]
   */
  public double calcIonicPackingFraction() {
    double eta = 0.0;
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10; // m
        eta += AVOGADRO * Math.PI / 6.0 * componentArray[i].getNumberOfMolesInPhase()
            * Math.pow(sigma, 3.0) / V;
      }
    }
    return eta;
  }

  /**
   * Calculate Debye screening parameter kappa. From Maribo-Mogensen Eq. 4.14:
   *
   * <pre>
   * κ² = (e² / (ε₀ε_r k_B T)) * Σ(ρ_i * z_i²)
   * </pre>
   *
   * where ρ_i is number density [1/m³].
   *
   * @return kappa [1/m]
   */
  public double calcKappa() {
    double sumRhoZ2 = 0.0;
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() != 0) {
        double ni = componentArray[i].getNumberOfMolesInPhase();
        double zi = componentArray[i].getIonicCharge();
        // ρ_i = n_i * N_A / V [1/m³]
        double rhoI = ni * AVOGADRO / V;
        sumRhoZ2 += rhoI * zi * zi;
      }
    }
    if (sumRhoZ2 < 1e-30) {
      return 0.0;
    }
    double factor = ELECTRON_CHARGE * ELECTRON_CHARGE
        / (VACUUM_PERMITTIVITY * mixturePermittivity * BOLTZMANN * temperature);
    return Math.sqrt(factor * sumRhoZ2);
  }

  /**
   * Calculate temperature derivative of kappa.
   *
   * @return dκ/dT [1/(m·K)]
   */
  public double calcKappadT() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    // κ² ∝ 1/(ε_mix * T), so dκ²/dT = κ² * (-1/T - (1/ε_mix)*dε_mix/dT)
    // Since ε_mix = ε_solv * (1-η)/(1+η/2) and η is T-independent at constant V,n:
    // (1/ε_mix)*dε_mix/dT = (1/ε_solv)*dε_solv/dT = solventPermittivitydT/solventPermittivity
    double dKappa2dT =
        kappa * kappa * (-1.0 / temperature - solventPermittivitydT / solventPermittivity);
    return dKappa2dT / (2.0 * kappa);
  }

  /**
   * Calculate Born solvation factor X_Born. X_Born = Σ(n_i * z_i² / R_Born,i)
   *
   * <p>
   * Born radius correlations from Maribo-Mogensen Table 6.6:
   * </p>
   * <ul>
   * <li>Cations: R_Born = 0.5*σ + 0.1 Å</li>
   * <li>Anions: R_Born = 0.5*σ + 0.85 Å</li>
   * </ul>
   *
   * @return X_Born [mol/m]
   */
  public double calcBornX() {
    double sum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      int charge = (int) componentArray[i].getIonicCharge();
      if (charge != 0) {
        double rBorn;
        // Use MM component class if available for better Born radius
        if (componentArray[i] instanceof ComponentSrkCPAMM) {
          rBorn = ((ComponentSrkCPAMM) componentArray[i]).getBornRadiusMeters();
        } else {
          double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10;
          rBorn = calcBornRadius(sigma, charge);
        }
        if (rBorn > 0) {
          sum += componentArray[i].getNumberOfMolesInPhase() * charge * charge / rBorn;
        }
      }
    }
    return sum;
  }

  /**
   * Calculate Born radius using Maribo-Mogensen empirical correlations.
   *
   * @param sigma Lennard-Jones diameter [m]
   * @param charge ionic charge (sign indicates cation/anion)
   * @return Born radius [m]
   */
  public double calcBornRadius(double sigma, int charge) {
    if (charge > 0) {
      // Cation: R_Born = 0.5*σ + 0.1 Å
      return 0.5 * sigma + 0.1e-10;
    } else {
      // Anion: R_Born = 0.5*σ + 0.85 Å
      return 0.5 * sigma + 0.85e-10;
    }
  }

  // ==================== Packing Fraction ====================

  /**
   * Calculate packing fraction (reduced density) η.
   *
   * <p>
   * The packing fraction is defined as:
   * </p>
   *
   * <pre>
   * η = (π N_A / 6V) × Σ n_i σ_i³
   * </pre>
   *
   * <p>
   * This is used in the short-range term following the Furst electrolyte model.
   * </p>
   *
   * @return packing fraction η [-]
   */
  public double calcPackingFraction() {
    double eta = 0.0;
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }

    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      double sigma = componentArray[i].getLennardJonesMolecularDiameter() * 1e-10; // m
      eta += AVOGADRO * Math.PI / 6.0 * ni * Math.pow(sigma, 3.0) / V;
    }
    return eta;
  }

  /**
   * Calculate volume derivative of packing fraction.
   *
   * <p>
   * dη/dV = -η/V
   * </p>
   *
   * @return dη/dV [1/m³]
   */
  public double calcPackingFractiondV() {
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }
    return -packingFraction / V;
  }

  /**
   * Get the packing fraction.
   *
   * @return packing fraction η [-]
   */
  public double getPackingFraction() {
    return packingFraction;
  }

  /**
   * Get the volume derivative of packing fraction.
   *
   * @return dη/dV [1/m³]
   */
  public double getPackingFractiondV() {
    return packingFractiondV;
  }

  // ==================== Ion-Solvent Interaction Parameter W ====================

  /**
   * Calculate ion-solvent short-range interaction parameter W using wij mixing rule parameters.
   *
   * <p>
   * Following the standard mixing rule convention, W is calculated as:
   * </p>
   *
   * <pre>
   * W = Σ_i Σ_j (n_i × n_j × wij(T) × R / V)
   * </pre>
   *
   * <p>
   * where wij contains the ΔU_iw values from Maribo-Mogensen thesis Table 6.11, populated via
   * {@link #initMixingRuleWij()}.
   * </p>
   *
   * @return W [K·mol²]
   */
  public double calcIonSolventW() {
    if (wij == null) {
      return 0.0;
    }
    double sum = 0.0;

    // W = Σ_i Σ_j n_i n_j wij (wij in Kelvin from ΔU_iw/kB)
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < numberOfComponents; j++) {
        if (wij[i][j] != 0.0) {
          double nj = componentArray[j].getNumberOfMolesInPhase();
          sum += ni * nj * wij[i][j];
        }
      }
    }
    return sum;
  }

  /**
   * Calculate temperature derivative of W using wijT mixing rule parameters.
   *
   * @return dW/dT [mol²] (wijT is dimensionless: d(wij)/dT in K/K = 1)
   */
  public double calcIonSolventWdT() {
    if (wijT == null) {
      return 0.0;
    }
    double sum = 0.0;

    // dW/dT = Σ_i Σ_j n_i n_j dwij/dT
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < numberOfComponents; j++) {
        if (wijT[i][j] != 0.0) {
          double nj = componentArray[j].getNumberOfMolesInPhase();
          sum += ni * nj * wijT[i][j];
        }
      }
    }
    return sum;
  }

  // ==================== Helmholtz Free Energy Terms ====================

  /**
   * Debye-Hückel contribution to Helmholtz free energy (extensive). From Maribo-Mogensen Eq. 4.19,
   * the intensive form is:
   *
   * <pre>
   * A^DH / (V k_B T) = -κ³ / (12π)
   * </pre>
   *
   * Converting to extensive F = A/(RT):
   *
   * <pre>
   * A^DH = -κ³ V k_B T / (12π)
   * F^DH = A^DH / (RT) = -κ³ V / (12π N_A)
   * </pre>
   *
   * <p>
   * The extended DH applies a screening factor τ(χ) = 3ψ(χ)/χ³ where χ = κd and d is the mean ion
   * closest-approach diameter. This accounts for finite ion size and reduces the DH contribution by
   * ~40% at 1 molal NaCl. F^DH_ext = F^DH_DHLL × τ(κd).
   * </p>
   *
   * @return F^DH contribution to Helmholtz energy [-]
   */
  public double FDebyeHuckel() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // Total volume in m³
    double kappa3 = kappa * kappa * kappa;
    return -kappa3 * V / (12.0 * Math.PI * AVOGADRO) * tauDH;
  }

  /**
   * Temperature derivative of Debye-Hückel term at constant V. dF^DH/dT
   *
   * <p>
   * Uses extended DH: dF/dT = -V/(12π N_A) × dκ³/dT × τ(κd). The dτ/dT correction is neglected
   * since it is O(0.03%/K) relative to the main term.
   * </p>
   *
   * @return dF^DH/dT [-/K]
   */
  public double dFDebyeHuckeldT() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // Total volume in m³
    double dKappa3dT = 3.0 * kappa * kappa * kappadT;

    return -V / (12.0 * Math.PI * AVOGADRO) * dKappa3dT * tauDH;
  }

  /**
   * Volume derivative of Debye-Hückel term. dF^DH/dV = ∂F^DH/∂V at constant κ.
   *
   * <p>
   * Uses extended DH: dF/dV = -κ³/(12π N_A) × τ(κd). Since τ is independent of V at constant κ, and
   * F is linear in V, this is exact.
   * </p>
   *
   * @return dF^DH/dV [1/m³]
   */
  public double dFDebyeHuckeldV() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double kappa3 = kappa * kappa * kappa;
    return -kappa3 * 1e-5 / (12.0 * Math.PI * AVOGADRO) * tauDH;
  }

  /**
   * Born solvation contribution to Helmholtz free energy (extensive). From Maribo-Mogensen Eq.
   * 4.23, converted to extensive form:
   *
   * <pre>
   * F^Born = (N_A * e² / (8π * ε₀ * R * T)) * (1/ε_r - 1) * X_Born
   * </pre>
   *
   * where X_Born = Σ(n_i * z_i² / R_Born,i). This is extensive (scales with n).
   *
   * @return F^Born contribution [-]
   */
  public double FBorn() {
    if (bornX < 1e-30) {
      return 0.0;
    }
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    return prefactor * solventTerm * bornX;
  }

  /**
   * Temperature derivative of Born term. dF^Born/dT
   *
   * @return dF^Born/dT [-/K]
   */
  public double dFBorndT() {
    if (bornX < 1e-30) {
      return 0.0;
    }
    double prefactor =
        AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    double dSolventTermdT = -solventPermittivitydT / (solventPermittivity * solventPermittivity);

    // F = prefactor * solventTerm * bornX / T
    // dF/dT = prefactor * bornX * [dSolventTerm/dT / T - solventTerm / T²]
    return prefactor * bornX
        * (dSolventTermdT / temperature - solventTerm / (temperature * temperature));
  }

  /**
   * Volume derivative of Born term. dF^Born/dV (Born term is almost independent of V)
   *
   * @return dF^Born/dV [1/m³]
   */
  public double dFBorndV() {
    // Born term has no direct volume dependence in the Maribo-Mogensen formulation
    return 0.0;
  }

  /**
   * Derivative of Born term with respect to dielectric constant.
   *
   * <pre>
   * F^Born = prefactor * (1/ε - 1) * X_Born
   * ∂F^Born/∂ε = prefactor * (-1/ε²) * X_Born
   * </pre>
   *
   * This is used for the composition derivative via chain rule: dF^Born/dn_i includes (∂F^Born/∂ε)
   * * (∂ε/∂n_i).
   *
   * @return ∂F^Born/∂ε [-]
   */
  public double FBornD() {
    if (bornX < 1e-30) {
      return 0.0;
    }
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    return -prefactor * bornX / (solventPermittivity * solventPermittivity);
  }

  /**
   * Derivative of Born term with respect to X_Born.
   *
   * <pre>
   * F^Born = prefactor * (1/ε - 1) * X_Born
   * ∂F^Born/∂X_Born = prefactor * (1/ε - 1)
   * </pre>
   *
   * @return ∂F^Born/∂X_Born [-]
   */
  public double FBornX() {
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    return prefactor * solventTerm;
  }

  /**
   * Second derivative of Born term with respect to dielectric constant.
   *
   * <pre>
   * ∂²F^Born/∂ε² = prefactor * 2 * X_Born / ε³
   * </pre>
   *
   * @return ∂²F^Born/∂ε² [-]
   */
  public double FBornDD() {
    if (bornX < 1e-30) {
      return 0.0;
    }
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    return 2.0 * prefactor * bornX
        / (solventPermittivity * solventPermittivity * solventPermittivity);
  }

  /**
   * Mixed derivative of Born term with respect to dielectric constant and X_Born.
   *
   * <pre>
   * ∂²F^Born/(∂ε ∂X_Born) = prefactor * (-1/ε²)
   * </pre>
   *
   * @return ∂²F^Born/(∂ε ∂X_Born) [-]
   */
  public double FBornDX() {
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    return -prefactor / (solventPermittivity * solventPermittivity);
  }

  // ==================== Short-Range Ion-Solvent Term (MM Formula) ====================

  /**
   * Short-range ion-solvent contribution to Helmholtz free energy using the Maribo-Mogensen
   * formula.
   *
   * <pre>
   * F_SR = W / (n_T × T × (1 - η))
   * </pre>
   *
   * <p>
   * where W = Σ_i Σ_j n_i n_j ΔU_ij(T) is computed from the ion-solvent wij parameters (from
   * IonParametersMM ΔU_iw values), n_T is total moles, T is temperature, and η is the packing
   * fraction.
   * </p>
   *
   * @return F_SR contribution [-]
   */
  public double FShortRange() {
    if (!shortRangeOn) {
      return 0.0;
    }
    if (Math.abs(ionSolventW) < 1e-30) {
      return 0.0;
    }
    double nT = numberOfMolesInPhase;
    if (nT < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return ionSolventW / (nT * temperature * oneMinusEps);
  }

  // -- FSR2 partial derivatives with respect to W, V, eps --

  /**
   * Partial derivative of FSR2 with respect to W.
   *
   * @return dFSR2/dW [1/(m³)]
   */
  public double FSR2W() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return 1.0 / (V * oneMinusEps);
  }

  /**
   * Partial derivative of FSR2 with respect to V.
   *
   * @return dFSR2/dV [1/m³]
   */
  public double FSR2V() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return -srW / (V * V * oneMinusEps);
  }

  /**
   * Partial derivative of FSR2 with respect to eps (packing fraction).
   *
   * @return dFSR2/deps [-]
   */
  public double FSR2eps() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return srW / (V * oneMinusEps * oneMinusEps);
  }

  // -- Second order partial derivatives --

  /**
   * d²FSR2/(dV²).
   *
   * @return d²FSR2/dV² [1/m⁶]
   */
  public double FSR2VV() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return 2.0 * srW / (V * V * V * oneMinusEps);
  }

  /**
   * d²FSR2/(dV deps).
   *
   * @return d²FSR2/(dV deps)
   */
  public double FSR2epsV() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return -srW / (V * V * oneMinusEps * oneMinusEps);
  }

  /**
   * d²FSR2/(deps²).
   *
   * @return d²FSR2/deps²
   */
  public double FSR2epseps() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return 2.0 * srW / (V * oneMinusEps * oneMinusEps * oneMinusEps);
  }

  /**
   * d²FSR2/(dV dW).
   *
   * @return d²FSR2/(dV dW)
   */
  public double FSR2VW() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return -1.0 / (V * V * oneMinusEps);
  }

  /**
   * d²FSR2/(deps dW).
   *
   * @return d²FSR2/(deps dW)
   */
  public double FSR2epsW() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return 1.0 / (V * oneMinusEps * oneMinusEps);
  }

  // -- Third order partial derivatives --

  /**
   * d³FSR2/(dV³).
   *
   * @return d³FSR2/dV³
   */
  public double FSR2VVV() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return -6.0 * srW / (V * V * V * V * oneMinusEps);
  }

  /**
   * d³FSR2/(deps² dV).
   *
   * @return d³FSR2/(deps² dV)
   */
  public double FSR2epsepsV() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return -2.0 * srW / (V * V * oneMinusEps * oneMinusEps * oneMinusEps);
  }

  /**
   * d³FSR2/(dV² deps).
   *
   * @return d³FSR2/(dV² deps)
   */
  public double FSR2VVeps() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return 2.0 * srW / (V * V * V * oneMinusEps * oneMinusEps);
  }

  /**
   * d³FSR2/(deps³).
   *
   * @return d³FSR2/deps³
   */
  public double FSR2epsepseps() {
    double V = getMolarVolume() * 1e-5 * numberOfMolesInPhase;
    if (V < 1e-30) {
      return 0.0;
    }
    double oneMinusEps = 1.0 - packingFraction;
    if (oneMinusEps < 0.01) {
      oneMinusEps = 0.01;
    }
    return 6.0 * srW / (V * oneMinusEps * oneMinusEps * oneMinusEps * oneMinusEps);
  }

  // -- Total FSR2 derivatives with respect to T and V --

  /**
   * Temperature derivative of MM short-range term: dF_SR/dT.
   *
   * <p>
   * F = W/(nT × T × (1-η)), so dF/dT = Wt/(nT × T × (1-η)) - W/(nT × T² × (1-η))
   * </p>
   *
   * @return dFSR/dT [-/K]
   */
  public double dFShortRangedT() {
    if (!shortRangeOn || numberOfMolesInPhase < 1e-30) {
      return 0.0;
    }
    double nT = numberOfMolesInPhase;
    double oneMinusEps = Math.max(1.0 - packingFraction, 0.01);
    return (ionSolventWdT - ionSolventW / temperature) / (nT * temperature * oneMinusEps);
  }

  /**
   * Second temperature derivative of MM short-range term.
   *
   * @return d²FSR/dT² [-/K²]
   */
  public double dFShortRangedTdT() {
    if (!shortRangeOn) {
      return 0.0;
    }
    // Approximate: d²F/dT² ≈ 2F/T²
    return 2.0 * FShortRange() / (temperature * temperature);
  }

  /**
   * Volume derivative of MM short-range term. The MM SR is F = W/(nT × T × (1-η)). Since η depends
   * on V, dF/dV = F × (dη/dV) / (1-η). For dilute solutions η is small and this is negligible.
   *
   * @return dFSR/dV [1/m³]
   */
  public double dFShortRangedV() {
    if (!shortRangeOn) {
      return 0.0;
    }
    double oneMinusEps = Math.max(1.0 - packingFraction, 0.01);
    return FShortRange() * packingFractiondV / oneMinusEps * 1e-5;
  }

  /**
   * Second volume derivative of MM short-range term. Approximately zero for dilute solutions.
   *
   * @return d²FSR/dV²
   */
  public double dFShortRangedVdV() {
    if (!shortRangeOn) {
    }
    return 0.0;
  }

  /**
   * Third volume derivative of MM short-range term.
   *
   * @return d³FSR/dV³
   */
  public double dFShortRangedVdVdV() {
    if (!shortRangeOn) {
    }
    return 0.0;
  }

  /**
   * Mixed temperature-volume derivative of MM short-range term.
   *
   * @return d²FSR/dTdV [1/(m³·K)]
   */
  public double dFShortRangedTdV() {
    if (!shortRangeOn) {
    }
    return 0.0;
  }

  // -- Electrolyte mixing rule accessor methods --

  /**
   * Compute Wi = dW/dn_i for component i from the electrolyte mixing rule.
   *
   * @param compNumb component index
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @param numbcomp number of components
   * @return Wi value
   */
  public double calcWi(int compNumb, double temperature, double pressure, int numbcomp) {
    if (electrolyteMixingRule == null) {
      return 0.0;
    }
    return electrolyteMixingRule.calcWi(compNumb, this, temperature, pressure, numbcomp);
  }

  /**
   * Compute ∂W/∂n_i for the MM short-range from the wij matrix. W = Σ_a Σ_b n_a n_b wij[a][b], so
   * ∂W/∂n_i = 2 Σ_b n_b wij[i][b].
   *
   * @param compNumb component index
   * @return dW/dn_i [K·mol]
   */
  public double calcMMWi(int compNumb) {
    if (wij == null) {
      return 0.0;
    }
    double sum = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      sum += componentArray[j].getNumberOfMolesInPhase() * wij[compNumb][j];
    }
    return 2.0 * sum;
  }

  /**
   * Compute d²W/(dn_i dT) for the MM short-range from the wijT matrix. Since W_T = Σ_a Σ_b n_a n_b
   * wijT[a][b], the derivative is ∂W_T/∂n_i = 2 Σ_b n_b wijT[i][b].
   *
   * @param compNumb component index
   * @return d²W/(dn_i dT) [mol] (wijT is dimensionless)
   */
  public double calcMMWiT(int compNumb) {
    if (wijT == null) {
      return 0.0;
    }
    double sum = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      sum += componentArray[j].getNumberOfMolesInPhase() * wijT[compNumb][j];
    }
    return 2.0 * sum;
  }

  /**
   * Compute WiT = d²W/(dn_i dT) for component i (Furst mixing rule version).
   *
   * @param compNumb component index
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @param numbcomp number of components
   * @return WiT value
   */
  public double calcWiT(int compNumb, double temperature, double pressure, int numbcomp) {
    if (electrolyteMixingRule == null) {
      return 0.0;
    }
    return electrolyteMixingRule.calcWiT(compNumb, this, temperature, pressure, numbcomp);
  }

  /**
   * Compute Wij = d²W/(dn_i dn_j) from the electrolyte mixing rule.
   *
   * @param i first component index
   * @param j second component index
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @param numbcomp number of components
   * @return Wij value
   */
  public double calcWij(int i, int j, double temperature, double pressure, int numbcomp) {
    if (electrolyteMixingRule == null) {
      return 0.0;
    }
    return electrolyteMixingRule.calcWij(i, j, this, temperature, pressure, numbcomp);
  }

  /**
   * Get the electrolyte mixing rule.
   *
   * @return the electrolyte mixing rule interface
   */
  public ElectrolyteMixingRulesInterface getElectrolyteMixingRule() {
    return electrolyteMixingRule;
  }

  /**
   * Get the W parameter from short-range mixing rule.
   *
   * @return W value
   */
  public double getSrW() {
    return srW;
  }

  /**
   * Get the WT parameter (temperature derivative of W).
   *
   * @return WT value
   */
  public double getSrWT() {
    return srWT;
  }

  /**
   * Second volume derivative of Debye-Hückel term. d²F^DH/dV²
   *
   * @return d²F^DH/dV² [1/m⁶]
   */
  public double dFDebyeHuckeldVdV() {
    // F^DH = -κ³Vm/(12πRT)
    // dF/dV = -κ³/(12πRT * n) * 1e-5
    // d²F/dV² = 0 (linear in V at constant κ)
    return 0.0;
  }

  /**
   * Third volume derivative of Debye-Hückel term. d³F^DH/dV³
   *
   * @return d³F^DH/dV³ [1/m⁹]
   */
  public double dFDebyeHuckeldVdVdV() {
    return 0.0;
  }

  /**
   * Second temperature derivative of Debye-Hückel term. d²F^DH/dT²
   *
   * <p>
   * Uses extended DH screening factor τ.
   * </p>
   *
   * @return d²F^DH/dT² [-/K²]
   */
  public double dFDebyeHuckeldTdT() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // Total volume in m³
    double kappa3 = kappa * kappa * kappa;

    // Simplified: main contribution from 1/T² dependence
    return -2.0 * V / (12.0 * Math.PI * R * AVOGADRO) * kappa3
        / (temperature * temperature * temperature) * tauDH;
  }

  /**
   * Mixed temperature-volume derivative of Debye-Hückel term. d²F^DH/dTdV
   *
   * <p>
   * Uses extended DH screening factor τ.
   * </p>
   *
   * @return d²F^DH/dTdV [1/(m³·K)]
   */
  public double dFDebyeHuckeldTdV() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double kappa3 = kappa * kappa * kappa;
    return kappa3 * 1e-5 / (12.0 * Math.PI * R * AVOGADRO * temperature * temperature) * tauDH;
  }

  /**
   * Second temperature derivative of Born term. d²F^Born/dT²
   *
   * @return d²F^Born/dT² [-/K²]
   */
  public double dFBorndTdT() {
    if (bornX < 1e-30) {
      return 0.0;
    }
    double prefactor =
        AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    double dSolventTermdT = -solventPermittivitydT / (solventPermittivity * solventPermittivity);
    double d2SolventTermdT2 = -solventPermittivitydTdT / (solventPermittivity * solventPermittivity)
        + 2.0 * solventPermittivitydT * solventPermittivitydT
            / (solventPermittivity * solventPermittivity * solventPermittivity);

    // d²F/dT² from product rule on dF/dT
    double term1 = d2SolventTermdT2 / temperature;
    double term2 = -2.0 * dSolventTermdT / (temperature * temperature);
    double term3 = 2.0 * solventTerm / (temperature * temperature * temperature);
    return prefactor * bornX * (term1 + term2 + term3);
  }

  // ==================== Override methods for total Helmholtz energy ====================

  /** {@inheritDoc} */
  @Override
  public double getF() {
    initElectrolyteProperties();
    double F = super.getF(); // SRK + CPA association
    if (debyeHuckelOn) {
      F += FDebyeHuckel();
    }
    if (bornOn) {
      F += FBorn();
    }
    if (shortRangeOn) {
      F += FShortRange();
    }
    return F;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    double dFdT = super.dFdT();
    if (debyeHuckelOn) {
      dFdT += dFDebyeHuckeldT();
    }
    if (bornOn) {
      dFdT += dFBorndT();
    }
    if (shortRangeOn) {
      dFdT += dFShortRangedT();
    }
    return dFdT;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    double dFdV = super.dFdV();
    if (debyeHuckelOn) {
      dFdV += dFDebyeHuckeldV();
    }
    if (bornOn) {
      dFdV += dFBorndV();
    }
    if (shortRangeOn) {
      dFdV += dFShortRangedV();
    }
    return dFdV;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    double dFdVdV = super.dFdVdV();
    if (debyeHuckelOn) {
      dFdVdV += dFDebyeHuckeldVdV();
    }
    if (shortRangeOn) {
      dFdVdV += dFShortRangedVdV();
    }
    return dFdVdV;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    double dFdVdVdV = super.dFdVdVdV();
    if (debyeHuckelOn) {
      dFdVdVdV += dFDebyeHuckeldVdVdV();
    }
    if (shortRangeOn) {
      dFdVdVdV += dFShortRangedVdVdV();
    }
    return dFdVdVdV;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    double dFdTdT = super.dFdTdT();
    if (debyeHuckelOn) {
      dFdTdT += dFDebyeHuckeldTdT();
    }
    if (bornOn) {
      dFdTdT += dFBorndTdT();
    }
    if (shortRangeOn) {
      dFdTdT += dFShortRangedTdT();
    }
    return dFdTdT;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    double dFdTdV = super.dFdTdV();
    if (debyeHuckelOn) {
      dFdTdV += dFDebyeHuckeldTdV();
    }
    if (shortRangeOn) {
      dFdTdV += dFShortRangedTdV();
    }
    return dFdTdV;
  }

  // ==================== Getters and Setters ====================

  /**
   * Get solvent permittivity.
   *
   * @return solvent permittivity [-]
   */
  public double getSolventPermittivity() {
    return solventPermittivity;
  }

  /**
   * Get temperature derivative of solvent permittivity.
   *
   * @return d(solvent permittivity)/dT [1/K]
   */
  public double getSolventPermittivitydT() {
    return solventPermittivitydT;
  }

  /**
   * Get mixture permittivity.
   *
   * @return mixture permittivity [-]
   */
  public double getMixturePermittivity() {
    return mixturePermittivity;
  }

  /**
   * Get Debye screening parameter.
   *
   * @return kappa [1/m]
   */
  @Override
  public double getKappa() {
    return kappa;
  }

  /**
   * Get Debye screening length.
   *
   * @return 1/kappa [m]
   */
  public double getDebyeLength() {
    if (kappa < 1e-30) {
      return Double.POSITIVE_INFINITY;
    }
    return 1.0 / kappa;
  }

  /**
   * Get the Born X parameter (sum of z_i²/R_Born,i for all ions).
   *
   * @return Born X parameter [1/m]
   */
  public double getBornX() {
    return bornX;
  }

  /**
   * Get the ion-solvent interaction parameter W.
   *
   * @return W parameter [J/mol]
   */
  public double getIonSolventW() {
    return ionSolventW;
  }

  // ==================== Helper methods for component derivatives ====================

  /**
   * Partial derivative of F^DH with respect to X_DH = Σ(n_i z_i²). F^DH = -κ³V/(12πRTn_T), where κ²
   * ∝ X_DH
   *
   * @return ∂F^DH/∂X_DH
   */
  public double FDebyeHuckelX() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    // κ² = C * X_DH / V, so κ = sqrt(C * X_DH / V)
    // κ³ = (C * X_DH / V)^(3/2)
    // F = -κ³V/(12πRTn_T) = -(C/V)^(3/2) * X_DH^(3/2) * V / (12πRTn_T)
    // ∂F/∂X_DH = -(3/2) * (C/V)^(3/2) * X_DH^(1/2) * V / (12πRTn_T)
    // = -(3/2) * κ³ * V / (X_DH * 12πRTn_T)
    // = -3κ³V / (2 * X_DH * 12πRTn_T)
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5;
    double kappa3 = kappa * kappa * kappa;
    double X_DH = calcIonicStrengthSum();
    if (X_DH < 1e-30) {
      return 0.0;
    }
    return -3.0 * kappa3 * V
        / (2.0 * X_DH * 12.0 * Math.PI * R * temperature * numberOfMolesInPhase);
  }

  /**
   * Calculate ionic strength sum X_DH = Σ(n_i z_i²).
   *
   * @return ionic strength sum [mol]
   */
  public double calcIonicStrengthSum() {
    double sum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double z = componentArray[i].getIonicCharge();
      sum += componentArray[i].getNumberOfMolesInPhase() * z * z;
    }
    return sum;
  }

  /**
   * Partial derivative of F^DH with respect to n_T (at constant κ).
   *
   * @return ∂F^DH/∂n_T at constant κ
   */
  public double FDebyeHuckelN() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    // F = -κ³V/(12πRTn_T), at constant κ and V
    // ∂F/∂n_T = κ³V/(12πRTn_T²)
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5;
    double kappa3 = kappa * kappa * kappa;
    return kappa3 * V
        / (12.0 * Math.PI * R * temperature * numberOfMolesInPhase * numberOfMolesInPhase);
  }

  /**
   * Partial derivative of F^Born with respect to n_T (at constant X_Born).
   *
   * @return ∂F^Born/∂n_T at constant X_Born
   */
  public double FBornN() {
    if (bornX < 1e-30) {
      return 0.0;
    }
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    return -prefactor * solventTerm * bornX / (numberOfMolesInPhase * numberOfMolesInPhase);
  }

  /**
   * Temperature derivative of FBornX.
   *
   * @return ∂²F^Born/(∂X_Born ∂T)
   */
  public double FBornXT() {
    double prefactor =
        AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    double dSolventTermdT = -solventPermittivitydT / (solventPermittivity * solventPermittivity);
    // ∂/∂T [prefactor * solventTerm / (T * n_T)]
    // = prefactor/(n_T) * [dSolventTerm/dT / T - solventTerm / T²]
    return prefactor / numberOfMolesInPhase
        * (dSolventTermdT / temperature - solventTerm / (temperature * temperature));
  }

  /**
   * Enable or disable Debye-Hückel term.
   *
   * @param on true to enable
   */
  public void setDebyeHuckelOn(boolean on) {
    this.debyeHuckelOn = on;
  }

  /**
   * Enable or disable Born term.
   *
   * @param on true to enable
   */
  public void setBornOn(boolean on) {
    this.bornOn = on;
  }

  /**
   * Enable or disable short-range ion-solvent term.
   *
   * @param on true to enable
   */
  public void setShortRangeOn(boolean on) {
    this.shortRangeOn = on;
  }

  /**
   * Check if short-range ion-solvent term is enabled.
   *
   * @return true if short-range term is enabled
   */
  public boolean isShortRangeOn() {
    return shortRangeOn;
  }

  /**
   * Check if Debye-Hückel term is enabled.
   *
   * @return true if DH term is enabled
   */
  public boolean isDebyeHuckelOn() {
    return debyeHuckelOn;
  }

  /**
   * Check if Born solvation term is enabled.
   *
   * @return true if Born term is enabled
   */
  public boolean isBornOn() {
    return bornOn;
  }
}
