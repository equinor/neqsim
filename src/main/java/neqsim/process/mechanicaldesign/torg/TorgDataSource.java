package neqsim.process.mechanicaldesign.torg;

import java.util.List;
import java.util.Optional;

/**
 * Interface for loading Technical Requirements Documents (TORG) from various data sources.
 *
 * <p>
 * Implementations can load TORG data from:
 * </p>
 * <ul>
 * <li>CSV files</li>
 * <li>Databases</li>
 * <li>JSON/XML configuration files</li>
 * <li>Web services/APIs</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public interface TorgDataSource {

  /**
   * Load a TORG document by project identifier.
   *
   * @param projectId the project identifier
   * @return optional containing the TORG if found
   */
  Optional<TechnicalRequirementsDocument> loadByProjectId(String projectId);

  /**
   * Load a TORG document by company and project name.
   *
   * @param companyIdentifier the company identifier
   * @param projectName the project name
   * @return optional containing the TORG if found
   */
  Optional<TechnicalRequirementsDocument> loadByCompanyAndProject(String companyIdentifier,
      String projectName);

  /**
   * Get a list of available project identifiers.
   *
   * @return list of project IDs available in this data source
   */
  List<String> getAvailableProjectIds();

  /**
   * Get a list of available companies.
   *
   * @return list of company identifiers
   */
  List<String> getAvailableCompanies();

  /**
   * Check if a project exists in this data source.
   *
   * @param projectId the project identifier
   * @return true if the project exists
   */
  default boolean hasProject(String projectId) {
    return loadByProjectId(projectId).isPresent();
  }

  /**
   * Create a new TORG and store it (if the data source supports writing).
   *
   * @param torg the TORG document to store
   * @return true if successfully stored
   * @throws UnsupportedOperationException if this data source is read-only
   */
  default boolean store(TechnicalRequirementsDocument torg) {
    throw new UnsupportedOperationException("This data source is read-only");
  }

  /**
   * Update an existing TORG (if the data source supports writing).
   *
   * @param torg the TORG document to update
   * @return true if successfully updated
   * @throws UnsupportedOperationException if this data source is read-only
   */
  default boolean update(TechnicalRequirementsDocument torg) {
    throw new UnsupportedOperationException("This data source is read-only");
  }

  /**
   * Check if this data source supports write operations.
   *
   * @return true if write operations are supported
   */
  default boolean isWritable() {
    return false;
  }
}
