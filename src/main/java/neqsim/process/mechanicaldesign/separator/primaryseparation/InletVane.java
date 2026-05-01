package neqsim.process.mechanicaldesign.separator.primaryseparation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Inlet vane device for primary separation in a separator vessel.
 *
 * <p>
 * An inlet vane distributor uses curved vanes to redirect the incoming two-phase flow, separate
 * bulk liquid by centrifugal force, and distribute gas evenly across the separator cross-section.
 * </p>
 *
 * <p>
 * Inlet vanes typically allow higher inlet momentum (up to 6000 Pa rho*v^2) compared to simple
 * deflector plates (3000 Pa), and achieve a bulk separation efficiency of 80-90%.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InletVane extends PrimarySeparation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(InletVane.class);

  /**
   * Constructs an InletVane with default parameters.
   */
  public InletVane() {
    super();
    setMaxInletMomentum(6000.0);
    setBulkSeparationEfficiency(0.85);
  }

  /**
   * Constructs an InletVane with a name.
   *
   * @param name the name of this inlet vane device
   */
  public InletVane(String name) {
    super(name);
    setMaxInletMomentum(6000.0);
    setBulkSeparationEfficiency(0.85);
  }
}
