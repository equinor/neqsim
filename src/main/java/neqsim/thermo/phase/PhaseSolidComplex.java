package neqsim.thermo.phase;

/**
 * PhaseSolidComplex class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseSolidComplex extends PhaseSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for PhaseSolidComplex.
   */
  public PhaseSolidComplex() {
    setType(PhaseType.SOLIDCOMPLEX);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSolidComplex clone() {
    PhaseSolidComplex clonedPhase = null;
    try {
      clonedPhase = (PhaseSolidComplex) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    setType(PhaseType.SOLIDCOMPLEX);
  }
}
