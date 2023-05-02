/*
 * PhasePureComponentSolid.java
 *
 * Created on 18. august 2001, 12:39
 */

package neqsim.thermo.phase;

/**
 * <p>
 * PhasePureComponentSolid class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhasePureComponentSolid extends PhaseSolid {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhasePureComponentSolid.
   * </p>
   */
  public PhasePureComponentSolid() {
    super();
    setType(PhaseType.SOLID);
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

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
  }
}
