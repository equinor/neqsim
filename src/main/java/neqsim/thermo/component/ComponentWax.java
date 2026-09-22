package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * ComponentWax class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentWax extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ComponentWax.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentWax(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      fugacityCoefficient = 1.0e50;
      return fugacityCoefficient;
    }

    return fugcoef2(phase1);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef2(PhaseInterface phase1) {
    return WaxModelCorrelations.solidFugacityCoefficient(this, phase1, 0.0);
  }

}
