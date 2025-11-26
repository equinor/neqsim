package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * Component class for the IAPWS-IF97 water model.
 * <p>
 * This component uses an ideal reference fugacity coefficient since the thermodynamic properties
 * are handled directly by the phase model.
 * </p>
 *
 * @author esol
 */
public class ComponentWater extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Construct a water component.
   *
   * @param name component name
   * @param moles total moles of the component
   * @param molesInPhase moles in the current phase
   * @param compIndex index of component in phase array
   */
  public ComponentWater(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * Constructor with basic critical properties – not used in the IF97 model but provided for
   * completeness.
   *
   * @param number a int
   * @param tc a double
   * @param pc a double
   * @param m a double
   * @param a a double
   * @param moles a double
   */
  public ComponentWater(int number, double tc, double pc, double m, double a, double moles) {
    super(number, tc, pc, m, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentWater clone() {
    ComponentWater cloned = null;
    try {
      cloned = (ComponentWater) super.clone();
    } catch (Exception e) {
      logger.error("Cloning of ComponentWater failed", e);
    }
    return cloned;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if (hasVolumeCorrection()) {
      return super.getVolumeCorrection();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double T, double p, double totalNumberOfMoles,
      double beta, int numberOfComponents, int initType) {
    // Ideal behaviour – simply set fugacity coefficient to 1.0
    if (initType == 3) {
      phase.getComponent(getComponentNumber()).setFugacityCoefficient(1.0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    return 1.0; // Ideal reference behaviour
  }
}
