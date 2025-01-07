package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentRK;

/**
 * <p>
 * PhaseRK class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseRK extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseRK.
   * </p>
   */
  public PhaseRK() {
    // mixRule = mixSelect.getMixingRule(2);
    uEOS = 1;
    wEOS = 0;
    delta1 = 1;
    delta2 = 0;
  }

  /** {@inheritDoc} */
  @Override
  public PhaseRK clone() {
    PhaseRK clonedPhase = null;
    try {
      clonedPhase = (PhaseRK) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentRK(name, moles, molesInPhase, compNumber);
  }
}
