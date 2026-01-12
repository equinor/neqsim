package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * Enum representing different types of compressor drivers.
 *
 * <p>
 * Each driver type has different characteristics for power delivery, efficiency, and response time.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum DriverType implements Serializable {
  /**
   * Electric motor driver. Fast response, constant torque, high efficiency at rated speed.
   */
  ELECTRIC_MOTOR("Electric Motor", 0.95, 1.0, true),

  /**
   * Variable frequency drive electric motor. Allows continuous speed variation with high
   * efficiency.
   */
  VFD_MOTOR("VFD Electric Motor", 0.93, 5.0, true),

  /**
   * Gas turbine driver. Variable speed, power dependent on ambient conditions.
   */
  GAS_TURBINE("Gas Turbine", 0.35, 30.0, false),

  /**
   * Steam turbine driver. Variable speed, power from steam supply.
   */
  STEAM_TURBINE("Steam Turbine", 0.40, 20.0, false),

  /**
   * Reciprocating engine driver. High efficiency, limited speed range.
   */
  RECIPROCATING_ENGINE("Reciprocating Engine", 0.42, 15.0, false),

  /**
   * Direct drive from process expander. No external power required.
   */
  EXPANDER_DRIVE("Expander Drive", 0.85, 5.0, false);

  private final String displayName;
  private final double typicalEfficiency;
  private final double typicalResponseTime;
  private final boolean isElectric;

  /**
   * Constructor for DriverType.
   *
   * @param displayName human-readable name
   * @param typicalEfficiency typical driver efficiency (0-1)
   * @param typicalResponseTime typical response time in seconds
   * @param isElectric true if electrically driven
   */
  DriverType(String displayName, double typicalEfficiency, double typicalResponseTime,
      boolean isElectric) {
    this.displayName = displayName;
    this.typicalEfficiency = typicalEfficiency;
    this.typicalResponseTime = typicalResponseTime;
    this.isElectric = isElectric;
  }

  /**
   * Get the human-readable display name.
   *
   * @return display name string
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get the typical efficiency for this driver type.
   *
   * @return efficiency as ratio (0-1)
   */
  public double getTypicalEfficiency() {
    return typicalEfficiency;
  }

  /**
   * Get the typical response time for speed changes.
   *
   * @return response time in seconds
   */
  public double getTypicalResponseTime() {
    return typicalResponseTime;
  }

  /**
   * Check if this is an electric driver.
   *
   * @return true if electrically driven
   */
  public boolean isElectric() {
    return isElectric;
  }

  /**
   * Check if this driver type supports continuous speed variation.
   *
   * @return true if variable speed is supported
   */
  public boolean supportsVariableSpeed() {
    return this != ELECTRIC_MOTOR; // Fixed-speed motors don't support variable speed
  }

  /**
   * Get driver type by name (case-insensitive).
   *
   * @param name the name to search for
   * @return the matching DriverType, or ELECTRIC_MOTOR as default
   */
  public static DriverType fromName(String name) {
    if (name == null) {
      return ELECTRIC_MOTOR;
    }
    String upperName = name.toUpperCase().replace(" ", "_").replace("-", "_");
    for (DriverType type : values()) {
      if (type.name().equals(upperName)
          || type.displayName.toUpperCase().replace(" ", "_").equals(upperName)) {
        return type;
      }
    }
    // Check partial matches
    if (upperName.contains("GAS") && upperName.contains("TURBINE")) {
      return GAS_TURBINE;
    }
    if (upperName.contains("STEAM")) {
      return STEAM_TURBINE;
    }
    if (upperName.contains("VFD") || upperName.contains("VARIABLE")) {
      return VFD_MOTOR;
    }
    if (upperName.contains("EXPANDER")) {
      return EXPANDER_DRIVE;
    }
    if (upperName.contains("RECIP") || upperName.contains("ENGINE")) {
      return RECIPROCATING_ENGINE;
    }
    return ELECTRIC_MOTOR;
  }
}
