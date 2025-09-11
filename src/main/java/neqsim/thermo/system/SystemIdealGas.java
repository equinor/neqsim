package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseIdealGas;

/**
 * Simple thermodynamic system using an ideal gas equation of state.
 *
 * @author esol
 */
public class SystemIdealGas extends SystemThermo {
  private static final long serialVersionUID = 1000L;

  /**
   * <p>
   * Constructor for SystemIdealGas.
   * </p>
   */
  public SystemIdealGas() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemIdealGas.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemIdealGas(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemIdealGas.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param checkForSolids a boolean
   */
  public SystemIdealGas(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "ideal gas";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseIdealGas();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemIdealGas clone() {
    SystemIdealGas cloned = null;
    try {
      cloned = (SystemIdealGas) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
  }
}

