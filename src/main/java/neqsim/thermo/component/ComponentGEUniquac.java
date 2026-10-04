/*
 * ComponentGEUniquac.java
 *
 * Created on 10. juli 2000, 21:06
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Base for UNIFAC components. Standalone UNIQUAC is unsupported because the activity-coefficient implementation and
 * parameter data are incomplete; no ideal-mixture fallback is supplied.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGEUniquac extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  double r = 0;
  double q = 0;

  /**
   * Constructor for ComponentGEUniquac.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   * @throws UnsupportedOperationException when constructing an unsupported standalone UNIQUAC component
   */
  public ComponentGEUniquac(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    if (getClass().equals(ComponentGEUniquac.class)) {
      throw new UnsupportedOperationException(
          "Standalone UNIQUAC is not supported: activity-coefficient implementation and parameter data are incomplete.");
    }
  }

  /**
   * Calculate, set and return fugacity coefficient.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object to get fugacity coefficient of.
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return Fugacity coefficient
   */
  public double fugcoef(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    fugacityCoefficient = (this.getGamma(phase, numberOfComponents, temperature, pressure, pt)
        * this.getAntoineVaporPressure(temperature) / pressure);
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam, String[][] mixRule) {
    return getGamma(phase, numberOfComponents, temperature, pressure, pt);
  }

  /**
   * Rejects the unimplemented UNIQUAC calculation. Supported subclasses supply their own activity model.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return activity coefficient for supported subclass implementations
   * @throws UnsupportedOperationException because standalone UNIQUAC is not implemented
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    throw new UnsupportedOperationException(
        "UNIQUAC activity coefficients are not implemented. Use a supported GE model such as NRTL or UNIFAC.");
  }

  /**
   * fugcoefDiffPres.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return a double
   */
  public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    dfugdp = (Math.log(fugcoef(phase, numberOfComponents, temperature, pressure + 0.01, pt))
        - Math.log(fugcoef(phase, numberOfComponents, temperature, pressure - 0.01, pt))) / 0.02;
    return dfugdp;
  }

  /**
   * fugcoefDiffTemp.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return a double
   */
  public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    dfugdt = (Math.log(fugcoef(phase, numberOfComponents, temperature + 0.01, pressure, pt))
        - Math.log(fugcoef(phase, numberOfComponents, temperature - 0.01, pressure, pt))) / 0.02;
    return dfugdt;
  }
  /*
   * public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
   * PhaseType pt){ // NumericalDerivative deriv = new NumericalDerivative(); // System.out.println("dfugdP : " +
   * NumericalDerivative.fugcoefDiffPres(this, phase, numberOfComponents, temperature, pressure, pt)); return
   * NumericalDerivative.fugcoefDiffPres(this, phase, numberOfComponents, temperature, pressure, pt); }
   *
   * public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
   * PhaseType pt){ NumericalDerivative deriv = new NumericalDerivative(); // System.out.println("dfugdT : " +
   * NumericalDerivative.fugcoefDiffTemp(this, phase, numberOfComponents, temperature, pressure, pt)); return
   * NumericalDerivative.fugcoefDiffTemp(this, phase, numberOfComponents, temperature, pressure, pt); }
   */

  /**
   * Getter for the field <code>r</code>.
   *
   * @return a double
   */
  public double getr() {
    return r;
  }

  /**
   * Getter for the field <code>q</code>.
   *
   * @return a double
   */
  public double getq() {
    return q;
  }
}
