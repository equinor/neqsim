package neqsim.process.equipment.compressor;

/**
 * Physical deposit (fouling) mechanisms that can accumulate on a centrifugal compressor impeller and reduce its
 * performance.
 *
 * <p>
 * Each mechanism carries a representative solid deposit density used to convert an accumulated deposit <em>mass</em>
 * into a deposit <em>volume</em> (and hence a film thickness and flow-area blockage) in {@link CompressorDeposit}.
 * Densities are screening-level literature values for the dominant solid phase and can be overridden per deposit when a
 * measured value is available.
 * </p>
 *
 * <table border="1">
 * <caption>Representative solid deposit densities</caption>
 * <tr>
 * <th>Mechanism</th>
 * <th>Density [kg/m3]</th>
 * <th>Typical source</th>
 * </tr>
 * <tr>
 * <td>SULFUR_S8</td>
 * <td>2070</td>
 * <td>Elemental sulfur from H2S oxidation (O2 ingress)</td>
 * </tr>
 * <tr>
 * <td>SALT_NACL</td>
 * <td>2160</td>
 * <td>Formation-water salt carry-over, marine air</td>
 * </tr>
 * <tr>
 * <td>SCALE_CACO3</td>
 * <td>2710</td>
 * <td>Calcium carbonate scale</td>
 * </tr>
 * <tr>
 * <td>SCALE_BASO4</td>
 * <td>4500</td>
 * <td>Barium sulfate scale</td>
 * </tr>
 * <tr>
 * <td>SCALE_CASO4</td>
 * <td>2960</td>
 * <td>Calcium sulfate scale</td>
 * </tr>
 * <tr>
 * <td>IRON_SULFIDE</td>
 * <td>4840</td>
 * <td>FeS corrosion product</td>
 * </tr>
 * <tr>
 * <td>IRON_OXIDE</td>
 * <td>5240</td>
 * <td>Rust / magnetite corrosion product</td>
 * </tr>
 * <tr>
 * <td>HYDROCARBON_WAX</td>
 * <td>900</td>
 * <td>Heavy hydrocarbon / wax / oil-mist deposits</td>
 * </tr>
 * <tr>
 * <td>PARTICULATE</td>
 * <td>2500</td>
 * <td>Airborne dust and particulates</td>
 * </tr>
 * <tr>
 * <td>GENERIC</td>
 * <td>2000</td>
 * <td>Unspecified / mixed deposit</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum DepositMechanism {
  /** Elemental sulfur (S8) from H2S oxidation. */
  SULFUR_S8(2070.0, "Elemental sulfur (S8) from H2S oxidation"),
  /** Sodium chloride salt from formation water / marine air. */
  SALT_NACL(2160.0, "NaCl salt crystallisation"),
  /** Calcium carbonate scale. */
  SCALE_CACO3(2710.0, "Calcium carbonate scale"),
  /** Barium sulfate scale. */
  SCALE_BASO4(4500.0, "Barium sulfate scale"),
  /** Calcium sulfate scale. */
  SCALE_CASO4(2960.0, "Calcium sulfate scale"),
  /** Iron sulfide corrosion product. */
  IRON_SULFIDE(4840.0, "Iron sulfide (FeS) corrosion product"),
  /** Iron oxide / rust corrosion product. */
  IRON_OXIDE(5240.0, "Iron oxide / magnetite corrosion product"),
  /** Heavy hydrocarbon / wax deposits. */
  HYDROCARBON_WAX(900.0, "Heavy hydrocarbon / wax / oil-mist deposits"),
  /** Airborne dust and particulates. */
  PARTICULATE(2500.0, "Airborne dust and particulates"),
  /** Unspecified or mixed deposit. */
  GENERIC(2000.0, "Unspecified / mixed deposit");

  private final double density;
  private final String description;

  /**
   * Constructor.
   *
   * @param density representative solid deposit density in kg/m3
   * @param description human-readable description of the deposit source
   */
  DepositMechanism(double density, String description) {
    this.density = density;
    this.description = description;
  }

  /**
   * Representative solid deposit density used to convert deposit mass into deposit volume.
   *
   * @return density in kg/m3
   */
  public double getDensity() {
    return density;
  }

  /**
   * Human-readable description of the deposit source.
   *
   * @return description text
   */
  public String getDescription() {
    return description;
  }
}
