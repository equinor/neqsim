/**
 * Condition-Based Reliability Package.
 *
 * <p>
 * This package provides condition-based reliability monitoring and prognostics capabilities.
 * Instead of relying solely on generic failure rates (e.g., from OREDA), it integrates real-time
 * equipment condition data to provide dynamic reliability estimates.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.safety.risk.condition.ConditionBasedReliability} - Main class for
 * condition-based reliability monitoring</li>
 * </ul>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Multiple condition indicators (vibration, temperature, pressure, etc.)</li>
 * <li>Health index calculation from weighted indicators</li>
 * <li>Dynamic failure rate adjustment based on equipment health</li>
 * <li>Remaining Useful Life (RUL) estimation</li>
 * <li>Multiple degradation models (linear, exponential, Weibull)</li>
 * <li>Alarm and critical threshold monitoring</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * // Create model with base OREDA failure rate
 * ConditionBasedReliability pump =
 *     new ConditionBasedReliability("P-101", "Main Export Pump", 5e-5); // Base failure rate from
 *                                                                       // OREDA
 *
 * // Add condition indicators
 * pump.addVibrationIndicator("V1", "Drive End Bearing", 2.0, 4.0, 7.0); // mm/s RMS
 * pump.addTemperatureIndicator("T1", "Bearing Temperature", 45.0, 65.0, 80.0); // Celsius
 *
 * // Update with real-time data (e.g., from OPC-UA)
 * pump.updateIndicator("V1", 3.5); // Elevated vibration
 * pump.updateIndicator("T1", 52.0); // Normal temperature
 *
 * // Get health and reliability
 * double health = pump.getHealthIndex(); // 0-1
 * double adjustedRate = pump.getAdjustedFailureRate();
 * double pof30d = pump.getProbabilityOfFailure(720);
 * double rul = pump.getRemainingUsefulLife();
 *
 * // Generate report
 * System.out.println(pump.toReport());
 * </pre>
 *
 * <h2>Integration with Risk Simulation</h2>
 * <p>
 * The adjusted failure rates from condition-based models can replace generic OREDA rates in the
 * operational risk simulator for more accurate, real-time risk assessment.
 * </p>
 * 
 * <pre>
 * // Use condition-based rates in risk simulation
 * EquipmentFailureMode mode = new EquipmentFailureMode();
 * mode.setFailureRate(pumpCBR.getAdjustedFailureRate()); // Dynamic rate
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.safety.risk.OperationalRiskSimulator
 */
package neqsim.process.safety.risk.condition;
