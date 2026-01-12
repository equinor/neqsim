/**
 * AI-friendly validation framework for NeqSim.
 *
 * <p>
 * This package provides structured validation with actionable error messages designed for both
 * human users and AI agents (like GitHub Copilot). Key features:
 * </p>
 *
 * <ul>
 * <li>Pre-execution validation to catch configuration errors early</li>
 * <li>Structured error messages with severity levels</li>
 * <li>Remediation hints that AI can parse and act upon</li>
 * <li>Integration with NeqSim's ML/RL infrastructure</li>
 * </ul>
 *
 * <h2>Quick Start:</h2>
 *
 * <pre>
 * {@code
 * // Validate any NeqSim object
 * ValidationResult result = SimulationValidator.validate(fluid);
 * if (!result.isValid()) {
 *   System.out.println(result.getReport());
 *   // Each issue includes: severity, message, remediation hint
 * }
 *
 * // For process systems with RL integration
 * AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
 * if (helper.isReady()) {
 *   ExecutionResult result = helper.safeRun();
 *   RLEnvironment env = helper.createRLEnvironment();
 * }
 * }
 * </pre>
 *
 * <h2>Key Classes:</h2>
 * <ul>
 * <li>{@link neqsim.util.validation.ValidationResult} - Container for validation issues</li>
 * <li>{@link neqsim.util.validation.SimulationValidator} - Static validation facade</li>
 * <li>{@link neqsim.util.validation.AIIntegrationHelper} - Unified AI/ML entry point</li>
 * </ul>
 *
 * <h2>Severity Levels:</h2>
 * <ul>
 * <li>{@code CRITICAL} - Blocks execution, must be fixed</li>
 * <li>{@code MAJOR} - Likely to cause incorrect results</li>
 * <li>{@code MINOR} - Unexpected but not fatal</li>
 * <li>{@code INFO} - Informational note</li>
 * </ul>
 *
 * @see neqsim.util.validation.contracts Module contracts for pre/post-condition checking
 * @see neqsim.util.annotation AI discovery annotations
 * @since 1.0
 */
package neqsim.util.validation;
