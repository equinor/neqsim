package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermTwu;

/**
 * <p>
 * ComponentTST class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentTST extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentTST.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentTST(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    a = 0.427481 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    b = .086641 * R * criticalTemperature / criticalPressure;
    // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
    // acentricFactor;

    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
    setAttractiveParameter(new AttractiveTermTwu(this));
  }

  /**
   * <p>
   * Constructor for ComponentTST.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentTST(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentTST clone() {
    ComponentTST clonedComponent = null;
    try {
      clonedComponent = (ComponentTST) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return .427481 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return .086641 * R * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if (this.getRacketZ() < 1e-10) {
      return 0.0;
    } else {
      return 0.40768 * (0.29441 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
    }
  }

  /**
   * <p>
   * getQpure.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getQpure(double temperature) {
    return this.getaT() / (this.getb() * R * temperature);
  }

  /**
   * <p>
   * getdQpuredT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getdQpuredT(double temperature) {
    return dqPuredT;
  }

  /**
   * <p>
   * getdQpuredTdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getdQpuredTdT(double temperature) {
    return dqPuredTdT;
  }
}
