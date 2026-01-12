/**
 * Automatic scenario generation for safety analysis.
 *
 * <p>
 * This package provides tools for systematic safety scenario creation:
 * </p>
 *
 * <ul>
 * <li><b>Failure Mode Analysis:</b> Identify equipment-specific failure modes</li>
 * <li><b>HAZOP Mapping:</b> Connect to standard deviation types</li>
 * <li><b>Combination Generation:</b> Multi-failure scenarios</li>
 * <li><b>Prioritization:</b> Rank by severity and likelihood</li>
 * </ul>
 *
 * <h2>Supported Failure Modes:</h2>
 * <ul>
 * <li>Cooling/heating loss</li>
 * <li>Valve stuck (open/closed)</li>
 * <li>Compressor/pump trip</li>
 * <li>Blocked outlet</li>
 * <li>Power/instrument failure</li>
 * <li>External fire</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * AutomaticScenarioGenerator generator = new AutomaticScenarioGenerator(process);
 *
 * generator.addFailureModes(FailureMode.COOLING_LOSS, FailureMode.VALVE_STUCK_CLOSED);
 *
 * // Single failures
 * List&lt;ProcessSafetyScenario&gt; scenarios = generator.generateSingleFailures();
 *
 * // Combination scenarios (up to 2 simultaneous)
 * List&lt;ProcessSafetyScenario&gt; combinations = generator.generateCombinations(2);
 *
 * // Run all scenarios
 * for (ProcessSafetyScenario s : scenarios) {
 *   ProcessSystem copy = process.copy();
 *   s.applyTo(copy);
 *   copy.run();
 *   // Analyze consequences
 * }
 * </pre>
 *
 * @see neqsim.process.safety.scenario.AutomaticScenarioGenerator
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.safety.scenario;
