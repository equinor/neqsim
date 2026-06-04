package neqsim.thermo.system;

/**
 * Thermodynamic system using the UMR-PRU equation of state with the 3-parameter Mathias-Copeman
 * pure-component alpha function.
 *
 * <p>
 * This is the best-validated UMR-PRU variant in NeqSim. It sets attractive term number 13, which
 * selects the 3-parameter Mathias-Copeman alpha <i>&alpha;(T) = [1 + c<sub>1</sub>(1 -
 * &radic;T<sub>r</sub>) + c<sub>2</sub>(1 - &radic;T<sub>r</sub>)<sup>2</sup> + c<sub>3</sub>(1 -
 * &radic;T<sub>r</sub>)<sup>3</sup>]<sup>2</sup></i>, and routes the UNIFAC group-interaction
 * lookup to the temperature-dependent {@code _umrmc} tables (<i>A<sub>nm</sub>(T) = a<sub>nm</sub>
 * + b<sub>nm</sub>T + c<sub>nm</sub>T<sup>2</sup></i>). The pure-component Mathias-Copeman
 * parameters and the {@code _umrmc} group-interaction parameters were regressed together and must
 * be used as a matched set.
 * </p>
 *
 * <p>
 * See {@link SystemUMRPRUEos} for the universal mixing rule, the <i>A = -0.53</i> constant, the
 * co-volume combining rule, and the rationale for omitting the Flory-Huggins (r) combinatorial
 * term.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUMRPRUMCEos extends SystemUMRPRUEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   */
  public SystemUMRPRUMCEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRPRUMCEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUMRPRUMCEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "UMR-PRU-MC-EoS";
    attractiveTermNumber = 13;
  }

  /** {@inheritDoc} */
  @Override
  public SystemUMRPRUMCEos clone() {
    SystemUMRPRUMCEos clonedSystem = null;
    try {
      clonedSystem = (SystemUMRPRUMCEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
