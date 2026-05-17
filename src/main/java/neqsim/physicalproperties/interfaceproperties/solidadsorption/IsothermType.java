package neqsim.physicalproperties.interfaceproperties.solidadsorption;

/**
 * Enumeration of supported adsorption isotherm models.
 *
 * <p>
 * Each isotherm type represents a different theoretical model for gas-solid adsorption:
 * </p>
 * <table>
 * <caption>Isotherm Types and Applications</caption>
 * <tr>
 * <th>Type</th>
 * <th>Best Application</th>
 * </tr>
 * <tr>
 * <td>DRA</td>
 * <td>Microporous adsorbents (activated carbon, zeolites)</td>
 * </tr>
 * <tr>
 * <td>LANGMUIR</td>
 * <td>Homogeneous surfaces, monolayer adsorption</td>
 * </tr>
 * <tr>
 * <td>FREUNDLICH</td>
 * <td>Heterogeneous surfaces, low pressure</td>
 * </tr>
 * <tr>
 * <td>BET</td>
 * <td>Multilayer adsorption, surface area determination</td>
 * </tr>
 * <tr>
 * <td>SIPS</td>
 * <td>Heterogeneous surfaces with saturation</td>
 * </tr>
 * <tr>
 * <td>EXTENDED_LANGMUIR</td>
 * <td>Multi-component competitive adsorption</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public enum IsothermType {
  /**
   * Dubinin-Radushkevich-Astakhov potential theory. Best for microporous adsorbents like activated
   * carbon and zeolites.
   */
  DRA("Dubinin-Radushkevich-Astakhov", true),

  /**
   * Langmuir isotherm for monolayer adsorption on homogeneous surfaces. Assumes single adsorption
   * site type and no adsorbate-adsorbate interactions.
   */
  LANGMUIR("Langmuir", false),

  /**
   * Freundlich isotherm for heterogeneous surfaces. Empirical model with no saturation limit.
   */
  FREUNDLICH("Freundlich", false),

  /**
   * Brunauer-Emmett-Teller (BET) isotherm for multilayer adsorption. Used for surface area
   * determination.
   */
  BET("Brunauer-Emmett-Teller", false),

  /**
   * Sips (Langmuir-Freundlich) isotherm. Combines Langmuir saturation with Freundlich
   * heterogeneity.
   */
  SIPS("Sips/Langmuir-Freundlich", false),

  /**
   * Extended Langmuir for multi-component competitive adsorption. Models competition between
   * adsorbates for surface sites.
   */
  EXTENDED_LANGMUIR("Extended Langmuir", true);

  private final String displayName;
  private final boolean supportsMultiComponent;

  /**
   * Constructor for IsothermType.
   *
   * @param displayName the human-readable name for the isotherm
   * @param supportsMultiComponent whether this model handles mixtures directly
   */
  IsothermType(String displayName, boolean supportsMultiComponent) {
    this.displayName = displayName;
    this.supportsMultiComponent = supportsMultiComponent;
  }

  /**
   * Get the display name of the isotherm type.
   *
   * @return the human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Check if this isotherm type supports multi-component mixtures directly.
   *
   * @return true if the model handles mixtures, false for pure component models
   */
  public boolean supportsMultiComponent() {
    return supportsMultiComponent;
  }

  /**
   * Get IsothermType from string name (case-insensitive).
   *
   * @param name the name of the isotherm type
   * @return the corresponding IsothermType, or DRA if not found
   */
  public static IsothermType fromString(String name) {
    if (name == null || name.trim().isEmpty()) {
      return DRA;
    }
    String upperName = name.toUpperCase().trim();
    for (IsothermType type : values()) {
      if (type.name().equals(upperName)) {
        return type;
      }
    }
    // Check display names
    for (IsothermType type : values()) {
      if (type.getDisplayName().equalsIgnoreCase(name)) {
        return type;
      }
    }
    return DRA;
  }
}
