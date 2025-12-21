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
   * For the Debye-Hückel term: F^DH = -κ³V_m / (12πRT)
   * </p>
   *
   * <p>
   * Taking the derivative with respect to n_i: dF^DH/dn_i = ∂F^DH/∂κ · ∂κ/∂n_i + ∂F^DH/∂V_m ·
   * ∂V_m/∂n_i + ∂F^DH/∂n_T
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
    double kappa3 = kappa * kappa * kappa;
    double z = getIonicCharge();
    double eps = mmPhase.getMixturePermittivity();

    // Calculate dκ/dn_i
    // κ² = (e²N_A)/(ε₀εk_BTV) * Σ(n_j z_j²)
    // Let S = Σ(n_j z_j²), then κ² = C * S / V where C = e²N_A/(ε₀εk_BT)
    // dκ²/dn_i = C * z_i² / V for ions, = 0 for solvents
    // dκ/dn_i = dκ²/dn_i / (2κ)
    double C = E_CHARGE * E_CHARGE * N_A / (EPSILON_0 * eps * K_B * temperature);
    double V = Vm * nT; // total volume in m³
    double dkappa2dni = C * z * z / V;
    double dkappaDni = (kappa > 1e-30) ? dkappa2dni / (2.0 * kappa) : 0.0;

    // F^DH = -κ³V_m/(12πRT*N_A)
    // = -κ³V/(12πRT*N_A*n_T)
    // dF^DH/dn_i = -V/(12πRT*N_A*n_T) * 3κ² * dκ/dn_i + κ³V/(12πRT*N_A*n_T²)
    // = -3κ²V/(12πRT*N_A*n_T) * dκ/dn_i + κ³V/(12πRT*N_A*n_T²)
    double factor = 1.0 / (12.0 * Math.PI * R * temperature * N_A);
    double term1 = -3.0 * kappa * kappa * V * factor / nT * dkappaDni;
    double term2 = kappa3 * V * factor / (nT * nT);

    return term1 + term2;
  }

  /**
   * Born contribution to dF/dN_i.
   *
   * <p>
   * For the Born term: F^Born = (N_Ae²/8πε₀RT) * (1/ε - 1) * X_Born / n_T
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

    double eps = mmPhase.getMixturePermittivity();
    if (eps < 1.0) {
      return 0.0;
    }

    double nT = phase.getNumberOfMolesInPhase();
    double prefactor = N_A * E_CHARGE * E_CHARGE / (8.0 * Math.PI * EPSILON_0 * R * temperature);
    double solventTerm = 1.0 / eps - 1.0;

    // X_Born = Σ(n_j * z_j² / R_Born,j)
    // dX_Born/dn_i = z_i² / R_Born,i for ions
    double z = getIonicCharge();
    double dXBorndni = 0.0;
    if (bornRadius > 0 && z != 0) {
      dXBorndni = z * z / (bornRadius * 1e-10); // Convert Å to m
    }

    // F^Born = prefactor * solventTerm * X_Born / nT
    // dF^Born/dn_i = prefactor * solventTerm * (dX_Born/dn_i / nT - X_Born / nT²)
    double XBorn = mmPhase.getBornX();
    return prefactor * solventTerm * (dXBorndni / nT - XBorn / (nT * nT));
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

    double nT = phase.getNumberOfMolesInPhase();
    double prefactor = N_A * E_CHARGE * E_CHARGE / (8.0 * Math.PI * EPSILON_0 * R);
    double solventTerm = 1.0 / eps - 1.0;
    double dSolventTermdT = -epsdT / (eps * eps);

    double z = getIonicCharge();
    double dXBorndni = 0.0;
    if (bornRadius > 0 && z != 0) {
      dXBorndni = z * z / (bornRadius * 1e-10);
    }
    double XBorn = mmPhase.getBornX();

    // F^Born_n = prefactor/T * solventTerm * (dX/dn / nT - X / nT²)
    // dF^Born_n/dT = -prefactor/T² * solventTerm * (...) + prefactor/T * dSolventTerm/dT * (...)
    double bracket = dXBorndni / nT - XBorn / (nT * nT);
    double term1 = -prefactor / (temperature * temperature) * solventTerm * bracket;
    double term2 = prefactor / temperature * dSolventTermdT * bracket;
    return term1 + term2;
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
    double kappa3 = kappa * kappa * kappa;
    double z = getIonicCharge();
    double eps = mmPhase.getMixturePermittivity();

    // κ² = C * S / V, so dκ²/dV = -C * S / V² = -κ²/V
    // dκ/dV = -κ/(2V)
    double dkappadV = -kappa / (2.0 * V);

    // dκ/dn_i = C * z_i² / (2κV)
    double C = E_CHARGE * E_CHARGE * N_A / (EPSILON_0 * eps * K_B * temperature);
    double dkappaDni = (z != 0 && kappa > 1e-30) ? C * z * z / (2.0 * kappa * V) : 0.0;

    // d²κ/(dn_i dV) = -C * z_i² / (2κV²) + C * z_i² * dκ/dV / (2κ²V)
    double d2kappaDniDV = 0.0;
    if (z != 0 && kappa > 1e-30) {
      d2kappaDniDV =
          -C * z * z / (2.0 * kappa * V * V) + C * z * z * dkappadV / (2.0 * kappa * kappa * V);
    }

    // F^DH = -κ³V/(12πRT*N_A*n_T)
    // dF/dn_i involves κ, dκ/dn_i, V, n_T
    // d²F/(dn_i dV) is complex; use chain rule approximation
    double factor = 1.0 / (12.0 * Math.PI * R * temperature * N_A * nT);

    // Term from dκ³/dV * dκ/dn_i
    double term1 = -3.0 * kappa * kappa * dkappadV * factor * V * dkappaDni;
    // Term from d(dκ/dn_i)/dV
    double term2 = -3.0 * kappa * kappa * factor * (dkappaDni + V * d2kappaDniDV);
    // Term from dV/dV = 1 in κ³V term
    double term3 = -3.0 * kappa * kappa * dkappaDni * factor;

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
    double kappa3 = kappa * kappa * kappa;
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

    // F^DH = -κ³V/(12πRT*N_A*n_T)
    // dF/dn_i = -3κ²V/(12πRT*N_A*n_T) * dκ/dn_i + κ³V/(12πRT*N_A*n_T²)
    // d²F/(dn_i dn_j) needs chain rule
    double factor = V / (12.0 * Math.PI * R * temperature * N_A);

    // Term 1: derivative of -3κ²/n_T * dκ/dn_i with respect to n_j
    double term1 =
        -factor / nT * (6.0 * kappa * dkappaDnj * dkappaDni + 3.0 * kappa * kappa * d2kappaDniDnj);
    // Term 2: derivative of -3κ²/n_T * dκ/dn_i w.r.t n_T (from n_j)
    double term2 = factor / (nT * nT) * 3.0 * kappa * kappa * dkappaDni;
    // Term 3: derivative of κ³/n_T² w.r.t κ
    double term3 = factor / (nT * nT) * 3.0 * kappa * kappa * dkappaDnj;
    // Term 4: derivative of κ³/n_T² w.r.t n_T
    double term4 = -factor / (nT * nT * nT) * 2.0 * kappa3;

    return term1 + term2 + term3 + term4;
  }

  /**
   * Composition derivative of Born contribution to dF/dN_i.
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

    double eps = mmPhase.getMixturePermittivity();
    if (eps < 1.0) {
      return 0.0;
    }

    double nT = phase.getNumberOfMolesInPhase();
    double prefactor = N_A * E_CHARGE * E_CHARGE / (8.0 * Math.PI * EPSILON_0 * R * temperature);
    double solventTerm = 1.0 / eps - 1.0;

    double zi = getIonicCharge();
    double dXBorndni = 0.0;
    if (bornRadius > 0 && zi != 0) {
      dXBorndni = zi * zi / (bornRadius * 1e-10);
    }

    ComponentSrkCPAMM compJ = null;
    if (phase.getComponent(j) instanceof ComponentSrkCPAMM) {
      compJ = (ComponentSrkCPAMM) phase.getComponent(j);
    }
    double zj = phase.getComponent(j).getIonicCharge();
    double dXBorndnj = 0.0;
    if (compJ != null && compJ.getBornRadius() > 0 && zj != 0) {
      dXBorndnj = zj * zj / (compJ.getBornRadiusMeters());
    }

    double XBorn = mmPhase.getBornX();

    // F^Born_n = prefactor * solventTerm * (dX/dn_i / nT - X / nT²)
    // d/dn_j [F^Born_n] = prefactor * solventTerm * (-dX/dn_i / nT² + dX/dn_j / nT² + 2X / nT³)
    // = prefactor * solventTerm * [(-dXBorndni + dXBorndnj)/nT² + 2XBorn/nT³]
    return prefactor * solventTerm
        * ((-dXBorndni + dXBorndnj) / (nT * nT) + 2.0 * XBorn / (nT * nT * nT));
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

    return dFdNdN;
  }
}
