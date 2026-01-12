package neqsim.process.mechanicaldesign.designstandards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Registry and factory for creating design standards based on international standards.
 *
 * <p>
 * The StandardRegistry provides:
 * </p>
 * <ul>
 * <li>Factory methods to create DesignStandard instances from StandardType enum</li>
 * <li>Standard discovery and lookup capabilities</li>
 * <li>Standard version management</li>
 * <li>Mapping between international standards and NeqSim design standard classes</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * // Get a pressure vessel standard
 * DesignStandard pvStandard =
 *     StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1, mechanicalDesign);
 *
 * // Find all applicable standards for a separator
 * List&lt;StandardType&gt; sepStandards = StandardRegistry.getApplicableStandards("Separator");
 *
 * // Get all NORSOK standards
 * List&lt;StandardType&gt; norsokStandards = StandardRegistry.getStandardsByOrganization("NORSOK");
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public final class StandardRegistry {

  /** Map of standard type to custom version overrides. */
  private static final Map<StandardType, String> versionOverrides =
      new HashMap<StandardType, String>();

  /** Private constructor to prevent instantiation. */
  private StandardRegistry() {
    // Utility class
  }

  /**
   * Create a DesignStandard instance for the given standard type.
   *
   * <p>
   * This factory method creates the appropriate DesignStandard subclass based on the standard
   * type's category. The standard name is set to the standard code.
   * </p>
   *
   * @param standardType the international standard type
   * @param equipment the mechanical design equipment context
   * @return a new DesignStandard instance
   * @throws IllegalArgumentException if standardType is null
   */
  public static DesignStandard createStandard(StandardType standardType,
      MechanicalDesign equipment) {
    return createStandard(standardType, null, equipment);
  }

  /**
   * Create a DesignStandard instance for the given standard type with a specific version.
   *
   * @param standardType the international standard type
   * @param version the specific version to use (null for default)
   * @param equipment the mechanical design equipment context
   * @return a new DesignStandard instance
   * @throws IllegalArgumentException if standardType is null
   */
  public static DesignStandard createStandard(StandardType standardType, String version,
      MechanicalDesign equipment) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }

    String effectiveVersion = version != null ? version : getEffectiveVersion(standardType);
    String standardName = standardType.getCode() + " " + effectiveVersion;

    String category = standardType.getDesignStandardCategory();

    // Create appropriate standard based on category
    switch (category) {
      case "pressure vessel design code":
        return new PressureVesselDesignStandard(standardName, equipment);

      case "separator process design":
        return new SeparatorDesignStandard(standardName, equipment);

      case "gas scrubber process design":
        return new GasScrubberDesignStandard(standardName, equipment);

      case "pipeline design codes":
        return new PipelineDesignStandard(standardName, equipment);

      case "compressor design codes":
        return new CompressorDesignStandard(standardName, equipment);

      case "material plate design codes":
        return new MaterialPlateDesignStandard(standardName, equipment);

      case "material pipe design codes":
        return new MaterialPipeDesignStandard(standardName, equipment);

      case "valve design codes":
        return new ValveDesignStandard(standardName, equipment);

      default:
        // Return base DesignStandard for unknown categories
        return new DesignStandard(standardName, equipment);
    }
  }

  /**
   * Get the effective version for a standard type, considering any overrides.
   *
   * @param standardType the standard type
   * @return the effective version string
   */
  public static String getEffectiveVersion(StandardType standardType) {
    if (standardType == null) {
      return "";
    }
    String override = versionOverrides.get(standardType);
    return override != null ? override : standardType.getDefaultVersion();
  }

  /**
   * Set a version override for a standard type.
   *
   * @param standardType the standard type to override
   * @param version the version to use (null to clear override)
   */
  public static void setVersionOverride(StandardType standardType, String version) {
    if (standardType == null) {
      return;
    }
    if (version == null) {
      versionOverrides.remove(standardType);
    } else {
      versionOverrides.put(standardType, version);
    }
  }

  /**
   * Clear all version overrides.
   */
  public static void clearVersionOverrides() {
    versionOverrides.clear();
  }

  /**
   * Get all standards applicable to a given equipment class name.
   *
   * @param equipmentClassName the simple class name of the equipment (e.g., "Separator")
   * @return list of applicable standards
   */
  public static List<StandardType> getApplicableStandards(String equipmentClassName) {
    return StandardType.getApplicableStandards(equipmentClassName);
  }

  /**
   * Get all standards from a specific organization.
   *
   * @param organization the organization code (e.g., "NORSOK", "ASME", "API", "DNV", "ISO", "ASTM")
   * @return list of standards from that organization
   */
  public static List<StandardType> getStandardsByOrganization(String organization) {
    if (organization == null) {
      return new ArrayList<StandardType>();
    }
    String org = organization.trim().toUpperCase();

    switch (org) {
      case "NORSOK":
        return StandardType.getNorsokStandards();
      case "ASME":
        return StandardType.getAsmeStandards();
      case "API":
        return StandardType.getApiStandards();
      case "DNV":
        return StandardType.getDnvStandards();
      case "ISO":
        return StandardType.getIsoStandards();
      case "ASTM":
        return StandardType.getAstmStandards();
      case "EN":
        return StandardType.getEnStandards();
      default:
        // Search by prefix
        List<StandardType> result = new ArrayList<StandardType>();
        for (StandardType type : StandardType.values()) {
          if (type.getCode().toUpperCase().startsWith(org)) {
            result.add(type);
          }
        }
        return result;
    }
  }

  /**
   * Get all standards for a specific design category.
   *
   * @param category the design standard category (e.g., "pressure vessel design code")
   * @return list of standards in that category
   */
  public static List<StandardType> getStandardsByCategory(String category) {
    return StandardType.getByCategory(category);
  }

  /**
   * Find a standard by its code.
   *
   * @param code the standard code (e.g., "ASME-VIII-Div1", "NORSOK-L-001")
   * @return the matching StandardType or null if not found
   */
  public static StandardType findByCode(String code) {
    return StandardType.fromCode(code);
  }

  /**
   * Get all available design standard categories.
   *
   * @return list of category keys
   */
  public static List<String> getAllCategories() {
    return StandardType.getAllCategories();
  }

  /**
   * Get all available standard types.
   *
   * @return array of all StandardType values
   */
  public static StandardType[] getAllStandards() {
    return StandardType.values();
  }

  /**
   * Check if a standard type is applicable to an equipment type.
   *
   * @param standardType the standard type to check
   * @param equipmentClassName the equipment class name
   * @return true if the standard applies to the equipment
   */
  public static boolean isApplicable(StandardType standardType, String equipmentClassName) {
    if (standardType == null) {
      return false;
    }
    return standardType.appliesTo(equipmentClassName);
  }

  /**
   * Get recommended standards for an equipment type organized by category.
   *
   * @param equipmentClassName the equipment class name
   * @return map of category to list of applicable standards
   */
  public static Map<String, List<StandardType>> getRecommendedStandards(String equipmentClassName) {
    Map<String, List<StandardType>> result = new HashMap<String, List<StandardType>>();

    List<StandardType> applicable = getApplicableStandards(equipmentClassName);
    for (StandardType type : applicable) {
      String category = type.getDesignStandardCategory();
      if (!result.containsKey(category)) {
        result.put(category, new ArrayList<StandardType>());
      }
      result.get(category).add(type);
    }

    return result;
  }

  /**
   * Get a summary of available standards as a formatted string.
   *
   * @return formatted string listing all standards by organization
   */
  public static String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Available Design Standards:\n");
    sb.append("===========================\n\n");

    String[] orgs = {"NORSOK", "ASME", "API", "DNV", "ISO", "ASTM", "EN", "PD"};
    for (String org : orgs) {
      List<StandardType> standards = getStandardsByOrganization(org);
      if (!standards.isEmpty()) {
        sb.append(org).append(" Standards:\n");
        for (StandardType std : standards) {
          sb.append("  - ").append(std.toString()).append("\n");
          sb.append("      Category: ").append(std.getDesignStandardCategory()).append("\n");
        }
        sb.append("\n");
      }
    }

    return sb.toString();
  }
}
