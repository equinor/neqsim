package neqsim.thermo.system;

/**
 * Thermodynamic system using the UMR-PRU (Universal Mixing Rule - Peng Robinson UNIFAC) equation of state.
 *
 * <p>
 * The UMR-PRU model combines the Peng-Robinson cubic EoS with a UNIFAC-type excess Gibbs energy model through the
 * universal mixing rule of Voutsas, Magoulas and Tassios (<i>Ind. Eng. Chem. Res.</i> 2004, 43, 6238-6246). The
 * attractive-term mixing rule is
 * </p>
 *
 * <p>
 * <i>&alpha;<sub>mix</sub> = &sum;<sub>i</sub> x<sub>i</sub> a<sub>i</sub>/(b<sub>i</sub>RT) + (1/A) &sum;<sub>i</sub>
 * x<sub>i</sub> ln&gamma;<sub>i</sub></i>
 * </p>
 *
 * <p>
 * with the universal constant <i>A = -0.53</i> for the Peng-Robinson EoS. The co-volume combining rule
 * <i>b<sub>ij</sub> = ((&radic;b<sub>i</sub> + &radic;b<sub>j</sub>)/2)<sup>2</sup></i> is used (see
 * {@code setBmixType(1)}); this combining rule recovers the Flory-Huggins free-volume term of the original UNIFAC
 * combinatorial part, which is why only the Staverman-Guggenheim combinatorial contribution (with coordination number z
 * = 10) plus the residual UNIFAC term are evaluated in the excess Gibbs energy model &mdash; the Flory-Huggins segment
 * (r) term is intentionally omitted to avoid double counting.
 * </p>
 *
 * <p>
 * This base class uses the standard Peng-Robinson alpha function (attractive term number 1). The sub-classes refine the
 * pure-component temperature dependence:
 * </p>
 *
 * <table>
 * <caption>UMR-PRU model variants</caption>
 * <tr>
 * <th>Class</th>
 * <th>Attractive term</th>
 * <th>Pure-component alpha</th>
 * <th>Group-interaction tables</th>
 * </tr>
 * <tr>
 * <td>{@link SystemUMRPRUEos}</td>
 * <td>1</td>
 * <td>standard PR alpha</td>
 * <td>{@code _umr}</td>
 * </tr>
 * <tr>
 * <td>{@link SystemUMRPRUMCEos}</td>
 * <td>13</td>
 * <td>3-parameter Mathias-Copeman</td>
 * <td>{@code _umrmc}</td>
 * </tr>
 * <tr>
 * <td>{@link SystemUMRPRUMCEosNew}</td>
 * <td>19</td>
 * <td>5-parameter Mathias-Copeman</td>
 * <td>{@code _umrmc}</td>
 * </tr>
 * </table>
 *
 * <p>
 * UMR-CPA ({@link SystemUMRCPAEoS}) adds an association term and reads its own {@code _umrcpa} tables.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUMRPRUEos extends SystemPrEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** True when the excess Gibbs energy model of the mixing rule has been chosen by a call. */
  private boolean excessGibbsModelSelected = false;

  /**
   * {@inheritDoc}
   *
   * <p>
   * The Huron-Vidal mixing rule ({@code "HV"}) is used with the UMR-PRU UNIFAC model unless another excess Gibbs energy
   * model has been chosen with {@link #setMixingRuleGEmodel(String)} or {@link #setMixingRule(String, String)}.
   * {@code setMixingRule("HV")} therefore gives the same model as {@code setMixingRule("HV", "UNIFAC_UMRPRU")}.
   * </p>
   */
  @Override
  public void setMixingRule(String typename) {
    if (!excessGibbsModelSelected && "HV".equalsIgnoreCase(typename)) {
      setMixingRuleGEmodel("UNIFAC_UMRPRU");
    }
    super.setMixingRule(typename);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRuleGEmodel(String name) {
    excessGibbsModelSelected = true;
    super.setMixingRuleGEmodel(name);
  }

  /**
   * Constructor for SystemUMRPRUEos.
   */
  public SystemUMRPRUEos() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemUMRPRUEos.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRPRUEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemUMRPRUEos.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUMRPRUEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    setBmixType(1);
    modelName = "UMR-PRU-EoS";
    attractiveTermNumber = 1;

    CapeOpenProperties11 = new String[] {"speedOfSound", "jouleThomsonCoefficient", "internalEnergy",
        "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy", "fugacityCoefficient",
        "logFugacityCoefficient", "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
        "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dtemperature", "entropy", "heatCapacityCp",
        "heatCapacityCv", "density", "volume"};
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if a required part of the clone cannot be copied
   */
  @Override
  public SystemUMRPRUEos clone() {
    SystemUMRPRUEos clonedSystem = null;
    try {
      clonedSystem = (SystemUMRPRUEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
      throw new IllegalStateException("Unable to complete clone of SystemUMRPRUEos", ex);
    }

    return clonedSystem;
  }

  /**
   * commonInitialization.
   */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(true);
    setImplementedPressureDeriativesofFugacity(true);
    setImplementedTemperatureDeriativesofFugacity(true);
  }
}
