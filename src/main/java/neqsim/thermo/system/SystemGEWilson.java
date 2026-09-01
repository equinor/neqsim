package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGEWilson;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the Wilson GE model.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemGEWilson extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemGEWilson.
   */
  public SystemGEWilson() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemGEWilson.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemGEWilson(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemGEWilson.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemGEWilson(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Wilson-GE-model";
    attractiveTermNumber = 0;

    configureEosGePhases(T, P, new PhaseSrkEos(), new PhaseGEWilson());
  }

  /** {@inheritDoc} */
  @Override
  public SystemGEWilson clone() {
    SystemGEWilson clonedSystem = null;
    try {
      clonedSystem = (SystemGEWilson) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
