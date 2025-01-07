package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentWax;

/**
 * <p>
 * PhaseWax class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseWax extends PhaseSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseWax.
   * </p>
   */
  public PhaseWax() {
    setType(PhaseType.WAX);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseWax clone() {
    PhaseWax clonedPhase = null;
    try {
      clonedPhase = (PhaseWax) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    setType(PhaseType.WAX);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentWax(name, moles, molesInPhase, compNumber);
    // componentArray[compNumber] = new ComponentWaxWilson(componentName, moles,
    // molesInPhase, compNumber);
    // componentArray[compNumber] = new ComponentWonWax(componentName, moles,
    // molesInPhase, compNumber);
  }
}
