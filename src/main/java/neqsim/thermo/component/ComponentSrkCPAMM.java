/*
 * ComponentSrkCPAMM.java
 *
 * Component class for the Maribo-Mogensen electrolyte CPA model.
 */

package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseElectrolyteCPAMM;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.util.constants.IonParametersMM;

/**
 * Component class for the Maribo-Mogensen electrolyte CPA (e-CPA) model.
 *
 * <p>
 * This class extends ComponentSrkCPA to add support for:
 * </p>
 * <ul>
 * <li>Temperature-dependent ion-solvent interaction parameters</li>
 * <li>Born radius calculations using empirical correlations</li>
 * <li>Ion-specific parameters from the Maribo-Mogensen thesis</li>
 * <li>Complete thermodynamic derivatives for electrolyte contributions</li>
 * </ul>
 *
 * <p>
 * The ion-solvent interaction energy follows:
 * </p>
 *
 * <pre>
 * ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
 * </pre>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSrkCPAMM extends ComponentSrkCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // Physical constants
  /** Vacuum permittivity [F/m]. */
  private static final double EPSILON_0 = 8.854187817e-12;
  /** Elementary charge [C]. */
  private static final double E_CHARGE = 1.602176634e-19;
  /** Avogadro's number [1/mol]. */
  private static final double N_A = ThermodynamicConstantsInterface.avagadroNumber;
  /** Boltzmann constant [J/K]. */
  private static final double K_B = ThermodynamicConstantsInterface.R / N_A;
  /** Gas constant [J/(mol·K)]. */
  private static final double R = ThermodynamicConstantsInterface.R;

  /** Ion-solvent interaction parameter at reference temperature [K]. */
  private double u0_iw = 0.0;

  /** Temperature coefficient of ion-solvent interaction [K/K]. */
  private double uT_iw = 0.0;

  /** Born radius [Å]. */
  private double bornRadius = 0.0;

  /** Flag indicating if this component has MM ion parameters. */
  private boolean hasMMParameters = false;

  /**
   * Constructor for ComponentSrkCPAMM.
   *
   * @param name Name of component
   * @param moles Total number of moles of component
   * @param molesInPhase Number of moles in phase
   * @param compIndex Index number of component in phase object component array
   * @param phase Phase object
   */
  public ComponentSrkCPAMM(String name, double moles, double molesInPhase, int compIndex,
      PhaseInterface phase) {
    super(name, moles, molesInPhase, compIndex, phase);
    initMMParameters(name);
  }

  /**
   * Initialize Maribo-Mogensen parameters from the ion database.
   *
   * <p>
   * For ions, the following parameters are set:
   * </p>
   * <ul>
   * <li>Ion-solvent interaction energy ΔU_iw from MM thesis Table 6.11</li>
   * <li>Born radius from empirical correlations</li>
   * <li>Lennard-Jones diameter σ</li>
   * <li>SRK b parameter: b = (α × σ³ + β) following Furst correlation</li>
   * <li>SRK a parameter: set to ~0 for ions (no van der Waals attraction)</li>
   * </ul>
   *
   * @param name component name
   */
  private void initMMParameters(String name) {
    IonParametersMM.IonData ionData = IonParametersMM.getIonData(name);
    if (ionData != null) {
      this.u0_iw = ionData.u0_iw;
      this.uT_iw = ionData.uT_iw;
      this.bornRadius = ionData.getBornRadius();
      this.hasMMParameters = true;

      // Override Lennard-Jones diameter if we have better data
      if (ionData.sigma > 0) {
        this.setLennardJonesMolecularDiameter(ionData.sigma);

        // Calculate ion b parameter from ionic diameter (same approach as Furst)
        // b = α × σ³ + β, using FurstElectrolyteConstants for CPA
        double sigma = ionData.sigma;
        double ionB = (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParamsCPA[0]
            * Math.pow(sigma, 3.0)
            + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParamsCPA[1]) * 1e5;
        this.setb(ionB);

        // Ion a parameter is essentially zero (no van der Waals attraction)
        this.seta(1.0e-35);
      }
    } else if (getIonicCharge() != 0) {
      // For ions without specific parameters, use empirical Born radius
      double sigma = getLennardJonesMolecularDiameter();
      if (sigma > 0) {
        if (getIonicCharge() > 0) {
          this.bornRadius = 0.5 * sigma + 0.1;
        } else {
          this.bornRadius = 0.5 * sigma + 0.85;
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSrkCPAMM clone() {
    ComponentSrkCPAMM clonedComponent = null;
    try {
      clonedComponent = (ComponentSrkCPAMM) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedComponent;
  }

  /**
   * Get the ion-solvent interaction energy at a given temperature.
   *
   * <pre>
   * ΔU_iw(T) = u⁰_iw + uᵀ_iw × (T - 298.15)
   * </pre>
   *
   * @param temperature temperature in Kelvin
   * @return interaction energy in Kelvin
   */
  public double getIonSolventInteractionEnergy(double temperature) {
    return u0_iw + uT_iw * (temperature - IonParametersMM.T_REF);
  }

  /**
   * Get the temperature derivative of ion-solvent interaction energy.
   *
   * @return dΔU_iw/dT in K/K
   */
  public double getIonSolventInteractionEnergydT() {
    return uT_iw;
  }

  /**
   * Get the Born radius.
   *
   * @return Born radius in Ångströms
   */
  public double getBornRadius() {
    return bornRadius;
  }

  /**
   * Get the Born radius in meters.
   *
   * @return Born radius in meters
   */
  public double getBornRadiusMeters() {
    return bornRadius * 1e-10;
  }

  /**
   * Set the Born radius.
   *
   * @param radius Born radius in Ångströms
   */
  public void setBornRadius(double radius) {
    this.bornRadius = radius;
  }

  /**
   * Get the u⁰_iw parameter.
   *
   * @return u⁰_iw in Kelvin
   */
  public double getU0_iw() {
    return u0_iw;
  }

  /**
   * Set the u⁰_iw parameter.
   *
   * @param u0_iw value in Kelvin
   */
  public void setU0_iw(double u0_iw) {
    this.u0_iw = u0_iw;
    this.hasMMParameters = true;
  }

  /**
   * Get the uᵀ_iw parameter.
   *
   * @return uᵀ_iw in K/K
   */
  public double getUT_iw() {
    return uT_iw;
  }

  /**
   * Set the uᵀ_iw parameter.
   *
   * @param uT_iw value in K/K
   */
  public void setUT_iw(double uT_iw) {
    this.uT_iw = uT_iw;
    this.hasMMParameters = true;
  }

  /**
   * Check if this component has Maribo-Mogensen parameters.
   *
   * @return true if MM parameters are available
   */
  public boolean hasMMParameters() {
    return hasMMParameters;
  }

  /**
   * Calculate the Born solvation contribution for this ion.
   *
   * <pre>
   * X_Born,i = z_i² / R_Born,i
   * </pre>
   *
   * @return Born contribution factor [1/Å]
   */
  public double getBornContribution() {
    if (bornRadius <= 0 || getIonicCharge() == 0) {
      return 0.0;
    }
    double z = getIonicCharge();
    return z * z / bornRadius;
  }

  // ==================== Electrolyte Contribution to Component Derivatives ====================

  /**
   * Check if phase is an electrolyte MM phase.
   *
   * @param phase the phase to check
   * @return the MM phase or null
   */
  private PhaseElectrolyteCPAMM getMMPhase(PhaseInterface phase) {
    if (phase instanceof PhaseElectrolyteCPAMM) {
      return (PhaseElectrolyteCPAMM) phase;
    }
    return null;
  }

  /**
   * Debye-Hückel contribution to dF/dN_i.
   *
   * <p>
   * For the Debye-Hückel term: F^DH = -κ³V / (12πRT·N_A)
   * </p>
   *
   * <p>
   * The full derivative at constant T, P is: dF^DH/dn_i = (∂F^DH/∂κ)(∂κ/∂n_i) + (∂F^DH/∂V)(∂V/∂n_i)
   * </p>
   *
   * <p>
   * For ions: ∂κ/∂n_i has contribution from z_i² term. For all components: adding moles changes V
   * which affects κ through the dilution effect.
   * </p>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF^DH/dn_i contribution
   */
  public double dFDebyeHuckeldN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double kappa = mmPhase.getKappa();
    if (kappa < 1e-30) {
      return 0.0;
    }

    double Vm = phase.getMolarVolume() * 1e-5; // m³/mol
    double nT = phase.getNumberOfMolesInPhase();
    double V = Vm * nT; // total volume in m³
    double z = getIonicCharge();
    double eps = mmPhase.getMixturePermittivity();

    // F^DH = -κ³V/(12πRT*N_A) (extensive form with R*T scaling)
    // ∂F^DH/∂κ = -3κ²V/(12πRT*N_A)
    // ∂F^DH/∂V = -κ³/(12πRT*N_A)
    double factor = 1.0 / (12.0 * Math.PI * R * temperature * N_A);
    double dFdkappa = -3.0 * kappa * kappa * V * factor;
    double dFdV = -kappa * kappa * kappa * factor;

    // κ² = (e²N_A)/(ε₀εk_BT) * (1/V) * Σ(n_j z_j²)
    // Let S = Σ(n_j z_j²), C = (e²N_A)/(ε₀εk_BT)
    // κ² = C * S / V
    // ∂κ²/∂n_i = C * z_i² / V - C * S * (∂V/∂n_i) / V²
    // At constant T, P: ∂V/∂n_i ≈ Vm (partial molar volume, using ideal approximation)
    double C = E_CHARGE * E_CHARGE * N_A / (EPSILON_0 * eps * K_B * temperature);

    // Calculate S = Σ(n_j z_j²)
    double S = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      double zj = phase.getComponent(j).getIonicCharge();
      if (zj != 0) {
        S += phase.getComponent(j).getNumberOfMolesInPhase() * zj * zj;
      }
    }

    // ∂κ²/∂n_i = C/V * (z_i² - S*Vm/V) = C/V * (z_i² - κ²V/(C*Vm) * Vm/V)
    // Simplify: ∂κ²/∂n_i = C*z_i²/V - κ²*Vm/V² = C*z_i²/V - κ²/(nT*Vm)
    double dkappa2dni = C * z * z / V - kappa * kappa * Vm / V;

    // ∂κ/∂n_i = ∂κ²/∂n_i / (2κ)
    double dkappaDni = dkappa2dni / (2.0 * kappa);

    // ∂V/∂n_i = Vm (at constant T, P, assuming ideal mixing for volume)
    double dVdni = Vm;

    // dF^DH/dn_i = ∂F/∂κ * ∂κ/∂n_i + ∂F/∂V * ∂V/∂n_i
    return dFdkappa * dkappaDni + dFdV * dVdni;
  }

  /**
   * Born contribution to dF/dN_i.
   *
   * <p>
   * For the extensive Born term: F^Born = (N_Ae²/8πε₀RT) * (1/ε - 1) * X_Born
   * </p>
   *
   * <p>
   * The complete derivative is: dF^Born/dn_i = (∂F/∂X_Born) × (∂X_Born/∂n_i) + (∂F/∂ε) × (∂ε/∂n_i)
   * </p>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF^Born/dn_i contribution
   */
  public double dFBorndN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double eps = mmPhase.getSolventPermittivity();
    if (eps < 1.0) {
      return 0.0;
    }

    // Term 1: ∂F/∂X_Born × ∂X_Born/∂n_i
    // dX_Born/dn_i = z_i² / R_Born,i for ions, 0 for solvents
    double z = getIonicCharge();
    double dXBorndni = 0.0;
    if (bornRadius > 0 && z != 0) {
      dXBorndni = z * z / (bornRadius * 1e-10); // Convert Å to m
    }
    double FBornXTerm = mmPhase.FBornX() * dXBorndni;

    // Term 2: ∂F/∂ε × ∂ε/∂n_i (dielectric change term)
    // ∂ε/∂n_i is non-zero for solvent components (changing solvent composition)
    double dEpsdni = mmPhase.calcSolventPermittivitydn(this.getComponentNumber(), temperature);
    double FBornDTerm = mmPhase.FBornD() * dEpsdni;

    // Total: both terms contribute for solvents (only FBornD term since dX_Born/dn_i = 0)
    // For ions: FBornX term + FBornD term (if dielectric changes with ion concentration)
    return FBornXTerm + FBornDTerm;
  }

  /**
   * Temperature derivative of Debye-Hückel contribution to dF/dN_i.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^DH/(dn_i dT) contribution
   */
  public double dFDebyeHuckeldNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double kappa = mmPhase.getKappa();
    if (kappa < 1e-30) {
      return 0.0;
    }

    // The main temperature dependence comes from 1/T and κ(T)
    // Approximate: d/dT[dF^DH/dn_i] ≈ -dF^DH/dn_i / T (dominant 1/T term)
    double dFDHdn = dFDebyeHuckeldN(phase, numberOfComponents, temperature, pressure);
    return -dFDHdn / temperature;
  }

  /**
   * Temperature derivative of Born contribution to dF/dN_i.
   *
   * <p>
   * The full derivative d²F^Born/(dn_i dT) includes temperature derivatives of both the FBornX term
   * and the FBornD term.
   * </p>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^Born/(dn_i dT) contribution
   */
  public double dFBorndNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double eps = mmPhase.getSolventPermittivity();
    double epsdT = mmPhase.getSolventPermittivitydT();
    if (eps < 1.0) {
      return 0.0;
    }

    double prefactor = N_A * E_CHARGE * E_CHARGE / (8.0 * Math.PI * EPSILON_0 * R);
    double solventTerm = 1.0 / eps - 1.0;
    double dSolventTermdT = -epsdT / (eps * eps);

    double z = getIonicCharge();
    double dXBorndni = 0.0;
    if (bornRadius > 0 && z != 0) {
      dXBorndni = z * z / (bornRadius * 1e-10);
    }

    // Term 1: d/dT of [FBornX * dX/dn_i]
    // FBornX = prefactor/T * (1/ε - 1)
    // d(FBornX)/dT = -prefactor/T² * (1/ε - 1) + prefactor/T * (-dε/dT/ε²)
    double term1 = -prefactor / (temperature * temperature) * solventTerm * dXBorndni;
    double term2 = prefactor / temperature * dSolventTermdT * dXBorndni;

    // Term 2: d/dT of [FBornD * dε/dn_i]
    // FBornD = -prefactor/T * X_Born / ε²
    // For simplicity, use product rule approximation
    double dEpsdni = mmPhase.calcSolventPermittivitydn(this.getComponentNumber(), temperature);
    double dEpsdnidT = mmPhase.calcSolventPermittivitydndT(this.getComponentNumber(), temperature);
    double bornX = mmPhase.getBornX();
    // d(FBornD)/dT = prefactor/T² * X_Born / ε² - prefactor/T * X_Born * (-2/ε³) * dε/dT
    double dFBornDdT = prefactor / (temperature * temperature) * bornX / (eps * eps)
        + prefactor / temperature * bornX * 2.0 * epsdT / (eps * eps * eps);
    double term3 = dFBornDdT * dEpsdni + mmPhase.FBornD() * dEpsdnidT;

    return term1 + term2 + term3;
  }

  /**
   * Volume derivative of Debye-Hückel contribution to dF/dN_i.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^DH/(dn_i dV) contribution
   */
  public double dFDebyeHuckeldNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double kappa = mmPhase.getKappa();
    if (kappa < 1e-30) {
      return 0.0;
    }

    double Vm = phase.getMolarVolume() * 1e-5;
    double nT = phase.getNumberOfMolesInPhase();
    double V = Vm * nT;
    double z = getIonicCharge();
    double eps = mmPhase.getMixturePermittivity();

    // κ² = C * S / V, so dκ²/dV = -C * S / V² = -κ²/V
    // dκ/dV = -κ/(2V)
    double dkappadV = -kappa / (2.0 * V);

    // dκ/dn_i = C * z_i² / (2κV)
    double C = E_CHARGE * E_CHARGE * N_A / (EPSILON_0 * eps * K_B * temperature);
    double dkappaDni = (z != 0 && kappa > 1e-30) ? C * z * z / (2.0 * kappa * V) : 0.0;

    // d²κ/(dn_i dV) = d/dV[C * z_i² / (2κV)]
    double d2kappaDniDV = 0.0;
    if (z != 0 && kappa > 1e-30) {
      // = -C*z²/(2κV²) - C*z²*dκ/dV/(2κ²V) = -C*z²/(2κV²) + C*z²/(4κ²V²)
      d2kappaDniDV = -C * z * z / (2.0 * kappa * V * V) + C * z * z / (4.0 * kappa * kappa * V * V);
    }

    // F^DH = -κ³V/(12πRT*N_A) (extensive)
    // dF/dn_i = -3κ²V/(12πRT*N_A) * dκ/dn_i
    // d²F/(dn_i dV) = -3/(12πRT*N_A) * [2κ*dκ/dV*V*dκ/dn_i + κ²*dκ/dn_i + κ²*V*d²κ/(dn_i dV)]
    double factor = 1.0 / (12.0 * Math.PI * R * temperature * N_A);

    double term1 = -3.0 * 2.0 * kappa * dkappadV * V * factor * dkappaDni;
    double term2 = -3.0 * kappa * kappa * factor * dkappaDni;
    double term3 = -3.0 * kappa * kappa * V * factor * d2kappaDniDV;

    return 1e-5 * (term1 + term2 + term3); // Scale factor for V in m³
  }

  /**
   * Volume derivative of Born contribution to dF/dN_i.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^Born/(dn_i dV) contribution
   */
  public double dFBorndNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    // Born term has no direct volume dependence in the MM formulation
    return 0.0;
  }

  /**
   * Composition derivative of Debye-Hückel contribution to dF/dN_i.
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^DH/(dn_i dn_j) contribution
   */
  public double dFDebyeHuckeldNdN(int j, PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double kappa = mmPhase.getKappa();
    if (kappa < 1e-30) {
      return 0.0;
    }

    double Vm = phase.getMolarVolume() * 1e-5;
    double nT = phase.getNumberOfMolesInPhase();
    double V = Vm * nT;
    double zi = getIonicCharge();
    double zj = phase.getComponent(j).getIonicCharge();
    double eps = mmPhase.getMixturePermittivity();

    double C = E_CHARGE * E_CHARGE * N_A / (EPSILON_0 * eps * K_B * temperature);

    // dκ/dn_i and dκ/dn_j
    double dkappaDni = (zi != 0 && kappa > 1e-30) ? C * zi * zi / (2.0 * kappa * V) : 0.0;
    double dkappaDnj = (zj != 0 && kappa > 1e-30) ? C * zj * zj / (2.0 * kappa * V) : 0.0;

    // d²κ/(dn_i dn_j) = -C * z_i² * dκ/dn_j / (2κ²V)
    double d2kappaDniDnj = 0.0;
    if (zi != 0 && kappa > 1e-30) {
      d2kappaDniDnj = -C * zi * zi * dkappaDnj / (2.0 * kappa * kappa * V);
    }

    // F^DH = -κ³V/(12πRT*N_A) (extensive form)
    // dF/dn_i = -3κ²V/(12πRT*N_A) * dκ/dn_i
    // d²F/(dn_i dn_j) = -V/(12πRT*N_A) * (6κ * dκ/dn_i * dκ/dn_j + 3κ² * d²κ/(dn_i dn_j))
    double factor = V / (12.0 * Math.PI * R * temperature * N_A);

    return -factor * (6.0 * kappa * dkappaDni * dkappaDnj + 3.0 * kappa * kappa * d2kappaDniDnj);
  }

  /**
   * Composition derivative of Born contribution to dF/dN_i.
   *
   * <p>
   * d²F^Born/(dn_i dn_j) includes cross derivatives of FBornX and FBornD terms.
   * </p>
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^Born/(dn_i dn_j) contribution
   */
  public double dFBorndNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null) {
      return 0.0;
    }

    double eps = mmPhase.getSolventPermittivity();
    if (eps < 1.0) {
      return 0.0;
    }

    // dF/dn_i = FBornX * dXBorn/dn_i + FBornD * dε/dn_i
    // d²F/(dn_i dn_j) includes:
    // 1. FBornDD * dε/dn_i * dε/dn_j (from FBornD depending on ε)
    // 2. FBornDX * dXBorn/dn_i * dε/dn_j (cross term)
    // 3. FBornDX * dε/dn_i * dXBorn/dn_j (cross term)
    // 4. FBornD * d²ε/(dn_i dn_j) (from dε/dn_i depending on n_j)
    // Note: d²XBorn/(dn_i dn_j) = 0 since dXBorn/dn_i = z_i²/r_i is independent of n_j

    double dXBorndni = 0.0;
    double z = getIonicCharge();
    if (bornRadius > 0 && z != 0) {
      dXBorndni = z * z / (bornRadius * 1e-10);
    }

    double dXBorndnj = 0.0;
    ComponentSrkCPAMM compJ = null;
    if (phase.getComponent(j) instanceof ComponentSrkCPAMM) {
      compJ = (ComponentSrkCPAMM) phase.getComponent(j);
      double zj = compJ.getIonicCharge();
      double rj = compJ.getBornRadius();
      if (rj > 0 && zj != 0) {
        dXBorndnj = zj * zj / (rj * 1e-10);
      }
    }

    double dEpsdni = mmPhase.calcSolventPermittivitydn(this.getComponentNumber(), temperature);
    double dEpsdnj = mmPhase.calcSolventPermittivitydn(j, temperature);

    // Second derivative of dielectric (assuming molar average: d²ε/(dn_i dn_j) exists)
    double d2Epsdnidnj =
        mmPhase.calcSolventPermittivitydndn(this.getComponentNumber(), j, temperature);

    double term1 = mmPhase.FBornDD() * dEpsdni * dEpsdnj;
    double term2 = mmPhase.FBornDX() * dXBorndni * dEpsdnj;
    double term3 = mmPhase.FBornDX() * dEpsdni * dXBorndnj;
    double term4 = mmPhase.FBornD() * d2Epsdnidnj;

    return term1 + term2 + term3 + term4;
  }

  // ==================== Short-Range Ion-Solvent Methods ====================

  /**
   * Short-range ion-solvent contribution to dF/dN_i.
   *
   * <p>
   * F^SR = W / (R * T) where W = Σ_i Σ_j (n_i * n_j * wij * R / V)
   * </p>
   *
   * <p>
   * For component k: dF^SR/dn_k = Σ_j (n_j * wij[k][j] + n_j * wij[j][k]) / (T * V) The wij mixing
   * rule parameters are obtained from the phase's initMixingRuleWij().
   * </p>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF^SR/dn_i contribution
   */
  public double dFShortRangedN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }

    // New formula: F^SR = W / (nT * T * (1-η))
    // dF/dn_k = (1/(nT*T*(1-η))) * [2*Σ_j(n_j*w_kj) - W/nT + W/(1-η) * dη/dn_k]
    double nT = phase.getNumberOfMolesInPhase();
    if (nT < 1e-30) {
      return 0.0;
    }

    double eta = mmPhase.getPackingFraction();
    double oneMinusEta = 1.0 - eta;
    if (oneMinusEta < 1e-10) {
      oneMinusEta = 1e-10;
    }

    int k = getComponentNumber();

    // Calculate W = Σ_i Σ_j n_i n_j w_ij
    double W = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double ni = phase.getComponent(i).getNumberOfMolesInPhase();
      for (int j = 0; j < numberOfComponents; j++) {
        double nj = phase.getComponent(j).getNumberOfMolesInPhase();
        W += ni * nj * mmPhase.getWij(i, j);
      }
    }

    // Calculate dW/dn_k = 2 * Σ_j n_j w_kj
    double dWdnk = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      double nj = phase.getComponent(j).getNumberOfMolesInPhase();
      dWdnk += nj * mmPhase.getWij(k, j);
    }
    dWdnk *= 2.0;

    // Calculate dη/dn_k = (πN_A/6V) * σ_k³
    double sigma = getLennardJonesMolecularDiameter() * 1e-10; // convert Å to m
    double V = phase.getMolarVolume() * 1e-5 * nT; // m³
    double dEtadnk =
        (Math.PI * neqsim.thermo.ThermodynamicConstantsInterface.avagadroNumber / (6 * V)) * sigma
            * sigma * sigma;

    double prefactor = 1.0 / (nT * temperature * oneMinusEta);

    // dF/dn_k = prefactor * [dW/dn_k - W/nT + W * dη/dn_k / (1-η)]
    double result = prefactor * (dWdnk - W / nT + W * dEtadnk / oneMinusEta);

    return result;
  }

  /**
   * Temperature derivative of short-range contribution to dF/dN_i using wij mixing rule.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^SR/(dn_i dT) contribution
   */
  public double dFShortRangedNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }

    double Vm = phase.getMolarVolume() * 1e-5; // m³/mol
    double nT = phase.getNumberOfMolesInPhase();
    double V = Vm * nT;
    if (V < 1e-30) {
      return 0.0;
    }

    int k = getComponentNumber();
    double sum = 0.0;

    // Sum over all components using wij/wijT mixing rule parameters
    for (int j = 0; j < numberOfComponents; j++) {
      double nj = phase.getComponent(j).getNumberOfMolesInPhase();
      double wkj = mmPhase.getWij(k, j);
      double wjk = mmPhase.getWij(j, k);
      double wkjT = mmPhase.getWijT(k, j);
      double wjkT = mmPhase.getWijT(j, k);
      if (wkj != 0.0 || wjk != 0.0 || wkjT != 0.0 || wjkT != 0.0) {
        // d/dT[w/(TV)] = wT/(TV) - w/(T²V)
        double w = wkj + wjk;
        double wT = wkjT + wjkT;
        sum += nj * (wT / (temperature * V) - w / (temperature * temperature * V));
      }
    }

    return sum;
  }

  /**
   * Volume derivative of short-range contribution to dF/dN_i using wij mixing rule.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^SR/(dn_i dV) contribution
   */
  public double dFShortRangedNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }

    double Vm = phase.getMolarVolume() * 1e-5; // m³/mol
    double nT = phase.getNumberOfMolesInPhase();
    double V = Vm * nT;
    if (V < 1e-30) {
      return 0.0;
    }

    int k = getComponentNumber();
    double sum = 0.0;

    // Sum over all components using wij mixing rule parameters
    for (int j = 0; j < numberOfComponents; j++) {
      double nj = phase.getComponent(j).getNumberOfMolesInPhase();
      double wkj = mmPhase.getWij(k, j);
      double wjk = mmPhase.getWij(j, k);
      if (wkj != 0.0 || wjk != 0.0) {
        // d/dV[w/(TV)] = -w/(TV²)
        sum += nj * (-(wkj + wjk) / (temperature * V * V)) * 1e-5;
      }
    }

    return sum;
  }

  /**
   * Composition derivative of short-range contribution to dF/dN_i using wij mixing rule.
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^SR/(dn_i dn_j) contribution
   */
  public double dFShortRangedNdN(int j, PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }

    double Vm = phase.getMolarVolume() * 1e-5; // m³/mol
    double nT = phase.getNumberOfMolesInPhase();
    double V = Vm * nT;
    if (V < 1e-30) {
      return 0.0;
    }

    int k = getComponentNumber();
    // d²F/(dn_k dn_j) = (wij[k][j] + wij[j][k]) / (T * V)
    double wkj = mmPhase.getWij(k, j);
    double wjk = mmPhase.getWij(j, k);
    if (wkj == 0.0 && wjk == 0.0) {
      return 0.0;
    }

    return (wkj + wjk) / (temperature * V);
  }

  // ==================== Override dFdN Methods ====================

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double dFdN = super.dFdN(phase, numberOfComponents, temperature, pressure);

    // Add electrolyte contributions
    dFdN += dFDebyeHuckeldN(phase, numberOfComponents, temperature, pressure);
    dFdN += dFBorndN(phase, numberOfComponents, temperature, pressure);
    dFdN += dFShortRangedN(phase, numberOfComponents, temperature, pressure);

    return dFdN;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double dFdNdT = super.dFdNdT(phase, numberOfComponents, temperature, pressure);

    // Add electrolyte contributions
    dFdNdT += dFDebyeHuckeldNdT(phase, numberOfComponents, temperature, pressure);
    dFdNdT += dFBorndNdT(phase, numberOfComponents, temperature, pressure);
    dFdNdT += dFShortRangedNdT(phase, numberOfComponents, temperature, pressure);

    return dFdNdT;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double dFdNdV = super.dFdNdV(phase, numberOfComponents, temperature, pressure);

    // Add electrolyte contributions
    dFdNdV += dFDebyeHuckeldNdV(phase, numberOfComponents, temperature, pressure);
    dFdNdV += dFBorndNdV(phase, numberOfComponents, temperature, pressure);
    dFdNdV += dFShortRangedNdV(phase, numberOfComponents, temperature, pressure);

    return dFdNdV;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double dFdNdN = super.dFdNdN(j, phase, numberOfComponents, temperature, pressure);

    // Add electrolyte contributions
    dFdNdN += dFDebyeHuckeldNdN(j, phase, numberOfComponents, temperature, pressure);
    dFdNdN += dFBorndNdN(j, phase, numberOfComponents, temperature, pressure);
    dFdNdN += dFShortRangedNdN(j, phase, numberOfComponents, temperature, pressure);

    return dFdNdN;
  }
}
