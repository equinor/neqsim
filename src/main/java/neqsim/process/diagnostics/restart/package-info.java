/**
 * Restart sequence generation, simulation, and optimisation for process systems after trip events.
 *
 * <p>
 * This sub-package handles the restart side of the post-trip workflow:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.diagnostics.restart.RestartConstraintChecker} — Validates whether the
 * process is safe to restart</li>
 * <li>{@link neqsim.process.diagnostics.restart.RestartSequenceGenerator} — Generates an ordered
 * restart sequence based on the trip type</li>
 * <li>{@link neqsim.process.diagnostics.restart.RestartSimulator} — Simulates the restart using
 * NeqSim dynamic simulation</li>
 * <li>{@link neqsim.process.diagnostics.restart.RestartOptimiser} — Optimises ramp rates and
 * settling times to minimise MTTR</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 * @see neqsim.process.diagnostics
 */
package neqsim.process.diagnostics.restart;
