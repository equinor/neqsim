/**
 * Probabilistic risk analysis framework for process safety.
 *
 * <p>
 * This package provides tools for quantitative risk assessment (QRA) including:
 * <ul>
 * <li>{@link neqsim.process.safety.risk.RiskEvent} - Individual risk events with frequency and
 * consequence</li>
 * <li>{@link neqsim.process.safety.risk.RiskModel} - Monte Carlo and deterministic risk
 * analysis</li>
 * <li>{@link neqsim.process.safety.risk.RiskResult} - Analysis results with F-N curve data</li>
 * <li>{@link neqsim.process.safety.risk.SensitivityResult} - Tornado diagram data for sensitivity
 * analysis</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * RiskModel model = new RiskModel("HP Separator Study");
 * model.addInitiatingEvent("Small Leak", 1e-3, ConsequenceCategory.MINOR);
 * model.addInitiatingEvent("Medium Leak", 1e-4, ConsequenceCategory.MODERATE);
 * RiskResult result = model.runMonteCarloAnalysis(10000);
 * System.out.println(result.getSummary());
 * </pre>
 *
 * @see neqsim.process.safety.envelope Safety envelope calculations
 * @see neqsim.process.safety.release Source term generation
 */
package neqsim.process.safety.risk;
