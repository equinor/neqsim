package neqsim.util.validation;

import java.io.Serializable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.ml.RLEnvironment;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.annotation.AIExposable;
import neqsim.util.annotation.AISchemaDiscovery;

/**
 * AI Integration Helper for NeqSim.
 * 
 * <p>
 * This class provides a unified entry point for AI/ML agents to interact with NeqSim, combining
 * validation, schema discovery, and RL environment access.
 * </p>
 * 
 * <h2>Features:</h2>
 * <ul>
 * <li>Pre-run validation to catch errors before simulation</li>
 * <li>API schema discovery for dynamic method documentation</li>
 * <li>RL environment creation with built-in constraint validation</li>
 * <li>Safe execution wrappers that return structured results</li>
 * </ul>
 * 
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * // Create AI helper
 * AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
 * 
 * // Get API documentation for AI agent
 * String apiDocs = helper.getAPIDocumentation();
 * 
 * // Validate before running
 * if (helper.isReady()) {
 *   helper.safeRun();
 * } else {
 *   System.out.println(helper.getValidationReport());
 * }
 * 
 * // Create RL environment
 * RLEnvironment env = helper.createRLEnvironment();
 * }
 * </pre>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class AIIntegrationHelper implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem process;
  private final AISchemaDiscovery schemaDiscovery;
  private ValidationResult lastValidation;

  /**
   * Create helper for a ProcessSystem.
   * 
   * @param process process system to wrap
   * @return AI integration helper
   */
  @AIExposable(description = "Create an AI integration helper for a process system",
      category = "ai-integration", example = "AIIntegrationHelper.forProcess(process)",
      priority = 100, safe = true)
  public static AIIntegrationHelper forProcess(ProcessSystem process) {
    return new AIIntegrationHelper(process);
  }

  /**
   * Constructor.
   * 
   * @param process process system to manage
   */
  private AIIntegrationHelper(ProcessSystem process) {
    this.process = process;
    this.schemaDiscovery = new AISchemaDiscovery();
    this.lastValidation = null;
  }

  /**
   * Validate the process system.
   * 
   * @return validation result
   */
  @AIExposable(description = "Validate the process system before running", category = "validation",
      example = "helper.validate()", priority = 90, safe = true)
  public ValidationResult validate() {
    this.lastValidation = SimulationValidator.validate(process);
    return this.lastValidation;
  }

  /**
   * Check if the process is ready to run.
   * 
   * @return true if ready (no critical/major issues)
   */
  @AIExposable(description = "Check if the process is ready to run without errors",
      category = "validation", example = "if (helper.isReady()) { ... }", priority = 95,
      safe = true)
  public boolean isReady() {
    if (this.lastValidation == null) {
      validate();
    }
    return this.lastValidation.isValid();
  }

  /**
   * Get a human-readable validation report.
   * 
   * @return validation report with issues and remediation hints
   */
  @AIExposable(description = "Get a human-readable validation report", category = "validation",
      example = "System.out.println(helper.getValidationReport())", priority = 85, safe = true)
  public String getValidationReport() {
    if (this.lastValidation == null) {
      validate();
    }
    return this.lastValidation.getReport();
  }

  /**
   * Get structured issues for AI parsing.
   * 
   * @return array of issues as structured text
   */
  @AIExposable(description = "Get structured validation issues for AI parsing",
      category = "validation", example = "String[] issues = helper.getIssuesAsText()",
      priority = 80, safe = true)
  public String[] getIssuesAsText() {
    if (this.lastValidation == null) {
      validate();
    }
    return this.lastValidation.getIssues().stream().map(issue -> String.format("[%s] %s - %s",
        issue.getSeverity(), issue.getMessage(), issue.getRemediation())).toArray(String[]::new);
  }

  /**
   * Safely run the process with validation.
   * 
   * @return execution result with success/failure info
   */
  @AIExposable(description = "Run the process simulation with pre-validation",
      category = "execution", example = "ExecutionResult result = helper.safeRun()", priority = 75,
      safe = false)
  public ExecutionResult safeRun() {
    ValidationResult preValidation = validate();
    if (!preValidation.isValid()) {
      return ExecutionResult.failure("Pre-run validation failed", preValidation);
    }

    try {
      process.run();
      ValidationResult postValidation = SimulationValidator.validateOutput(process);
      if (!postValidation.isValid()) {
        return ExecutionResult.warning("Process ran but has output issues", postValidation);
      }
      return ExecutionResult.success(postValidation);
    } catch (Exception e) {
      return ExecutionResult.error("Execution failed: " + e.getMessage(), e);
    }
  }

  /**
   * Get API documentation for the process components.
   * 
   * @return formatted API documentation
   */
  @AIExposable(description = "Get API documentation for AI agent consumption",
      category = "discovery", example = "String docs = helper.getAPIDocumentation()",
      priority = 100, safe = true)
  public String getAPIDocumentation() {
    return schemaDiscovery.getQuickStartPrompt();
  }

  /**
   * Create an RL environment for the process.
   * 
   * <p>
   * The RL environment includes built-in validation constraints.
   * </p>
   * 
   * @return configured RL environment
   */
  @AIExposable(description = "Create an RL training environment for the process", category = "ml",
      example = "RLEnvironment env = helper.createRLEnvironment()", priority = 70, safe = true)
  public RLEnvironment createRLEnvironment() {
    return new RLEnvironment(process);
  }

  /**
   * Validate a specific equipment piece.
   * 
   * @param equipment equipment to validate
   * @return validation result
   */
  @AIExposable(description = "Validate a specific piece of equipment", category = "validation",
      example = "ValidationResult result = helper.validateEquipment(separator)", priority = 75,
      safe = true)
  public ValidationResult validateEquipment(ProcessEquipmentInterface equipment) {
    return SimulationValidator.validate(equipment);
  }

  /**
   * Validate a thermodynamic system.
   * 
   * @param system thermodynamic system to validate
   * @return validation result
   */
  @AIExposable(description = "Validate a thermodynamic system configuration",
      category = "validation", example = "ValidationResult result = helper.validateFluid(fluid)",
      priority = 75, safe = true)
  public ValidationResult validateFluid(SystemInterface system) {
    return SimulationValidator.validate(system);
  }

  /**
   * Get the underlying process system.
   * 
   * @return process system
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Result of a safe execution.
   */
  public static class ExecutionResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Execution status. */
    public enum Status {
      SUCCESS, WARNING, FAILURE, ERROR
    }

    private final Status status;
    private final String message;
    private final ValidationResult validation;
    private final Exception exception;

    private ExecutionResult(Status status, String message, ValidationResult validation,
        Exception exception) {
      this.status = status;
      this.message = message;
      this.validation = validation;
      this.exception = exception;
    }

    public static ExecutionResult success(ValidationResult validation) {
      return new ExecutionResult(Status.SUCCESS, "Process ran successfully", validation, null);
    }

    public static ExecutionResult warning(String message, ValidationResult validation) {
      return new ExecutionResult(Status.WARNING, message, validation, null);
    }

    public static ExecutionResult failure(String message, ValidationResult validation) {
      return new ExecutionResult(Status.FAILURE, message, validation, null);
    }

    public static ExecutionResult error(String message, Exception e) {
      return new ExecutionResult(Status.ERROR, message, null, e);
    }

    public Status getStatus() {
      return status;
    }

    public String getMessage() {
      return message;
    }

    public ValidationResult getValidation() {
      return validation;
    }

    public Exception getException() {
      return exception;
    }

    public boolean isSuccess() {
      return status == Status.SUCCESS;
    }

    /**
     * Get a structured report for AI consumption.
     * 
     * @return formatted report
     */
    public String toAIReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("## Execution Result\n\n");
      sb.append("**Status:** ").append(status).append("\n");
      sb.append("**Message:** ").append(message).append("\n\n");

      if (validation != null) {
        sb.append("### Validation Details\n");
        sb.append(validation.getReport());
      }

      if (exception != null) {
        sb.append("### Exception\n");
        sb.append("**Type:** ").append(exception.getClass().getSimpleName()).append("\n");
        sb.append("**Message:** ").append(exception.getMessage()).append("\n");
      }

      return sb.toString();
    }
  }
}
