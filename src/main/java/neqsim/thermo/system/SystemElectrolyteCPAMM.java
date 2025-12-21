/*
 * SystemElectrolyteCPAMM.java
 *
 * Thermodynamic system using the Maribo-Mogensen electrolyte CPA model.
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPAMM;
import neqsim.thermo.phase.PhaseType;

/**
 * Thermodynamic system class using the Maribo-Mogensen electrolyte CPA (e-CPA) equation of state.
 *
 * <p>
 * This model is based on the PhD thesis: "Development of an Electrolyte CPA Equation of State for
 * Mixed Solvent Electrolytes" by Bjørn Maribo-Mogensen, Technical University of Denmark, 2014.
 * </p>
 *
 * <p>
 * The residual Helmholtz free energy consists of:
 * </p>
 * <ul>
 * <li>SRK cubic equation of state term</li>
 * <li>CPA association term for hydrogen bonding</li>
 * <li>Debye-Hückel term for long-range electrostatic interactions</li>
 * <li>Born solvation term for ion hydration</li>
 * </ul>
 *
 * <p>
 * Key differences from the standard electrolyte CPA in NeqSim:
 * </p>
 * <ul>
 * <li>Uses Debye-Hückel instead of MSA for long-range electrostatics (simpler, faster)</li>
 * <li>Empirical Born radius correlations for cations and anions</li>
 * <li>Temperature-dependent ion-solvent interaction parameters</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * SystemInterface system = new SystemElectrolyteCPAMM(298.15, 1.0);
 * system.addComponent("water", 1.0);
 * system.addComponent("Na+", 0.1);
 * system.addComponent("Cl-", 0.1);
 * system.setMixingRule(10); // Electrolyte CPA mixing rule
 * system.init(0);
 * system.init(1);
 * </pre>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemElectrolyteCPAMM extends SystemSrkCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for SystemElectrolyteCPAMM with default conditions (298.15 K, 1 bar).
   */
  public SystemElectrolyteCPAMM() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemElectrolyteCPAMM.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bar (absolute)
   */
  public SystemElectrolyteCPAMM(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-CPA-MM";
    attractiveTermNumber = 0;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseElectrolyteCPAMM();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    // Disable volume correction for electrolyte systems by default
    this.useVolumeCorrection(false);
  }

  /**
   * Constructor for SystemElectrolyteCPAMM with specified number of phases.
   *
   * @param T temperature in Kelvin
   * @param P pressure in bar (absolute)
   * @param checkForSolids whether to include solid phase check
   */
  public SystemElectrolyteCPAMM(double T, double P, boolean checkForSolids) {
    this(T, P);
    setNumberOfPhases(2);
    if (checkForSolids) {
      solidPhaseCheck = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemElectrolyteCPAMM clone() {
    SystemElectrolyteCPAMM clonedSystem = null;
    try {
      clonedSystem = (SystemElectrolyteCPAMM) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }

  /**
   * Enable or disable the Debye-Hückel electrostatic term.
   *
   * @param on true to enable, false to disable
   */
  public void setDebyeHuckelOn(boolean on) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAMM) {
        ((PhaseElectrolyteCPAMM) phaseArray[i]).setDebyeHuckelOn(on);
      }
    }
  }

  /**
   * Enable or disable the Born solvation term.
   *
   * @param on true to enable, false to disable
   */
  public void setBornOn(boolean on) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseElectrolyteCPAMM) {
        ((PhaseElectrolyteCPAMM) phaseArray[i]).setBornOn(on);
      }
    }
  }

  /**
   * Get the Debye screening length for the specified phase.
   *
   * @param phaseNumber phase index
   * @return Debye length in meters
   */
  public double getDebyeLength(int phaseNumber) {
    if (phaseArray[phaseNumber] instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phaseArray[phaseNumber]).getDebyeLength();
    }
    return Double.NaN;
  }

  /**
   * Get the solvent permittivity (dielectric constant) for the specified phase.
   *
   * @param phaseNumber phase index
   * @return solvent permittivity (dimensionless)
   */
  public double getSolventPermittivity(int phaseNumber) {
    if (phaseArray[phaseNumber] instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phaseArray[phaseNumber]).getSolventPermittivity();
    }
    return Double.NaN;
  }

  /**
   * Get the mixture permittivity including ion effects for the specified phase.
   *
   * @param phaseNumber phase index
   * @return mixture permittivity (dimensionless)
   */
  public double getMixturePermittivity(int phaseNumber) {
    if (phaseArray[phaseNumber] instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phaseArray[phaseNumber]).getMixturePermittivity();
    }
    return Double.NaN;
  }
}
