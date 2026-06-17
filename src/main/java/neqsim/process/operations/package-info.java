/**
 * Operational study helpers that connect P&amp;ID semantics, plant data, automation addresses, and
 * NeqSim process simulations.
 *
 * <p>
 * The package provides orchestration classes only. It reuses measurement devices for field data,
 * {@link neqsim.process.automation.ProcessAutomation} for variable access, existing process logic
 * actions for valve manipulation, and {@link neqsim.process.processmodel.ProcessSystem} for
 * steady-state and transient execution.
 * </p>
 */
package neqsim.process.operations;