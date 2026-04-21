/**
 * Post-trip root cause analysis and restart optimisation for process systems.
 *
 * <p>
 * This package provides automated trip detection, hypothesis-based root cause analysis, failure
 * propagation tracing, and restart sequence generation with MTTR optimisation.
 * </p>
 *
 * <p>
 * Key classes:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.diagnostics.TripEventDetector} — Monitors a process system during
 * dynamic simulation and detects trip events (compressor trips, ESD activations, etc.)</li>
 * <li>{@link neqsim.process.diagnostics.RootCauseAnalyzer} — Orchestrates hypothesis evaluation,
 * failure propagation tracing, and report generation</li>
 * <li>{@link neqsim.process.diagnostics.RootCauseReport} — Comprehensive report with ranked
 * hypotheses, propagation chain, and recommended actions</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 * @see neqsim.process.diagnostics.restart
 */
package neqsim.process.diagnostics;
