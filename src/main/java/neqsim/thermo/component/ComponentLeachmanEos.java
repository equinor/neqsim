package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentLeachman class.
 * </p>
 *
 * @author Even Solbraa Leachman
 * @version $Id: $Id
 */
public class ComponentLeachmanEos extends ComponentFundamentalEOS {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentLeachman.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentLeachmanEos(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }



  /** {@inheritDoc} */
  @Override
  public ComponentLeachmanEos clone() {
    ComponentLeachmanEos clonedComponent = null;
    try {
      clonedComponent = (ComponentLeachmanEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }



  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    return fugacityCoefficient;
  }




}
