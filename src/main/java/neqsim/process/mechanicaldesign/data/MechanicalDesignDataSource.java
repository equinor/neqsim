package neqsim.process.mechanicaldesign.data;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import neqsim.process.mechanicaldesign.DesignLimitData;

/**
 * Data source used to supply mechanical design limits for process equipment.
 *
 * <p>
 * Implementations can load data from various sources including:
 * </p>
 * <ul>
 * <li>CSV files with company/standard specifications</li>
 * <li>Database tables</li>
 * <li>API endpoints</li>
 * <li>Configuration files</li>
 * </ul>
 *
 * @author esol
 * @version 1.1
 */
public interface MechanicalDesignDataSource {
  /**
   * Retrieve design limit data for a given equipment type and company identifier.
   *
   * @param equipmentTypeName canonical equipment type identifier (e.g. "Pipeline").
   * @param companyIdentifier company specific design code identifier.
   * @return optional design limit data if available.
   */
  Optional<DesignLimitData> getDesignLimits(String equipmentTypeName, String companyIdentifier);

  /**
   * Retrieve design limit data for a given equipment type based on an international standard.
   *
   * <p>
   * This method allows looking up design limits by standard code (e.g., "NORSOK-L-001") rather than
   * company identifier.
   * </p>
   *
   * @param standardCode the international standard code (e.g., "NORSOK-L-001", "ASME-VIII-Div1")
   * @param version the standard version (e.g., "Rev 6", "2021"), null for default
   * @param equipmentTypeName canonical equipment type identifier
   * @return optional design limit data if available
   */
  default Optional<DesignLimitData> getDesignLimitsByStandard(String standardCode, String version,
      String equipmentTypeName) {
    // Default implementation falls back to company-based lookup using standard code as identifier
    return getDesignLimits(equipmentTypeName, standardCode);
  }

  /**
   * Get a list of available standards in this data source.
   *
   * @param equipmentTypeName the equipment type to filter by (null for all)
   * @return list of standard codes available
   */
  default List<String> getAvailableStandards(String equipmentTypeName) {
    return Collections.emptyList();
  }

  /**
   * Get a list of available versions for a given standard.
   *
   * @param standardCode the standard code to query
   * @return list of available versions
   */
  default List<String> getAvailableVersions(String standardCode) {
    return Collections.emptyList();
  }

  /**
   * Check if this data source contains data for a specific standard.
   *
   * @param standardCode the standard code to check
   * @return true if data is available for this standard
   */
  default boolean hasStandard(String standardCode) {
    List<String> standards = getAvailableStandards(null);
    if (standardCode == null) {
      return false;
    }
    for (String std : standards) {
      if (std.equalsIgnoreCase(standardCode)) {
        return true;
      }
    }
    return false;
  }
}
