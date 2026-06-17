/**
 * Stable automation API for programmatic interaction with running NeqSim process simulations.
 *
 * <p>
 * This package provides a string-addressable facade over NeqSim's process simulation internals.
 * Variables in the simulation are reachable through stable dot-notation paths such as
 * {@code "separator-1.gasOutStream.temperature"}, enabling scripts and AI agents to interact
 * with simulations without awareness of the Java class hierarchy.
 * </p>
 *
 * <p>
 * Core classes:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.automation.ProcessAutomation} &mdash; the main facade providing
 *     getUnitList, getVariableList, getVariableValue, setVariableValue</li>
 * <li>{@link neqsim.process.automation.SimulationVariable} &mdash; descriptor for a simulation
 *     variable (address, type, unit, description)</li>
 * </ul>
 *
 * @see neqsim.process.automation.ProcessAutomation
 */
package neqsim.process.automation;
