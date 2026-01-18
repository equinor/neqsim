/**
 * Field Development Planning utilities for NeqSim.
 *
 * <p>
 * This package provides tools for oil and gas field development planning, including:
 * <ul>
 * <li>{@link neqsim.process.util.fielddevelopment.ProductionProfile} - Decline curve modeling and
 * production forecasting with plateau rate support</li>
 * <li>{@link neqsim.process.util.fielddevelopment.WellScheduler} - Well intervention and workover
 * planning with availability tracking</li>
 * <li>{@link neqsim.process.util.fielddevelopment.FacilityCapacity} - Extended bottleneck analysis
 * with debottleneck option evaluation</li>
 * <li>{@link neqsim.process.util.fielddevelopment.SensitivityAnalysis} - Monte Carlo simulation for
 * uncertainty quantification</li>
 * </ul>
 *
 * <h2>Integration with NeqSim Process Simulation</h2>
 * <p>
 * These classes integrate with the existing NeqSim process simulation framework:
 * <ul>
 * <li>{@link neqsim.process.util.optimizer.ProductionOptimizer} provides the underlying
 * bottleneck analysis and optimization algorithms</li>
 * <li>{@link neqsim.process.equipment.reservoir.SimpleReservoir} and
 * {@link neqsim.process.equipment.reservoir.Well} provide reservoir and well models</li>
 * <li>{@link neqsim.process.processmodel.ProcessSystem} represents the surface facility</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create a production profile with decline curve
 * ProductionProfile profile = new ProductionProfile(facilityProcess);
 * ProductionProfile.DeclineParameters decline = new ProductionProfile.DeclineParameters(10000.0,
 *     0.15, ProductionProfile.DeclineType.EXPONENTIAL, "Sm3/day");
 * ProductionProfile.ProductionForecast forecast =
 *     profile.forecast(feedStream, decline, 8000.0, 3.0, 500.0, 20.0, 30.0);
 *
 * // Run Monte Carlo sensitivity analysis
 * SensitivityAnalysis sensitivity = new SensitivityAnalysis(facilityProcess);
 * sensitivity.addParameter(new SensitivityAnalysis.UncertainParameter("feedTemperature", 15.0,
 *     20.0, 25.0, SensitivityAnalysis.DistributionType.TRIANGULAR,
 *     (proc, val) -> feedStream.setTemperature(val, "C")));
 * SensitivityAnalysis.MonteCarloResult result =
 *     sensitivity.runMonteCarloOptimization(feedStream, 1000.0, 50000.0, "kg/hr",
 *         opt -> opt.getOptimalRate(), new SensitivityAnalysis.SensitivityConfig());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @since 3.0
 */
package neqsim.process.util.fielddevelopment;

