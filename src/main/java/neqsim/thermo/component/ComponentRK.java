package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermRk;

/**
 * <p>
 * ComponentRK class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentRK extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentRK.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentRK(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    a = 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature
        * criticalTemperature / criticalPressure;
    b = (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
    delta1 = 1.0;
    delta2 = 0.0;
    setAttractiveParameter(new AttractiveTermRk(this));
  }

  /**
   * <p>
   * Constructor for ComponentRK.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature
   * @param PC Critical pressure
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentRK(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentRK clone() {
    ComponentRK clonedComponent = null;
    try {
      clonedComponent = (ComponentRK) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature
        * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    return 0.0;
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
