package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSrkPeneloux;

/**
 * <p>
 * PhaseSrkPenelouxEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSrkPenelouxEos extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseSrkPenelouxEos.
   * </p>
   */
  public PhaseSrkPenelouxEos() {}

  /** {@inheritDoc} */
  @Override
  public PhaseSrkPenelouxEos clone() {
    PhaseSrkPenelouxEos clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkPenelouxEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSrkPeneloux(name, moles, molesInPhase, compNumber);
  }
}
