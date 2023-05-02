package neqsim.thermo.component;

import neqsim.thermo.component.attractiveEosTerm.AttractiveTermTwu;

/**
 * <p>
 * ComponentTST class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentTST extends ComponentEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentTST.
   * </p>
   *
   * @param component_name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compnumber a int
   */
  public ComponentTST(String component_name, double moles, double molesInPhase, int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);

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
   * @param number a int
   * @param TC Critical temperature
   * @param PC Critical pressure
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Number of moles
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
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
      int type) {
    // todo: redundant?
    super.init(temperature, pressure, totalNumberOfMoles, beta, type);
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
