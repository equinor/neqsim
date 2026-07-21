package neqsim.process.mechanicaldesign.designstandards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.EquipmentDesignKernel;
import neqsim.process.engineering.calculation.EquipmentDesignKernelRegistry;
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
 * DesignStandard pvStandard = StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1, mechanicalDesign);
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
  private static final Map<StandardType, String> versionOverrides = new HashMap<StandardType, String>();

  /** Private constructor to prevent instantiation. */
  private StandardRegistry() {
    // Utility class
  }

  /**
   * Create a DesignStandard instance for the given standard type.
   *
   * <p>
   * This factory method creates the appropriate DesignStandard subclass based on the standard type's category. The
   * standard name is set to the standard code.
   * </p>
   *
   * @param standardType the international standard type
   * @param equipment the mechanical design equipment context
   * @return a new DesignStandard instance
   * @throws IllegalArgumentException if standardType is null
   */
  public static DesignStandard createStandard(StandardType standardType, MechanicalDesign equipment) {
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
  public static DesignStandard createStandard(StandardType standardType, String version, MechanicalDesign equipment) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }

    String effectiveVersion = version != null ? version : getEffectiveVersion(standardType);
    String standardName = standardType.getCode() + " " + effectiveVersion;

    return instantiateStandard(standardType, standardName, equipment);
  }

  /**
   * Create a standard from a typed edition selection.
   *
   * <p>
   * Strict selections fail closed when the standard is catalog-only, its calculation is not connected to this registry,
   * the equipment context is missing, or the standard is not listed for the equipment type. Legacy-compatible
   * selections preserve the permissive factory behavior while still using an explicit, reproducible edition.
   * </p>
   *
   * @param selection typed standard selection
   * @param equipment mechanical design equipment context
   * @return a new DesignStandard instance
   * @throws StandardSelectionException if a strict selection cannot be honored
   */
  public static DesignStandard createStandard(StandardSelection selection, MechanicalDesign equipment) {
    if (selection == null) {
      throw new StandardSelectionException(StandardSelectionException.Reason.MISSING_SELECTION, null, null,
          "selection cannot be null");
    }

    StandardType standardType = selection.getStandardType();
    if (selection.getMode() == StandardSelection.Mode.STRICT) {
      StandardSupport support = StandardSupportAudit.getSupport(standardType);
      if (support.getSupportLevel() == StandardSupportLevel.CATALOGUED) {
        throw new StandardSelectionException(StandardSelectionException.Reason.CATALOG_ONLY, standardType, null,
            support.getLimitation());
      }
      if (!support.isRegistryConnected()) {
        throw new StandardSelectionException(StandardSelectionException.Reason.NOT_REGISTRY_CONNECTED, standardType,
            null, support.getLimitation());
      }
      EquipmentDesignKernelRegistry.Lookup kernel = getDesignKernel(standardType);
      if (kernel.isImplemented() && !kernel.supports(selection.getEdition())) {
        throw new StandardSelectionException(StandardSelectionException.Reason.EDITION_NOT_IMPLEMENTED, standardType,
            null, "No registered kernel implements " + selection.getEdition().getDisplayName());
      }

      StandardApplicability applicability = assessApplicability(standardType, equipment);
      if (applicability.getStatus() == StandardApplicability.Status.UNKNOWN) {
        throw new StandardSelectionException(StandardSelectionException.Reason.MISSING_EQUIPMENT, standardType, null,
            applicability.getReason());
      }
      if (applicability.getStatus() == StandardApplicability.Status.NOT_APPLICABLE) {
        throw new StandardSelectionException(StandardSelectionException.Reason.NOT_APPLICABLE, standardType,
            applicability.getEquipmentType(), applicability.getReason());
      }
    }

    return instantiateStandard(standardType, selection.getEdition().getDisplayName(), equipment);
  }

  /**
   * Assess applicability using the process equipment represented by a mechanical design.
   *
   * @param standardType standard to assess
   * @param equipment mechanical design equipment context
   * @return structured applicability result
   * @throws IllegalArgumentException if {@code standardType} is null
   */
  public static StandardApplicability assessApplicability(StandardType standardType, MechanicalDesign equipment) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    if (equipment == null || equipment.getProcessEquipment() == null) {
      return StandardApplicability.unknown(standardType,
          "A mechanical design with process equipment is required to assess applicability.");
    }

    String equipmentType = equipment.getProcessEquipment().getClass().getSimpleName();
    return StandardApplicability.assess(standardType, equipmentType);
  }

  /**
   * Look up the standard-specific design kernel exposed through the engineering workflow.
   *
   * @param standardType standard to inspect
   * @return explicit implemented or not-implemented lookup
   */
  public static EquipmentDesignKernelRegistry.Lookup getDesignKernel(StandardType standardType) {
    return EquipmentDesignKernelRegistry.lookup(standardType);
  }

  /**
   * Require an executable common kernel for an explicit standard edition.
   *
   * <p>
   * This is the fail-closed migration path from a metadata/factory selection to the typed engineering calculation API.
   * Applicability to the concrete input remains enforced by the returned kernel.
   * </p>
   *
   * @param selection explicit standard and edition basis
   * @return registered typed-kernel contract
   * @throws StandardSelectionException when the selection is missing, no kernel exists, or the edition is unsupported
   */
  public static EquipmentDesignKernel<?, ?> requireDesignKernel(StandardSelection selection) {
    if (selection == null) {
      throw new StandardSelectionException(StandardSelectionException.Reason.MISSING_SELECTION, null, null,
          "selection cannot be null");
    }
    StandardType standardType = selection.getStandardType();
    EquipmentDesignKernelRegistry.Lookup lookup = getDesignKernel(standardType);
    if (!lookup.isImplemented()) {
      throw new StandardSelectionException(StandardSelectionException.Reason.KERNEL_NOT_IMPLEMENTED, standardType, null,
          "No common engineering design kernel is registered for the selected standard");
    }
    if (!lookup.supports(selection.getEdition())) {
      throw new StandardSelectionException(StandardSelectionException.Reason.EDITION_NOT_IMPLEMENTED, standardType,
          null, "No registered kernel implements " + selection.getEdition().getDisplayName());
    }
    return lookup.requireKernel();
  }

  private static DesignStandard instantiateStandard(StandardType standardType, String standardName,
      MechanicalDesign equipment) {

    Class<? extends DesignStandard> implementationClass = getMappedImplementationClass(standardType);

    if (implementationClass == PressureVesselDesignStandard.class) {
      return new PressureVesselDesignStandard(standardName, equipment);
    } else if (implementationClass == SeparatorDesignStandard.class) {
      return new SeparatorDesignStandard(standardName, equipment);
    } else if (implementationClass == GasScrubberDesignStandard.class) {
      return new GasScrubberDesignStandard(standardName, equipment);
    } else if (implementationClass == PipelineDesignStandard.class) {
      return new PipelineDesignStandard(standardName, equipment);
    } else if (implementationClass == CompressorDesignStandard.class) {
      return new CompressorDesignStandard(standardName, equipment);
    } else if (implementationClass == MaterialPlateDesignStandard.class) {
      return new MaterialPlateDesignStandard(standardName, equipment);
    } else if (implementationClass == MaterialPipeDesignStandard.class) {
      return new MaterialPipeDesignStandard(standardName, equipment);
    } else if (implementationClass == ValveDesignStandard.class) {
      return new ValveDesignStandard(standardName, equipment);
    }
    return new DesignStandard(standardName, equipment);
  }

  /**
   * Get the class selected by the category-based standards factory.
   *
   * <p>
   * A mapping to {@link DesignStandard} means that the registry only creates a metadata holder; it does not imply that
   * the named standard has an executable calculation. Use {@link StandardSupportAudit#getSupport(StandardType)} to
   * inspect the implementation evidence.
   * </p>
   *
   * @param standardType standard to inspect
   * @return class selected by {@link #createStandard(StandardType, MechanicalDesign)}
   * @throws IllegalArgumentException if {@code standardType} is null
   */
  public static Class<? extends DesignStandard> getMappedImplementationClass(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }

    if (standardType == StandardType.API_521 || standardType == StandardType.API_526) {
      return ValveDesignStandard.class;
    }

    String category = standardType.getDesignStandardCategory();
    switch (category) {
    case "pressure vessel design code":
      return PressureVesselDesignStandard.class;
    case "separator process design":
      return SeparatorDesignStandard.class;
    case "gas scrubber process design":
      return GasScrubberDesignStandard.class;
    case "pipeline design codes":
      return PipelineDesignStandard.class;
    case "compressor design codes":
      return CompressorDesignStandard.class;
    case "material plate design codes":
      return MaterialPlateDesignStandard.class;
    case "material pipe design codes":
      return MaterialPipeDesignStandard.class;
    case "valve design codes":
      return ValveDesignStandard.class;
    default:
      return DesignStandard.class;
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
   * Set a process-global version override for legacy callers.
   *
   * @param standardType the standard type to override
   * @param version the version to use (null to clear override)
   * @deprecated use an explicit {@link StandardEdition} inside {@link StandardSelection}; global mutable edition state
   * is not reproducible across concurrent designs
   */
  @Deprecated
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
   * Clear process-global version overrides retained for legacy callers.
   *
   * @deprecated explicit {@link StandardSelection} values require no global cleanup
   */
  @Deprecated
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

    String[] orgs = { "NORSOK", "ASME", "API", "DNV", "ISO", "ASTM", "EN", "PD" };
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
