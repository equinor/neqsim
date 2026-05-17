package neqsim.process.util.topology;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a functional location tag following STID/ISO 14224 conventions.
 *
 * <p>
 * Format: PPPP-TT-NNNNN[S] where:
 * </p>
 * <ul>
 * <li>PPPP = Platform/Installation code (e.g., 1775 = Gullfaks C)</li>
 * <li>TT = Equipment type code (KA=compressor, PA=pump, VA=valve, etc.)</li>
 * <li>NNNNN = Sequential number</li>
 * <li>S = Suffix for parallel units (A, B, C...)</li>
 * </ul>
 *
 * <p>
 * Example: 1775-KA-23011A = Gullfaks C, Compressor, unit 23011, train A
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class FunctionalLocation implements Serializable, Comparable<FunctionalLocation> {

  private static final long serialVersionUID = 1000L;

  /** Pattern for parsing STID tags. */
  private static final Pattern STID_PATTERN = Pattern.compile("(\\d{4})-(\\w{2})-(\\d{5})([A-Z])?");

  // Installation codes (examples)
  /** Gullfaks A installation code. */
  public static final String GULLFAKS_A = "1770";
  /** Gullfaks B installation code. */
  public static final String GULLFAKS_B = "1773";
  /** Gullfaks C installation code. */
  public static final String GULLFAKS_C = "1775";
  /** Åsgard A installation code. */
  public static final String ASGARD_A = "2540";
  /** Åsgard B installation code. */
  public static final String ASGARD_B = "2541";
  /** Åsgard C installation code. */
  public static final String ASGARD_C = "2542";
  /** Troll A installation code. */
  public static final String TROLL_A = "1910";
  /** Oseberg A installation code. */
  public static final String OSEBERG_A = "1820";

  // Equipment type codes (ISO 14224 / NORSOK)
  /** Compressor type code. */
  public static final String TYPE_COMPRESSOR = "KA";
  /** Pump type code. */
  public static final String TYPE_PUMP = "PA";
  /** Valve type code. */
  public static final String TYPE_VALVE = "VA";
  /** Heat exchanger type code. */
  public static final String TYPE_HEAT_EXCHANGER = "WA";
  /** Separator type code. */
  public static final String TYPE_SEPARATOR = "VG";
  /** Turbine type code. */
  public static final String TYPE_TURBINE = "GA";
  /** Electric motor type code. */
  public static final String TYPE_MOTOR = "MA";
  /** Tank type code. */
  public static final String TYPE_TANK = "TK";
  /** Pipeline type code. */
  public static final String TYPE_PIPELINE = "PL";
  /** Cooler type code. */
  public static final String TYPE_COOLER = "WC";
  /** Heater type code. */
  public static final String TYPE_HEATER = "WH";

  private String installationCode;
  private String installationName;
  private String equipmentTypeCode;
  private String sequentialNumber;
  private String trainSuffix;
  private String fullTag;

  // Additional metadata
  private String system;
  private String subsystem;
  private String area;
  private String description;

  /**
   * Creates a functional location from a full STID tag.
   *
   * @param stidTag the full STID tag (e.g., "1775-KA-23011A")
   */
  public FunctionalLocation(String stidTag) {
    parseTag(stidTag);
  }

  /**
   * Creates a functional location with individual components.
   *
   * @param installationCode the installation code (e.g., "1775")
   * @param equipmentTypeCode the equipment type code (e.g., "KA")
   * @param sequentialNumber the sequential number (e.g., "23011")
   * @param trainSuffix the train suffix (e.g., "A") or null
   */
  public FunctionalLocation(String installationCode, String equipmentTypeCode,
      String sequentialNumber, String trainSuffix) {
    this.installationCode = installationCode;
    this.equipmentTypeCode = equipmentTypeCode;
    this.sequentialNumber = sequentialNumber;
    this.trainSuffix = trainSuffix;
    this.fullTag = buildTag();
    this.installationName = getInstallationNameFromCode(installationCode);
  }

  private void parseTag(String tag) {
    this.fullTag = tag;
    Matcher matcher = STID_PATTERN.matcher(tag);
    if (matcher.matches()) {
      this.installationCode = matcher.group(1);
      this.equipmentTypeCode = matcher.group(2);
      this.sequentialNumber = matcher.group(3);
      this.trainSuffix = matcher.group(4);
      this.installationName = getInstallationNameFromCode(installationCode);
    } else {
      // Non-standard format - store as-is
      this.installationCode = "";
      this.equipmentTypeCode = "";
      this.sequentialNumber = tag;
      this.trainSuffix = null;
    }
  }

  private String buildTag() {
    StringBuilder sb = new StringBuilder();
    sb.append(installationCode).append("-");
    sb.append(equipmentTypeCode).append("-");
    sb.append(sequentialNumber);
    if (trainSuffix != null && !trainSuffix.isEmpty()) {
      sb.append(trainSuffix);
    }
    return sb.toString();
  }

  private String getInstallationNameFromCode(String code) {
    if (code == null) {
      return "Unknown";
    }
    switch (code) {
      case "1770":
        return "Gullfaks A";
      case "1773":
        return "Gullfaks B";
      case "1775":
        return "Gullfaks C";
      case "2540":
        return "Åsgard A";
      case "2541":
        return "Åsgard B";
      case "2542":
        return "Åsgard C";
      case "1910":
        return "Troll A";
      case "1820":
        return "Oseberg A";
      default:
        return "Installation " + code;
    }
  }

  /**
   * Gets the equipment type description from code.
   *
   * @return human-readable equipment type
   */
  public String getEquipmentTypeDescription() {
    if (equipmentTypeCode == null) {
      return "Unknown";
    }
    switch (equipmentTypeCode) {
      case "KA":
        return "Compressor";
      case "PA":
        return "Pump";
      case "VA":
        return "Valve";
      case "WA":
        return "Heat Exchanger";
      case "VG":
        return "Separator";
      case "GA":
        return "Turbine";
      case "MA":
        return "Electric Motor";
      case "TK":
        return "Tank";
      case "PL":
        return "Pipeline";
      case "WC":
        return "Cooler";
      case "WH":
        return "Heater";
      default:
        return "Type " + equipmentTypeCode;
    }
  }

  /**
   * Checks if this is a parallel unit (has train suffix).
   *
   * @return true if parallel unit
   */
  public boolean isParallelUnit() {
    return trainSuffix != null && !trainSuffix.isEmpty();
  }

  /**
   * Gets the base tag without train suffix (for finding parallel units).
   *
   * @return base tag without suffix
   */
  public String getBaseTag() {
    return installationCode + "-" + equipmentTypeCode + "-" + sequentialNumber;
  }

  /**
   * Checks if this equipment is on the same installation.
   *
   * @param other other functional location
   * @return true if same installation
   */
  public boolean isSameInstallation(FunctionalLocation other) {
    return this.installationCode != null && this.installationCode.equals(other.installationCode);
  }

  /**
   * Checks if this is a parallel train to another equipment.
   *
   * @param other other functional location
   * @return true if parallel trains (same base tag, different suffix)
   */
  public boolean isParallelTo(FunctionalLocation other) {
    return this.getBaseTag().equals(other.getBaseTag()) && !this.fullTag.equals(other.fullTag);
  }

  /**
   * Checks if this is in the same system (same first 2 digits of sequential number).
   *
   * @param other other functional location
   * @return true if same system
   */
  public boolean isSameSystem(FunctionalLocation other) {
    if (sequentialNumber == null || other.sequentialNumber == null) {
      return false;
    }
    if (sequentialNumber.length() < 2 || other.sequentialNumber.length() < 2) {
      return false;
    }
    return sequentialNumber.substring(0, 2).equals(other.sequentialNumber.substring(0, 2));
  }

  // Builder for creating functional locations
  /**
   * Creates a new builder for FunctionalLocation.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for FunctionalLocation.
   */
  public static class Builder {
    private String installationCode;
    private String equipmentTypeCode;
    private String sequentialNumber;
    private String trainSuffix;
    private String system;
    private String description;

    /**
     * Sets the installation code.
     *
     * @param code installation code
     * @return this builder
     */
    public Builder installation(String code) {
      this.installationCode = code;
      return this;
    }

    /**
     * Sets the equipment type code.
     *
     * @param type equipment type code
     * @return this builder
     */
    public Builder type(String type) {
      this.equipmentTypeCode = type;
      return this;
    }

    /**
     * Sets the sequential number.
     *
     * @param number sequential number
     * @return this builder
     */
    public Builder number(String number) {
      this.sequentialNumber = number;
      return this;
    }

    /**
     * Sets the train suffix.
     *
     * @param suffix train suffix (A, B, C...)
     * @return this builder
     */
    public Builder train(String suffix) {
      this.trainSuffix = suffix;
      return this;
    }

    /**
     * Sets the system.
     *
     * @param system system name
     * @return this builder
     */
    public Builder system(String system) {
      this.system = system;
      return this;
    }

    /**
     * Sets the description.
     *
     * @param description description
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Builds the FunctionalLocation.
     *
     * @return new FunctionalLocation
     */
    public FunctionalLocation build() {
      FunctionalLocation loc = new FunctionalLocation(installationCode, equipmentTypeCode,
          sequentialNumber, trainSuffix);
      loc.setSystem(system);
      loc.setDescription(description);
      return loc;
    }
  }

  // Getters and setters
  /**
   * Gets the installation code.
   *
   * @return installation code
   */
  public String getInstallationCode() {
    return installationCode;
  }

  /**
   * Gets the installation name.
   *
   * @return installation name
   */
  public String getInstallationName() {
    return installationName;
  }

  /**
   * Gets the equipment type code.
   *
   * @return equipment type code
   */
  public String getEquipmentTypeCode() {
    return equipmentTypeCode;
  }

  /**
   * Gets the sequential number.
   *
   * @return sequential number
   */
  public String getSequentialNumber() {
    return sequentialNumber;
  }

  /**
   * Gets the train suffix.
   *
   * @return train suffix or null
   */
  public String getTrainSuffix() {
    return trainSuffix;
  }

  /**
   * Gets the full STID tag.
   *
   * @return full tag
   */
  public String getFullTag() {
    return fullTag;
  }

  /**
   * Gets the system.
   *
   * @return system
   */
  public String getSystem() {
    return system;
  }

  /**
   * Sets the system.
   *
   * @param system system
   */
  public void setSystem(String system) {
    this.system = system;
  }

  /**
   * Gets the subsystem.
   *
   * @return subsystem
   */
  public String getSubsystem() {
    return subsystem;
  }

  /**
   * Sets the subsystem.
   *
   * @param subsystem subsystem
   */
  public void setSubsystem(String subsystem) {
    this.subsystem = subsystem;
  }

  /**
   * Gets the area.
   *
   * @return area
   */
  public String getArea() {
    return area;
  }

  /**
   * Sets the area.
   *
   * @param area area
   */
  public void setArea(String area) {
    this.area = area;
  }

  /**
   * Gets the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description.
   *
   * @param description description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public int compareTo(FunctionalLocation other) {
    return this.fullTag.compareTo(other.fullTag);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    FunctionalLocation other = (FunctionalLocation) obj;
    return fullTag != null && fullTag.equals(other.fullTag);
  }

  @Override
  public int hashCode() {
    return fullTag != null ? fullTag.hashCode() : 0;
  }

  @Override
  public String toString() {
    return fullTag;
  }
}
