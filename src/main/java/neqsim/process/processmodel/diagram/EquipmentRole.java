package neqsim.process.processmodel.diagram;

/**
 * Classifies process equipment by its functional role in the process.
 *
 * <p>
 * This classification is used by the layout intelligence layer to determine optimal positioning in
 * professional PFD diagrams. Equipment role follows oil &amp; gas industry conventions:
 * </p>
 * <ul>
 * <li><b>GAS</b> - Equipment handling predominantly gas phase (compressors, gas coolers)</li>
 * <li><b>LIQUID</b> - Equipment handling predominantly liquid phase (pumps, liquid heaters)</li>
 * <li><b>SEPARATOR</b> - Phase separation equipment (separators, scrubbers, flash drums)</li>
 * <li><b>MIXED</b> - Equipment handling mixed phases (heat exchangers, pipes)</li>
 * <li><b>FEED</b> - Feed/inlet streams</li>
 * <li><b>PRODUCT</b> - Product/outlet streams</li>
 * <li><b>UTILITY</b> - Utility equipment (coolers, heaters with external utility)</li>
 * <li><b>CONTROL</b> - Control equipment (valves, controllers)</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public enum EquipmentRole {
  /**
   * Gas processing equipment - positioned in upper section of PFD.
   */
  GAS("Gas", "upper", "#87CEEB", 0),

  /**
   * Liquid processing equipment - positioned in lower section of PFD.
   */
  LIQUID("Liquid", "lower", "#4169E1", 2),

  /**
   * Separator equipment - anchor point, gas exits top, liquid exits bottom.
   */
  SEPARATOR("Separator", "center", "#32CD32", 1),

  /**
   * Mixed phase equipment - positioned based on context.
   */
  MIXED("Mixed", "center", "#FFD700", 1),

  /**
   * Feed/inlet streams - positioned at left of diagram.
   */
  FEED("Feed", "left", "#90EE90", 1),

  /**
   * Product/outlet streams - positioned at right of diagram.
   */
  PRODUCT("Product", "right", "#FFA500", 1),

  /**
   * Utility equipment - positioned at side/periphery.
   */
  UTILITY("Utility", "side", "#D3D3D3", 3),

  /**
   * Control equipment - positioned inline with controlled equipment.
   */
  CONTROL("Control", "inline", "#FF69B4", 1),

  /**
   * Unknown equipment role.
   */
  UNKNOWN("Unknown", "center", "#FFFFFF", 1);

  /** Display name for this role. */
  private final String displayName;

  /** Preferred zone in PFD layout. */
  private final String preferredZone;

  /** Default color for this role (hex). */
  private final String defaultColor;

  /** Rank priority for vertical layout (0=top, higher=bottom). */
  private final int rankPriority;

  /**
   * Constructor for EquipmentRole.
   *
   * @param displayName human-readable name
   * @param preferredZone preferred zone in layout
   * @param defaultColor default color in hex format
   * @param rankPriority rank priority for vertical layout
   */
  EquipmentRole(String displayName, String preferredZone, String defaultColor, int rankPriority) {
    this.displayName = displayName;
    this.preferredZone = preferredZone;
    this.defaultColor = defaultColor;
    this.rankPriority = rankPriority;
  }

  /**
   * Gets the display name.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the preferred zone for layout.
   *
   * @return preferred zone
   */
  public String getPreferredZone() {
    return preferredZone;
  }

  /**
   * Gets the default color in hex format.
   *
   * @return hex color string
   */
  public String getDefaultColor() {
    return defaultColor;
  }

  /**
   * Gets the rank priority for vertical layout (0 = top, higher = bottom).
   *
   * @return rank priority
   */
  public int getRankPriority() {
    return rankPriority;
  }
}
