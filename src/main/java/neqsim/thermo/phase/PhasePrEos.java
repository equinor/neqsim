package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentPR;

/**
 * <p>
 * PhasePrEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePrEos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhasePrEos.
   * </p>
   */
  public PhasePrEos() {
    thermoPropertyModelName = "PR-EoS";
    uEOS = 2;
    wEOS = -1;
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
  }

  /** {@inheritDoc} */
  @Override
  public PhasePrEos clone() {
    PhasePrEos clonedPhase = null;
    try {
      clonedPhase = (PhasePrEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentPR(name, moles, molesInPhase, compNumber);
  }
}
