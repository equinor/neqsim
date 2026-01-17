package neqsim.process.design;

import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Interface for process templates that define standard configurations.
 *
 * <p>
 * Process templates encapsulate common process configurations (e.g., three-stage separation,
 * compression trains, fractionation systems) with standardized equipment sizing rules and operating
 * limits.
 * </p>
 *
 * <p>
 * Example implementation:
 * </p>
 * 
 * <pre>
 * public class ThreeStageSeparationTemplate implements ProcessTemplate {
 *   &#64;Override
 *   public ProcessSystem create(ProcessBasis basis) {
 *     ProcessSystem process = new ProcessSystem();
 *     // Create HP, MP, LP separators with auto-sizing
 *     // ...
 *     return process;
 *   }
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface ProcessTemplate {

  /**
   * Get the template name.
   *
   * @return template name
   */
  String getName();

  /**
   * Get a description of the process template.
   *
   * @return template description
   */
  String getDescription();

  /**
   * Create a process system from this template using the given process basis.
   *
   * @param basis the process basis containing feed conditions and constraints
   * @return a configured ProcessSystem
   */
  ProcessSystem create(ProcessBasis basis);

  /**
   * Check if this template is applicable for the given fluid.
   *
   * @param fluid the fluid to check
   * @return true if template is applicable
   */
  boolean isApplicable(SystemInterface fluid);

  /**
   * Get the required equipment types for this template.
   *
   * @return array of equipment type names
   */
  String[] getRequiredEquipmentTypes();

  /**
   * Get the expected outputs from this process template.
   *
   * @return array of output stream names/types
   */
  String[] getExpectedOutputs();
}
