package neqsim.util.validation;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.validation.contracts.ProcessSystemContract;
import neqsim.util.validation.contracts.SeparatorContract;
import neqsim.util.validation.contracts.StreamContract;
import neqsim.util.validation.contracts.ThermodynamicSystemContract;

/**
 * AI-friendly validation helper for NeqSim simulations.
 * 
 * <p>
 * Provides a unified interface for validating NeqSim objects. AI agents can use this class to
 * validate setup before execution and get actionable remediation advice.
 * </p>
 * 
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * // Validate before running
 * ValidationResult result = SimulationValidator.validate(system);
 * if (!result.isValid()) {
 *   // Parse result.getReport() for fixes
 *   System.out.println(result.getReport());
 * }
 * 
 * // Validate after running
 * stream.run();
 * ValidationResult postResult = SimulationValidator.validateOutput(stream);
 * }
 * </pre>
 * 
 * @author NeqSim
 * @version 1.0
 */
public final class SimulationValidator {

  private SimulationValidator() {
    // Utility class
  }

  /**
   * Validate any NeqSim object before execution.
   * 
   * <p>
   * Automatically detects the object type and applies appropriate validation.
   * </p>
   * 
   * @param obj object to validate (SystemInterface, StreamInterface, ProcessSystem, etc.)
   * @return validation result with errors/warnings and remediation hints
   */
  public static ValidationResult validate(Object obj) {
    if (obj == null) {
      ValidationResult result = new ValidationResult("null");
      result.addError("input", "Object is null", "Provide a valid NeqSim object");
      return result;
    }

    if (obj instanceof SystemInterface) {
      return ThermodynamicSystemContract.getInstance().checkPreconditions((SystemInterface) obj);
    }

    if (obj instanceof StreamInterface) {
      return StreamContract.getInstance().checkPreconditions((StreamInterface) obj);
    }

    if (obj instanceof Separator) {
      return SeparatorContract.getInstance().checkPreconditions((Separator) obj);
    }

    if (obj instanceof ProcessSystem) {
      return ProcessSystemContract.getInstance().checkPreconditions((ProcessSystem) obj);
    }

    if (obj instanceof ProcessEquipmentInterface) {
      return ((ProcessEquipmentInterface) obj).validateSetup();
    }

    // Unknown type - return empty valid result
    ValidationResult result = new ValidationResult(obj.getClass().getSimpleName());
    result.addWarning("type", "No specific validator for " + obj.getClass().getSimpleName(),
        "Consider using validateSetup() if available");
    return result;
  }

  /**
   * Validate object output after execution.
   * 
   * <p>
   * Checks postconditions to ensure the calculation produced valid results.
   * </p>
   * 
   * @param obj object to validate (after run() has been called)
   * @return validation result with any output issues
   */
  public static ValidationResult validateOutput(Object obj) {
    if (obj == null) {
      ValidationResult result = new ValidationResult("null");
      result.addError("output", "Object is null", "Ensure object exists after calculation");
      return result;
    }

    if (obj instanceof SystemInterface) {
      return ThermodynamicSystemContract.getInstance().checkPostconditions((SystemInterface) obj);
    }

    if (obj instanceof StreamInterface) {
      return StreamContract.getInstance().checkPostconditions((StreamInterface) obj);
    }

    if (obj instanceof Separator) {
      return SeparatorContract.getInstance().checkPostconditions((Separator) obj);
    }

    if (obj instanceof ProcessSystem) {
      return ProcessSystemContract.getInstance().checkPostconditions((ProcessSystem) obj);
    }

    // Unknown type
    return new ValidationResult(obj.getClass().getSimpleName() + " (output)");
  }

  /**
   * Validate and run if valid.
   * 
   * <p>
   * Convenience method that validates preconditions, runs if valid, then validates output.
   * </p>
   * 
   * @param equipment process equipment to validate and run
   * @return combined validation result (pre + post)
   */
  public static ValidationResult validateAndRun(ProcessEquipmentInterface equipment) {
    ValidationResult preResult = validate(equipment);
    if (!preResult.isValid()) {
      return preResult;
    }

    try {
      equipment.run();
    } catch (Exception e) {
      preResult.addError("execution", "Run failed: " + e.getMessage(), getExceptionRemediation(e));
      return preResult;
    }

    ValidationResult postResult = validateOutput(equipment);

    // Merge results
    for (ValidationResult.ValidationIssue issue : postResult.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        preResult.addError(issue.getCategory(), issue.getMessage(), issue.getRemediation());
      } else {
        preResult.addWarning(issue.getCategory(), issue.getMessage(), issue.getRemediation());
      }
    }

    return preResult;
  }

  /**
   * Validate and run a process system.
   * 
   * @param processSystem process system to validate and run
   * @return validation result
   */
  public static ValidationResult validateAndRun(ProcessSystem processSystem) {
    ValidationResult preResult = validate(processSystem);
    if (!preResult.isValid()) {
      return preResult;
    }

    try {
      processSystem.run();
    } catch (Exception e) {
      preResult.addError("execution", "Process run failed: " + e.getMessage(),
          getExceptionRemediation(e));
      return preResult;
    }

    ValidationResult postResult = validateOutput(processSystem);
    for (ValidationResult.ValidationIssue issue : postResult.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        preResult.addError(issue.getCategory(), issue.getMessage(), issue.getRemediation());
      } else {
        preResult.addWarning(issue.getCategory(), issue.getMessage(), issue.getRemediation());
      }
    }

    return preResult;
  }

  /**
   * Get remediation advice for an exception.
   * 
   * @param e the exception
   * @return remediation string
   */
  private static String getExceptionRemediation(Exception e) {
    // Check for NeqSim exceptions with getRemediation()
    if (e instanceof neqsim.util.exception.InvalidInputException) {
      return ((neqsim.util.exception.InvalidInputException) e).getRemediation();
    }
    if (e instanceof neqsim.util.exception.TooManyIterationsException) {
      return ((neqsim.util.exception.TooManyIterationsException) e).getRemediation();
    }
    if (e instanceof neqsim.util.exception.IsNaNException) {
      return ((neqsim.util.exception.IsNaNException) e).getRemediation();
    }
    if (e instanceof neqsim.util.exception.InvalidOutputException) {
      return ((neqsim.util.exception.InvalidOutputException) e).getRemediation();
    }
    if (e instanceof neqsim.util.exception.NotInitializedException) {
      return ((neqsim.util.exception.NotInitializedException) e).getRemediation();
    }

    // Generic advice
    return "Check error message: " + e.getMessage();
  }

  /**
   * Quick check if an object is ready for simulation.
   * 
   * @param obj object to check
   * @return true if valid, false otherwise
   */
  public static boolean isReady(Object obj) {
    return validate(obj).isValid();
  }

  /**
   * Get a summary report for multiple objects.
   * 
   * @param objects objects to validate
   * @return combined report
   */
  public static String getValidationReport(Object... objects) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Simulation Validation Report ===\n\n");

    boolean allValid = true;
    for (Object obj : objects) {
      ValidationResult result = validate(obj);
      if (!result.isValid()) {
        allValid = false;
      }
      sb.append(result.getReport()).append("\n");
    }

    sb.append("=== Overall Status: ");
    sb.append(allValid ? "READY" : "ISSUES FOUND");
    sb.append(" ===\n");

    return sb.toString();
  }
}
