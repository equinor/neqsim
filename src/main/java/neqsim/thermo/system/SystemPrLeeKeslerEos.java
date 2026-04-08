package neqsim.thermo.system;

/**
 * Thermodynamic system using the Peng-Robinson EOS with the Lee-Kesler alpha function.
 *
 * <p>
 * HYSYS/UniSim refers to this as "Peng-Robinson" (PR-LK). The Lee-Kesler m-factor:
 *
 * <pre>
 *   m = 0.480 + 1.574 \omega - 0.176 \omega^2
 * </pre>
 *
 * differs from the standard PR1978 formulation. This produces slightly different vapour fractions
 * (typically 1–3%) for natural gas/oil systems and is the recommended choice when validating
 * against UniSim simulation results.
 *
 * <p>
 * The EOS form and mixing rules are identical to {@link SystemPrEos}; only the alpha function
 * m-factor changes.
 *
 * @author Even Solbraa
 */
public class SystemPrLeeKeslerEos extends SystemPrEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Default constructor creating a PR-LK system at 298.15 K and 1 bara.
   */
  public SystemPrLeeKeslerEos() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructs a PR-LK system at the specified temperature and pressure.
   *
   * @param T the temperature in Kelvin
   * @param P the pressure in bara (absolute pressure)
   */
  public SystemPrLeeKeslerEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructs a PR-LK system with optional solid phase calculations.
   *
   * @param T the temperature in Kelvin
   * @param P the pressure in bara (absolute pressure)
   * @param checkForSolids set {@code true} to enable solid phase calculations
   */
  public SystemPrLeeKeslerEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "PR-LK-EoS";
    attractiveTermNumber = 21;
  }

  /** {@inheritDoc} */
  @Override
  public SystemPrLeeKeslerEos clone() {
    SystemPrLeeKeslerEos clonedSystem = null;
    try {
      clonedSystem = (SystemPrLeeKeslerEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
