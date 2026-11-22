/**
 * Time-series replay of a NeqSim {@link neqsim.process.processmodel.ProcessSystem} or
 * {@link neqsim.process.processmodel.ProcessModel} driven by plant data.
 *
 * <p>
 * {@link neqsim.process.operations.replay.ProcessReplay} writes measured inputs through the
 * {@link neqsim.process.automation.ProcessAutomation} facade, runs the model with a convergence gate for every time
 * step, and records predicted outputs and residuals against measured tags. The
 * {@link neqsim.process.operations.replay.ReplayResult} summarises the run, computes window bias and RMS, baseline
 * statistics, standardised residuals and CUSUM change points, and exports CSV or JSON.
 * </p>
 */
package neqsim.process.operations.replay;
