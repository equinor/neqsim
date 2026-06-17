/**
 * Restart sequence generation and optimisation after process trips.
 *
 * <p>
 * This sub-package provides automated generation of restart sequences following equipment trips
 * or plant shutdowns. It integrates with the diagnostics package
 * ({@link neqsim.process.diagnostics.TripEventDetector},
 * {@link neqsim.process.diagnostics.FailurePropagationTracer}) and the process logic
 * infrastructure ({@link neqsim.process.logic.startup.StartupLogic}).
 * </p>
 *
 * <p>
 * Main entry point: {@link neqsim.process.diagnostics.restart.RestartSequenceGenerator}.
 * </p>
 *
 * @author NeqSim Development Team
 */
package neqsim.process.diagnostics.restart;
