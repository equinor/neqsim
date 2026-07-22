package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentTST;

/**
 * PhaseTSTEos class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseTSTEos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for PhaseTSTEos.
   */
  public PhaseTSTEos() {
    uEOS = 2.5;
    wEOS = -1.5;
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseTSTEos clone() {
    PhaseTSTEos clonedPhase = null;
    try {
      clonedPhase = (PhaseTSTEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentTST(name, moles, molesInPhase, compNumber);
  }
}
