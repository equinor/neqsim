package neqsim.thermo.util;

/**
 * Enumeration of reservoir fluid types based on Whitson classification.
 *
 * <p>
 * Classification is based on phase behavior characteristics as described in the Whitson wiki
 * (https://wiki.whitson.com/phase_behavior/classification/reservoir_fluid_type/).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see FluidClassifier
 */
public enum ReservoirFluidType {
  /**
   * Dry gas - no liquid dropout at any pressure/temperature.
   *
   * <p>
   * Characteristics:
   * <ul>
   * <li>API gravity: N/A (no liquid)</li>
   * <li>GOR: Very high (&gt; 100,000 scf/STB)</li>
   * <li>C7+ content: &lt; 0.7 mol%</li>
   * <li>No retrograde condensation</li>
   * </ul>
   * </p>
   */
  DRY_GAS("Dry Gas", "> 100,000", "< 0.7"),

  /**
   * Wet gas - produces liquid at surface but remains single-phase in reservoir.
   *
   * <p>
   * Characteristics:
   * <ul>
   * <li>API gravity: 40-60° (very light condensate)</li>
   * <li>GOR: 15,000 - 100,000 scf/STB</li>
   * <li>C7+ content: 0.7 - 4 mol%</li>
   * <li>Reservoir T &gt; Cricondentherm</li>
   * </ul>
   * </p>
   */
  WET_GAS("Wet Gas", "15,000 - 100,000", "0.7 - 4"),

  /**
   * Gas condensate - retrograde condensation in reservoir.
   *
   * <p>
   * Characteristics:
   * <ul>
   * <li>API gravity: 40-60°</li>
   * <li>GOR: 3,300 - 15,000 scf/STB</li>
   * <li>C7+ content: 4 - 12.5 mol%</li>
   * <li>Reservoir T between Tc and Cricondentherm</li>
   * </ul>
   * </p>
   */
  GAS_CONDENSATE("Gas Condensate", "3,300 - 15,000", "4 - 12.5"),

  /**
   * Volatile oil - high shrinkage oil with significant gas liberation.
   *
   * <p>
   * Characteristics:
   * <ul>
   * <li>API gravity: 40-50°</li>
   * <li>GOR: 1,000 - 3,300 scf/STB</li>
   * <li>C7+ content: 12.5 - 20 mol%</li>
   * <li>Reservoir T close to Tc</li>
   * </ul>
   * </p>
   */
  VOLATILE_OIL("Volatile Oil", "1,000 - 3,300", "12.5 - 20"),

  /**
   * Black oil - conventional crude oil with moderate gas content.
   *
   * <p>
   * Characteristics:
   * <ul>
   * <li>API gravity: 15-40°</li>
   * <li>GOR: &lt; 1,000 scf/STB</li>
   * <li>C7+ content: &gt; 20 mol%</li>
   * <li>Reservoir T well below Tc</li>
   * </ul>
   * </p>
   */
  BLACK_OIL("Black Oil", "< 1,000", "> 20"),

  /**
   * Heavy oil - high viscosity, low API gravity oil.
   *
   * <p>
   * Characteristics:
   * <ul>
   * <li>API gravity: 10-15°</li>
   * <li>GOR: Very low (&lt; 200 scf/STB)</li>
   * <li>C7+ content: &gt; 30 mol%</li>
   * </ul>
   * </p>
   */
  HEAVY_OIL("Heavy Oil", "< 200", "> 30"),

  /**
   * Unknown or unclassified fluid type.
   */
  UNKNOWN("Unknown", "N/A", "N/A");

  private final String displayName;
  private final String typicalGORRange;
  private final String typicalC7PlusRange;

  ReservoirFluidType(String displayName, String typicalGORRange, String typicalC7PlusRange) {
    this.displayName = displayName;
    this.typicalGORRange = typicalGORRange;
    this.typicalC7PlusRange = typicalC7PlusRange;
  }

  /**
   * Get the display name for this fluid type.
   *
   * @return human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get the typical GOR range for this fluid type.
   *
   * @return GOR range in scf/STB
   */
  public String getTypicalGORRange() {
    return typicalGORRange;
  }

  /**
   * Get the typical C7+ content range for this fluid type.
   *
   * @return C7+ range in mol%
   */
  public String getTypicalC7PlusRange() {
    return typicalC7PlusRange;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
