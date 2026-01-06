package neqsim.process.mechanicaldesign.torg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/**
 * Represents a Technical Requirements Document (TORG) that specifies design standards and methods
 * to be used in process design for a project.
 *
 * <p>
 * A TORG typically contains:
 * </p>
 * <ul>
 * <li>Project identification and metadata</li>
 * <li>Design standards to be applied per equipment category</li>
 * <li>Company-specific design requirements</li>
 * <li>Material specifications</li>
 * <li>Safety factors and margins</li>
 * <li>Environmental conditions (ambient temperature, seismic zone, etc.)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
 *     .projectId("PROJECT-001").projectName("Offshore Gas Platform").companyIdentifier("Equinor")
 *     .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1)
 *     .addStandard("separator process design", StandardType.API_12J)
 *     .addStandard("pipeline design codes", StandardType.NORSOK_L_001).minAmbientTemperature(-40.0)
 *     .maxAmbientTemperature(45.0).build();
 *
 * // Apply to a process system
 * processSystem.setTechnicalRequirements(torg);
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class TechnicalRequirementsDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Project identifier. */
  private final String projectId;

  /** Project name/description. */
  private final String projectName;

  /** Company or operator identifier. */
  private final String companyIdentifier;

  /** TORG document revision/version. */
  private final String revision;

  /** Date of issue (ISO format YYYY-MM-DD). */
  private final String issueDate;

  /** Map of design category to list of applicable standards. */
  private final Map<String, List<StandardType>> designStandards;

  /** Map of equipment type to specific standards (overrides category defaults). */
  private final Map<String, List<StandardType>> equipmentStandards;

  /** Environmental design conditions. */
  private final EnvironmentalConditions environmentalConditions;

  /** Safety factors for various calculations. */
  private final SafetyFactors safetyFactors;

  /** Material specifications. */
  private final MaterialSpecifications materialSpecs;

  /** Additional project-specific parameters. */
  private final Map<String, Object> customParameters;

  /**
   * Private constructor - use Builder.
   *
   * @param builder the builder instance
   */
  private TechnicalRequirementsDocument(Builder builder) {
    this.projectId = builder.projectId;
    this.projectName = builder.projectName;
    this.companyIdentifier = builder.companyIdentifier;
    this.revision = builder.revision;
    this.issueDate = builder.issueDate;
    this.designStandards = Collections.unmodifiableMap(new HashMap<>(builder.designStandards));
    this.equipmentStandards =
        Collections.unmodifiableMap(new HashMap<>(builder.equipmentStandards));
    this.environmentalConditions = builder.environmentalConditions;
    this.safetyFactors = builder.safetyFactors;
    this.materialSpecs = builder.materialSpecs;
    this.customParameters = Collections.unmodifiableMap(new HashMap<>(builder.customParameters));
  }

  /**
   * Create a new builder.
   *
   * @return new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Get the project identifier.
   *
   * @return project ID
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Get the project name.
   *
   * @return project name
   */
  public String getProjectName() {
    return projectName;
  }

  /**
   * Get the company identifier.
   *
   * @return company identifier
   */
  public String getCompanyIdentifier() {
    return companyIdentifier;
  }

  /**
   * Get the document revision.
   *
   * @return revision string
   */
  public String getRevision() {
    return revision;
  }

  /**
   * Get the issue date.
   *
   * @return issue date in ISO format
   */
  public String getIssueDate() {
    return issueDate;
  }

  /**
   * Get standards for a specific design category.
   *
   * @param category the design category (e.g., "pressure vessel design code")
   * @return list of applicable standards, empty if none defined
   */
  public List<StandardType> getStandardsForCategory(String category) {
    List<StandardType> standards = designStandards.get(category);
    return standards != null ? new ArrayList<>(standards) : new ArrayList<>();
  }

  /**
   * Get standards specifically assigned to an equipment type.
   *
   * @param equipmentType the equipment type (e.g., "Separator")
   * @return list of applicable standards, empty if none defined
   */
  public List<StandardType> getStandardsForEquipment(String equipmentType) {
    List<StandardType> standards = equipmentStandards.get(equipmentType);
    return standards != null ? new ArrayList<>(standards) : new ArrayList<>();
  }

  /**
   * Get all applicable standards for a given equipment type. This combines equipment-specific
   * standards with category-based standards.
   *
   * @param equipmentType the equipment type
   * @return combined list of applicable standards
   */
  public List<StandardType> getAllApplicableStandards(String equipmentType) {
    List<StandardType> result = new ArrayList<>();

    // Add equipment-specific standards first (higher priority)
    result.addAll(getStandardsForEquipment(equipmentType));

    // Add category-based standards for applicable categories
    for (StandardType type : StandardType.values()) {
      if (type.appliesTo(equipmentType)) {
        String category = type.getDesignStandardCategory();
        List<StandardType> categoryStandards = getStandardsForCategory(category);
        for (StandardType std : categoryStandards) {
          if (!result.contains(std) && std.appliesTo(equipmentType)) {
            result.add(std);
          }
        }
      }
    }

    return result;
  }

  /**
   * Get all design standard categories defined in this TORG.
   *
   * @return set of category names
   */
  public java.util.Set<String> getDefinedCategories() {
    return new java.util.HashSet<>(designStandards.keySet());
  }

  /**
   * Get environmental conditions.
   *
   * @return environmental conditions, or null if not defined
   */
  public EnvironmentalConditions getEnvironmentalConditions() {
    return environmentalConditions;
  }

  /**
   * Get safety factors.
   *
   * @return safety factors, or null if not defined
   */
  public SafetyFactors getSafetyFactors() {
    return safetyFactors;
  }

  /**
   * Get material specifications.
   *
   * @return material specifications, or null if not defined
   */
  public MaterialSpecifications getMaterialSpecifications() {
    return materialSpecs;
  }

  /**
   * Get a custom parameter value.
   *
   * @param key parameter key
   * @return parameter value, or null if not found
   */
  public Object getCustomParameter(String key) {
    return customParameters.get(key);
  }

  /**
   * Get a custom parameter as a specific type.
   *
   * @param <T> the expected type
   * @param key parameter key
   * @param type the class of the expected type
   * @return parameter value cast to the type, or null if not found or wrong type
   */
  public <T> T getCustomParameter(String key, Class<T> type) {
    Object value = customParameters.get(key);
    if (value != null && type.isInstance(value)) {
      return type.cast(value);
    }
    return null;
  }

  /**
   * Check if this TORG has standards defined for a category.
   *
   * @param category the design category
   * @return true if standards are defined
   */
  public boolean hasStandardsForCategory(String category) {
    List<StandardType> standards = designStandards.get(category);
    return standards != null && !standards.isEmpty();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TechnicalRequirementsDocument {\n");
    sb.append("  projectId: ").append(projectId).append("\n");
    sb.append("  projectName: ").append(projectName).append("\n");
    sb.append("  company: ").append(companyIdentifier).append("\n");
    sb.append("  revision: ").append(revision).append("\n");
    sb.append("  issueDate: ").append(issueDate).append("\n");
    sb.append("  designStandards: {\n");
    for (Map.Entry<String, List<StandardType>> entry : designStandards.entrySet()) {
      sb.append("    ").append(entry.getKey()).append(": ");
      List<String> codes = new ArrayList<>();
      for (StandardType st : entry.getValue()) {
        codes.add(st.getCode());
      }
      sb.append(codes).append("\n");
    }
    sb.append("  }\n");
    sb.append("}");
    return sb.toString();
  }

  // ============== INNER CLASSES ==============

  /**
   * Environmental design conditions.
   */
  public static class EnvironmentalConditions implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double minAmbientTemperature;
    private final double maxAmbientTemperature;
    private final double designSeawaterTemperature;
    private final String seismicZone;
    private final double windSpeed;
    private final double waveHeight;
    private final String location;

    /**
     * Constructor.
     *
     * @param minAmbientTemp minimum ambient temperature [C]
     * @param maxAmbientTemp maximum ambient temperature [C]
     * @param seawaterTemp design seawater temperature [C]
     * @param seismicZone seismic zone classification
     * @param windSpeed design wind speed [m/s]
     * @param waveHeight design wave height [m]
     * @param location installation location
     */
    public EnvironmentalConditions(double minAmbientTemp, double maxAmbientTemp,
        double seawaterTemp, String seismicZone, double windSpeed, double waveHeight,
        String location) {
      this.minAmbientTemperature = minAmbientTemp;
      this.maxAmbientTemperature = maxAmbientTemp;
      this.designSeawaterTemperature = seawaterTemp;
      this.seismicZone = seismicZone;
      this.windSpeed = windSpeed;
      this.waveHeight = waveHeight;
      this.location = location;
    }

    /**
     * Get minimum ambient temperature.
     *
     * @return temperature in Celsius
     */
    public double getMinAmbientTemperature() {
      return minAmbientTemperature;
    }

    /**
     * Get maximum ambient temperature.
     *
     * @return temperature in Celsius
     */
    public double getMaxAmbientTemperature() {
      return maxAmbientTemperature;
    }

    /**
     * Get design seawater temperature.
     *
     * @return temperature in Celsius
     */
    public double getDesignSeawaterTemperature() {
      return designSeawaterTemperature;
    }

    /**
     * Get seismic zone.
     *
     * @return seismic zone classification
     */
    public String getSeismicZone() {
      return seismicZone;
    }

    /**
     * Get design wind speed.
     *
     * @return wind speed in m/s
     */
    public double getWindSpeed() {
      return windSpeed;
    }

    /**
     * Get design wave height.
     *
     * @return wave height in m
     */
    public double getWaveHeight() {
      return waveHeight;
    }

    /**
     * Get installation location.
     *
     * @return location string
     */
    public String getLocation() {
      return location;
    }
  }

  /**
   * Safety factors for design calculations.
   */
  public static class SafetyFactors implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double pressureSafetyFactor;
    private final double temperatureSafetyMargin;
    private final double corrosionAllowance;
    private final double wallThicknessTolerance;
    private final double loadFactor;

    /**
     * Constructor.
     *
     * @param pressureSF pressure safety factor (multiplier)
     * @param tempMargin temperature safety margin [C]
     * @param corrosion corrosion allowance [mm]
     * @param wallTolerance wall thickness tolerance (fraction)
     * @param loadFactor load factor (multiplier)
     */
    public SafetyFactors(double pressureSF, double tempMargin, double corrosion,
        double wallTolerance, double loadFactor) {
      this.pressureSafetyFactor = pressureSF;
      this.temperatureSafetyMargin = tempMargin;
      this.corrosionAllowance = corrosion;
      this.wallThicknessTolerance = wallTolerance;
      this.loadFactor = loadFactor;
    }

    /**
     * Get pressure safety factor.
     *
     * @return safety factor (e.g., 1.1 for 10% margin)
     */
    public double getPressureSafetyFactor() {
      return pressureSafetyFactor;
    }

    /**
     * Get temperature safety margin.
     *
     * @return margin in Celsius
     */
    public double getTemperatureSafetyMargin() {
      return temperatureSafetyMargin;
    }

    /**
     * Get corrosion allowance.
     *
     * @return allowance in mm
     */
    public double getCorrosionAllowance() {
      return corrosionAllowance;
    }

    /**
     * Get wall thickness tolerance.
     *
     * @return tolerance as fraction (e.g., 0.125 for 12.5%)
     */
    public double getWallThicknessTolerance() {
      return wallThicknessTolerance;
    }

    /**
     * Get load factor.
     *
     * @return load factor multiplier
     */
    public double getLoadFactor() {
      return loadFactor;
    }
  }

  /**
   * Material specifications.
   */
  public static class MaterialSpecifications implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String defaultPlateMaterial;
    private final String defaultPipeMaterial;
    private final double minDesignTemperature;
    private final double maxDesignTemperature;
    private final boolean requireImpactTesting;
    private final String materialStandard;

    /**
     * Constructor.
     *
     * @param plateMaterial default plate material code
     * @param pipeMaterial default pipe material code
     * @param minTemp minimum design temperature [C]
     * @param maxTemp maximum design temperature [C]
     * @param impactTesting whether impact testing is required
     * @param materialStd material standard (e.g., "ASTM", "EN")
     */
    public MaterialSpecifications(String plateMaterial, String pipeMaterial, double minTemp,
        double maxTemp, boolean impactTesting, String materialStd) {
      this.defaultPlateMaterial = plateMaterial;
      this.defaultPipeMaterial = pipeMaterial;
      this.minDesignTemperature = minTemp;
      this.maxDesignTemperature = maxTemp;
      this.requireImpactTesting = impactTesting;
      this.materialStandard = materialStd;
    }

    /**
     * Get default plate material.
     *
     * @return material code
     */
    public String getDefaultPlateMaterial() {
      return defaultPlateMaterial;
    }

    /**
     * Get default pipe material.
     *
     * @return material code
     */
    public String getDefaultPipeMaterial() {
      return defaultPipeMaterial;
    }

    /**
     * Get minimum design temperature.
     *
     * @return temperature in Celsius
     */
    public double getMinDesignTemperature() {
      return minDesignTemperature;
    }

    /**
     * Get maximum design temperature.
     *
     * @return temperature in Celsius
     */
    public double getMaxDesignTemperature() {
      return maxDesignTemperature;
    }

    /**
     * Check if impact testing is required.
     *
     * @return true if required
     */
    public boolean isImpactTestingRequired() {
      return requireImpactTesting;
    }

    /**
     * Get material standard.
     *
     * @return standard identifier
     */
    public String getMaterialStandard() {
      return materialStandard;
    }
  }

  // ============== BUILDER ==============

  /**
   * Builder for TechnicalRequirementsDocument.
   */
  public static class Builder {
    private String projectId = "";
    private String projectName = "";
    private String companyIdentifier = "";
    private String revision = "1";
    private String issueDate = "";
    private Map<String, List<StandardType>> designStandards = new HashMap<>();
    private Map<String, List<StandardType>> equipmentStandards = new HashMap<>();
    private EnvironmentalConditions environmentalConditions;
    private SafetyFactors safetyFactors;
    private MaterialSpecifications materialSpecs;
    private Map<String, Object> customParameters = new HashMap<>();

    /**
     * Set project ID.
     *
     * @param projectId the project identifier
     * @return this builder
     */
    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    /**
     * Set project name.
     *
     * @param projectName the project name
     * @return this builder
     */
    public Builder projectName(String projectName) {
      this.projectName = projectName;
      return this;
    }

    /**
     * Set company identifier.
     *
     * @param companyIdentifier the company/operator identifier
     * @return this builder
     */
    public Builder companyIdentifier(String companyIdentifier) {
      this.companyIdentifier = companyIdentifier;
      return this;
    }

    /**
     * Set document revision.
     *
     * @param revision the revision string
     * @return this builder
     */
    public Builder revision(String revision) {
      this.revision = revision;
      return this;
    }

    /**
     * Set issue date.
     *
     * @param issueDate the issue date (ISO format YYYY-MM-DD)
     * @return this builder
     */
    public Builder issueDate(String issueDate) {
      this.issueDate = issueDate;
      return this;
    }

    /**
     * Add a standard for a design category.
     *
     * @param category the design category
     * @param standard the standard to add
     * @return this builder
     */
    public Builder addStandard(String category, StandardType standard) {
      List<StandardType> list = designStandards.get(category);
      if (list == null) {
        list = new ArrayList<>();
        designStandards.put(category, list);
      }
      if (!list.contains(standard)) {
        list.add(standard);
      }
      return this;
    }

    /**
     * Set all standards for a category (replaces existing).
     *
     * @param category the design category
     * @param standards the standards to set
     * @return this builder
     */
    public Builder setStandards(String category, List<StandardType> standards) {
      designStandards.put(category, new ArrayList<>(standards));
      return this;
    }

    /**
     * Add a standard for a specific equipment type.
     *
     * @param equipmentType the equipment type
     * @param standard the standard to add
     * @return this builder
     */
    public Builder addEquipmentStandard(String equipmentType, StandardType standard) {
      List<StandardType> list = equipmentStandards.get(equipmentType);
      if (list == null) {
        list = new ArrayList<>();
        equipmentStandards.put(equipmentType, list);
      }
      if (!list.contains(standard)) {
        list.add(standard);
      }
      return this;
    }

    /**
     * Set environmental conditions.
     *
     * @param conditions the environmental conditions
     * @return this builder
     */
    public Builder environmentalConditions(EnvironmentalConditions conditions) {
      this.environmentalConditions = conditions;
      return this;
    }

    /**
     * Set environmental conditions with common parameters.
     *
     * @param minAmbientTemp minimum ambient temperature [C]
     * @param maxAmbientTemp maximum ambient temperature [C]
     * @return this builder
     */
    public Builder environmentalConditions(double minAmbientTemp, double maxAmbientTemp) {
      this.environmentalConditions =
          new EnvironmentalConditions(minAmbientTemp, maxAmbientTemp, 4.0, "0", 0, 0, "");
      return this;
    }

    /**
     * Set safety factors.
     *
     * @param safetyFactors the safety factors
     * @return this builder
     */
    public Builder safetyFactors(SafetyFactors safetyFactors) {
      this.safetyFactors = safetyFactors;
      return this;
    }

    /**
     * Set material specifications.
     *
     * @param materialSpecs the material specifications
     * @return this builder
     */
    public Builder materialSpecifications(MaterialSpecifications materialSpecs) {
      this.materialSpecs = materialSpecs;
      return this;
    }

    /**
     * Add a custom parameter.
     *
     * @param key parameter key
     * @param value parameter value
     * @return this builder
     */
    public Builder customParameter(String key, Object value) {
      this.customParameters.put(key, value);
      return this;
    }

    /**
     * Build the TechnicalRequirementsDocument.
     *
     * @return the constructed document
     */
    public TechnicalRequirementsDocument build() {
      return new TechnicalRequirementsDocument(this);
    }
  }
}
