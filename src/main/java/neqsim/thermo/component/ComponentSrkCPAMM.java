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
  public ComponentSrkCPAMM(String name, double moles, double molesInPhase, int compIndex, PhaseInterface phase) {
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

      // Zero association sites prevent ion association. The CPA covolume derivative
      // must remain enabled because ions displace the associating solvent.
      this.cpaon = 1;

      // Override Lennard-Jones diameter if we have better data
      if (ionData.sigma > 0) {
        this.setLennardJonesMolecularDiameter(ionData.sigma);

        // Calculate ion b parameter from ionic diameter (same approach as Furst)
        // b = α × σ³ + β, using FurstElectrolyteConstants for CPA
        double sigma = ionData.sigma;
        double ionB = (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParamsCPA[0] * Math.pow(sigma, 3.0)
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
   * Stored log fugacity coefficient to avoid exp/log round-trip precision loss. For electrolyte systems, dF/dN can be
   * O(-800) for divalent ions, making Math.exp() underflow to 0 and Math.log(0) return -Infinity. This field stores the
   * raw value directly.
   */
  private double storedLogFugCoeff = 0.0;

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    storedLogFugCoeff = dFdN(phase, phase.getNumberOfComponents(), temperature, pressure)
        - Math.log(pressure * phase.getMolarVolume() / (ThermodynamicConstantsInterface.R * temperature));
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
   * Exact dFDebyeHuckeldN derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF^DH/dn_i contribution
   */
  public double dFDebyeHuckeldN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 0, getComponentNumber(), -1, -1);
  }

  /**
   * Exact dFBorndN derivative at fixed remaining extensive variables. Includes the composition, volume and temperature
   * dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF^Born/dn_i contribution
   */
  public double dFBorndN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 1, getComponentNumber(), -1, -1);
  }

  /**
   * Exact dFDebyeHuckeldNdT derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^DH/(dn_i dT) contribution
   */
  public double dFDebyeHuckeldNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 0, getComponentNumber(), numberOfComponents + 1, -1);
  }

  /**
   * Exact dFBorndNdT derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^Born/(dn_i dT) contribution
   */
  public double dFBorndNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 1, getComponentNumber(), numberOfComponents + 1, -1);
  }

  /**
   * Exact dFDebyeHuckeldNdV derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^DH/(dn_i dV) contribution
   */
  public double dFDebyeHuckeldNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 0, getComponentNumber(), numberOfComponents, -1);
  }

  /**
   * Exact dFBorndNdV derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^Born/(dn_i dV) contribution
   */
  public double dFBorndNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 1, getComponentNumber(), numberOfComponents, -1);
  }

  /**
   * Exact dFDebyeHuckeldNdN derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^DH/(dn_i dn_j) contribution
   */
  public double dFDebyeHuckeldNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return electrolyteDerivative(phase, 0, getComponentNumber(), j, -1);
  }

  /**
   * Exact dFBorndNdN derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F^Born/(dn_i dn_j) contribution
   */
  public double dFBorndNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 1, getComponentNumber(), j, -1);
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
   * Exact dFShortRangedN derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return dF_SR/dn_i contribution
   */
  public double dFShortRangedN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 2, getComponentNumber(), -1, -1);
  }

  /**
   * Exact dFShortRangedNdT derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F_SR/(dn_i dT) contribution
   */
  public double dFShortRangedNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 2, getComponentNumber(), numberOfComponents + 1, -1);
  }

  /**
   * Exact dFShortRangedNdV derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F_SR/(dn_i dV) contribution
   */
  public double dFShortRangedNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    return electrolyteDerivative(phase, 2, getComponentNumber(), numberOfComponents, -1);
  }

  /**
   * Exact dFShortRangedNdN derivative at fixed remaining extensive variables. Includes the composition, volume and
   * temperature dependence of all intermediate quantities.
   *
   * @param j index of second component
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return d²F_SR/(dn_i dn_j) contribution
   */
  public double dFShortRangedNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return electrolyteDerivative(phase, 2, getComponentNumber(), j, -1);
  }

  /**
   * Preserves the zero electrolyte contribution for callers supplying another phase model.
   *
   * @param phase phase passed by the property caller
   * @param term electrolyte term selector
   * @param first first derivative direction
   * @param second second derivative direction or -1
   * @param third third derivative direction or -1
   * @return electrolyte residual derivative, or zero for a non-MM phase
   */
  private double electrolyteDerivative(PhaseInterface phase, int term, int first, int second, int third) {
    PhaseElectrolyteCPAMM mmPhase = getMMPhase(phase);
    return mmPhase == null ? 0.0
        : neqsim.thermo.phase.MMResidualDerivative.derivative(mmPhase, term, first, second, third);
  }

  // ==================== Override dFdN Methods ====================

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    double result = super.dFdN(phase, numberOfComponents, temperature, pressure);
    // Non-associating ions still displace associating solvent through the covolume.
    // Restore this contribution also for older serialized components with cpaon = 0.
    if (cpaon == 0 && ((PhaseCPAInterface) phase).getTotalNumberOfAccociationSites() > 0) {
      result += dFCPAdN(phase, numberOfComponents, temperature, pressure);
    }
    return result + electrolyteDerivative(phase, -1, getComponentNumber(), -1, -1);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    double result = super.dFdNdT(phase, numberOfComponents, temperature, pressure);
    return result + electrolyteDerivative(phase, -1, getComponentNumber(), numberOfComponents + 1, -1);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    double result = super.dFdNdV(phase, numberOfComponents, temperature, pressure);
    return result + electrolyteDerivative(phase, -1, getComponentNumber(), numberOfComponents, -1);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature, double pressure) {
    double result = super.dFdNdN(j, phase, numberOfComponents, temperature, pressure);
    return result + electrolyteDerivative(phase, -1, getComponentNumber(), j, -1);
  }
}
