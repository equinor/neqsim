package neqsim.util.validation.contracts;

import neqsim.util.validation.ValidationResult;

/**
 * Base interface for module contracts.
 * 
 * <p>
 * Module contracts define what a component requires (preconditions) and provides (postconditions).
 * AI agents can use these contracts to:
 * <ul>
 * <li>Validate setup before execution</li>
 * <li>Understand dependencies between modules</li>
 * <li>Self-correct when requirements are not met</li>
 * </ul>
 * 
 * <h2>Contract Pattern:</h2>
 * 
 * <pre>
 * {@code
 * // Before running equipment
 * ValidationResult pre = contract.checkPreconditions(equipment);
 * if (!pre.isValid()) {
 *   // AI reads errors and fixes setup
 * }
 * 
 * equipment.run();
 * 
 * // Verify output is valid
 * ValidationResult post = contract.checkPostconditions(equipment);
 * }
 * </pre>
 * 
 * @param <T> the type of object this contract validates
 * @author NeqSim
 * @version 1.0
 */
public interface ModuleContract<T> {

  /**
   * Get the name of this contract.
   * 
   * @return contract name for logging/debugging
   */
  String getContractName();

  /**
   * Check preconditions before execution.
   * 
   * <p>
   * Validates that all requirements are met before running the module.
   * </p>
   * 
   * @param target object to validate
   * @return validation result with any precondition failures
   */
  ValidationResult checkPreconditions(T target);

  /**
   * Check postconditions after execution.
   * 
   * <p>
   * Validates that the module produced valid output.
   * </p>
   * 
   * @param target object to validate
   * @return validation result with any postcondition failures
   */
  ValidationResult checkPostconditions(T target);

  /**
   * Get a description of what this module requires.
   * 
   * <p>
   * AI agents can use this to understand setup requirements.
   * </p>
   * 
   * @return human-readable requirements description
   */
  String getRequirementsDescription();

  /**
   * Get a description of what this module provides.
   * 
   * <p>
   * AI agents can use this to understand available outputs.
   * </p>
   * 
   * @return human-readable outputs description
   */
  String getProvidesDescription();
}
