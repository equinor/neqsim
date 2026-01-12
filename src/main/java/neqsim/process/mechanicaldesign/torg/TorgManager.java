package neqsim.process.mechanicaldesign.torg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.DesignStandard;
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manager class for applying Technical Requirements Documents (TORG) to process systems.
 *
 * <p>
 * The TorgManager provides methods to:
 * </p>
 * <ul>
 * <li>Load TORG documents from various data sources</li>
 * <li>Apply TORG requirements to individual equipment or entire process systems</li>
 * <li>Validate equipment designs against TORG specifications</li>
 * <li>Generate compliance reports</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * // Create TORG manager with CSV data source
 * TorgManager manager = new TorgManager();
 * manager.addDataSource(CsvTorgDataSource.fromResource("designdata/torg/projects.csv"));
 *
 * // Load and apply TORG to process system
 * manager.loadAndApply("PROJECT-001", processSystem);
 *
 * // Or manually create and apply a TORG
 * TechnicalRequirementsDocument torg =
 *     TechnicalRequirementsDocument.builder().projectId("MANUAL-001")
 *         .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1).build();
 *
 * manager.apply(torg, processSystem);
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class TorgManager {
  private static final Logger logger = LogManager.getLogger(TorgManager.class);

  /** Data sources for loading TORG documents. */
  private final List<TorgDataSource> dataSources;

  /** Currently active TORG. */
  private TechnicalRequirementsDocument activeTorg;

  /** Cache of applied standards per equipment. */
  private final Map<String, List<StandardType>> appliedStandards;

  /**
   * Create a new TorgManager with no data sources.
   */
  public TorgManager() {
    this.dataSources = new ArrayList<>();
    this.appliedStandards = new HashMap<>();
  }

  /**
   * Create a TorgManager with a single data source.
   *
   * @param dataSource the data source to use
   */
  public TorgManager(TorgDataSource dataSource) {
    this();
    addDataSource(dataSource);
  }

  /**
   * Add a data source for loading TORG documents.
   *
   * @param dataSource the data source to add
   * @return this manager for chaining
   */
  public TorgManager addDataSource(TorgDataSource dataSource) {
    if (dataSource != null && !dataSources.contains(dataSource)) {
      dataSources.add(dataSource);
    }
    return this;
  }

  /**
   * Load a TORG by project ID from the configured data sources.
   *
   * @param projectId the project identifier
   * @return optional containing the TORG if found
   */
  public Optional<TechnicalRequirementsDocument> load(String projectId) {
    for (TorgDataSource source : dataSources) {
      Optional<TechnicalRequirementsDocument> torg = source.loadByProjectId(projectId);
      if (torg.isPresent()) {
        return torg;
      }
    }
    return Optional.empty();
  }

  /**
   * Load a TORG by company and project name.
   *
   * @param companyIdentifier the company identifier
   * @param projectName the project name
   * @return optional containing the TORG if found
   */
  public Optional<TechnicalRequirementsDocument> load(String companyIdentifier,
      String projectName) {
    for (TorgDataSource source : dataSources) {
      Optional<TechnicalRequirementsDocument> torg =
          source.loadByCompanyAndProject(companyIdentifier, projectName);
      if (torg.isPresent()) {
        return torg;
      }
    }
    return Optional.empty();
  }

  /**
   * Load a TORG and apply it to a process system.
   *
   * @param projectId the project identifier
   * @param processSystem the process system to configure
   * @return true if TORG was found and applied
   */
  public boolean loadAndApply(String projectId, ProcessSystem processSystem) {
    Optional<TechnicalRequirementsDocument> torg = load(projectId);
    if (torg.isPresent()) {
      apply(torg.get(), processSystem);
      return true;
    }
    logger.warn("TORG not found for project: " + projectId);
    return false;
  }

  /**
   * Apply a TORG to a process system.
   *
   * <p>
   * This method iterates through all equipment in the process system and applies the appropriate
   * design standards from the TORG based on equipment type.
   * </p>
   *
   * @param torg the TORG to apply
   * @param processSystem the process system to configure
   */
  public void apply(TechnicalRequirementsDocument torg, ProcessSystem processSystem) {
    if (torg == null || processSystem == null) {
      return;
    }

    this.activeTorg = torg;
    this.appliedStandards.clear();

    logger.info("Applying TORG {} to process system {}", torg.getProjectId(),
        processSystem.getName());

    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      applyToEquipment(torg, unit);
    }

    logger.info("Applied TORG to {} equipment units", appliedStandards.size());
  }

  /**
   * Apply a TORG to a single equipment item.
   *
   * @param torg the TORG to apply
   * @param equipment the equipment to configure
   */
  public void applyToEquipment(TechnicalRequirementsDocument torg,
      ProcessEquipmentInterface equipment) {
    if (torg == null || equipment == null) {
      return;
    }

    String equipmentType = equipment.getClass().getSimpleName();
    String equipmentName = equipment.getName();

    List<StandardType> standards = torg.getAllApplicableStandards(equipmentType);

    if (standards.isEmpty()) {
      logger.debug("No applicable standards in TORG for equipment type: {}", equipmentType);
      return;
    }

    MechanicalDesign design = equipment.getMechanicalDesign();

    // Apply each standard
    List<StandardType> appliedList = new ArrayList<>();
    for (StandardType standardType : standards) {
      try {
        DesignStandard standard = StandardRegistry.createStandard(standardType, design);
        String category = standardType.getDesignStandardCategory();
        design.setDesignStandard(category, standard);
        appliedList.add(standardType);
        logger.debug("Applied {} to {}", standardType.getCode(), equipmentName);
      } catch (Exception e) {
        logger.warn("Failed to apply standard {} to {}: {}", standardType.getCode(), equipmentName,
            e.getMessage());
      }
    }

    // Apply environmental conditions if available
    TechnicalRequirementsDocument.EnvironmentalConditions env = torg.getEnvironmentalConditions();
    if (env != null) {
      design.setMinOperationTemperature(env.getMinAmbientTemperature());
    }

    // Apply safety factors if available
    TechnicalRequirementsDocument.SafetyFactors safety = torg.getSafetyFactors();
    if (safety != null) {
      design.setCorrosionAllowance(safety.getCorrosionAllowance());
    }

    appliedStandards.put(equipmentName, appliedList);
  }

  /**
   * Get the currently active TORG.
   *
   * @return the active TORG, or null if none
   */
  public TechnicalRequirementsDocument getActiveTorg() {
    return activeTorg;
  }

  /**
   * Set the active TORG without applying it.
   *
   * @param torg the TORG to set as active
   */
  public void setActiveTorg(TechnicalRequirementsDocument torg) {
    this.activeTorg = torg;
  }

  /**
   * Get the standards that were applied to a specific equipment.
   *
   * @param equipmentName the equipment name
   * @return list of applied standards, empty if none
   */
  public List<StandardType> getAppliedStandards(String equipmentName) {
    List<StandardType> standards = appliedStandards.get(equipmentName);
    return standards != null ? new ArrayList<>(standards) : new ArrayList<>();
  }

  /**
   * Get all applied standards across all equipment.
   *
   * @return map of equipment name to applied standards
   */
  public Map<String, List<StandardType>> getAllAppliedStandards() {
    return new HashMap<>(appliedStandards);
  }

  /**
   * Get a list of all available project IDs across all data sources.
   *
   * @return list of available project IDs
   */
  public List<String> getAvailableProjects() {
    List<String> projects = new ArrayList<>();
    for (TorgDataSource source : dataSources) {
      for (String projectId : source.getAvailableProjectIds()) {
        if (!projects.contains(projectId)) {
          projects.add(projectId);
        }
      }
    }
    return projects;
  }

  /**
   * Generate a summary report of applied standards.
   *
   * @return formatted summary string
   */
  public String generateSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("TORG Application Summary\n");
    sb.append("========================\n\n");

    if (activeTorg != null) {
      sb.append("Project: ").append(activeTorg.getProjectId()).append("\n");
      sb.append("Name: ").append(activeTorg.getProjectName()).append("\n");
      sb.append("Company: ").append(activeTorg.getCompanyIdentifier()).append("\n");
      sb.append("Revision: ").append(activeTorg.getRevision()).append("\n\n");
    }

    sb.append("Applied Standards by Equipment:\n");
    sb.append("-------------------------------\n");

    for (Map.Entry<String, List<StandardType>> entry : appliedStandards.entrySet()) {
      sb.append(entry.getKey()).append(":\n");
      for (StandardType std : entry.getValue()) {
        sb.append("  - ").append(std.getCode()).append(" (").append(std.getName()).append(")\n");
      }
    }

    return sb.toString();
  }

  /**
   * Clear all applied standards and reset the manager.
   */
  public void reset() {
    this.activeTorg = null;
    this.appliedStandards.clear();
  }
}
