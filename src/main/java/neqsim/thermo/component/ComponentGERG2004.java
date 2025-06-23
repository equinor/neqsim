package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentGERG2004 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGERG2004 extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentGERG2004.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGERG2004(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * Constructor for ComponentGERG2004.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentGERG2004(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentGERG2004 clone() {
    ComponentGERG2004 clonedComponent = null;
    try {
      clonedComponent = (ComponentGERG2004) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return 1;
  }
}
