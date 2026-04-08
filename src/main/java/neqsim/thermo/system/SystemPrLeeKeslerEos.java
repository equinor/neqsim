package neqsim.thermo.system;

/**
 * Thermodynamic system combining the Peng-Robinson EOS with the Lee-Kesler BWR method for
 * enthalpy/entropy/Cp departures (PR-LK).
 *
 * <p>
 * This property package uses two separate models:
 * <ol>
 * <li><b>VLE (phase equilibrium)</b>: standard PR76 alpha function applied to all acentric factors:
 *
 * <pre>
 *   m = 0.37464 + 1.54226 \omega - 0.26992 \omega^2
 * </pre>
 *
 * </li>
 * <li><b>Enthalpy / Entropy / Cp departures</b>: Lee-Kesler BWR correlation instead of PR departure
 * functions — hence the "PR-LK" label.</li>
 * </ol>
 *
 * <p>
 * The key difference from {@link SystemPrEos1978} is that the PR76 alpha applies to <em>all</em>
 * components including heavy pseudo-fractions (ω &gt; 0.49), whereas PR1978 uses a modified
 * polynomial for those components. For typical natural-gas and oil systems the vapour fraction
 * predicted by this class will differ from the PR1978 formulation for components with ω &gt; 0.49.
 *
 * <p>
 * <b>Note on enthalpy:</b> the Lee-Kesler BWR enthalpy override is currently not implemented;
 * H/S/Cp departures are calculated using the standard PR EOS departure functions. A future
 * enhancement should override {@code getEnthalpy} in the phase classes to use LK BWR tables.
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
    // Use PR76 alpha (attractiveTermNumber=1) for all components — the original
    // PR76 m-factor is applied to all acentric factors without the PR1978 modification.
    attractiveTermNumber = 1;
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
