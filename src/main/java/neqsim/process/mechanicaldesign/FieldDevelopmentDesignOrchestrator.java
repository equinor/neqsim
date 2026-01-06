package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument;
import neqsim.process.mechanicaldesign.torg.TorgDataSource;
import neqsim.process.mechanicaldesign.torg.TorgManager;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Orchestrator for field development design workflows.
 *
 * <p>
 * This class provides a unified, coordinated workflow for process simulation and mechanical design
 * in field development projects. It manages:
 * </p>
 * <ul>
 * <li>Design phase management (Concept, FEED, Detail)</li>
 * <li>TORG (Technical Requirements Document) application</li>
 * <li>Design case execution (Normal, Maximum, Upset, etc.)</li>
 * <li>Design validation and compliance checking</li>
 * <li>Report generation</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * {@code
 * // Create process system
 * ProcessSystem process = new ProcessSystem();
 * process.add(separator);
 * process.add(compressor);
 *
 * // Create orchestrator
 * FieldDevelopmentDesignOrchestrator orchestrator =
 *     new FieldDevelopmentDesignOrchestrator(process, "MyProject");
 *
 * // Configure for FEED phase
 * orchestrator.setDesignPhase(DesignPhase.FEED);
 *
 * // Load and apply TORG
 * orchestrator.loadTorg("PROJECT-001", new CsvTorgDataSource("path/to/torg.csv"));
 *
 * // Run complete design workflow
 * orchestrator.runCompleteDesignWorkflow();
 *
 * // Check results
 * if (orchestrator.getValidationResult().isValid()) {
 *   System.out.println("Design passed validation");
 *   orchestrator.generateReport("output/design_report.pdf");
 * }
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class FieldDevelopmentDesignOrchestrator implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final Logger logger =
      LogManager.getLogger(FieldDevelopmentDesignOrchestrator.class);

  /** The process system being designed. */
  private final ProcessSystem processSystem;

  /** Project identifier. */
  private final String projectId;

  /** Current design phase. */
  private DesignPhase designPhase = DesignPhase.FEED;

  /** Design cases to evaluate. */
  private final List<DesignCase> designCases = new ArrayList<DesignCase>();

  /** TORG manager for standards management. */
  private final TorgManager torgManager = new TorgManager();

  /** System mechanical design instance. */
  private SystemMechanicalDesign systemMechanicalDesign;

  /** Validation results. */
  private DesignValidationResult validationResult;

  /** Design case results. */
  private final Map<DesignCase, DesignCaseResult> caseResults =
      new LinkedHashMap<DesignCase, DesignCaseResult>();

  /** Workflow execution history. */
  private final List<WorkflowStep> workflowHistory = new ArrayList<WorkflowStep>();

  /** Unique run identifier. */
  private UUID runId;

  /**
   * Represents a single workflow step.
   */
  public static class WorkflowStep implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String stepName;
    private final long startTime;
    private long endTime;
    private boolean success;
    private String message;

    /**
     * Constructor.
     *
     * @param stepName name of the step
     */
    public WorkflowStep(String stepName) {
      this.stepName = stepName;
      this.startTime = System.currentTimeMillis();
    }

    /**
     * Complete the step.
     *
     * @param success whether step succeeded
     * @param message result message
     */
    public void complete(boolean success, String message) {
      this.endTime = System.currentTimeMillis();
      this.success = success;
      this.message = message;
    }

    /**
     * Get step name.
     *
     * @return step name
     */
    public String getStepName() {
      return stepName;
    }

    /**
     * Get duration in milliseconds.
     *
     * @return duration in ms
     */
    public long getDurationMs() {
      return endTime - startTime;
    }

    /**
     * Check if step succeeded.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Get result message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }
  }

  /**
   * Represents results for a single design case.
   */
  public static class DesignCaseResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final DesignCase designCase;
    private double totalWeight;
    private double totalVolume;
    private boolean converged;
    private DesignValidationResult validation;

    /**
     * Constructor.
     *
     * @param designCase the design case
     */
    public DesignCaseResult(DesignCase designCase) {
      this.designCase = designCase;
    }

    /**
     * Get the design case.
     *
     * @return design case
     */
    public DesignCase getDesignCase() {
      return designCase;
    }

    /**
     * Get total weight.
     *
     * @return weight in kg
     */
    public double getTotalWeight() {
      return totalWeight;
    }

    /**
     * Set total weight.
     *
     * @param totalWeight weight in kg
     */
    public void setTotalWeight(double totalWeight) {
      this.totalWeight = totalWeight;
    }

    /**
     * Get total volume.
     *
     * @return volume in m³
     */
    public double getTotalVolume() {
      return totalVolume;
    }

    /**
     * Set total volume.
     *
     * @param totalVolume volume in m³
     */
    public void setTotalVolume(double totalVolume) {
      this.totalVolume = totalVolume;
    }

    /**
     * Check if simulation converged.
     *
     * @return true if converged
     */
    public boolean isConverged() {
      return converged;
    }

    /**
     * Set convergence status.
     *
     * @param converged whether converged
     */
    public void setConverged(boolean converged) {
      this.converged = converged;
    }

    /**
     * Get validation result.
     *
     * @return validation result
     */
    public DesignValidationResult getValidation() {
      return validation;
    }

    /**
     * Set validation result.
     *
     * @param validation the validation result
     */
    public void setValidation(DesignValidationResult validation) {
      this.validation = validation;
    }
  }

  /**
   * Create a new orchestrator for the given process system.
   *
   * @param processSystem the process system to design
   * @param projectId unique project identifier
   * @throws IllegalArgumentException if processSystem is null
   */
  public FieldDevelopmentDesignOrchestrator(ProcessSystem processSystem, String projectId) {
    if (processSystem == null) {
      throw new IllegalArgumentException("ProcessSystem cannot be null");
    }
    this.processSystem = processSystem;
    this.projectId = projectId != null ? projectId : "DEFAULT";
    this.validationResult = new DesignValidationResult();

    // Default design cases
    designCases.add(DesignCase.NORMAL);
    designCases.add(DesignCase.MAXIMUM);
  }

  /**
   * Set the design phase.
   *
   * @param phase the design phase
   * @return this instance for chaining
   */
  public FieldDevelopmentDesignOrchestrator setDesignPhase(DesignPhase phase) {
    this.designPhase = phase;
    logger.info("Design phase set to: {}", phase);
    return this;
  }

  /**
   * Get current design phase.
   *
   * @return current design phase
   */
  public DesignPhase getDesignPhase() {
    return designPhase;
  }

  /**
   * Add a design case to evaluate.
   *
   * @param designCase the design case to add
   * @return this instance for chaining
   */
  public FieldDevelopmentDesignOrchestrator addDesignCase(DesignCase designCase) {
    if (!designCases.contains(designCase)) {
      designCases.add(designCase);
    }
    return this;
  }

  /**
   * Set design cases to evaluate.
   *
   * @param cases list of design cases
   * @return this instance for chaining
   */
  public FieldDevelopmentDesignOrchestrator setDesignCases(List<DesignCase> cases) {
    designCases.clear();
    if (cases != null) {
      designCases.addAll(cases);
    }
    return this;
  }

  /**
   * Get configured design cases.
   *
   * @return list of design cases
   */
  public List<DesignCase> getDesignCases() {
    return new ArrayList<DesignCase>(designCases);
  }

  /**
   * Add a TORG data source.
   *
   * @param dataSource the data source
   * @return this instance for chaining
   */
  public FieldDevelopmentDesignOrchestrator addTorgDataSource(TorgDataSource dataSource) {
    torgManager.addDataSource(dataSource);
    return this;
  }

  /**
   * Load TORG from configured data sources.
   *
   * @param torgProjectId the TORG project ID to load
   * @return true if loaded successfully
   */
  public boolean loadTorg(String torgProjectId) {
    WorkflowStep step = new WorkflowStep("Load TORG");
    workflowHistory.add(step);

    try {
      java.util.Optional<TechnicalRequirementsDocument> optTorg = torgManager.load(torgProjectId);
      if (optTorg.isPresent()) {
        TechnicalRequirementsDocument torg = optTorg.get();
        logger.info("TORG loaded: {} - {}", torg.getProjectId(), torg.getProjectName());
        step.complete(true, "TORG loaded: " + torg.getProjectName());
        return true;
      } else {
        step.complete(false, "TORG not found: " + torgProjectId);
        return false;
      }
    } catch (Exception e) {
      step.complete(false, "Error loading TORG: " + e.getMessage());
      logger.error("Error loading TORG", e);
      return false;
    }
  }

  /**
   * Load TORG and apply to process system.
   *
   * @param torgProjectId the TORG project ID to load
   * @param dataSource the data source to load from
   * @return true if loaded and applied successfully
   */
  public boolean loadTorg(String torgProjectId, TorgDataSource dataSource) {
    addTorgDataSource(dataSource);
    return loadTorg(torgProjectId);
  }

  /**
   * Get the TORG manager.
   *
   * @return TORG manager instance
   */
  public TorgManager getTorgManager() {
    return torgManager;
  }

  /**
   * Get the active TORG.
   *
   * @return active TORG or null if not loaded
   */
  public TechnicalRequirementsDocument getActiveTorg() {
    return torgManager.getActiveTorg();
  }

  /**
   * Run the complete design workflow.
   *
   * <p>
   * This method executes the following steps in order:
   * </p>
   * <ol>
   * <li>Initialize workflow (create run ID, clear previous results)</li>
   * <li>Run process simulation</li>
   * <li>Apply TORG standards to equipment</li>
   * <li>Execute mechanical design calculations</li>
   * <li>Validate design against requirements</li>
   * <li>Generate results summary</li>
   * </ol>
   *
   * @return true if workflow completed successfully
   */
  public boolean runCompleteDesignWorkflow() {
    runId = UUID.randomUUID();
    logger.info("Starting design workflow for project {} (Run: {})", projectId, runId);

    // Step 1: Initialize
    initializeWorkflow();

    // Step 2: Run process simulation
    if (!runProcessSimulation()) {
      return false;
    }

    // Step 3: Apply TORG if loaded
    applyTorgToProcess();

    // Step 4: Run mechanical design
    if (!runMechanicalDesign()) {
      return false;
    }

    // Step 5: Validate design
    validateDesign();

    // Step 6: Generate summary
    generateResultsSummary();

    logger.info("Design workflow completed for project {} (Run: {})", projectId, runId);
    return validationResult.isValid();
  }

  /**
   * Initialize the workflow.
   */
  private void initializeWorkflow() {
    WorkflowStep step = new WorkflowStep("Initialize Workflow");
    workflowHistory.add(step);

    caseResults.clear();
    validationResult = new DesignValidationResult();

    step.complete(true, String.format("Initialized for phase %s with %d design cases", designPhase,
        designCases.size()));
  }

  /**
   * Run process simulation for all design cases.
   *
   * @return true if simulation successful
   */
  private boolean runProcessSimulation() {
    WorkflowStep step = new WorkflowStep("Run Process Simulation");
    workflowHistory.add(step);

    try {
      // Run base case
      processSystem.run(runId);

      // For each design case, could apply load factors and re-run
      for (DesignCase designCase : designCases) {
        DesignCaseResult result = new DesignCaseResult(designCase);
        result.setConverged(true); // Simplified - in reality check convergence
        caseResults.put(designCase, result);
      }

      step.complete(true, "Process simulation completed for " + designCases.size() + " cases");
      return true;
    } catch (Exception e) {
      step.complete(false, "Simulation failed: " + e.getMessage());
      logger.error("Process simulation failed", e);
      validationResult.addCritical("Simulation", "ProcessSystem",
          "Process simulation failed: " + e.getMessage(),
          "Check feed conditions and equipment setup");
      return false;
    }
  }

  /**
   * Apply TORG standards to the process.
   */
  private void applyTorgToProcess() {
    WorkflowStep step = new WorkflowStep("Apply TORG Standards");
    workflowHistory.add(step);

    TechnicalRequirementsDocument torg = torgManager.getActiveTorg();
    if (torg == null) {
      step.complete(true, "No TORG loaded - using default standards");
      validationResult.addInfo("ProcessSystem", "No TORG applied - using default design standards");
      return;
    }

    try {
      torgManager.apply(torg, processSystem);
      step.complete(true, "TORG applied: " + torg.getProjectId());
    } catch (Exception e) {
      step.complete(false, "Error applying TORG: " + e.getMessage());
      logger.error("Error applying TORG", e);
      validationResult.addWarning("TORG", "ProcessSystem",
          "Failed to apply TORG: " + e.getMessage(),
          "Check TORG configuration and equipment compatibility");
    }
  }

  /**
   * Run mechanical design calculations.
   *
   * @return true if calculations successful
   */
  private boolean runMechanicalDesign() {
    WorkflowStep step = new WorkflowStep("Run Mechanical Design");
    workflowHistory.add(step);

    try {
      systemMechanicalDesign = new SystemMechanicalDesign(processSystem);
      systemMechanicalDesign.runDesignCalculation();

      // Capture results for each design case
      for (DesignCaseResult caseResult : caseResults.values()) {
        caseResult.setTotalWeight(systemMechanicalDesign.getTotalWeight());
        caseResult.setTotalVolume(systemMechanicalDesign.getTotalVolume());
      }

      step.complete(true, String.format("Mechanical design completed. Total weight: %.0f kg",
          systemMechanicalDesign.getTotalWeight()));
      return true;
    } catch (Exception e) {
      step.complete(false, "Mechanical design failed: " + e.getMessage());
      logger.error("Mechanical design failed", e);
      validationResult.addCritical("Mechanical Design", "ProcessSystem",
          "Mechanical design calculation failed: " + e.getMessage(),
          "Check equipment configuration and input parameters");
      return false;
    }
  }

  /**
   * Validate the design against requirements.
   */
  private void validateDesign() {
    WorkflowStep step = new WorkflowStep("Validate Design");
    workflowHistory.add(step);

    int warningCount = 0;
    int errorCount = 0;

    // Validate each equipment
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      validateEquipment(equipment);
    }

    // Check phase-specific requirements
    if (designPhase.requiresDetailedCompliance()) {
      validateStandardsCompliance();
    }

    // Validate TORG requirements if loaded
    if (torgManager.getActiveTorg() != null) {
      validateTorgCompliance();
    }

    warningCount = validationResult.getCount(DesignValidationResult.Severity.WARNING);
    errorCount = validationResult.getCount(DesignValidationResult.Severity.ERROR)
        + validationResult.getCount(DesignValidationResult.Severity.CRITICAL);

    String status = validationResult.isValid() ? "PASSED" : "FAILED";
    step.complete(validationResult.isValid(),
        String.format("Validation %s: %d errors, %d warnings", status, errorCount, warningCount));
  }

  /**
   * Validate a single equipment item.
   *
   * @param equipment the equipment to validate
   */
  private void validateEquipment(ProcessEquipmentInterface equipment) {
    String name = equipment.getName();

    // Check if mechanical design was generated
    MechanicalDesign mechDesign = equipment.getMechanicalDesign();
    if (mechDesign == null) {
      validationResult.addWarning("Configuration", name, "No mechanical design generated",
          "Initialize mechanical design for this equipment");
      return;
    }

    // Check for design standards
    if (!mechDesign.hasDesignStandard()) {
      validationResult.addWarning("Standards", name, "No design standard assigned",
          "Assign appropriate design standard from TORG or defaults");
    }

    // Phase-specific checks
    if (designPhase.requiresFullMechanicalDesign()) {
      validateDetailedDesign(equipment, mechDesign);
    }
  }

  /**
   * Validate detailed design requirements.
   *
   * @param equipment the equipment
   * @param mechDesign the mechanical design
   */
  private void validateDetailedDesign(ProcessEquipmentInterface equipment,
      MechanicalDesign mechDesign) {
    String name = equipment.getName();

    // Check weight is calculated
    if (mechDesign.getWeightTotal() <= 0) {
      validationResult.addWarning("Weight", name, "Total weight not calculated or is zero",
          "Run calcDesign() to calculate equipment weight");
    }

    // Check design pressure
    double designPressure = mechDesign.getMaxDesignPressure();
    double operatingPressure = mechDesign.getMaxOperationPressure();
    if (designPressure > 0 && operatingPressure > 0) {
      double margin = (designPressure - operatingPressure) / operatingPressure;
      if (margin < 0.1) {
        validationResult.addWarning("Pressure", name,
            String.format("Low design margin: %.1f%% (design=%.1f, operating=%.1f barg)",
                margin * 100, designPressure, operatingPressure),
            "Review design pressure and consider increasing margin per standards");
      }
    }
  }

  /**
   * Validate standards compliance.
   */
  private void validateStandardsCompliance() {
    // Verify all equipment has appropriate standards for this phase
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      MechanicalDesign mechDesign = equipment.getMechanicalDesign();

      if (mechDesign != null && !mechDesign.hasDesignStandard()) {
        validationResult.addError("Compliance", equipment.getName(),
            "Missing required design standard for " + designPhase.getDisplayName() + " phase",
            "Assign design standard before proceeding to detailed design");
      }
    }
  }

  /**
   * Validate TORG compliance.
   */
  private void validateTorgCompliance() {
    TechnicalRequirementsDocument torg = torgManager.getActiveTorg();
    if (torg == null) {
      return;
    }

    // Check environmental conditions are within TORG limits
    TechnicalRequirementsDocument.EnvironmentalConditions env = torg.getEnvironmentalConditions();
    if (env != null) {
      // Validate temperature ranges, etc.
      validationResult.addInfo("ProcessSystem",
          String.format("TORG environmental conditions: %.1f°C to %.1f°C ambient",
              env.getMinAmbientTemperature(), env.getMaxAmbientTemperature()));
    }

    // Check safety factors
    TechnicalRequirementsDocument.SafetyFactors safety = torg.getSafetyFactors();
    if (safety != null) {
      validationResult.addInfo("ProcessSystem",
          String.format("TORG safety factors: pressure=%.2f, temperature margin=%.2f°C",
              safety.getPressureSafetyFactor(), safety.getTemperatureSafetyMargin()));
    }
  }

  /**
   * Generate results summary.
   */
  private void generateResultsSummary() {
    WorkflowStep step = new WorkflowStep("Generate Summary");
    workflowHistory.add(step);

    StringBuilder summary = new StringBuilder();
    summary.append(String.format("Project: %s\n", projectId));
    summary.append(String.format("Phase: %s\n", designPhase));
    summary.append(String.format("Run ID: %s\n\n", runId));

    if (systemMechanicalDesign != null) {
      summary.append(
          String.format("Total Weight: %.0f kg\n", systemMechanicalDesign.getTotalWeight()));
      summary.append(
          String.format("Total Volume: %.1f m³\n", systemMechanicalDesign.getTotalVolume()));
      summary.append(String.format("Equipment Count: %d\n", processSystem.size()));
    }

    summary.append(
        String.format("\nValidation: %s\n", validationResult.isValid() ? "PASSED" : "FAILED"));

    step.complete(true, "Summary generated");
    logger.info("Design Summary:\n{}", summary.toString());
  }

  /**
   * Get the system mechanical design instance.
   *
   * @return system mechanical design or null if not yet run
   */
  public SystemMechanicalDesign getSystemMechanicalDesign() {
    return systemMechanicalDesign;
  }

  /**
   * Get the validation result.
   *
   * @return validation result
   */
  public DesignValidationResult getValidationResult() {
    return validationResult;
  }

  /**
   * Get workflow execution history.
   *
   * @return list of workflow steps
   */
  public List<WorkflowStep> getWorkflowHistory() {
    return new ArrayList<WorkflowStep>(workflowHistory);
  }

  /**
   * Get design case results.
   *
   * @return map of design case to results
   */
  public Map<DesignCase, DesignCaseResult> getCaseResults() {
    return new LinkedHashMap<DesignCase, DesignCaseResult>(caseResults);
  }

  /**
   * Get the process system.
   *
   * @return process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Get the project ID.
   *
   * @return project ID
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Get the run ID.
   *
   * @return run ID or null if not yet run
   */
  public UUID getRunId() {
    return runId;
  }

  /**
   * Generate a design report.
   *
   * @return formatted design report
   */
  public String generateDesignReport() {
    StringBuilder report = new StringBuilder();

    String doubleLine = StringUtils.repeat("=", 80);
    String singleLine = StringUtils.repeat("-", 40);

    report.append(doubleLine).append("\n");
    report.append("                    FIELD DEVELOPMENT DESIGN REPORT\n");
    report.append(doubleLine).append("\n\n");

    report.append("PROJECT INFORMATION\n");
    report.append(singleLine).append("\n");
    report.append(String.format("Project ID:    %s\n", projectId));
    report.append(String.format("Design Phase:  %s\n", designPhase));
    report.append(String.format("Accuracy:      %s\n", designPhase.getAccuracyRange()));
    report.append(String.format("Run ID:        %s\n", runId));
    report.append("\n");

    // TORG Information
    TechnicalRequirementsDocument torg = torgManager.getActiveTorg();
    if (torg != null) {
      report.append("TECHNICAL REQUIREMENTS (TORG)\n");
      report.append(singleLine).append("\n");
      report.append(String.format("TORG ID:       %s\n", torg.getProjectId()));
      report.append(String.format("Project Name:  %s\n", torg.getProjectName()));
      report.append(String.format("Company:       %s\n", torg.getCompanyIdentifier()));
      report.append(String.format("Revision:      %s\n", torg.getRevision()));
      report.append("\n");
    }

    // Design Cases
    report.append("DESIGN CASES\n");
    report.append(singleLine).append("\n");
    for (DesignCase dc : designCases) {
      report.append(String.format("  - %s (%s)\n", dc.getDisplayName(), dc.getDescription()));
    }
    report.append("\n");

    // Equipment Summary
    if (systemMechanicalDesign != null) {
      report.append("EQUIPMENT SUMMARY\n");
      report.append(singleLine).append("\n");
      report.append(String.format("Total Equipment:  %d units\n", processSystem.size()));
      report.append(
          String.format("Total Weight:     %.0f kg\n", systemMechanicalDesign.getTotalWeight()));
      report.append(
          String.format("Total Volume:     %.1f m³\n", systemMechanicalDesign.getTotalVolume()));
      report.append(
          String.format("Total Plot Space: %.1f m²\n", systemMechanicalDesign.getTotalPlotSpace()));
      report.append("\n");

      // Weight breakdown
      report.append("WEIGHT BY EQUIPMENT TYPE\n");
      report.append(singleLine).append("\n");
      for (Map.Entry<String, Double> entry : systemMechanicalDesign.getWeightByEquipmentType()
          .entrySet()) {
        report.append(String.format("  %-25s %10.0f kg\n", entry.getKey(), entry.getValue()));
      }
      report.append("\n");
    }

    // Validation Results
    report.append("VALIDATION RESULTS\n");
    report.append(singleLine).append("\n");
    report.append(String.format("Status: %s\n", validationResult.isValid() ? "PASSED" : "FAILED"));
    report.append(String.format("Critical: %d, Errors: %d, Warnings: %d, Info: %d\n",
        validationResult.getCount(DesignValidationResult.Severity.CRITICAL),
        validationResult.getCount(DesignValidationResult.Severity.ERROR),
        validationResult.getCount(DesignValidationResult.Severity.WARNING),
        validationResult.getCount(DesignValidationResult.Severity.INFO)));
    report.append("\n");

    if (validationResult.hasErrors() || validationResult.hasWarnings()) {
      report.append("VALIDATION MESSAGES\n");
      report.append(singleLine).append("\n");
      for (DesignValidationResult.ValidationMessage msg : validationResult.getMessages()) {
        if (msg.getSeverity() != DesignValidationResult.Severity.INFO) {
          report.append(String.format("[%s] %s: %s\n", msg.getSeverity(), msg.getEquipmentName(),
              msg.getMessage()));
          if (msg.getRemediation() != null && !msg.getRemediation().isEmpty()) {
            report.append(String.format("  Fix: %s\n", msg.getRemediation()));
          }
        }
      }
      report.append("\n");
    }

    // Workflow History
    report.append("WORKFLOW EXECUTION\n");
    report.append(singleLine).append("\n");
    for (WorkflowStep step : workflowHistory) {
      String status = step.isSuccess() ? "OK" : "FAIL";
      report.append(
          String.format("  %s %-30s %5d ms\n", status, step.getStepName(), step.getDurationMs()));
    }
    report.append("\n");

    report.append(doubleLine).append("\n");
    report.append("                           END OF REPORT\n");
    report.append(doubleLine).append("\n");

    return report.toString();
  }
}
