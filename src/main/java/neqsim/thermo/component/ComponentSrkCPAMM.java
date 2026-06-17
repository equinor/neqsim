/*
 * ComponentSrkCPAMM.java
 *
 * Component class for the Maribo-Mogensen electrolyte CPA model.
 */

package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseCPAInterface;
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

      // Ions do not participate in hydrogen-bond association; disable CPA for ions.
      // The CPA association equilibrium should only involve self-associating molecules
      // (water, alcohols). Without this, adding ions reduces the water mole fraction,
      // which changes water's association equilibrium and creates an unphysical penalty
      // of ~-1.5 in ln(gamma_pm) at 1 molal.
      this.cpaon = 0;

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

  /**
   * Stored log fugacity coefficient to avoid exp/log round-trip precision loss. For electrolyte
   * systems, dF/dN can be O(-800) for divalent ions, making Math.exp() underflow to 0 and
   * Math.log(0) return -Infinity. This field stores the raw value directly.
   */
  private double storedLogFugCoeff = 0.0;

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    storedLogFugCoeff = dFdN(phase, phase.getNumberOfComponents(), temperature, pressure) - Math
        .log(pressure * phase.getMolarVolume() / (ThermodynamicConstantsInterface.R * temperature));
    fugacityCoefficient = Math.exp(storedLogFugCoeff);
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getLogFugacityCoefficient() {
    return storedLogFugCoeff;
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
    if (mmPhase == null || !mmPhase.isDebyeHuckelOn()) {
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

    // Extended DH screening factor and correction
    double tauDH = mmPhase.getTauDH();
    double tauDHp = mmPhase.getTauDHprime();
    double dMean = mmPhase.getMeanIonDiameter();

    // DHLL partials: F = -κ³V/(12πN_A)
    // ∂F/∂κ|_V = -3κ²V/(12πN_A)
    // ∂F/∂V|_κ = -κ³/(12πN_A)
    double factor = 1.0 / (12.0 * Math.PI * N_A);
    double dFdkappa_DHLL = -3.0 * kappa * kappa * V * factor;
    double dFdV_DHLL = -kappa * kappa * kappa * factor;
    double F_DHLL = -kappa * kappa * kappa * V * factor;

    // Extended DH partials:
    // ∂F_ext/∂κ|_V = ∂F_DHLL/∂κ × τ + F_DHLL × τ'(χ) × d
    // ∂F_ext/∂V|_κ = ∂F_DHLL/∂V × τ
    double dFdkappa = dFdkappa_DHLL * tauDH + F_DHLL * tauDHp * dMean;
    double dFdV = dFdV_DHLL * tauDH;

    // κ² = C × S / V where C = e²N_A/(ε₀εk_BT), S = Σ(n_j z_j²)
    double C = E_CHARGE * E_CHARGE * N_A / (EPSILON_0 * eps * K_B * temperature);

    double S = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      double zj = phase.getComponent(j).getIonicCharge();
      if (zj != 0) {
        S += phase.getComponent(j).getNumberOfMolesInPhase() * zj * zj;
      }
    }

    // ∂κ²/∂n_i = C z_i²/V - κ² Vm/V
    double dkappa2dni = C * z * z / V - kappa * kappa * Vm / V;
    double dkappaDni = dkappa2dni / (2.0 * kappa);

    // ∂V/∂n_i = Vm
    double dVdni = Vm;

    // dF^DH_ext/dn_i = ∂F_ext/∂κ × ∂κ/∂n_i + ∂F_ext/∂V × ∂V/∂n_i
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
    if (mmPhase == null || !mmPhase.isBornOn()) {
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
    if (mmPhase == null || !mmPhase.isDebyeHuckelOn()) {
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
    if (mmPhase == null || !mmPhase.isBornOn()) {
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
    if (mmPhase == null || !mmPhase.isDebyeHuckelOn()) {
      return 0.0;
    }

    double kappa = mmPhase.getKappa();
    if (kappa < 1e-30) {
      return 0.0;
    }

    double tauDH = mmPhase.getTauDH();

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
      d2kappaDniDV = -C * z * z / (2.0 * kappa * V * V) + C * z * z / (4.0 * kappa * kappa * V * V);
    }

    // Extended DH: multiply DHLL terms by tauDH
    double factor = 1.0 / (12.0 * Math.PI * R * temperature * N_A);

    double term1 = -3.0 * 2.0 * kappa * dkappadV * V * factor * dkappaDni;
    double term2 = -3.0 * kappa * kappa * factor * dkappaDni;
    double term3 = -3.0 * kappa * kappa * V * factor * d2kappaDniDV;

    return 1e-5 * (term1 + term2 + term3) * tauDH;
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
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isBornOn()) {
    }
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
    if (mmPhase == null || !mmPhase.isDebyeHuckelOn()) {
      return 0.0;
    }

    double kappa = mmPhase.getKappa();
    if (kappa < 1e-30) {
      return 0.0;
    }

    double tauDH = mmPhase.getTauDH();

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

    // Extended DH: multiply DHLL terms by tauDH
    double factor = V / (12.0 * Math.PI * R * temperature * N_A);

    return -factor * (6.0 * kappa * dkappaDni * dkappaDnj + 3.0 * kappa * kappa * d2kappaDniDnj)
        * tauDH;
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
    if (mmPhase == null || !mmPhase.isBornOn()) {
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

  // ==================== Short-Range Ion-Solvent Methods (Furst FSR2 Framework)
  // ====================

  /** Wi = dW/dn_i from the electrolyte mixing rule. */
  private double srWi = 0.0;
  /** WiT = d²W/(dn_i dT) from the electrolyte mixing rule. */
  private double srWiT = 0.0;
  /** epsi = d(eps)/dn_i = packing fraction composition derivative. */
  private double srEpsi = 0.0;
  /** epsiV = d²(eps)/(dn_i dV). */
  private double srEpsiV = 0.0;

  /**
   * Initialize short-range parameters for this component from the electrolyte mixing rule.
   *
   * @param phase the MM phase
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @param numberOfComponents number of components
   */
  private void initSRParameters(PhaseElectrolyteCPAMM phase, double temperature, double pressure,
      int numberOfComponents) {
    if (!phase.isShortRangeOn()) {
      return;
    }
    srWi = phase.calcWi(getComponentNumber(), temperature, pressure, numberOfComponents);
    srWiT = phase.calcWiT(getComponentNumber(), temperature, pressure, numberOfComponents);

    // epsi = dEps/dn_i = N_A * pi/6 * sigma_i³ / V
    double V = phase.getMolarVolume() * 1e-5 * phase.getNumberOfMolesInPhase();
    if (V > 1e-30) {
      double sigma = getLennardJonesMolecularDiameter() * 1e-10; // m
      srEpsi = N_A * Math.PI / 6.0 * sigma * sigma * sigma / V;
      srEpsiV = -srEpsi / V;
    }
  }

  /**
   * Short-range ion-solvent contribution to dF/dN_i using the Furst FSR2 framework.
   *
   * <pre>
   * dFSR2/dn_i = FSR2eps * epsi + FSR2W * Wi
   * </pre>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF_SR/dn_i contribution
   */
  public double dFShortRangedN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }
    // MM formula: F_SR = W / (nT * T * (1-η))
    // dF_SR/dn_i = Wi / (nT * T * (1-η)) - F_SR / nT
    // ≈ Wi / (nT * T) - F_SR / nT (η correction negligible for dilute)
    double nT = mmPhase.getNumberOfMolesInPhase();
    if (nT < 1e-30) {
      return 0.0;
    }
    double Wi = mmPhase.calcMMWi(getComponentNumber());
    double fSR = mmPhase.FShortRange();
    double oneMinusEps = Math.max(1.0 - mmPhase.getPackingFraction(), 0.01);
    return Wi / (nT * temperature * oneMinusEps) - fSR / nT;
  }

  /**
   * Temperature derivative of MM short-range contribution to dF/dN_i.
   *
   * <p>
   * d²F_SR/(dn_i dT) = WiT/(nT*T*(1-η)) - Wi/(nT*T²*(1-η)) - dFSRdT/nT
   * </p>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F_SR/(dn_i dT) contribution
   */
  public double dFShortRangedNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }
    double nT = mmPhase.getNumberOfMolesInPhase();
    if (nT < 1e-30) {
      return 0.0;
    }
    double Wi = mmPhase.calcMMWi(getComponentNumber());
    double WiT = mmPhase.calcMMWiT(getComponentNumber());
    double oneMinusEps = Math.max(1.0 - mmPhase.getPackingFraction(), 0.01);
    double denom = nT * temperature * oneMinusEps;
    return (WiT - Wi / temperature) / denom - mmPhase.dFShortRangedT() / nT;
  }

  /**
   * Volume derivative of MM short-range contribution to dF/dN_i. Approximately zero for dilute
   * electrolyte solutions where the packing fraction η is small.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F_SR/(dn_i dV) contribution
   */
  public double dFShortRangedNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
    }
    // For dilute solutions, η << 1 and the V-dependence is negligible
    return 0.0;
  }

  /**
   * Composition derivative of MM short-range contribution to dF/dN_i.
   *
   * <p>
   * d²F_SR/(dn_i dn_j) = 2*wij[i][j]/(nT*T*(1-η)) - (Wi + Wj)/(nT²*T*(1-η)) + 2*F_SR/nT²
   * </p>
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F_SR/(dn_i dn_j) contribution
   */
  public double dFShortRangedNdN(int j, PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    if (mmPhase == null || !mmPhase.isShortRangeOn()) {
      return 0.0;
    }
    double nT = mmPhase.getNumberOfMolesInPhase();
    if (nT < 1e-30) {
      return 0.0;
    }
    double Wi = mmPhase.calcMMWi(getComponentNumber());
    double Wj = mmPhase.calcMMWi(j);
    double wijIJ = mmPhase.getWij(getComponentNumber(), j);
    double fSR = mmPhase.FShortRange();
    double oneMinusEps = Math.max(1.0 - mmPhase.getPackingFraction(), 0.01);
    double nT2 = nT * nT;
    double denom = nT * temperature * oneMinusEps;
    return 2.0 * wijIJ / denom - (Wi + Wj) / (nT2 * temperature * oneMinusEps) + 2.0 * fSR / nT2;
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

    // For ions (cpaon=0), the parent class still adds CPA to dFdNdT — remove it
    if (cpaon == 0 && ((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      dFdNdT -= dFCPAdNdT(phase, numberOfComponents, temperature, pressure);
    }

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

    // For ions (cpaon=0), the parent class still adds CPA to dFdNdV — remove it
    if (cpaon == 0 && ((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      dFdNdV -= dFCPAdNdV(phase, numberOfComponents, temperature, pressure);
    }

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

    // For ions (cpaon=0), the parent class still adds CPA to dFdNdN — remove it
    if (cpaon == 0 && ((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      dFdNdN -= dFCPAdNdN(j, phase, numberOfComponents, temperature, pressure);
    }

    // Add electrolyte contributions
    dFdNdN += dFDebyeHuckeldNdN(j, phase, numberOfComponents, temperature, pressure);
    dFdNdN += dFBorndNdN(j, phase, numberOfComponents, temperature, pressure);
    dFdNdN += dFShortRangedNdN(j, phase, numberOfComponents, temperature, pressure);

    return dFdNdN;
  }
}
