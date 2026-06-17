/**
 * Equipment-level operational diagnostics and root cause analysis.
 *
 * <p>
 * This package integrates process simulation, multi-source reliability data, plant historian
 * time-series, and STID design conditions to diagnose equipment issues using a Bayesian-inspired
 * methodology:
 * </p>
 *
 * <ol>
 * <li><b>Prior</b> — OREDA failure mode frequencies set initial hypothesis probabilities</li>
 * <li><b>Likelihood</b> — historian data evidence updates hypothesis scores via trend analysis,
 * correlation, threshold exceedance, and rate-of-change detection</li>
 * <li><b>Verification</b> — process simulation confirms if a hypothesized failure reproduces
 * observed symptoms by cloning the process, applying perturbations, and comparing KPIs</li>
 * </ol>
 *
 * <p>
 * Main entry point is {@link neqsim.process.diagnostics.RootCauseAnalyzer}. Typical usage:
 * </p>
 *
 * <pre>
 * RootCauseAnalyzer rca = new RootCauseAnalyzer(processSystem, "Compressor-1");
 * rca.setSymptom(Symptom.HIGH_VIBRATION);
 * rca.setHistorianData(historianMap, timestamps);
 * RootCauseReport report = rca.analyze();
 * System.out.println(report.toTextReport());
 * </pre>
 *
 * <h2>Post-Trip Analysis</h2>
 *
 * <p>
 * For automated post-trip workflows, this package also provides:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.diagnostics.TripEvent} — immutable data class representing a detected
 * trip</li>
 * <li>{@link neqsim.process.diagnostics.TripEventDetector} — monitors process equipment parameters
 * and detects trip conditions</li>
 * <li>{@link neqsim.process.diagnostics.FailurePropagationTracer} — traces how a failure cascades
 * through the process</li>
 * <li>{@link neqsim.process.diagnostics.restart.RestartSequenceGenerator} — generates optimised
 * restart sequences after a trip</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.diagnostics;
