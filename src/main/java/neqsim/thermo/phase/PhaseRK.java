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
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseRK.
   * </p>
   */
  public PhaseRK() {
    super();
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
  public void addcomponent(String componentName, double moles, double molesInPhase,
      int compNumber) {
    super.addcomponent(molesInPhase);
    componentArray[compNumber] = new ComponentRK(componentName, moles, molesInPhase, compNumber);
  }
}
