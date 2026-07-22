/*
 * PhasePureComponentSolid.java
 *
 * Created on 18. august 2001, 12:39
 */

package neqsim.thermo.phase;

/**
 * PhasePureComponentSolid class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhasePureComponentSolid extends PhaseSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for PhasePureComponentSolid.
   */
  public PhasePureComponentSolid() {
  }

  /** {@inheritDoc} */
  @Override
  public PhasePureComponentSolid clone() {
    PhasePureComponentSolid clonedPhase = null;
    try {
      clonedPhase = (PhasePureComponentSolid) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }
}
