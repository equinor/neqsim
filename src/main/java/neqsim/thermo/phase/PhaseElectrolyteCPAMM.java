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
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.constants.IonParametersMM;

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
   * Flag to enable/disable short-range ion-solvent term. Disabled by default as ion-solvent
   * short-range interactions are typically handled through the CPA mixing rule binary interaction
   * parameters. Enable this for experimental testing of explicit short-range terms.
   */
  protected boolean shortRangeOn = false;

  /**
   * Constructor for PhaseElectrolyteCPAMM.
   */
  public PhaseElectrolyteCPAMM() {
    super();
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
    initMixingRuleWij(); // Initialize wij from component ΔU_iw parameters
    solventPermittivity = calcSolventPermittivity(temperature);
    solventPermittivitydT = calcSolventPermittivitydT(temperature);
    solventPermittivitydTdT = calcSolventPermittivitydTdT(temperature);
    mixturePermittivity = calcMixturePermittivity();
    kappa = calcKappa();
    kappadT = calcKappadT();
    bornX = calcBornX();
    ionSolventW = calcIonSolventW();
    ionSolventWdT = calcIonSolventWdT();
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
        }
        // Solvent-ion pairs: solvent at i, ion at j (symmetric)
        else if (componentArray[i].getIonicCharge() == 0
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

  /**
   * Calculate solvent static permittivity as mole-fraction weighted average.
   *
   * @param T temperature [K]
   * @return solvent permittivity [-]
   */
  public double calcSolventPermittivity(double T) {
    double sumEps = 0.0;
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumEps += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDiElectricConstant(T);
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return sumEps / sumMoles;
  }

  /**
   * Calculate temperature derivative of solvent permittivity.
   *
   * @param T temperature [K]
   * @return dε/dT [1/K]
   */
  public double calcSolventPermittivitydT(double T) {
    double sumEpsdT = 0.0;
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumEpsdT += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDiElectricConstantdT(T);
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return sumEpsdT / sumMoles;
  }

  /**
   * Calculate second temperature derivative of solvent permittivity.
   *
   * @param T temperature [K]
   * @return d²ε/dT² [1/K²]
   */
  public double calcSolventPermittivitydTdT(double T) {
    double sumEpsdTdT = 0.0;
    double sumMoles = 1e-50;
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i].getIonicCharge() == 0) {
        sumEpsdTdT += componentArray[i].getNumberOfMolesInPhase()
            * componentArray[i].getDiElectricConstantdTdT(T);
        sumMoles += componentArray[i].getNumberOfMolesInPhase();
      }
    }
    return sumEpsdTdT / sumMoles;
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
    // κ² ∝ 1/(ε_r * T), so dκ²/dT = κ² * (-1/T - (1/ε_r)*dε_r/dT)
    double dKappa2dT =
        kappa * kappa * (-1.0 / temperature - solventPermittivitydT / mixturePermittivity);
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
   * @return W [J/mol]
   */
  public double calcIonSolventW() {
    if (wij == null) {
      return 0.0;
    }
    double sum = 0.0;
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }

    // Sum over all pairs using wij mixing rule parameters
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < numberOfComponents; j++) {
        if (wij[i][j] != 0.0) {
          double nj = componentArray[j].getNumberOfMolesInPhase();
          // W contribution: n_i * n_j * wij * R / V
          sum += ni * nj * wij[i][j] * ThermodynamicConstantsInterface.R / V;
        }
      }
    }
    return sum;
  }

  /**
   * Calculate temperature derivative of W using wijT mixing rule parameters.
   *
   * @return dW/dT [J/(mol·K)]
   */
  public double calcIonSolventWdT() {
    if (wijT == null) {
      return 0.0;
    }
    double sum = 0.0;
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }

    // Sum over all pairs using wijT mixing rule parameters
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = componentArray[i].getNumberOfMolesInPhase();
      for (int j = 0; j < numberOfComponents; j++) {
        if (wijT[i][j] != 0.0) {
          double nj = componentArray[j].getNumberOfMolesInPhase();
          sum += ni * nj * wijT[i][j] * ThermodynamicConstantsInterface.R / V;
        }
      }
    }
    return sum;
  }

  // ==================== Helmholtz Free Energy Terms ====================

  /**
   * Debye-Hückel contribution to Helmholtz free energy (extensive). From Maribo-Mogensen Eq. 4.19,
   * converted to extensive form:
   *
   * <pre>
   * F^DH = -κ³V / (12π * R * T * N_A)
   * </pre>
   *
   * where V is the total volume [m³]. This is extensive (scales with n).
   *
   * @return F^DH contribution to Helmholtz energy [-]
   */
  public double FDebyeHuckel() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // Total volume in m³
    double kappa3 = kappa * kappa * kappa;
    return -kappa3 * V / (12.0 * Math.PI * R * temperature * AVOGADRO);
  }

  /**
   * Temperature derivative of Debye-Hückel term at constant V. dF^DH/dT
   *
   * @return dF^DH/dT [-/K]
   */
  public double dFDebyeHuckeldT() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // Total volume in m³
    double kappa3 = kappa * kappa * kappa;
    double dKappa3dT = 3.0 * kappa * kappa * kappadT;

    // F = -κ³V/(12πRT*N_A), so dF/dT = -V/(12πR*N_A) * [dκ³/dT * 1/T - κ³/T²]
    double term1 = -V / (12.0 * Math.PI * R * AVOGADRO) * dKappa3dT / temperature;
    double term2 = V / (12.0 * Math.PI * R * AVOGADRO) * kappa3 / (temperature * temperature);
    return term1 + term2;
  }

  /**
   * Volume derivative of Debye-Hückel term. dF^DH/dV = ∂F^DH/∂V
   *
   * @return dF^DH/dV [1/m³]
   */
  public double dFDebyeHuckeldV() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    // F = -κ³V/(12πRT*N_A), so dF/dV = -κ³/(12πRT*N_A)
    double kappa3 = kappa * kappa * kappa;
    return -kappa3 * 1e-5 / (12.0 * Math.PI * R * temperature * AVOGADRO);
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

  // ==================== Short-Range Ion-Solvent Term ====================

  /**
   * Short-range ion-solvent contribution to Helmholtz free energy. Following Maribo-Mogensen, the
   * ion-solvent short-range term is:
   *
   * <pre>
   * F^SR = W / (V * (1 - η))
   * </pre>
   *
   * where W = Σ_i Σ_s (n_i * n_s * ΔU_is) for ion i and solvent s, and η is the packing fraction.
   * Simplified here as F^SR = W for now (excluding packing fraction correction).
   *
   * @return F^SR contribution [-]
   */
  public double FShortRange() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }
    // F^SR = W / (R * T) where W already includes R factor
    return ionSolventW / (R * temperature);
  }

  /**
   * Temperature derivative of short-range term. dF^SR/dT
   *
   * @return dF^SR/dT [-/K]
   */
  public double dFShortRangedT() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    // F = W / (RT), dF/dT = (dW/dT) / (RT) - W / (RT²)
    return ionSolventWdT / (R * temperature) - ionSolventW / (R * temperature * temperature);
  }

  /**
   * Volume derivative of short-range term. dF^SR/dV
   *
   * @return dF^SR/dV [1/m³]
   */
  public double dFShortRangedV() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }
    // W ∝ 1/V, so dW/dV = -W/V and d(W/RT)/dV = -W/(RT*V)
    return -ionSolventW / (R * temperature * V) * 1e-5;
  }

  /**
   * Second volume derivative of short-range term. d²F^SR/dV²
   *
   * @return d²F^SR/dV² [1/m⁶]
   */
  public double dFShortRangedVdV() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }
    // d²(W/RT)/dV² = 2W/(RT*V²)
    return 2.0 * ionSolventW / (R * temperature * V * V) * 1e-10;
  }

  /**
   * Third volume derivative of short-range term. d³F^SR/dV³
   *
   * @return d³F^SR/dV³ [1/m⁹]
   */
  public double dFShortRangedVdVdV() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }
    // d³(W/RT)/dV³ = -6W/(RT*V³)
    return -6.0 * ionSolventW / (R * temperature * V * V * V) * 1e-15;
  }

  /**
   * Second temperature derivative of short-range term. d²F^SR/dT²
   *
   * @return d²F^SR/dT² [-/K²]
   */
  public double dFShortRangedTdT() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    // F = W / (RT), dF/dT = WdT/RT - W/RT², d²F/dT² ≈ 2W/RT³ (ignoring WdTdT)
    return 2.0 * ionSolventW / (R * temperature * temperature * temperature)
        - 2.0 * ionSolventWdT / (R * temperature * temperature);
  }

  /**
   * Mixed temperature-volume derivative of short-range term. d²F^SR/dTdV
   *
   * @return d²F^SR/dTdV [1/(m³·K)]
   */
  public double dFShortRangedTdV() {
    if (!shortRangeOn || ionSolventW == 0.0) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // m³
    if (V < 1e-30) {
      return 0.0;
    }
    // d²/dTdV[W/RT] = W/(RT²V)
    return ionSolventW / (R * temperature * temperature * V) * 1e-5;
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
   * @return d²F^DH/dT² [-/K²]
   */
  public double dFDebyeHuckeldTdT() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double V = getMolarVolume() * numberOfMolesInPhase * 1e-5; // Total volume in m³
    double kappa3 = kappa * kappa * kappa;

    // Simplified: main contribution from 1/T² dependence
    // F = -κ³V/(12πRT*N_A), dF/dT ∝ κ³/T², d²F/dT² ∝ -2κ³/T³
    return -2.0 * V / (12.0 * Math.PI * R * AVOGADRO) * kappa3
        / (temperature * temperature * temperature);
  }

  /**
   * Mixed temperature-volume derivative of Debye-Hückel term. d²F^DH/dTdV
   *
   * @return d²F^DH/dTdV [1/(m³·K)]
   */
  public double dFDebyeHuckeldTdV() {
    if (kappa < 1e-30) {
      return 0.0;
    }
    double kappa3 = kappa * kappa * kappa;
    // d/dV of dF/dT: F = -κ³V/(12πRT*N_A), so dF/dT has V term
    // d²F/dTdV = d/dV(κ³V/(12πRT²*N_A)) = κ³/(12πRT²*N_A)
    return kappa3 * 1e-5 / (12.0 * Math.PI * R * AVOGADRO * temperature * temperature);
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
   * Partial derivative of F^Born with respect to X_Born.
   *
   * @return ∂F^Born/∂X_Born
   */
  public double FBornX() {
    double prefactor = AVOGADRO * ELECTRON_CHARGE * ELECTRON_CHARGE
        / (8.0 * Math.PI * VACUUM_PERMITTIVITY * R * temperature);
    double solventTerm = 1.0 / solventPermittivity - 1.0;
    return prefactor * solventTerm / numberOfMolesInPhase;
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
}
