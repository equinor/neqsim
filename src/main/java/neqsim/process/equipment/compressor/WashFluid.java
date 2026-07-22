package neqsim.process.equipment.compressor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Wash fluids used for online (live) or offline compressor washing, with a screening-level solubility of each
 * {@link DepositMechanism} in the fluid.
 *
 * <p>
 * The solubility (kg deposit dissolved per kg wash fluid) drives which deposits a given wash fluid can remove: water
 * dissolves salt but not elemental sulfur, whereas an aromatic solvent such as xylene dissolves sulfur and wax but not
 * salt. These values are screening-level and can be used to <em>recommend</em> a wash fluid for a given deposit and to
 * <em>plan</em> the wash rate and duration; they are not a substitute for a solvent qualification test.
 * </p>
 *
 * <table border="1">
 * <caption>Screening deposit solubilities (kg deposit / kg wash fluid)</caption>
 * <tr>
 * <th>Wash fluid</th>
 * <th>Dissolves</th>
 * </tr>
 * <tr>
 * <td>Water</td>
 * <td>NaCl salt (well), carbonate/sulfate scale (poorly)</td>
 * </tr>
 * <tr>
 * <td>Xylene / Toluene</td>
 * <td>Elemental sulfur (S8), wax / heavy hydrocarbon</td>
 * </tr>
 * <tr>
 * <td>Condensate</td>
 * <td>Wax / heavy hydrocarbon (well), sulfur (poorly)</td>
 * </tr>
 * <tr>
 * <td>Methanol</td>
 * <td>Salt (moderate), light hydrocarbon (poorly)</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum WashFluid {
  /** Fresh water (with or without detergent). */
  WATER("Water"),
  /** Xylene (aromatic solvent). */
  XYLENE("Xylene"),
  /** Toluene (aromatic solvent). */
  TOLUENE("Toluene"),
  /** Stabilised condensate / light hydrocarbon solvent. */
  CONDENSATE("Condensate"),
  /** Methanol. */
  METHANOL("Methanol");

  private final String displayName;

  WashFluid(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Human-readable name.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /** Solubility matrix: kg deposit dissolved per kg wash fluid. */
  private static final Map<WashFluid, Map<DepositMechanism, Double>> SOLUBILITY = new EnumMap<>(WashFluid.class);

  static {
    // Water: salt readily, scale poorly, no sulfur/wax.
    put(WATER, DepositMechanism.SALT_NACL, 0.30);
    put(WATER, DepositMechanism.SCALE_CACO3, 0.0015);
    put(WATER, DepositMechanism.SCALE_CASO4, 0.0020);
    // Aromatic solvents: sulfur and wax, not salt.
    put(XYLENE, DepositMechanism.SULFUR_S8, 0.020);
    put(XYLENE, DepositMechanism.HYDROCARBON_WAX, 0.15);
    put(TOLUENE, DepositMechanism.SULFUR_S8, 0.017);
    put(TOLUENE, DepositMechanism.HYDROCARBON_WAX, 0.13);
    // Condensate: wax well, sulfur weakly.
    put(CONDENSATE, DepositMechanism.HYDROCARBON_WAX, 0.10);
    put(CONDENSATE, DepositMechanism.SULFUR_S8, 0.005);
    // Methanol: salt moderately, light HC weakly.
    put(METHANOL, DepositMechanism.SALT_NACL, 0.05);
    put(METHANOL, DepositMechanism.HYDROCARBON_WAX, 0.02);
  }

  private static void put(WashFluid fluid, DepositMechanism mechanism, double solubility) {
    Map<DepositMechanism, Double> map = SOLUBILITY.get(fluid);
    if (map == null) {
      map = new EnumMap<>(DepositMechanism.class);
      SOLUBILITY.put(fluid, map);
    }
    map.put(mechanism, solubility);
  }

  /**
   * Screening solubility of a deposit mechanism in this wash fluid.
   *
   * @param mechanism deposit mechanism
   * @return solubility in kg deposit per kg wash fluid (0 if the fluid does not dissolve it)
   */
  public double getSolubility(DepositMechanism mechanism) {
    Map<DepositMechanism, Double> map = SOLUBILITY.get(this);
    if (map == null || mechanism == null) {
      return 0.0;
    }
    Double value = map.get(mechanism);
    return value == null ? 0.0 : value;
  }

  /**
   * Whether this wash fluid can dissolve the given deposit mechanism.
   *
   * @param mechanism deposit mechanism
   * @return true if the solubility is positive
   */
  public boolean dissolves(DepositMechanism mechanism) {
    return getSolubility(mechanism) > 0.0;
  }
}
