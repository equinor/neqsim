/**
 * Dynamic risk simulation with transient modeling.
 *
 * <p>
 * This package extends the standard risk simulation with dynamic effects:
 * </p>
 * <ul>
 * <li>Startup/shutdown production losses</li>
 * <li>Ramp-up time after equipment restoration</li>
 * <li>Time-varying production profiles</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link DynamicRiskSimulator} - Monte Carlo with transient simulation</li>
 * <li>{@link ProductionProfile} - Time-varying production during failure</li>
 * <li>{@link DynamicRiskResult} - Results with transient statistics</li>
 * <li>{@link TransientLossStatistics} - Cumulative transient loss tracking</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * DynamicRiskSimulator simulator = new DynamicRiskSimulator(processSystem);
 * simulator.setRampUpTimeHours(2.0);
 * simulator.addEquipmentReliability("Compressor", 0.05, 24.0);
 * 
 * DynamicRiskResult result = simulator.runDynamicSimulation(1000, 365);
 * System.out.println("Transient loss fraction: " + result.getTransientLossPercent() + "%");
 * }
 * </pre>
 *
 * @see neqsim.process.safety.risk.OperationalRiskSimulator
 */
package neqsim.process.safety.risk.dynamic;
